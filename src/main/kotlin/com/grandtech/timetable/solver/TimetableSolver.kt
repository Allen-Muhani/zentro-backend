package com.grandtech.timetable.solver

import com.google.ortools.Loader
import com.google.ortools.sat.BoolVar
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverStatus
import com.google.ortools.sat.LinearExpr
import com.google.ortools.sat.LinearExprBuilder
import com.google.ortools.sat.Literal
import com.grandtech.model.Stream
import com.grandtech.model.Subject
import com.grandtech.model.SubjectType
import com.grandtech.model.Teacher
import com.grandtech.model.TimetableEntry
import com.grandtech.timetable.config.KenyaCurriculumConfig
import com.grandtech.timetable.config.KenyaCurriculumConfig.Companion.BREAK_AFTER_PERIODS
import com.grandtech.timetable.config.KenyaCurriculumConfig.Companion.CAS
import com.grandtech.timetable.config.KenyaCurriculumConfig.Companion.DAYS_PER_WEEK
import com.grandtech.timetable.config.KenyaCurriculumConfig.Companion.PERIODS_PER_DAY
import com.grandtech.timetable.config.KenyaCurriculumConfig.Companion.slotKey
import com.grandtech.timetable.model.SolverResult
import com.grandtech.timetable.model.SolverStatus
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * CP-SAT timetable solver for Kenya CBC Junior Secondary Schools.
 *
 * ## Decision variable
 * `schedule[teacherIdx][subjectIdx][streamIdx][dayIdx][periodIdx] = 1`
 * when the given teacher teaches the given subject to the given stream
 * on that day and period. All indices are 0-based internally; they are
 * converted to 1-based period numbers and human-readable day names only
 * when building [TimetableEntry] output.
 *
 * ## Constraints (see individual method KDocs for details)
 *
 * | ID   | Type | Method                             |
 * |------|------|------------------------------------|
 * | HC1  | Hard | [enforceSlotCoverage]              |
 * | HC2  | Hard | [enforceTeacherConflictFreedom]    |
 * | HC3  | Hard | [enforceSubjectFrequency]          |
 * | HC4  | Hard | [enforceTeacherQualification]      |
 * | HC5  | Hard | [enforceSingleTeacherPerClass]     |
 * | HC6  | Hard | [enforceWorkloadLimits]            |
 * | HC7  | Hard | [enforceTeacherUnavailability]     |
 * | HC8  | Hard | [enforceDailySubjectCap]           |
 * | HC9  | Hard | [enforceNoBreakSpanning]           |
 * | HC10 | Hard | [enforceDoublePeriodRequirement]   |
 * | HC11 | Hard | [enforceNoConsecutiveSameTeacherInStream] |
 * | SC1  | Soft | [penaliseCoreSubjectsInAfternoon]  |
 * | SC2  | Soft | [penaliseCreativeArtsOutsideBreaks]|
 * | SC3  | Soft | [penaliseSubjectClustering]        |
 *
 * Rooms are not modelled in this version.
 */
@ApplicationScoped
class TimetableSolver {

    /** Bell-schedule and subject-ID constants for Kenya CBC JSS. */
    @Inject
    lateinit var curriculumConfig: KenyaCurriculumConfig

    companion object {
        /**
         * 0-indexed consecutive-period pairs that may form a double lesson.
         *
         * Each pair represents two back-to-back periods within the same
         * teaching block. A double lesson is only valid when both periods
         * belong to the same uninterrupted block — no break may fall
         * between them.
         *
         * Valid pairs and the blocks they belong to:
         * - {0, 1} → P1–P2  (first morning block, before the first break)
         * - {2, 3} → P3–P4  (second morning block, between breaks)
         * - {4, 5} → P5–P6  (pre-lunch block, between second break and lunch)
         * - {6, 7} → P7–P8  (afternoon block, after lunch)
         *
         * Excluded pairs — these cross a break boundary:
         * - {1, 2}: P2→P3  crosses the first break  (09:40–10:00)
         * - {3, 4}: P4→P5  crosses the second break (11:20–11:35)
         * - {5, 6}: P6→P7  crosses lunch            (12:55–14:00)
         */
        private val VALID_DOUBLE_PERIOD_PAIRS = arrayOf(
            intArrayOf(0, 1),
            intArrayOf(2, 3),
            intArrayOf(4, 5),
            intArrayOf(6, 7),
        )

        /**
         * 0-indexed period slots that fall immediately before a break.
         * Used by [penaliseCreativeArtsOutsideBreaks] (SC2) to reward
         * CAS placed at these positions (P2 idx 1, P4 idx 3, P6 idx 5).
         */
        private val BEFORE_BREAK_PERIOD_INDICES = setOf(1, 3, 5)

        private val DAY_NAMES =
            listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")

        init {
            Loader.loadNativeLibraries()
            Log.info("OR-Tools native libraries loaded.")
        }
    }

