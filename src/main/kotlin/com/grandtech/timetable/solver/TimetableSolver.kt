package com.grandtech.timetable.solver

import ai.timefold.solver.core.api.score.HardSoftScore
import ai.timefold.solver.core.api.solver.SolutionManager
import ai.timefold.solver.core.api.solver.SolverFactory
import ai.timefold.solver.core.config.solver.SolverConfig
import com.grandtech.model.Stream
import com.grandtech.model.Subject
import com.grandtech.model.Teacher
import com.grandtech.model.TimetableEntry
import com.grandtech.timetable.config.KenyaCurriculumConfig
import com.grandtech.timetable.config.KenyaCurriculumConfig.Companion.BREAK_AFTER_PERIODS
import com.grandtech.timetable.config.KenyaCurriculumConfig.Companion.slotKey
import com.grandtech.timetable.domain.Lesson
import com.grandtech.timetable.domain.Timetable
import com.grandtech.timetable.model.SolverResult
import com.grandtech.timetable.model.SolverStatus
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.time.Duration

/**
 * Timefold-based timetable solver for Kenya CBC Junior Secondary Schools.
 *
 * Replaces the former OR-Tools CP-SAT solver. The key improvement is that
 * Timefold **always** returns a solution — even when hard constraints cannot
 * be fully satisfied — and exposes per-constraint violation counts via
 * [ScoreManager]. Admins receive actionable messages such as
 * *"HC2: Teacher conflict – 3 violation(s)"* instead of a silent INFEASIBLE.
 *
 * The solver models each required teaching occurrence as a [Lesson] planning
 * entity. All constraints are defined in [TimetableConstraintProvider].
 *
 * ## Constraints
 *
 * | ID   | Type | Constraint                                         |
 * |------|------|----------------------------------------------------|
 * | HC1  | Hard | No two lessons in the same stream share a slot     |
 * | HC2  | Hard | A teacher cannot be in two places simultaneously   |
 * | HC4  | Hard | Teachers only teach qualified subjects              |
 * | HC5  | Hard | One teacher per (subject, stream) pair all week     |
 * | HC6  | Hard | Teacher weekly and daily period limits              |
 * | HC8  | Hard | Subject daily appearance cap                        |
 * | HC9  | Hard | No subject spans a break boundary                   |
 * | HC10 | Hard | Practical subjects require a double period          |
 * | HC11 | Hard | No consecutive different-subject same-teacher       |
 * | SC1  | Soft | Core subjects prefer morning slots                  |
 * | SC2  | Soft | CAS prefers slots before breaks                    |
 * | SC3  | Soft | Multi-period subjects spread across days            |
 */
@ApplicationScoped
class TimetableSolver {

    /** Bell-schedule and subject-ID constants for Kenya CBC JSS. */
    @Inject
    lateinit var curriculumConfig: KenyaCurriculumConfig

    companion object {
        /**
         * 0-indexed consecutive-period pairs that form valid double lessons.
         * Used by [markDoublePeriods] to flag [TimetableEntry.isDoubleStart] and
         * [TimetableEntry.isDoubleContinuation] on the output entries.
         */
        private val VALID_DOUBLE_PERIOD_PAIRS = arrayOf(
            intArrayOf(0, 1),
            intArrayOf(2, 3),
            intArrayOf(4, 5),
            intArrayOf(6, 7),
        )

        private val DAY_NAMES =
            listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")
    }

    // ── Public entry point ────────────────────────────────────────────────────

    /**
     * Builds and solves the Timefold planning problem for the given school,
     * then returns a [SolverResult].
     *
     * PPI is excluded from the model and will be handled as fixed entries in a
     * future milestone.
     *
     * @param streams           all streams belonging to the school
     * @param teachers          all teachers with their qualified subject IDs
     *                          and workload limits
     * @param subjects          all CBC subjects; PPI is filtered out before modelling
     * @param timeLimitSeconds  maximum wall-clock time given to the solver
     * @return [SolverResult] with FEASIBLE on full hard-constraint satisfaction,
     *         or INFEASIBLE with detailed violation messages otherwise
     */
    fun solve(
        streams: List<Stream>,
        teachers: List<Teacher>,
        subjects: List<Subject>,
        timeLimitSeconds: Int,
    ): SolverResult {
        val wallStart = System.currentTimeMillis()
        val teachingSubjects = subjects.filter { !it.isPpiFixed }

        Log.infof(
            "Timefold solving: %d teachers, %d subjects, %d streams.",
            teachers.size, teachingSubjects.size, streams.size,
        )

        val problem = buildProblem(streams, teachers, teachingSubjects)

        val solverConfig = SolverConfig()
            .withSolutionClass(Timetable::class.java)
            .withEntityClasses(Lesson::class.java)
            .withConstraintProviderClass(TimetableConstraintProvider::class.java)
            .withTerminationSpentLimit(Duration.ofSeconds(timeLimitSeconds.toLong()))

        val solverFactory = SolverFactory.create<Timetable>(solverConfig)

        Log.infof(
            "Starting Timefold (limit=%ds, lessons=%d)...",
            timeLimitSeconds, problem.lessons.size,
        )
        val solution = solverFactory.buildSolver().solve(problem)
        val wallMs = System.currentTimeMillis() - wallStart

        val score = solution.score ?: HardSoftScore.ZERO
        Log.infof("Timefold done in %dms: score=%s.", wallMs, score)

        return if (score.isFeasible) {
            val entries = extractEntries(solution)
            markDoublePeriods(entries)
            SolverResult(
                status         = SolverStatus.FEASIBLE,
                entries        = entries,
                wallTimeMs     = wallMs,
                objectiveValue = (-score.softScore()).toLong(),
            )
        } else {
            val violations = extractViolations(solverFactory, solution)
            Log.warnf("Timefold infeasible: %s. Violations: %s", score, violations)
            SolverResult(
                status     = SolverStatus.INFEASIBLE,
                violations = violations,
                wallTimeMs = wallMs,
            )
        }
    }

