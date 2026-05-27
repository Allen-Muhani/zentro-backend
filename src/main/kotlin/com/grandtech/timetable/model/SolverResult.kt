package com.grandtech.timetable.model

import com.grandtech.model.TimetableEntry

/** Status codes returned by the Timefold solver. */
enum class SolverStatus {
    /**
     * Solver found a valid solution with all hard constraints satisfied.
     * (Timefold does not distinguish OPTIMAL from FEASIBLE; we always
     * report FEASIBLE on success and let the objective value convey quality.)
     */
    OPTIMAL,
    /** Solver found a valid solution within the time limit. */
    FEASIBLE,
    /**
     * The solution has one or more hard constraint violations.
     * Unlike CP-SAT, Timefold still returns a (partial) solution —
     * [SolverResult.violations] lists which constraints are broken and
     * by how much so admins can take corrective action.
     */
    INFEASIBLE,
    /** Solver timed out without finding any solution. */
    UNKNOWN,
}

/**
 * Output produced by [com.grandtech.timetable.solver.TimetableSolver].
 * Not persisted directly — [TimetableRunService] maps it into Neo4j nodes.
 */
data class SolverResult(
    /** Terminal status reported after the Timefold solve. */
    val status: SolverStatus,
    /** Timetable entries extracted from the solved Timefold model. */
    val entries: List<TimetableEntry> = emptyList(),
    /**
     * Human-readable hard-constraint violation messages from the solve,
     * e.g. `"HC2: Teacher conflict: 3 violation(s)"`.
     * Empty when the solution is feasible.
     */
    val violations: List<String> = emptyList(),
    /** Wall-clock time the solver ran, in milliseconds. */
    val wallTimeMs: Long = 0L,
    /**
     * Cumulative soft-constraint penalty; lower means fewer soft violations.
     * Derived from the absolute value of Timefold's soft score.
     */
    val objectiveValue: Long = 0L,
) {
    /** Returns true when the solve produced a usable timetable. */
    fun isSuccessful() = status == SolverStatus.OPTIMAL || status == SolverStatus.FEASIBLE

    companion object {
        /**
         * Convenience factory for a hard-failure result.
         *
         * @param reason human-readable explanation of why the model is infeasible
         * @return a [SolverResult] with status INFEASIBLE and [reason] as its
         *         single violation message
         */
        fun infeasible(reason: String) = SolverResult(
            status = SolverStatus.INFEASIBLE,
            violations = listOf(reason),
        )

        /**
         * Convenience factory for a timeout result.
         *
         * @param wallTimeMs wall-clock milliseconds elapsed before the solver stopped
         * @return a [SolverResult] with status UNKNOWN and a timeout violation message
         */
        fun unknown(wallTimeMs: Long) = SolverResult(
            status = SolverStatus.UNKNOWN,
            wallTimeMs = wallTimeMs,
            violations = listOf("Solver timed out without finding a feasible solution."),
        )
    }
}