    // ── Public entry point ───────────────────────────────────────────────────

    /**
     * Builds and solves the CP-SAT model for the given school, then
     * returns a [SolverResult].
     *
     * The model is constructed fresh for each call. PPI is excluded from
     * the CP-SAT model and will be handled as fixed entries in a future
     * milestone.
     *
     * @param streams           all streams belonging to the school
     * @param teachers          all teachers with their qualified subject
     *                          IDs and workload limits
     * @param subjects          all CBC subjects; PPI is filtered out
     *                          before modelling
     * @param timeLimitSeconds  maximum wall-clock time given to the solver
     * @return [SolverResult] with OPTIMAL or FEASIBLE on success,
     *         or INFEASIBLE/UNKNOWN on failure
     */
    fun solve(
        streams: List<Stream>,
        teachers: List<Teacher>,
        subjects: List<Subject>,
        timeLimitSeconds: Int,
    ): SolverResult {
        val wallStart = System.currentTimeMillis()

        val teachingSubjects = subjects.filter { !it.isPpiFixed }

        val numTeachers = teachers.size
        val numSubjects = teachingSubjects.size
        val numStreams   = streams.size
        val numDays      = DAYS_PER_WEEK
        val numPeriods   = PERIODS_PER_DAY

        Log.infof(
            "Solving: %d teachers, %d subjects, %d streams.",
            numTeachers, numSubjects, numStreams,
        )

        // Index map used by SC2 to look up a subject's array position by its ID.
        val subjectIndexById = teachingSubjects
            .mapIndexed { idx, subject -> subject.id to idx }
            .toMap()

        val model      = CpModel()
        val schedule   = createScheduleVars(
            model, numTeachers, numSubjects, numStreams, numDays, numPeriods,
        )
        val isAssigned = createAssignmentVars(
            model, numTeachers, numSubjects, numStreams,
        )

        enforceSlotCoverage(
            model, schedule, numTeachers, numSubjects, numStreams, numDays, numPeriods,
        )
        enforceTeacherConflictFreedom(
            model, schedule, numTeachers, numSubjects, numStreams, numDays, numPeriods,
        )
        enforceSubjectFrequency(
            model, schedule, numTeachers, numSubjects, numStreams,
            numDays, numPeriods, teachingSubjects,
        )
        enforceTeacherQualification(
            model, schedule, isAssigned, numTeachers, numSubjects, numStreams,
            numDays, numPeriods, teachers, teachingSubjects,
        )
        enforceSingleTeacherPerClass(
            model, schedule, isAssigned, numTeachers, numSubjects, numStreams,
            numDays, numPeriods, teachers, teachingSubjects,
        )
        enforceWorkloadLimits(
            model, schedule, numTeachers, numSubjects, numStreams,
            numDays, numPeriods, teachers,
        )
        enforceTeacherUnavailability(
            model, schedule, numTeachers, numSubjects, numStreams,
            numDays, numPeriods, teachers,
        )
        enforceDailySubjectCap(
            model, schedule, numTeachers, numSubjects, numStreams,
            numDays, numPeriods, teachingSubjects,
        )
        enforceNoBreakSpanning(
            model, schedule, numTeachers, numSubjects, numStreams, numDays, numPeriods,
        )
        enforceDoublePeriodRequirement(
            model, schedule, numTeachers, numSubjects, numStreams,
            numDays, numPeriods, teachingSubjects,
        )
        enforceNoConsecutiveSameTeacherInStream(
            model, schedule, numTeachers, numSubjects, numStreams, numDays,
        )

        val objective = LinearExpr.newBuilder()
        penaliseCoreSubjectsInAfternoon(
            objective, schedule, numTeachers, numSubjects, numStreams,
            numDays, numPeriods, teachingSubjects,
        )
        penaliseCreativeArtsOutsideBreaks(
            objective, schedule, numTeachers, numSubjects, numStreams,
            numDays, numPeriods, subjectIndexById,
        )
        penaliseSubjectClustering(
            objective, schedule, numTeachers, numSubjects, numStreams,
            numDays, numPeriods, teachingSubjects,
        )
        model.minimize(objective.build())

        val solver = CpSolver()
        solver.parameters.maxTimeInSeconds  = timeLimitSeconds.toDouble()
        solver.parameters.numWorkers        = 4
        solver.parameters.logSearchProgress = false

        Log.infof("Starting CP-SAT (limit=%ds)...", timeLimitSeconds)
        val solverStatus = solver.solve(model)
        val wallMs = System.currentTimeMillis() - wallStart
        Log.infof(
            "Solver done in %dms: status=%s obj=%.0f.",
            wallMs, solverStatus, solver.objectiveValue(),
        )

        val isInfeasible = solverStatus == CpSolverStatus.INFEASIBLE ||
            solverStatus == CpSolverStatus.MODEL_INVALID
        if (isInfeasible) {
            return SolverResult.infeasible(
                "CP-SAT status: ${solverStatus.name}. " +
                    "Check teacher qualifications and subject coverage.",
            )
        }
        if (solverStatus == CpSolverStatus.UNKNOWN) return SolverResult.unknown(wallMs)

        val entries = extractEntries(
            solver, schedule, numTeachers, numSubjects, numStreams, numDays, numPeriods,
            teachers, teachingSubjects, streams,
        )
        markDoublePeriods(entries)

        val finalStatus = if (solverStatus == CpSolverStatus.OPTIMAL) {
            SolverStatus.OPTIMAL
        } else {
            SolverStatus.FEASIBLE
        }
        return SolverResult(
            status         = finalStatus,
            entries        = entries,
            wallTimeMs     = wallMs,
            objectiveValue = solver.objectiveValue().toLong(),
        )
    }

