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
 * subjects, streams, available days and periods) that Timefold uses to
 * assign day, period, and teacher to each lesson.
 *
 * Timefold maximises [score]; a score of `(0hard / Xsoft)` means all
 * hard constraints are satisfied. A negative hard score means the solution
 * is infeasible — [TimetableSolver] inspects the [ScoreManager] explanation
 * to extract actionable violation messages.
 */
@PlanningSolution
open class Timetable {

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
     * All valid school days.
     * Serves as the value range for [Lesson.day].
     */
    @ValueRangeProvider
    val days: List<String> = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")

    /**
     * All valid 1-based period numbers (1–8).
     * Serves as the value range for [Lesson.period].
     */
    @ValueRangeProvider
    val periods: List<Int> = (1..8).toList()

    /** All lessons to be scheduled; the solver assigns planning variables to each. */
    @PlanningEntityCollectionProperty
    lateinit var lessons: List<Lesson>

    /** The score computed by Timefold; null until solving begins. */
    @PlanningScore
    open var score: HardSoftScore? = null

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