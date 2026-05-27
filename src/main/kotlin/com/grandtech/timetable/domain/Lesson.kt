package com.grandtech.timetable.domain

import ai.timefold.solver.core.api.domain.entity.PlanningEntity
import ai.timefold.solver.core.api.domain.variable.PlanningVariable
import com.grandtech.model.Stream
import com.grandtech.model.Subject
import com.grandtech.model.Teacher

/**
 * A single teaching occurrence that Timefold must schedule.
 *
 * Each [Lesson] represents one period of a given [subject] that must be
 * taught to a specific [stream]. The solver assigns a [day], [period],
 * and [teacher] to each lesson.
 *
 * One [Lesson] is created per required teaching occurrence — if Mathematics
 * needs 5 periods per week in stream 7A, five distinct [Lesson] instances
 * are created for that (stream, subject) pair.
 */
@PlanningEntity
open class Lesson {

    /**
     * Unique identifier in the form `"streamId_subjectId_N"`.
     * N is the 0-based occurrence index within the (stream, subject) group.
     */
    lateinit var id: String

    /** The stream (class group) this lesson belongs to. Fixed; not a planning variable. */
    lateinit var stream: Stream

    /** The subject being taught. Fixed; not a planning variable. */
    lateinit var subject: Subject

    /** Assigned day of the week (e.g. "MONDAY"). Set by the solver. */
    @PlanningVariable
    open var day: String? = null

    /** Assigned 1-based period number (1–8). Set by the solver. */
    @PlanningVariable
    open var period: Int? = null

    /** Assigned teacher. Set by the solver. */
    @PlanningVariable
    open var teacher: Teacher? = null

    /** No-arg constructor required by Timefold for class enhancement. */
    constructor()

    constructor(id: String, stream: Stream, subject: Subject) {
        this.id = id
        this.stream = stream
        this.subject = subject
    }

    override fun toString(): String =
        "Lesson(id=$id, stream=${stream.id}, subject=${subject.id}, " +
            "day=$day, period=$period, teacher=${teacher?.id})"
}