    // ── Variable creation ────────────────────────────────────────────────────

    /**
     * Creates the primary 5-dimensional Boolean decision variable array.
     *
     * `schedule[t][s][g][d][p] = 1` means teacher `t` teaches subject `s`
     * to stream `g` on day `d` at period `p` (all 0-based).
     */
    private fun createScheduleVars(
        model: CpModel,
        numTeachers: Int,
        numSubjects: Int,
        numStreams: Int,
        numDays: Int,
        numPeriods: Int,
    ): Array<Array<Array<Array<Array<BoolVar>>>>> =
        Array(numTeachers) { t ->
            Array(numSubjects) { s ->
                Array(numStreams) { g ->
                    Array(numDays) { d ->
                        Array(numPeriods) { p ->
                            model.newBoolVar("schedule_t${t}_s${s}_g${g}_d${d}_p$p")
                        }
                    }
                }
            }
        }

    /**
     * Creates the 3-dimensional teacher-assignment variable array.
     *
     * `isAssigned[t][s][g] = 1` when teacher `t` is the sole designated
     * teacher for subject `s` in stream `g` for the entire week (HC5).
     */
    private fun createAssignmentVars(
        model: CpModel,
        numTeachers: Int,
        numSubjects: Int,
        numStreams: Int,
    ): Array<Array<Array<BoolVar>>> =
        Array(numTeachers) { t ->
            Array(numSubjects) { s ->
                Array(numStreams) { g ->
                    model.newBoolVar("isAssigned_t${t}_s${s}_g$g")
                }
            }
        }

    // ── Hard constraints ─────────────────────────────────────────────────────

    /**
     * HC1: Every (stream, day, period) slot must be filled by exactly one
     * (teacher, subject) combination — no gaps and no double-bookings per slot.
     */
    private fun enforceSlotCoverage(
        model: CpModel,
        schedule: Array<Array<Array<Array<Array<BoolVar>>>>>,
        numTeachers: Int,
        numSubjects: Int,
        numStreams: Int,
        numDays: Int,
        numPeriods: Int,
    ) {
        for (g in 0 until numStreams) {
            for (d in 0 until numDays) {
                for (p in 0 until numPeriods) {
                    val allCombinationsForSlot = mutableListOf<Literal>()
                    for (t in 0 until numTeachers)
                        for (s in 0 until numSubjects)
                            allCombinationsForSlot.add(schedule[t][s][g][d][p])
                    model.addExactlyOne(allCombinationsForSlot)
                }
            }
        }
    }

