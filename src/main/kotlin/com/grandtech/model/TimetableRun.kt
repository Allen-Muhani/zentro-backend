package com.grandtech.model

/**
 * A timetable generation job for a school, stored as a `(:TimetableRun)` node.
 *
 * Graph relationships:
 * - `(:School {fedUid})-[:HAS_TIMETABLE_RUN]->(:TimetableRun)`
 * - `(:TimetableRun)-[:HAS_ENTRY]->(:TimetableEntry)`
 */
data class TimetableRun(
    /** Unique identifier for this run node (UUID assigned on creation). */
    val id: String? = null,
    /** fedUid of the school that owns this run. */
    val schoolFedUid: String? = null,
    /** Academic year label (e.g. "2024"). */
    val academicYear: String? = null,
    /** Term label (e.g. "Term 1"). */
    val term: String? = null,
    /** RUNNING, COMPLETED, or FAILED. */
    val status: String? = null,
    /** OPTIMAL, FEASIBLE, INFEASIBLE, or UNKNOWN — set on completion. */
    val solverStatus: String? = null,
    /** Minimised objective value returned by CP-SAT; lower is better. */
    val objectiveValue: Long? = null,
    /** Wall-clock time the solver ran in milliseconds. */
    val solverWallTimeMs: Long? = null,
    /** Maximum solver wall-clock time allowed, in seconds. */
    val timeLimitSeconds: Int? = null,
    /** JSON-serialised DiagnosticReport from the pre-solve validator. */
    val diagnosticReport: String? = null,
    /** Soft-constraint and PPI violations reported by the solver. */
    val violations: List<String>? = null,
    /** ISO-8601 timestamp when the run was created. */
    val generatedAt: String? = null,
    /** ISO-8601 timestamp when the run reached a terminal state. */
    val completedAt: String? = null,
)
