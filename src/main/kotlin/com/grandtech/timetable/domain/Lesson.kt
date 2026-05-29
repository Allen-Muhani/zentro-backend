package com.grandtech.timetable.domain

import ai.timefold.solver.core.api.domain.entity.PlanningEntity
import ai.timefold.solver.core.api.domain.common.PlanningId
import ai.timefold.solver.core.api.domain.variable.PlanningVariable
import com.grandtech.model.Stream
import com.grandtech.model.Subject
import com.grandtech.model.Teacher

/**
 * A single teaching occurrence that Timefold must schedule.
 *
 * Each [Lesson] represents one period of a given [subject] that must be
 * taught to a specific [stream]. The solver assigns a [timeSlot] (combining
 * day and period atomically) and a [teacher] to each lesson.
 *
 * One [Lesson] is created per required teaching occurrence — if Mathematics
 * needs 5 periods per week in stream 7A, five distinct [Lesson] instances
 * are created for that (stream, subject) pair.
 *
 * ## Why a combined [TimeSlot] instead of separate day/period variables?
 *
 * With two separate planning variables the solver changes one at a time,
 * producing an intermediate state (e.g. new day, old period) that typically
 * violates hard constraints and is rejected.  A single [TimeSlot] variable
 * allows Timefold to move a lesson from one (day, period) to another in one
 * atomic step, dramatically improving convergence for multi-stream problems.
 */
@PlanningEntity
class Lesson {

    /**
     * Unique identifier in the form `"streamId_subjectId_N"`.
     * N is the 0-based occurrence index within the (stream, subject) group.
     * Annotated with [@PlanningId] so Timefold can deduplicate [A,B] vs [B,A]
     * pairs in [forEachUniquePair] constraints.
     */
    @PlanningId
    lateinit var id: String

    /** The stream (class group) this lesson belongs to. Fixed; not a planning variable. */
    lateinit var stream: Stream

    /** The subject being taught. Fixed; not a planning variable. */
    lateinit var subject: Subject

    /**
     * Combined (day, period) assignment. Set by the solver as a single atomic
     * planning variable so that slot changes never pass through a half-assigned
     * intermediate state.
     */
    @PlanningVariable
    var timeSlot: TimeSlot? = null

    /** Convenience accessor — delegates to [timeSlot]. */
    val day: String? get() = timeSlot?.day

    /** Convenience accessor — delegates to [timeSlot]. */
    val period: Int? get() = timeSlot?.period

    /** Assigned teacher. Set by the solver. */
    @PlanningVariable
    var teacher: Teacher? = null

    /** No-arg constructor required by Timefold for class enhancement. */
    constructor()

    constructor(id: String, stream: Stream, subject: Subject) {
        this.id = id
        this.stream = stream
        this.subject = subject
    }

    override fun toString(): String =
        "Lesson(id=$id, stream=${stream.id}, subject=${subject.id}, " +
            "timeSlot=$timeSlot, teacher=${teacher?.id})"
}