    /**
     * HC2: A teacher can only be in one place at a time — across all
     * streams, a teacher may appear in at most one slot per (day, period).
     */
    private fun enforceTeacherConflictFreedom(
        model: CpModel,
        schedule: Array<Array<Array<Array<Array<BoolVar>>>>>,
        numTeachers: Int,
        numSubjects: Int,
        numStreams: Int,
        numDays: Int,
        numPeriods: Int,
    ) {
        for (t in 0 until numTeachers) {
            for (d in 0 until numDays) {
                for (p in 0 until numPeriods) {
                    val teacherSlotsThisPeriod = mutableListOf<Literal>()
                    for (s in 0 until numSubjects)
                        for (g in 0 until numStreams)
                            teacherSlotsThisPeriod.add(schedule[t][s][g][d][p])
                    model.addAtMostOne(teacherSlotsThisPeriod)
                }
            }
        }
    }

    /**
     * HC3: Each subject must appear exactly [Subject.periodsPerWeek] times
     * in every stream across the full week.
     */
    private fun enforceSubjectFrequency(
        model: CpModel,
        schedule: Array<Array<Array<Array<Array<BoolVar>>>>>,
        numTeachers: Int,
        numSubjects: Int,
        numStreams: Int,
        numDays: Int,
        numPeriods: Int,
        subjects: List<Subject>,
    ) {
        for (g in 0 until numStreams) {
            for (s in 0 until numSubjects) {
                val weeklySlotSum = LinearExpr.newBuilder()
                for (t in 0 until numTeachers)
                    for (d in 0 until numDays)
                        for (p in 0 until numPeriods)
                            weeklySlotSum.add(schedule[t][s][g][d][p])
                model.addEquality(
                    weeklySlotSum.build(), subjects[s].periodsPerWeek.toLong(),
                )
            }
        }
    }

    /**
     * HC4: A teacher may only appear in schedule slots for subjects they
     * are qualified to teach. All (teacher, subject) pairs where the
     * teacher lacks the qualification are fixed to zero in both the
     * schedule and the assignment variables, shrinking the search space.
     */
    private fun enforceTeacherQualification(
        model: CpModel,
        schedule: Array<Array<Array<Array<Array<BoolVar>>>>>,
        isAssigned: Array<Array<Array<BoolVar>>>,
        numTeachers: Int,
        numSubjects: Int,
        numStreams: Int,
        numDays: Int,
        numPeriods: Int,
        teachers: List<Teacher>,
        subjects: List<Subject>,
    ) {
        for (t in 0 until numTeachers) {
            val qualifiedSubjectIds = teachers[t].subjectIds ?: emptyList()
            for (s in 0 until numSubjects) {
                if (subjects[s].id !in qualifiedSubjectIds) {
                    for (g in 0 until numStreams) {
                        model.addEquality(isAssigned[t][s][g], 0)
                        for (d in 0 until numDays)
                            for (p in 0 until numPeriods)
                                model.addEquality(schedule[t][s][g][d][p], 0)
                    }
                }
            }
        }
    }

    /**
     * HC5: Exactly one teacher is assigned to each (subject, stream) pair
     * for the full week — no mid-week teacher switches.
     *
     * Enforced in two parts:
     * 1. `addExactlyOne` over the qualified `isAssigned[t][s][g]`
     *    variables selects one teacher.
     * 2. A linking constraint ensures a teacher's total schedule slots
     *    for (s, g) equals `periodsPerWeek × isAssigned[t][s][g]` —
     *    zero for all non-assigned teachers.
     */
    private fun enforceSingleTeacherPerClass(
        model: CpModel,
        schedule: Array<Array<Array<Array<Array<BoolVar>>>>>,
        isAssigned: Array<Array<Array<BoolVar>>>,
        numTeachers: Int,
        numSubjects: Int,
        numStreams: Int,
        numDays: Int,
        numPeriods: Int,
        teachers: List<Teacher>,
        subjects: List<Subject>,
    ) {
        for (s in 0 until numSubjects) {
            val requiredPeriods = subjects[s].periodsPerWeek
            for (g in 0 until numStreams) {
                val qualifiedAssignmentVars = mutableListOf<Literal>()
                for (t in 0 until numTeachers) {
                    val qualifiedSubjectIds = teachers[t].subjectIds ?: emptyList()
                    if (subjects[s].id in qualifiedSubjectIds) {
                        qualifiedAssignmentVars.add(isAssigned[t][s][g])
                    }
                }
                if (qualifiedAssignmentVars.isNotEmpty()) {
                    model.addExactlyOne(qualifiedAssignmentVars)
                }

                // Each teacher's weekly slots for (s,g) must equal
                // requiredPeriods × isAssigned[t][s][g].
                for (t in 0 until numTeachers) {
                    val teacherWeeklySlots = LinearExpr.newBuilder()
                    for (d in 0 until numDays)
                        for (p in 0 until numPeriods)
                            teacherWeeklySlots.add(schedule[t][s][g][d][p])
                    teacherWeeklySlots.addTerm(
                        isAssigned[t][s][g], -requiredPeriods.toLong(),
                    )
                    model.addEquality(teacherWeeklySlots.build(), 0)
                }
            }
        }
    }