    // ── Problem construction ──────────────────────────────────────────────────

    /**
     * Builds the [Timetable] planning problem by creating one [Lesson] per
     * required teaching occurrence.
     *
     * For each (stream, subject) pair, [Subject.periodsPerWeek] [Lesson]
     * instances are created. For example, Mathematics (5 periods/week) in a
     * school with 3 streams produces 15 lessons.
     */
    private fun buildProblem(
        streams: List<Stream>,
        teachers: List<Teacher>,
        teachingSubjects: List<Subject>,
    ): Timetable {
        val lessons = mutableListOf<Lesson>()
        for (stream in streams) {
            for (subject in teachingSubjects) {
                repeat(subject.periodsPerWeek) { instance ->
                    lessons.add(
                        Lesson(
                            id      = "${stream.id}_${subject.id}_$instance",
                            stream  = stream,
                            subject = subject,
                        ),
                    )
                }
            }
        }
        return Timetable(
            teachers = teachers,
            subjects = teachingSubjects,
            streams  = streams,
            lessons  = lessons,
        )
    }

    // ── Violation extraction ──────────────────────────────────────────────────

    /**
     * Uses [SolutionManager] to extract human-readable hard-constraint violation
     * messages from the solved timetable.
     *
     * Returns one message per violated constraint in the form
     * `"HC2: Teacher conflict – 3 violation(s)"`. Only hard constraints
     * (negative hard score) are reported; soft penalties are omitted.
     */
    private fun extractViolations(
        solverFactory: SolverFactory<Timetable>,
        solution: Timetable,
    ): List<String> {
        val solutionManager: SolutionManager<Timetable, HardSoftScore> =
            SolutionManager.create(solverFactory)
        val analysis = solutionManager.analyze(solution)
        return analysis.constraintMap().values
            .filter { ca -> ca.score().hardScore() < 0 }
            .map { ca -> "${ca.constraintRef().constraintName()}: ${-ca.score().hardScore()} violation(s)" }
            .sortedDescending()
    }

    // ── Solution extraction ───────────────────────────────────────────────────

    /**
     * Converts the solved [Lesson] objects into [TimetableEntry] objects with
     * human-readable day names and real clock times from the bell schedule.
     *
     * Lessons that were not fully assigned (null day/period/teacher) are skipped.
     */
    private fun extractEntries(solution: Timetable): MutableList<TimetableEntry> {
        val weeklySlotMap = curriculumConfig.getWeeklySlotMap()
        return solution.lessons
            .filter { it.day != null && it.period != null && it.teacher != null }
            .map { lesson ->
                val dayIndex = DAY_NAMES.indexOf(lesson.day!!)
                val (startTime, endTime) =
                    weeklySlotMap[slotKey(dayIndex, lesson.period!!)] ?: ("" to "")
                TimetableEntry(
                    streamId  = lesson.stream.id ?: "",
                    subjectId = lesson.subject.id,
                    teacherId = lesson.teacher!!.id ?: "",
                    day       = lesson.day!!,
                    period    = lesson.period,
                    startTime = startTime,
                    endTime   = endTime,
                )
            }
            .toMutableList()
    }

    /**
     * Flags entries that form valid double-lesson pairs by setting
     * [TimetableEntry.isDoubleStart] on the first entry and
     * [TimetableEntry.isDoubleContinuation] on the second.
     *
     * A pair qualifies when:
     * - the two entries are consecutive periods within a teaching block,
     * - they teach the same subject in the same stream, and
     * - the first period does not fall immediately before a break.
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

                val isConsecutive    = (nextEntry.period ?: 0) == (currentEntry.period ?: 0) + 1
                val isSameSubject    = currentEntry.subjectId == nextEntry.subjectId
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
     * Returns true if [period1Based] falls immediately before a break,
     * meaning any continuation into the next period would cross a break boundary.
     */
    private fun isBreakBoundary(period1Based: Int) = period1Based in BREAK_AFTER_PERIODS
}