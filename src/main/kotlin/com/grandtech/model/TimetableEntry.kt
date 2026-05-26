package com.grandtech.model

/**
 * A single scheduled lesson produced by the solver and persisted as a
 * `(:TimetableEntry)` node.
 *
 * Graph relationships:
 * - `(:TimetableRun)-[:HAS_ENTRY]->(:TimetableEntry)`
 * - `(:TimetableEntry)-[:FOR_STREAM]->(:Stream {id})`
 * - `(:TimetableEntry)-[:FOR_SUBJECT]->(:Subject {id})`
 * - `(:TimetableEntry)-[:TAUGHT_BY]->(:Teacher {id})` — omitted for unresolved PPI
 */
data class TimetableEntry(
    /** Unique identifier for this entry node (UUID assigned on save). */
    val id: String? = null,
    /** ID of the stream this lesson is scheduled for. */
    val streamId: String? = null,
    /** Display name of the stream, e.g. "Grade 7 Blue". */
    val streamName: String? = null,
    /** ID of the subject being taught. */
    val subjectId: String? = null,
    /** Full display name of the subject, e.g. "Integrated Science". */
    val subjectName: String? = null,
    /** Short symbol for the subject, e.g. "SCI". */
    val subjectSymbol: String? = null,
    /** Null for PPI entries where the form teacher could not be resolved. */
    val teacherId: String? = null,
    /** Full name of the teacher, null when teacherId is null. */
    val teacherName: String? = null,
    /** Day name: MONDAY … FRIDAY. */
    val day: String? = null,
    /** 1-based teaching period number (1–8). */
    val period: Int? = null,
    /** Wall-clock start time of this period (e.g. "08:20"). */
    val startTime: String? = null,
    /** Wall-clock end time of this period (e.g. "09:00"). */
    val endTime: String? = null,
    /** True when this is the first period of a double lesson. */
    val isDoubleStart: Boolean = false,
    /** True when this is the second period of a double lesson. */
    val isDoubleContinuation: Boolean = false,
)