    /**
     * HC6: Each teacher's total teaching load must not exceed their
     * configured limits. Defaults when no explicit limits are set:
     * - Weekly cap: 23 periods (KICD recommendation for JSS)
     * - Daily cap:  6 periods
     */
    private fun enforceWorkloadLimits(
        model: CpModel,
        schedule: Array<Array<Array<Array<Array<BoolVar>>>>>,
        numTeachers: Int,
        numSubjects: Int,
        numStreams: Int,
        numDays: Int,
        numPeriods: Int,
        teachers: List<Teacher>,
    ) {
        for (t in 0 until numTeachers) {
            val maxWeeklyPeriods = teachers[t].maxPeriodsPerWeek ?: 23
            val maxDailyPeriods  = teachers[t].maxPeriodsPerDay  ?: 6

            val weeklyWorkload = LinearExpr.newBuilder()
            for (s in 0 until numSubjects)
                for (g in 0 until numStreams)
                    for (d in 0 until numDays)
                        for (p in 0 until numPeriods)
                            weeklyWorkload.add(schedule[t][s][g][d][p])
            model.addLinearConstraint(
                weeklyWorkload.build(), 0, maxWeeklyPeriods.toLong(),
            )

            for (d in 0 until numDays) {
                val dailyWorkload = LinearExpr.newBuilder()
                for (s in 0 until numSubjects)
                    for (g in 0 until numStreams)
                        for (p in 0 until numPeriods)
                            dailyWorkload.add(schedule[t][s][g][d][p])
                model.addLinearConstraint(
                    dailyWorkload.build(), 0, maxDailyPeriods.toLong(),
                )
            }
        }
    }

    /**
     * HC7: Teacher unavailability — no-op for pre-MVP.
     * The teacher unavailability feature does not yet have an API or
     * graph data. This placeholder will be wired up in a future milestone.
     */
    @Suppress("UnusedParameter")
    private fun enforceTeacherUnavailability(
        model: CpModel,
        schedule: Array<Array<Array<Array<Array<BoolVar>>>>>,
        numTeachers: Int,
        numSubjects: Int,
        numStreams: Int,
        numDays: Int,
        numPeriods: Int,
        teachers: List<Teacher>,
    ) {
        // No-op: unavailability data not yet managed via the API.
    }

    /**
     * HC8: A subject may appear at most [Subject.maxPeriodsPerDay] times
     * in a given stream on any single day, preventing one subject from
     * filling an entire block.
     */
    private fun enforceDailySubjectCap(
        model: CpModel,
        schedule: Array<Array<Array<Array<Array<BoolVar>>>>>,
        numTeachers: Int,
        numSubjects: Int,
        numStreams: Int,
        numDays: Int,
        numPeriods: Int,
        subjects: List<Subject>,
    ) {
        for (g in 0 until numStreams) {
            for (s in 0 until numSubjects) {
                val dailyCap = subjects[s].maxPeriodsPerDay
                for (d in 0 until numDays) {
                    val dailyOccurrences = LinearExpr.newBuilder()
                    for (t in 0 until numTeachers)
                        for (p in 0 until numPeriods)
                            dailyOccurrences.add(schedule[t][s][g][d][p])
                    model.addLinearConstraint(
                        dailyOccurrences.build(), 0, dailyCap.toLong(),
                    )
                }
            }
        }
    }

