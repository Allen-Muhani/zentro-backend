package com.grandtech.timetable.solver

import ai.timefold.solver.core.api.score.HardSoftScore
import ai.timefold.solver.core.api.score.stream.Constraint
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors
import ai.timefold.solver.core.api.score.stream.ConstraintFactory
import ai.timefold.solver.core.api.score.stream.ConstraintProvider
import ai.timefold.solver.core.api.score.stream.Joiners
import com.grandtech.model.SubjectType
import com.grandtech.model.Teacher
import com.grandtech.timetable.config.KenyaCurriculumConfig.Companion.CAS
import com.grandtech.timetable.domain.Lesson

/**
 * Defines all timetable constraints using Timefold Constraint Streams.
 *
 * Maps one-for-one to the constraints in the former CP-SAT solver. Unlike
 * CP-SAT, which returns a silent INFEASIBLE, Timefold always produces a
 * solution and exposes per-constraint violation counts via [ScoreManager],
 * letting admins see exactly what is broken (e.g. "HC2: teacher conflict –
 * 3 violation(s)").
 *
 * | ID   | Type | Method                                    |
 * |------|------|-------------------------------------------|
 * | HC1  | Hard | [hc1StreamSlotConflict]                   |
 * | HC2  | Hard | [hc2TeacherConflict]                      |
 * | HC4  | Hard | [hc4TeacherQualification]                 |
 * | HC5  | Hard | [hc5SingleTeacherPerClass]                |
 * | HC6a | Hard | [hc6WeeklyWorkloadLimit]                  |
 * | HC6b | Hard | [hc6DailyWorkloadLimit]                   |
 * | HC8  | Hard | [hc8DailySubjectCap]                      |
 * | HC9  | Hard | [hc9NoBreakSpanning]                      |
 * | HC10 | Hard | [hc10DoublePeriodRequired]                |
 * | HC11 | Hard | [hc11NoConsecutiveSameTeacherInStream]    |
 * | SC1  | Soft | [sc1CoreSubjectsInAfternoon]               |
 * | SC2  | Soft | [sc2CreativeArtsOutsideBreaks]            |
 * | SC3  | Soft | [sc3SubjectClustering]                    |
 *
 * HC3 (subject frequency) and HC7 (teacher unavailability) are omitted:
 * HC3 is guaranteed structurally (one [Lesson] per required occurrence) and
 * HC7 has no data model yet.
 */
class TimetableConstraintProvider : ConstraintProvider {

    override fun defineConstraints(factory: ConstraintFactory): Array<Constraint> = arrayOf(
        hc1StreamSlotConflict(factory),
        hc2TeacherConflict(factory),
        hc4TeacherQualification(factory),
        hc5SingleTeacherPerClass(factory),
        hc6WeeklyWorkloadLimit(factory),
        hc6DailyWorkloadLimit(factory),
        hc8DailySubjectCap(factory),
        hc9NoBreakSpanning(factory),
        hc10DoublePeriodRequired(factory),
        hc11NoConsecutiveSameTeacherInStream(factory),
        sc1CoreSubjectsInAfternoon(factory),
        sc2CreativeArtsOutsideBreaks(factory),
        sc3SubjectClustering(factory),
    )

    // ── Hard constraints ──────────────────────────────────────────────────────

    /**
     * HC1: No two lessons in the same stream may occupy the same (day, period)
     * slot. Penalises every conflicting pair once.
     */
    private fun hc1StreamSlotConflict(factory: ConstraintFactory): Constraint =
        factory.forEachUniquePair(
            Lesson::class.java,
            Joiners.equal { l: Lesson -> l.stream.id ?: "" },
            Joiners.equal { l: Lesson -> l.day },
            Joiners.equal { l: Lesson -> l.period },
        )
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("HC1: Stream slot conflict")

    /**
     * HC2: A teacher may not be scheduled in two different places at the same
     * (day, period) — they cannot teach two streams simultaneously.
     */
    private fun hc2TeacherConflict(factory: ConstraintFactory): Constraint =
        factory.forEachUniquePair(
            Lesson::class.java,
            Joiners.equal { l: Lesson -> l.teacher },
            Joiners.equal { l: Lesson -> l.day },
            Joiners.equal { l: Lesson -> l.period },
        )
            .filter { l1, _ -> l1.teacher != null }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("HC2: Teacher conflict")

    /**
     * HC4: A teacher may only be assigned to lessons for subjects they are
     * qualified to teach (i.e. the subject ID is in [Teacher.subjectIds]).
     */
    private fun hc4TeacherQualification(factory: ConstraintFactory): Constraint =
        factory.forEach(Lesson::class.java)
            .filter { lesson ->
                lesson.teacher != null &&
                    lesson.subject.id !in (lesson.teacher!!.subjectIds ?: emptyList<String>())
            }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("HC4: Teacher not qualified for subject")

