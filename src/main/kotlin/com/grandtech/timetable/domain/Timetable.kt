package com.grandtech.timetable.domain

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty
import ai.timefold.solver.core.api.domain.solution.PlanningScore
import ai.timefold.solver.core.api.domain.solution.PlanningSolution
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider
import ai.timefold.solver.core.api.score.HardSoftScore
import com.grandtech.model.Stream
import com.grandtech.model.Subject
import com.grandtech.model.Teacher

/**
 * The planning solution: the full weekly timetable for a school.
 *
 * Holds all [Lesson] planning entities and the problem facts (teachers,
 * subjects, streams, available time slots) that Timefold uses to assign
 * a [TimeSlot] and a teacher to each lesson.
 *
 * [TimeSlot] combines day and period into a single planning variable value.
 * This lets Timefold move a lesson's entire time assignment atomically,
 * which improves convergence compared to separate day/period variables.
 *
 * Timefold maximises [score]; a score of `(0hard / Xsoft)` means all
 * hard constraints are satisfied.
 */
@PlanningSolution
class Timetable {

    /**
     * All teachers for the school.
     * Also serves as the value range for [Lesson.teacher].
     */
    @ProblemFactCollectionProperty
    @ValueRangeProvider
    lateinit var teachers: List<Teacher>

    /** All non-PPI CBC subjects; used as problem facts in constraints. */
    @ProblemFactCollectionProperty
    lateinit var subjects: List<Subject>

    /** All streams for the school; used as problem facts in constraints. */
    @ProblemFactCollectionProperty
    lateinit var streams: List<Stream>

    /**
     * All 40 valid (day, period) time slots for a five-day, eight-period week.
     * Serves as the value range for [Lesson.timeSlot].
     */
    @ValueRangeProvider
    val timeSlots: List<TimeSlot> = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")
        .flatMap { day -> (1..8).map { period -> TimeSlot(day, period) } }

    /** All lessons to be scheduled; the solver assigns planning variables to each. */
    @PlanningEntityCollectionProperty
    lateinit var lessons: List<Lesson>

    /** The score computed by Timefold; null until solving begins. */
    @PlanningScore
    var score: HardSoftScore? = null

    /** No-arg constructor required by Timefold for class enhancement. */
    constructor()

    constructor(
        teachers: List<Teacher>,
        subjects: List<Subject>,
        streams: List<Stream>,
        lessons: List<Lesson>,
    ) {
        this.teachers = teachers
        this.subjects = subjects
        this.streams = streams
        this.lessons = lessons
    }
}