    /**
     * HC9: No subject may span a break boundary within the same stream on
     * the same day. If a subject appears in the period immediately before
     * a break, it must not also appear in the period immediately after.
     *
     * The sum of all teacher variables for (s, g, d) on either side of
     * a boundary is capped at 1.
     *
     * Break boundaries (0-indexed period pairs):
     * - {1, 2}: first break  — between P2 and P3
     * - {3, 4}: second break — between P4 and P5
     * - {5, 6}: lunch break  — between P6 and P7
     */
    private fun enforceNoBreakSpanning(
        model: CpModel,
        schedule: Array<Array<Array<Array<Array<BoolVar>>>>>,
        numTeachers: Int,
        numSubjects: Int,
        numStreams: Int,
        numDays: Int,
        numPeriods: Int,
    ) {
        val breakBoundaryPairs =
            arrayOf(intArrayOf(1, 2), intArrayOf(3, 4), intArrayOf(5, 6))
        for (g in 0 until numStreams) {
            for (s in 0 until numSubjects) {
                for (d in 0 until numDays) {
                    for ((periodBeforeBreak, periodAfterBreak) in breakBoundaryPairs) {
                        val breakSpanSum = LinearExpr.newBuilder()
                        for (t in 0 until numTeachers) {
                            breakSpanSum.add(schedule[t][s][g][d][periodBeforeBreak])
                            breakSpanSum.add(schedule[t][s][g][d][periodAfterBreak])
                        }
                        // At most one side of each break boundary may be occupied.
                        model.addLinearConstraint(breakSpanSum.build(), 0, 1)
                    }
                }
            }
        }
    }

    /**
     * HC10: Subjects that [Subject.requiresDoubledPeriod] must have at
     * least one valid double-lesson slot somewhere in the week — two
     * consecutive periods of the same subject in the same stream, drawn
     * from [VALID_DOUBLE_PERIOD_PAIRS].
     *
     * Auxiliary variables per candidate pair (s, g, d, firstPeriod):
     * - `periodOccupied1` / `periodOccupied2`: whether any teacher fills
     *   this subject at each slot
     * - `isDoublePeriodAt`: true iff both consecutive slots are occupied
     *   by this subject
     */
    private fun enforceDoublePeriodRequirement(
        model: CpModel,
        schedule: Array<Array<Array<Array<Array<BoolVar>>>>>,
        numTeachers: Int,
        numSubjects: Int,
        numStreams: Int,
        numDays: Int,
        numPeriods: Int,
        subjects: List<Subject>,
    ) {
        for (g in 0 until numStreams) {
            for (s in 0 until numSubjects) {
                if (!subjects[s].requiresDoubledPeriod) continue
                val validDoubleOptions = mutableListOf<Literal>()

                for (d in 0 until numDays) {
                    for ((firstPeriod, secondPeriod) in VALID_DOUBLE_PERIOD_PAIRS) {
                        val tag = "s${s}_g${g}_d${d}"

                        val periodOccupied1 =
                            model.newBoolVar("occ1_${tag}_p$firstPeriod")
                        val periodOccupied2 =
                            model.newBoolVar("occ2_${tag}_p$secondPeriod")

                        // Each occupancy var = 1 iff any teacher teaches here.
                        val slotSum1 = LinearExpr.newBuilder()
                        val slotSum2 = LinearExpr.newBuilder()
                        for (t in 0 until numTeachers) {
                            slotSum1.add(schedule[t][s][g][d][firstPeriod])
                            slotSum2.add(schedule[t][s][g][d][secondPeriod])
                        }
                        slotSum1.addTerm(periodOccupied1, -1)
                        slotSum2.addTerm(periodOccupied2, -1)
                        model.addEquality(slotSum1.build(), 0)
                        model.addEquality(slotSum2.build(), 0)

                        // isDoublePeriodAt = 1 iff both consecutive slots occupied.
                        val isDoublePeriodAt =
                            model.newBoolVar("dbl_${tag}_p$firstPeriod")
                        model.addImplication(isDoublePeriodAt, periodOccupied1)
                        model.addImplication(isDoublePeriodAt, periodOccupied2)
                        model.addBoolOr(
                            arrayOf<Literal>(
                                periodOccupied1.not(),
                                periodOccupied2.not(),
                                isDoublePeriodAt,
                            ),
                        )
                        validDoubleOptions.add(isDoublePeriodAt)
                    }
                }

                // At least one valid double must exist somewhere in the week.
                if (validDoubleOptions.isNotEmpty()) model.addBoolOr(validDoubleOptions)
            }
        }
    }