    /**
     * HC5: All lessons for the same (stream, subject) must be taught by the
     * same teacher — no mid-week teacher switches for a single class.
     */
    private fun hc5SingleTeacherPerClass(factory: ConstraintFactory): Constraint =
        factory.forEachUniquePair(
            Lesson::class.java,
            Joiners.equal { l: Lesson -> l.stream.id ?: "" },
            Joiners.equal { l: Lesson -> l.subject.id },
        )
            .filter { l1, l2 ->
                l1.teacher != null && l2.teacher != null && l1.teacher != l2.teacher
            }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("HC5: Multiple teachers for same subject in stream")

    /**
     * HC6a: A teacher's total lessons across the full week must not exceed
     * their [Teacher.maxPeriodsPerWeek] (defaults to 23).
     * Penalises by the number of periods over the limit.
     */
    private fun hc6WeeklyWorkloadLimit(factory: ConstraintFactory): Constraint =
        factory.forEach(Lesson::class.java)
            .filter { it.teacher != null }
            .groupBy(
                { l: Lesson -> l.teacher },
                ConstraintCollectors.count(),
            )
            .filter { teacher: Teacher?, count: Int ->
                count > (teacher?.maxPeriodsPerWeek ?: 23)
            }
            .penalize(HardSoftScore.ONE_HARD) { teacher: Teacher?, count: Int ->
                count - (teacher?.maxPeriodsPerWeek ?: 23)
            }
            .asConstraint("HC6: Teacher weekly workload exceeded")

    /**
     * HC6b: A teacher's lessons on any single day must not exceed their
     * [Teacher.maxPeriodsPerDay] (defaults to 6).
     * Penalises by the number of periods over the daily limit.
     */
    private fun hc6DailyWorkloadLimit(factory: ConstraintFactory): Constraint =
        factory.forEach(Lesson::class.java)
            .filter { it.teacher != null && it.day != null }
            .groupBy(
                { l: Lesson -> l.teacher },
                { l: Lesson -> l.day },
                ConstraintCollectors.count(),
            )
            .filter { teacher: Teacher?, _: String?, count: Int ->
                count > (teacher?.maxPeriodsPerDay ?: 6)
            }
            .penalize(HardSoftScore.ONE_HARD) { teacher: Teacher?, _: String?, count: Int ->
                count - (teacher?.maxPeriodsPerDay ?: 6)
            }
            .asConstraint("HC6: Teacher daily workload exceeded")

    /**
     * HC8: A subject may appear at most [Subject.maxPeriodsPerDay] times in
     * a given stream on any single day, preventing one subject from monopolising
     * a teaching block.
     */
    private fun hc8DailySubjectCap(factory: ConstraintFactory): Constraint =
        factory.forEach(Lesson::class.java)
            .filter { it.day != null }
            .groupBy(
                { l: Lesson -> "${l.stream.id ?: ""}|${l.subject.id}|${l.day}" },
                ConstraintCollectors.toList(),
            )
            .filter { _: String, lessons: List<Lesson> ->
                lessons.size > lessons.first().subject.maxPeriodsPerDay
            }
            .penalize(HardSoftScore.ONE_HARD) { _: String, lessons: List<Lesson> ->
                lessons.size - lessons.first().subject.maxPeriodsPerDay
            }
            .asConstraint("HC8: Subject daily cap exceeded")

    /**
     * HC9: A subject may not appear on both sides of a break boundary within
     * the same stream on the same day — it must not span the break.
     *
     * Break boundary 1-based period pairs: (2,3), (4,5), (6,7).
     */
    private fun hc9NoBreakSpanning(factory: ConstraintFactory): Constraint {
        val breakPairs = setOf(2 to 3, 4 to 5, 6 to 7)
        return factory.forEachUniquePair(
            Lesson::class.java,
            Joiners.equal { l: Lesson -> l.stream.id ?: "" },
            Joiners.equal { l: Lesson -> l.subject.id },
            Joiners.equal { l: Lesson -> l.day },
        )
            .filter { l1, l2 ->
                val p1 = l1.period ?: return@filter false
                val p2 = l2.period ?: return@filter false
                (p1 to p2) in breakPairs || (p2 to p1) in breakPairs
            }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("HC9: Subject spans break boundary")
    }

