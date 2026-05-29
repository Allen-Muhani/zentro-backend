package com.grandtech.timetable.solver

import ai.timefold.solver.core.api.score.HardSoftScore
import ai.timefold.solver.core.api.solver.SolverFactory
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig
import ai.timefold.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig
import ai.timefold.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig
import ai.timefold.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig
import ai.timefold.solver.core.config.solver.SolverConfig
import ai.timefold.solver.core.config.solver.termination.TerminationConfig
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
import kotlin.system.measureTimeMillis

/**
 * Timefold-based timetable solver for Kenya CBC Junior Secondary Schools.
 *
 * Replaces the former OR-Tools CP-SAT solver. The key improvement is that
 * Timefold **always** returns a solution — even when hard constraints cannot
 * be fully satisfied — and exposes per-constraint violation counts derived
 * from the partial solution. Admins receive actionable messages such as
 * *"HC2 - Teacher conflict: 3 violation(s)"* instead of a silent INFEASIBLE.
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
        private val DAY_NAMES =
            listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")

        /** Period pairs that form a break boundary — used by HC9 and [isBreakBoundary]. */
        private val BREAK_PAIRS = setOf(2 to 3, 4 to 5, 6 to 7)

        /** Period pairs that are consecutive within the same teaching block. */
        private val VALID_BLOCK_PAIRS = setOf(1 to 2, 3 to 4, 5 to 6, 7 to 8)

        /**
         * Duration (seconds) reserved for the soft-constraint polish phase.
         * After the hard-feasibility phase finds a 0-hard-violation solution,
         * the solver runs for this many more seconds to optimise SC1–SC3.
         */
        private const val SOFT_POLISH_SECONDS = 5L
    }

    // ── Public entry point ────────────────────────────────────────────────────

    /**
     * Builds and solves the Timefold planning problem for the given school,
     * then returns a [SolverResult].
     *
     * The solver runs two local-search phases:
     *
     * 1. **Hard feasibility phase** – runs until a fully feasible solution
     *    (0 hard violations) is found, or until `timeLimitSeconds − 5` seconds
     *    have elapsed (whichever comes first). This preserves the fast early-exit
     *    for simple schools while allowing harder multi-stream problems to use
     *    almost the full time budget.
     *
     * 2. **Soft polish phase** – runs for up to 5 more seconds after feasibility
     *    is achieved, optimising soft constraints so that core subjects land in
     *    morning slots (SC1), CAS appears before breaks (SC2), and multi-period
     *    subjects are spread across days (SC3).
     *
     * An overall wall-clock cap of [timeLimitSeconds] acts as a safety net.
     * The solver never exceeds this total, even if both phases are still active.
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
     *         or INFEASIBLE with per-constraint violation counts otherwise
     */
    fun solve(
        streams: List<Stream>,
        teachers: List<Teacher>,
        subjects: List<Subject>,
        timeLimitSeconds: Int,
    ): SolverResult {
        val teachingSubjects = subjects.filter { !it.isPpiFixed }

        Log.info(
            "Timefold solving: ${teachers.size} teachers, " +
                "${teachingSubjects.size} subjects, ${streams.size} streams.",
        )

        val problem = buildProblem(streams, teachers, teachingSubjects)

        // Shared move selector for both local-search phases:
        //   ChangeMove  — reassigns a lesson's timeSlot or teacher to a new value.
        //   SwapMove    — atomically swaps the timeSlot of two lessons so the solver
        //                 can escape fully-packed configurations without creating a
        //                 temporarily-empty or doubly-occupied slot.
        val moveSelector = UnionMoveSelectorConfig(
            listOf(
                ChangeMoveSelectorConfig(),
                SwapMoveSelectorConfig()
                    .withVariableNameIncludes("timeSlot"),
            ),
        )

        // Phase 2: focus on satisfying all hard constraints.
        // Exits immediately when a feasible solution is found (bestScoreFeasible),
        // but is capped at timeLimitSeconds − 5 so the soft polish can always run.
        val hardPhase = LocalSearchPhaseConfig()
            .withTerminationConfig(
                TerminationConfig()
                    .withBestScoreFeasible(true)
                    .withSecondsSpentLimit(maxOf(timeLimitSeconds.toLong() - SOFT_POLISH_SECONDS, 1L)),
            )
            .withMoveSelectorConfig(moveSelector)

        // Phase 3: 5-second soft-constraint polish.
        // Runs after feasibility is achieved and improves SC1 (core subjects to morning),
        // SC2 (CAS before breaks), and SC3 (subject spreading across days).
        val softPolishPhase = LocalSearchPhaseConfig()
            .withTerminationConfig(
                TerminationConfig()
                    .withSecondsSpentLimit(SOFT_POLISH_SECONDS),
            )
            .withMoveSelectorConfig(moveSelector)

        val solverConfig = SolverConfig()
            .withSolutionClass(Timetable::class.java)
            .withEntityClasses(Lesson::class.java)
            .withConstraintProviderClass(TimetableConstraintProvider::class.java)
            .withTerminationConfig(
                TerminationConfig().withSecondsSpentLimit(timeLimitSeconds.toLong()),
            )
            .withPhases(ConstructionHeuristicPhaseConfig(), hardPhase, softPolishPhase)

        val solverFactory = SolverFactory.create<Timetable>(solverConfig)

        Log.info(
            "Starting Timefold (limit=${timeLimitSeconds}s, " +
                "hard-phase=${timeLimitSeconds - SOFT_POLISH_SECONDS}s, " +
                "soft-polish=${SOFT_POLISH_SECONDS}s, lessons=${problem.lessons.size})...",
        )

        var solution: Timetable? = null
        val wallMs = measureTimeMillis {
            solution = solverFactory.buildSolver().solve(problem)
        }
        val solved = checkNotNull(solution) { "Solver returned null solution" }

        val score = solved.score ?: HardSoftScore.ZERO
        Log.info("Timefold done in ${wallMs}ms: score=$score.")

        return if (score.isFeasible) {
            val entries = extractEntries(solved)
            markDoublePeriods(entries)
            SolverResult(
                status         = SolverStatus.FEASIBLE,
                entries        = entries,
                wallTimeMs     = wallMs,
                objectiveValue = -score.softScore(),
            )
        } else {
            val violations = extractViolations(solved)
            Log.warn("Timefold infeasible: $score. Violations: $violations")
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
     * Manually inspects the partial [solution] and returns one violation message
     * per broken hard constraint, e.g. `"HC2 - Teacher conflict: 3 violation(s)"`.
     *
     * This mirrors the constraint logic in [TimetableConstraintProvider] without
     * relying on Timefold's score analysis API (a commercial feature).
     * Only constraints with at least one violation are included.
     * Results are sorted descending so the most violated constraint appears first.
     */
    private fun extractViolations(solution: Timetable): List<String> {
        val violations = mutableListOf<String>()
        val assigned   = solution.lessons.filter { it.day != null && it.period != null }

        // HC1: no two lessons in the same stream at the same (day, period)
        val hc1 = assigned
            .groupBy { "${it.stream.id}_${it.day}_${it.period}" }
            .count { it.value.size > 1 }
        if (hc1 > 0) violations.add("HC1 - Stream slot conflict: $hc1 violation(s)")

        // HC2: no teacher in the same (day, period) for two different streams
        val hc2 = assigned
            .filter { it.teacher != null }
            .groupBy { "${it.teacher!!.id}_${it.day}_${it.period}" }
            .count { it.value.size > 1 }
        if (hc2 > 0) violations.add("HC2 - Teacher conflict: $hc2 violation(s)")

        // HC4: teacher must be qualified for the assigned subject
        val hc4 = assigned.count { lesson ->
            lesson.teacher != null &&
                lesson.subject.id !in (lesson.teacher!!.subjectIds ?: emptyList<String>())
        }
        if (hc4 > 0) violations.add("HC4 - Teacher not qualified for subject: $hc4 violation(s)")

        // HC5: all lessons for the same (stream, subject) must share the same teacher
        val hc5 = solution.lessons
            .filter { it.teacher != null }
            .groupBy { "${it.stream.id}|${it.subject.id}" }
            .count { (_, group) -> group.map { it.teacher!!.id }.toSet().size > 1 }
        if (hc5 > 0) violations.add("HC5 - Multiple teachers for same subject in stream: $hc5 violation(s)")

        // HC6a: teacher weekly workload must not exceed maxPeriodsPerWeek
        val hc6a = solution.lessons
            .filter { it.teacher != null }
            .groupBy { it.teacher!! }
            .count { (teacher, group) -> group.size > (teacher.maxPeriodsPerWeek ?: 23) }
        if (hc6a > 0) violations.add("HC6 - Teacher weekly workload exceeded: $hc6a violation(s)")

        // HC6b: teacher daily workload must not exceed maxPeriodsPerDay
        val hc6b = solution.lessons
            .filter { it.teacher != null && it.day != null }
            .groupBy { "${it.teacher!!.id}_${it.day}" }
            .count { (_, group) -> group.size > (group.first().teacher!!.maxPeriodsPerDay ?: 6) }
        if (hc6b > 0) violations.add("HC6 - Teacher daily workload exceeded: $hc6b violation(s)")

        // HC8: subject may not exceed maxPeriodsPerDay appearances per stream per day
        val hc8 = assigned
            .groupBy { "${it.stream.id}|${it.subject.id}|${it.day}" }
            .count { (_, group) -> group.size > group.first().subject.maxPeriodsPerDay }
        if (hc8 > 0) violations.add("HC8 - Subject daily cap exceeded: $hc8 violation(s)")

        // HC9: a subject may not appear on both sides of a break boundary on the same day/stream
        val hc9 = assigned
            .groupBy { "${it.stream.id}|${it.subject.id}|${it.day}" }
            .count { (_, group) ->
                group.any { l1 ->
                    group.any { l2 ->
                        l1 !== l2 &&
                            l1.period != null && l2.period != null &&
                            ((l1.period!! to l2.period!!) in BREAK_PAIRS ||
                                (l2.period!! to l1.period!!) in BREAK_PAIRS)
                    }
                }
            }
        if (hc9 > 0) violations.add("HC9 - Subject spans break boundary: $hc9 violation(s)")

        // HC10: subjects requiring a double must have at least one valid back-to-back pair
        val hc10 = solution.lessons
            .filter { it.subject.requiresDoubledPeriod }
            .groupBy { "${it.stream.id}|${it.subject.id}" }
            .count { (_, group) ->
                group.none { l1 ->
                    group.any { l2 ->
                        l1 !== l2 &&
                            l1.day != null && l1.day == l2.day &&
                            l1.period != null && l2.period != null &&
                            (minOf(l1.period!!, l2.period!!) to maxOf(l1.period!!, l2.period!!)) in VALID_BLOCK_PAIRS
                    }
                }
            }
        if (hc10 > 0) violations.add("HC10 - Required double period missing: $hc10 violation(s)")

        // HC11: a teacher may not teach different subjects in consecutive within-block periods
        //       for the same stream on the same day
        val hc11 = assigned
            .filter { it.teacher != null }
            .groupBy { "${it.teacher!!.id}|${it.stream.id}|${it.day}" }
            .count { (_, group) ->
                group.any { l1 ->
                    group.any { l2 ->
                        l1 !== l2 &&
                            l1.subject.id != l2.subject.id &&
                            l1.period != null && l2.period != null &&
                            (minOf(l1.period!!, l2.period!!) to maxOf(l1.period!!, l2.period!!)) in VALID_BLOCK_PAIRS
                    }
                }
            }
        if (hc11 > 0) violations.add("HC11 - Teacher teaches different subjects consecutively in same stream: $hc11 violation(s)")

        return violations.sortedDescending()
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
        val doubleStartIndices        = mutableSetOf<Int>()
        val doubleContinuationIndices = mutableSetOf<Int>()

        // Index entries so we can track positions without identity-hash tricks.
        val indexed      = entries.mapIndexed { idx, entry -> idx to entry }
        val byStreamDay  = indexed.groupBy { (_, e) -> "${e.streamId}_${e.day}" }

        for (group in byStreamDay.values) {
            val sortedByPeriod = group.sortedBy { (_, e) -> e.period ?: 0 }
            for (i in 0 until sortedByPeriod.size - 1) {
                val (currentIdx, currentEntry) = sortedByPeriod[i]
                val (nextIdx,    nextEntry)    = sortedByPeriod[i + 1]

                val isConsecutive     = (nextEntry.period ?: 0) == (currentEntry.period ?: 0) + 1
                val isSameSubject     = currentEntry.subjectId == nextEntry.subjectId
                val doesNotCrossBreak = !isBreakBoundary(currentEntry.period ?: 0)

                if (isConsecutive && isSameSubject && doesNotCrossBreak) {
                    doubleStartIndices.add(currentIdx)
                    doubleContinuationIndices.add(nextIdx)
                }
            }
        }

        val flaggedEntries = entries.mapIndexed { idx, entry ->
            when (idx) {
                in doubleStartIndices        -> entry.copy(isDoubleStart = true)
                in doubleContinuationIndices -> entry.copy(isDoubleContinuation = true)
                else                         -> entry
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