    /**
     * HC11: A teacher may not appear in two consecutive periods for the same
     * stream unless the two periods form a valid double lesson (identical
     * subject back-to-back). Teaching different subjects to the same class in
     * adjacent slots — e.g. Math then Science with no other teacher in between
     * — is disallowed regardless of how many subjects the teacher is qualified
     * for or has been pre-assigned to.
     *
     * Only within-block consecutive pairs are checked, matching the valid
     * double-period pairs defined in [VALID_DOUBLE_PERIOD_PAIRS]:
     * `(P1,P2)`, `(P3,P4)`, `(P5,P6)`, `(P7,P8)`. Pairs that cross a break
     * boundary are excluded because the intervening break provides natural
     * separation between lessons.
     *
     * Formal constraint — for every teacher `t`, stream `g`, day `d`, and
     * each valid consecutive pair `(p, p+1)`:
     * ```
     * ∀ s1 ≠ s2:  schedule[t][s1][g][d][p] + schedule[t][s2][g][d][p+1] ≤ 1
     * ```
     * Pairs where `s1 = s2` are deliberately excluded: those represent valid
     * double lessons and are independently enforced by
     * [enforceDoublePeriodRequirement] (HC10).
     */
    private fun enforceNoConsecutiveSameTeacherInStream(
        model: CpModel,
        schedule: Array<Array<Array<Array<Array<BoolVar>>>>>,
        numTeachers: Int,
        numSubjects: Int,
        numStreams: Int,
        numDays: Int,
    ) {
        for (t in 0 until numTeachers) {
            for (g in 0 until numStreams) {
                for (d in 0 until numDays) {
                    for ((p, p1) in VALID_DOUBLE_PERIOD_PAIRS) {
                        for (s1 in 0 until numSubjects) {
                            for (s2 in 0 until numSubjects) {
                                if (s1 == s2) continue
                                val consecutiveSum = LinearExpr.newBuilder()
                                    .add(schedule[t][s1][g][d][p])
                                    .add(schedule[t][s2][g][d][p1])
                                model.addLinearConstraint(consecutiveSum.build(), 0, 1)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Soft constraints ─────────────────────────────────────────────────────

    /**
     * SC1 (penalty = 10 per slot): Discourages core subjects (language,
     * mathematics, science) from being placed in afternoon slots
     * (P5–P8, 0-indexed 4–7). The solver minimises the objective, so
     * it naturally pushes these subjects into the morning.
     */
    private fun penaliseCoreSubjectsInAfternoon(
        objective: LinearExprBuilder,
        schedule: Array<Array<Array<Array<Array<BoolVar>>>>>,
        numTeachers: Int,
        numSubjects: Int,
        numStreams: Int,
        numDays: Int,
        numPeriods: Int,
        subjects: List<Subject>,
    ) {
        val coreSubjectTypes =
            setOf(SubjectType.LANGUAGE, SubjectType.MATHEMATICS, SubjectType.SCIENCE)
        for (s in 0 until numSubjects) {
            if (subjects[s].type !in coreSubjectTypes) continue
            for (g in 0 until numStreams) {
                for (d in 0 until numDays) {
                    for (p in 4 until numPeriods) { // 0-indexed P5–P8 are afternoon
                        for (t in 0 until numTeachers) {
                            objective.addTerm(schedule[t][s][g][d][p], 10)
                        }
                    }
                }
            }
        }
    }

    /**
     * SC2 (penalty = 8 per slot): Discourages Creative Arts & Sports
     * (CAS) from being placed at any slot other than
     * [BEFORE_BREAK_PERIOD_INDICES]. The activity-based nature of CAS
     * benefits from a natural movement break immediately following the
     * lesson.
     */
    private fun penaliseCreativeArtsOutsideBreaks(
        objective: LinearExprBuilder,
        schedule: Array<Array<Array<Array<Array<BoolVar>>>>>,
        numTeachers: Int,
        numSubjects: Int,
        numStreams: Int,
        numDays: Int,
        numPeriods: Int,
        subjectIndexById: Map<String, Int>,
    ) {
        val creativeArtsIdx = subjectIndexById[CAS] ?: return
        for (g in 0 until numStreams) {
            for (d in 0 until numDays) {
                for (p in 0 until numPeriods) {
                    if (p !in BEFORE_BREAK_PERIOD_INDICES) {
                        for (t in 0 until numTeachers) {
                            objective.addTerm(schedule[t][creativeArtsIdx][g][d][p], 8)
                        }
                    }
                }
            }
        }
    }

    /**
     * SC3 (penalty = 3 per slot): Discourages day-clustering by applying
     * a light penalty to every scheduled slot for subjects with more than
     * one period per week. The solver then favours spreading subject
     * occurrences evenly across the five days.
     */
    private fun penaliseSubjectClustering(
        objective: LinearExprBuilder,
        schedule: Array<Array<Array<Array<Array<BoolVar>>>>>,
        numTeachers: Int,
        numSubjects: Int,
        numStreams: Int,
        numDays: Int,
        numPeriods: Int,
        subjects: List<Subject>,
    ) {
        for (s in 0 until numSubjects) {
            if (subjects[s].periodsPerWeek <= 1) continue
            for (g in 0 until numStreams) {
                for (d in 0 until numDays) {
                    for (p in 0 until numPeriods) {
                        for (t in 0 until numTeachers) {
                            objective.addTerm(schedule[t][s][g][d][p], 3)
                        }
                    }
                }
            }
        }
    }

    // ── Solution extraction ──────────────────────────────────────────────────

    /**
     * Reads the solved Boolean values from the CP-SAT solver and converts
     * them into a list of [TimetableEntry] objects with human-readable day
     * names and real clock times.
     */
    private fun extractEntries(
        solver: CpSolver,
        schedule: Array<Array<Array<Array<Array<BoolVar>>>>>,
        numTeachers: Int,
        numSubjects: Int,
        numStreams: Int,
        numDays: Int,
        numPeriods: Int,
        teachers: List<Teacher>,
        subjects: List<Subject>,
        streams: List<Stream>,
    ): MutableList<TimetableEntry> {
        val weeklySlotMap = curriculumConfig.getWeeklySlotMap()
        val entries = mutableListOf<TimetableEntry>()

        for (t in 0 until numTeachers) {
            for (s in 0 until numSubjects) {
                for (g in 0 until numStreams) {
                    for (d in 0 until numDays) {
                        for (p in 0 until numPeriods) {
                            if (!solver.booleanValue(schedule[t][s][g][d][p])) continue
                            val period1Based = p + 1
                            val (startTime, endTime) =
                                weeklySlotMap[slotKey(d, period1Based)] ?: ("" to "")
                            entries.add(
                                TimetableEntry(
                                    streamId  = streams[g].id,
                                    subjectId = subjects[s].id,
                                    teacherId = teachers[t].id,
                                    day       = DAY_NAMES[d],
                                    period    = period1Based,
                                    startTime = startTime,
                                    endTime   = endTime,
                                ),
                            )
                        }
                    }
                }
            }
        }
        return entries
    }

    /**
     * Flags entries that form valid double-lesson pairs by setting
     * [TimetableEntry.isDoubleStart] on the first entry and
     * [TimetableEntry.isDoubleContinuation] on the second.
     *
     * A pair qualifies when:
     * - the two entries are consecutive periods,
     * - they teach the same subject in the same stream, and
     * - the first period does not end at a break boundary.
     */
    private fun markDoublePeriods(entries: MutableList<TimetableEntry>) {
        val entriesByStreamAndDay = entries.groupBy { "${it.streamId}_${it.day}" }
        val doubleStartSet        = mutableSetOf<Int>()
        val doubleContinuationSet = mutableSetOf<Int>()

        for (dayEntries in entriesByStreamAndDay.values) {
            val sortedByPeriod = dayEntries.sortedBy { it.period ?: 0 }
            for (i in 0 until sortedByPeriod.size - 1) {
                val currentEntry = sortedByPeriod[i]
                val nextEntry    = sortedByPeriod[i + 1]

                val isConsecutive =
                    (nextEntry.period ?: 0) == (currentEntry.period ?: 0) + 1
                val isSameSubject     = currentEntry.subjectId == nextEntry.subjectId
                val doesNotCrossBreak = !isBreakBoundary(currentEntry.period ?: 0)

                if (isConsecutive && isSameSubject && doesNotCrossBreak) {
                    doubleStartSet.add(System.identityHashCode(currentEntry))
                    doubleContinuationSet.add(System.identityHashCode(nextEntry))
                }
            }
        }

        val flaggedEntries = entries.map { entry ->
            when {
                System.identityHashCode(entry) in doubleStartSet ->
                    entry.copy(isDoubleStart = true)
                System.identityHashCode(entry) in doubleContinuationSet ->
                    entry.copy(isDoubleContinuation = true)
                else -> entry
            }
        }
        entries.clear()
        entries.addAll(flaggedEntries)
    }

    /**
     * Returns true if [period1Based] is the last period before a break,
     * meaning a lesson pair spanning into the next period would cross a
     * break boundary. Delegates to [BREAK_AFTER_PERIODS].
     */
    private fun isBreakBoundary(period1Based: Int) = period1Based in BREAK_AFTER_PERIODS
}