    /**
     * HC10: Subjects that [Subject.requiresDoubledPeriod] must have at least
     * one valid back-to-back pair of occurrences (same subject, same stream,
     * same day, consecutive within a teaching block).
     *
     * Valid consecutive pairs (1-based): (1,2), (3,4), (5,6), (7,8).
     * Pairs crossing a break — (2,3), (4,5), (6,7) — are excluded.
     *
     * Penalises once per (stream, subject) group that has no valid double.
     */
    private fun hc10DoublePeriodRequired(factory: ConstraintFactory): Constraint {
        val validDoublePairs = setOf(1 to 2, 3 to 4, 5 to 6, 7 to 8)
        return factory.forEach(Lesson::class.java)
            .filter { it.subject.requiresDoubledPeriod }
            .groupBy(
                { l: Lesson -> "${l.stream.id ?: ""}|${l.subject.id}" },
                ConstraintCollectors.toList(),
            )
            .filter { _: String, lessons: List<Lesson> ->
                val noValidDouble = lessons.none { l1 ->
                    lessons.any { l2 ->
                        l1 !== l2 &&
                            l1.day != null && l1.day == l2.day &&
                            l1.period != null && l2.period != null &&
                            (minOf(l1.period!!, l2.period!!) to maxOf(l1.period!!, l2.period!!)) in validDoublePairs
                    }
                }
                noValidDouble
            }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("HC10: Required double period missing")
    }

    /**
     * HC11: A teacher may not appear in two consecutive within-block periods
     * for the same stream while teaching *different* subjects. Valid same-subject
     * back-to-back doubles are explicitly excluded.
     *
     * Only within-block pairs are checked: (1,2), (3,4), (5,6), (7,8).
     * Break-crossing pairs — (2,3), (4,5), (6,7) — are skipped because the
     * intervening break provides natural separation.
     */
    private fun hc11NoConsecutiveSameTeacherInStream(factory: ConstraintFactory): Constraint {
        val validPairs = setOf(1 to 2, 3 to 4, 5 to 6, 7 to 8)
        return factory.forEachUniquePair(
            Lesson::class.java,
            Joiners.equal { l: Lesson -> l.teacher },
            Joiners.equal { l: Lesson -> l.stream.id ?: "" },
            Joiners.equal { l: Lesson -> l.day },
        )
            .filter { l1, l2 ->
                l1.teacher != null &&
                    l1.period != null && l2.period != null &&
                    l1.subject.id != l2.subject.id &&
                    (minOf(l1.period!!, l2.period!!) to maxOf(l1.period!!, l2.period!!)) in validPairs
            }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("HC11: Teacher teaches different subjects consecutively in same stream")
    }

    // ── Soft constraints ──────────────────────────────────────────────────────

    /**
     * SC1 (weight 10 per slot): Core subjects (Language, Mathematics, Science)
     * placed in afternoon slots (P5–P8, 1-based ≥ 5) are penalised.
     * The solver prefers morning slots for cognitively demanding subjects.
     */
    private fun sc1CoreSubjectsInAfternoon(factory: ConstraintFactory): Constraint {
        val coreSubjectTypes =
            setOf(SubjectType.LANGUAGE, SubjectType.MATHEMATICS, SubjectType.SCIENCE)
        return factory.forEach(Lesson::class.java)
            .filter { lesson ->
                lesson.subject.type in coreSubjectTypes && (lesson.period ?: 0) >= 5
            }
            .penalize(HardSoftScore.ONE_SOFT) { _: Lesson -> 10 }
            .asConstraint("SC1: Core subject in afternoon")
    }

    /**
     * SC2 (weight 8 per slot): Creative Arts & Sports (CAS) lessons not placed
     * immediately before a break (1-based P2, P4, or P6) are penalised.
     * Activity-based lessons benefit from the natural movement break that follows.
     */
    private fun sc2CreativeArtsOutsideBreaks(factory: ConstraintFactory): Constraint {
        val beforeBreakPeriods = setOf(2, 4, 6)
        return factory.forEach(Lesson::class.java)
            .filter { lesson ->
                lesson.subject.id == CAS && (lesson.period ?: -1) !in beforeBreakPeriods
            }
            .penalize(HardSoftScore.ONE_SOFT) { _: Lesson -> 8 }
            .asConstraint("SC2: CAS not placed before break")
    }

    /**
     * SC3 (weight 3 per slot): A light penalty per slot for subjects with more
     * than one period per week. The solver naturally spreads occurrences across
     * the five days to minimise this cumulative penalty.
     */
    private fun sc3SubjectClustering(factory: ConstraintFactory): Constraint =
        factory.forEach(Lesson::class.java)
            .filter { it.subject.periodsPerWeek > 1 }
            .penalize(HardSoftScore.ONE_SOFT) { _: Lesson -> 3 }
            .asConstraint("SC3: Subject clustering")
}