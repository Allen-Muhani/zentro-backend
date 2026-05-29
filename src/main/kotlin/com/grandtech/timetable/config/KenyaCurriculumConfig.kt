package com.grandtech.timetable.config

import jakarta.enterprise.context.ApplicationScoped

/**
 * Static Kenya CBC JSS curriculum facts per KICD Dec 2023 rationalisation.
 *
 * Bell schedule (all days identical; PPI excluded for pre-MVP):
 *
 *   P1  08:20 – 09:00
 *   P2  09:00 – 09:40
 *       Break  09:40 – 10:00
 *   P3  10:00 – 10:40
 *   P4  10:40 – 11:20
 *       Break  11:20 – 11:35
 *   P5  11:35 – 12:15
 *   P6  12:15 – 12:55
 *       Lunch  12:55 – 14:00
 *   P7  14:00 – 14:40
 *   P8  14:40 – 15:20
 *
 * Subject IDs match the nodes seeded by [com.grandtech.service.CbcDataSeeder]:
 *   ENG 5 · KIS 4 · MAT 5 · SCI 5 · PTS 4 · SST 4 · RE 4 · AGR 4 · CAS 5
 *   (PPI skipped for now — 40 periods per stream per week)
 */
@ApplicationScoped
class KenyaCurriculumConfig {

    companion object {
        /** Number of teaching periods per school day (P1–P8). */
        const val PERIODS_PER_DAY = 8
        /** Number of teaching days per week (Monday–Friday). */
        const val DAYS_PER_WEEK = 5

        /** 1-based period numbers after which a break falls. */
        const val FIRST_BREAK_AFTER_PERIOD = 2   // after P2
        /** 1-based period number after which the second short break falls. */
        const val SECOND_BREAK_AFTER_PERIOD = 4  // after P4
        /** 1-based period number after which the lunch break falls. */
        const val LUNCH_BREAK_AFTER_PERIOD = 6   // after P6

        /** Subject ID for English. */
        const val ENG = "ENG"
        /** Subject ID for Kiswahili. */
        const val KIS = "KIS"
        /** Subject ID for Mathematics. */
        const val MAT = "MAT"
        /** Subject ID for Integrated Science. */
        const val SCI = "SCI"
        /** Subject ID for Pre-Technical Studies. */
        const val PTS = "PTS"
        /** Subject ID for Social Studies. */
        const val SST = "SST"
        /** Subject ID for Religious Education. */
        const val RE = "RE"
        /** Subject ID for Agriculture. */
        const val AGR = "AGR"
        /** Subject ID for Creative Arts & Sports. */
        const val CAS = "CAS"
        /**
         * Subject ID for Pastoral Programme of Instruction
         * (form-teacher-taught).
         */
        const val PPI = "PPI"

        /**
         * Builds a map key from a 0-based day index and a 1-based period number.
         *
         * @param dayIndex 0-based day index (0 = Monday … 4 = Friday)
         * @param period   1-based period number (1–8)
         * @return a string key in the form "dayIndex_period"
         */
        fun slotKey(dayIndex: Int, period: Int) = "${dayIndex}_$period"

        /** All 1-based period numbers that immediately precede a break or lunch. */
        val BREAK_AFTER_PERIODS = setOf(
            FIRST_BREAK_AFTER_PERIOD,
            SECOND_BREAK_AFTER_PERIOD,
            LUNCH_BREAK_AFTER_PERIOD,
        )
    }

    /**
     * Returns period → (startTime, endTime) for each teaching period.
     * The schedule is the same for all days (PPI on Monday is excluded).
     *
     * @param dayIndex 0 = Monday … 4 = Friday (not used currently —
     *                 same slots each day)
     * @return map of 1-based period number to (startTime, endTime) pair
     */
    fun getDailySlots(dayIndex: Int): Map<Int, Pair<String, String>> = mapOf(
        1 to ("08:20" to "09:00"),
        2 to ("09:00" to "09:40"),
        // break 09:40 – 10:00
        3 to ("10:00" to "10:40"),
        4 to ("10:40" to "11:20"),
        // break 11:20 – 11:35
        5 to ("11:35" to "12:15"),
        6 to ("12:15" to "12:55"),
        // lunch 12:55 – 14:00
        7 to ("14:00" to "14:40"),
        8 to ("14:40" to "15:20"),
    )

    /**
     * Returns a flat map of slotKey → (startTime, endTime) for every
     * teaching slot across all 5 days.
     *
     * @return map keyed by [slotKey] to (startTime, endTime) for all slots
     */
    fun getWeeklySlotMap(): Map<String, Pair<String, String>> {
        val map = mutableMapOf<String, Pair<String, String>>()
        for (d in 0 until DAYS_PER_WEEK) {
            for ((period, times) in getDailySlots(d)) {
                map[slotKey(d, period)] = times
            }
        }
        return map
    }
}
