package com.grandtech.timetable.domain

/**
 * A combined (day, period) time slot used as a single planning variable value.
 *
 * Representing the time dimension as one composite value — rather than two
 * separate [Lesson.day] / [Lesson.period] variables — lets Timefold move an
 * entire slot assignment atomically in a single [ChangeMove].  With separate
 * variables the solver has to change day *then* period in two steps, passing
 * through an intermediate state that typically worsens the score and is
 * rejected by the acceptance criterion.  The combined approach eliminates
 * those dead-end intermediate states and dramatically speeds up convergence
 * for multi-stream problems.
 *
 * @property day    one of the five school days (e.g. "MONDAY")
 * @property period 1-based period number within the day (1–8)
 */
data class TimeSlot(val day: String, val period: Int) {
    override fun toString(): String = "$day-P$period"
}