package com.grandtech.timetable.model

import com.grandtech.model.TimetableEntry

/** Status codes returned by the OR-Tools CP-SAT solver. */
enum class SolverStatus {
    /** Solver proved the solution is globally optimal. */
    OPTIMAL,
    /** Solver found a valid solution within the time limit. */
    FEASIBLE,
    /** No solution exists satisfying all hard constraints. */
    INFEASIBLE,
    /** Solver timed out without finding any solution. */
    UNKNOWN,
}

/**
 * Output produced by [com.grandtech.timetable.solver.TimetableSolver].
 * Not persisted directly — [TimetableRunService] maps it into Neo4j nodes.
 */
data class SolverResult(
    /** Terminal status code returned by CP-SAT. */
    val status: SolverStatus,
    /** Timetable entries extracted from the solved CP-SAT model. */
    val entries: List<TimetableEntry> = emptyList(),
    /** Human-readable soft-constraint and structural violations from the solve. */
    val violations: List<String> = emptyList(),
    /** Wall-clock time the solver ran, in milliseconds. */
    val wallTimeMs: Long = 0L,
    /**
     * Minimised CP-SAT objective value; lower means fewer
     * soft-constraint penalties.
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
