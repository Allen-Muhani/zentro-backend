package com.grandtech.timetable.solver

import com.grandtech.model.Teacher
import com.grandtech.model.TimetableEntry
import com.grandtech.service.CbcDataSeeder
import com.grandtech.timetable.config.KenyaCurriculumConfig
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end constraint verification tests for [TimetableSolver].
 *
 * Each test solves a real CP-SAT model and then inspects the output entries to
 * confirm the relevant constraint holds — no mocks, no stubbed solver output.
 * HC11 is covered separately in [SolverHc11Test].
 *
 * All solves use [SOLVER_TIME_LIMIT_SECONDS]. Single-stream configurations
 * finish well under 30 s on a development machine.
 */
@QuarkusTest
class SolverConstraintTest : TimetableSolverTestBase() {

    companion object {
        private const val SOLVER_TIME_LIMIT_SECONDS = 30

        /** Number of teaching periods modelled per week per stream (PPI excluded). */
        private const val PERIODS_PER_WEEK = KenyaCurriculumConfig.PERIODS_PER_DAY *
            KenyaCurriculumConfig.DAYS_PER_WEEK

        /**
         * 1-based period numbers that immediately precede a break.
         * Pairs crossing these boundaries are exempt from HC9 / HC11 checks.
         */
        private val BREAK_AFTER_PERIODS = KenyaCurriculumConfig.BREAK_AFTER_PERIODS
    }

    // ── HC1: Slot coverage ────────────────────────────────────────────────────

    /**
     * HC1: Every (stream, day, period) slot must contain exactly one
     * (teacher, subject) entry — no gaps, no double-bookings.
     *
     * Verified by counting unique (streamId, day, period) tuples; the total
     * must equal [PERIODS_PER_WEEK] × number of streams.
     */
    @Test
    fun `HC1 - every slot in every stream is filled exactly once`() {
        val streams = listOf(stream("s1"))
        val result = solver.solve(
            streams          = streams,
            teachers         = oneTeacherPerSubject(),
            subjects         = allSubjects,
            timeLimitSeconds = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")

        val slotKeys = result.entries
            .map { "${it.streamId}_${it.day}_${it.period}" }

        // No duplicates — every slot key appears exactly once.
        assertEquals(
            slotKeys.size,
            slotKeys.toSet().size,
            "HC1 violated: duplicate entries found for the same (stream, day, period) slot",
        )
        // No gaps — total slots = periods per day × days per week × streams.
        assertEquals(
            PERIODS_PER_WEEK * streams.size,
            slotKeys.size,
            "HC1 violated: expected ${PERIODS_PER_WEEK * streams.size} slots " +
            "but found ${slotKeys.size}",
        )
    }

    @Test
    fun `HC1 - slot coverage holds across multiple streams`() {
        val streams = listOf(stream("s1", name = "Blue"), stream("s2", name = "White"))
        val result = solver.solve(
            streams          = streams,
            teachers         = oneTeacherPerSubject(),
            subjects         = allSubjects,
            timeLimitSeconds = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")

        val slotKeys = result.entries.map { "${it.streamId}_${it.day}_${it.period}" }
        assertEquals(
            slotKeys.size,
            slotKeys.toSet().size,
            "HC1 violated: duplicate (stream, day, period) entries found with multiple streams",
        )
        assertEquals(PERIODS_PER_WEEK * streams.size, slotKeys.size)
    }

    // ── HC2: Teacher conflict freedom ─────────────────────────────────────────

    /**
     * HC2: A teacher may appear in at most one slot per (day, period) across
     * all streams — a teacher cannot teach two streams simultaneously.
     *
     * Verified by grouping entries by (teacherId, day, period) and asserting
     * every group has exactly one entry.
     */
    @Test
    fun `HC2 - no teacher appears in the same period across two streams`() {
        val streams = listOf(stream("s1", name = "Blue"), stream("s2", name = "White"))
        val result = solver.solve(
            streams          = streams,
            teachers         = oneTeacherPerSubject(),
            subjects         = allSubjects,
            timeLimitSeconds = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")

        val conflicts = result.entries
            .groupBy { "${it.teacherId}_${it.day}_${it.period}" }
            .filter { (_, group) -> group.size > 1 }

        assertTrue(
            conflicts.isEmpty(),
            "HC2 violated: teacher(s) assigned to multiple streams in the same slot: " +
            conflicts.keys,
        )
    }

    // ── HC3: Subject frequency ────────────────────────────────────────────────

    /**
     * HC3: Each subject must appear exactly [Subject.periodsPerWeek] times
     * per stream across the full week.
     *
     * PPI is excluded from the solver model and is therefore not present in
     * the output entries.
     */
    @Test
    fun `HC3 - each subject appears exactly periodsPerWeek times per stream`() {
        val streams = listOf(stream("s1"))
        val result = solver.solve(
            streams          = streams,
            teachers         = oneTeacherPerSubject(),
            subjects         = allSubjects,
            timeLimitSeconds = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")

        val teachingSubjects = allSubjects.filterNot { it.isPpiFixed }
        for (sub in teachingSubjects) {
            val count = result.entries.count { it.subjectId == sub.id && it.streamId == "s1" }
            assertEquals(
                sub.periodsPerWeek,
                count,
                "HC3 violated: subject '${sub.id}' expected ${sub.periodsPerWeek} periods " +
                "but found $count",
            )
        }
    }

    @Test
    fun `HC3 - subject frequency is independent per stream`() {
        val streams = listOf(stream("sA", name = "Blue"), stream("sB", name = "White"))
        val result = solver.solve(
            streams          = streams,
            teachers         = oneTeacherPerSubject(),
            subjects         = allSubjects,
            timeLimitSeconds = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")

        val teachingSubjects = allSubjects.filterNot { it.isPpiFixed }
        for (stream in streams) {
            for (sub in teachingSubjects) {
                val count = result.entries.count {
                    it.subjectId == sub.id && it.streamId == stream.id
                }
                assertEquals(
                    sub.periodsPerWeek, count,
                    "HC3 violated: stream '${stream.id}' subject '${sub.id}' " +
                    "expected ${sub.periodsPerWeek} but got $count",
                )
            }
        }
    }

    // ── HC4: Teacher qualification ────────────────────────────────────────────

    /**
     * HC4: Every entry's teacher must be qualified to teach the entry's
     * subject (i.e. the subject must appear in the teacher's subjectIds).
     */
    @Test
    fun `HC4 - every entry has a teacher qualified for the subject`() {
        val teachers = oneTeacherPerSubject()
        val result = solver.solve(
            streams          = listOf(stream()),
            teachers         = teachers,
            subjects         = allSubjects,
            timeLimitSeconds = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")

        val teacherById = teachers.associateBy { it.id }
        val violations = result.entries.filter { entry ->
            val teacher = teacherById[entry.teacherId]
            teacher?.subjectIds?.contains(entry.subjectId) != true
        }

        assertTrue(
            violations.isEmpty(),
            "HC4 violated: unqualified teacher assignments found: " +
            violations.map { "${it.teacherId} → ${it.subjectId}" },
        )
    }

    // ── HC5: Single teacher per class ─────────────────────────────────────────

    /**
     * HC5: Exactly one teacher must cover each (subject, stream) pair for
     * the full week — no mid-week teacher substitutions.
     *
     * Verified by collecting distinct teacher IDs per (streamId, subjectId)
     * and asserting the count is always 1.
     */
    @Test
    fun `HC5 - exactly one teacher is assigned to each subject-stream pair`() {
        val result = solver.solve(
            streams          = listOf(stream()),
            teachers         = oneTeacherPerSubject(),
            subjects         = allSubjects,
            timeLimitSeconds = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")

        val teachingSubjects = allSubjects.filterNot { it.isPpiFixed }
        for (sub in teachingSubjects) {
            val uniqueTeachers = result.entries
                .filter { it.subjectId == sub.id }
                .mapNotNull { it.teacherId }
                .toSet()

            assertEquals(
                1,
                uniqueTeachers.size,
                "HC5 violated: subject '${sub.id}' is taught by ${uniqueTeachers.size} " +
                "different teachers: $uniqueTeachers",
            )
        }
    }

    // ── HC6: Workload limits ──────────────────────────────────────────────────

    /**
     * HC6: No teacher may exceed their [Teacher.maxPeriodsPerWeek] or
     * [Teacher.maxPeriodsPerDay] limits.
     */
    @Test
    fun `HC6 - no teacher exceeds their weekly period limit`() {
        val maxWeekly = 23
        val teachers  = oneTeacherPerSubject().map { it.copy(maxPeriodsPerWeek = maxWeekly) }
        val result = solver.solve(
            streams          = listOf(stream()),
            teachers         = teachers,
            subjects         = allSubjects,
            timeLimitSeconds = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")

        result.entries
            .groupBy { it.teacherId }
            .forEach { (teacherId, entries) ->
                assertTrue(
                    entries.size <= maxWeekly,
                    "HC6 violated: teacher '$teacherId' has ${entries.size} weekly periods " +
                    "(limit $maxWeekly)",
                )
            }
    }

    @Test
    fun `HC6 - no teacher exceeds their daily period limit`() {
        val maxDaily = 6
        val teachers = oneTeacherPerSubject().map { it.copy(maxPeriodsPerDay = maxDaily) }
        val result = solver.solve(
            streams          = listOf(stream()),
            teachers         = teachers,
            subjects         = allSubjects,
            timeLimitSeconds = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")

        result.entries
            .groupBy { "${it.teacherId}_${it.day}" }
            .forEach { (key, entries) ->
                assertTrue(
                    entries.size <= maxDaily,
                    "HC6 violated: teacher+day '$key' has ${entries.size} periods (limit $maxDaily)",
                )
            }
    }

    @Test
    fun `HC6 - solver respects a tight daily limit of 4 periods`() {
        // Set maxPeriodsPerDay to 4 for all teachers and verify the constraint holds.
        val tightDaily = 4
        val teachers = oneTeacherPerSubject().map {
            it.copy(maxPeriodsPerWeek = 23, maxPeriodsPerDay = tightDaily)
        }
        val result = solver.solve(
            streams          = listOf(stream()),
            teachers         = teachers,
            subjects         = allSubjects,
            timeLimitSeconds = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution with tight daily limit")

        result.entries
            .groupBy { "${it.teacherId}_${it.day}" }
            .forEach { (key, entries) ->
                assertTrue(
                    entries.size <= tightDaily,
                    "HC6 violated: '$key' has ${entries.size} periods (tight limit $tightDaily)",
                )
            }
    }

    // ── HC8: Daily subject cap ─────────────────────────────────────────────────

    /**
     * HC8: A subject may appear at most [Subject.maxPeriodsPerDay] times in
     * any given stream on a single day.
     *
     * For single-period subjects (ENG, KIS, MAT, SST, RE) the cap is 1.
     * For practical subjects (SCI, PTS, AGR, CAS) the cap is 2 to allow
     * the required double-lesson.
     */
    @Test
    fun `HC8 - no subject appears more than maxPeriodsPerDay times in one day`() {
        val result = solver.solve(
            streams          = listOf(stream()),
            teachers         = oneTeacherPerSubject(),
            subjects         = allSubjects,
            timeLimitSeconds = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")

        val teachingSubjects = allSubjects.filterNot { it.isPpiFixed }
        val subjectCap       = teachingSubjects.associate { it.id to it.maxPeriodsPerDay }
        val days             = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")

        for (day in days) {
            result.entries
                .filter { it.day == day }
                .groupBy { it.subjectId }
                .forEach { (subjectId, entries) ->
                    val cap = subjectCap[subjectId] ?: 1
                    assertTrue(
                        entries.size <= cap,
                        "HC8 violated: subject '$subjectId' appears ${entries.size} times on $day " +
                        "(cap $cap)",
                    )
                }
        }
    }

    // ── HC9: No break spanning ────────────────────────────────────────────────

    /**
     * HC9: No subject may appear immediately before AND immediately after a
     * break boundary in the same stream on the same day.
     *
     * Break boundaries (1-based): P2→P3, P4→P5, P6→P7.
     */
    @Test
    fun `HC9 - no subject spans a break boundary in the same stream on the same day`() {
        // Break boundary pairs: (periodBefore, periodAfter) in 1-based periods.
        val breakBoundaryPairs = listOf(2 to 3, 4 to 5, 6 to 7)

        val result = solver.solve(
            streams          = listOf(stream()),
            teachers         = oneTeacherPerSubject(),
            subjects         = allSubjects,
            timeLimitSeconds = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")

        val byStreamAndDay = result.entries.groupBy { "${it.streamId}_${it.day}" }

        for ((_, dayEntries) in byStreamAndDay) {
            val periodToSubject = dayEntries.associate { it.period to it.subjectId }
            for ((before, after) in breakBoundaryPairs) {
                val subjectBefore = periodToSubject[before]
                val subjectAfter  = periodToSubject[after]
                if (subjectBefore != null && subjectAfter != null) {
                    assertFalse(
                        subjectBefore == subjectAfter,
                        "HC9 violated: subject '$subjectBefore' spans break boundary " +
                        "P$before→P$after on day ${dayEntries.first().day} " +
                        "stream ${dayEntries.first().streamId}",
                    )
                }
            }
        }
    }

    // ── HC10: Double period requirement ───────────────────────────────────────

    /**
     * HC10: Subjects with [Subject.requiresDoubledPeriod] must have at least
     * one valid double-lesson (two consecutive same-subject periods within a
     * teaching block) per stream somewhere in the week.
     *
     * Subjects requiring a double: SCI, PTS, AGR, CAS.
     */
    @Test
    fun `HC10 - every requiresDoubledPeriod subject has at least one double lesson per stream`() {
        val result = solver.solve(
            streams          = listOf(stream()),
            teachers         = oneTeacherPerSubject(),
            subjects         = allSubjects,
            timeLimitSeconds = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")

        val doubleSubjects = allSubjects.filter { it.requiresDoubledPeriod && !it.isPpiFixed }
        assertTrue(doubleSubjects.isNotEmpty(), "Precondition: expected at least one double subject")

        for (subject in doubleSubjects) {
            val hasDoublePeriod = result.entries.any { it.isDoubleStart && it.subjectId == subject.id }
            assertTrue(
                hasDoublePeriod,
                "HC10 violated: subject '${subject.id}' requires a double period " +
                "but no isDoubleStart entry was found",
            )
        }
    }

    @Test
    fun `HC10 - double lessons are within a single teaching block and do not cross breaks`() {
        val result = solver.solve(
            streams          = listOf(stream()),
            teachers         = oneTeacherPerSubject(),
            subjects         = allSubjects,
            timeLimitSeconds = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")

        val doubleStarts = result.entries.filter { it.isDoubleStart }
        assertTrue(doubleStarts.isNotEmpty(), "Precondition: no double-start entries found")

        for (startEntry in doubleStarts) {
            val startPeriod = startEntry.period ?: continue

            // A valid double start must not sit immediately before a break.
            assertFalse(
                startPeriod in BREAK_AFTER_PERIODS,
                "HC10 violated: isDoubleStart entry for '${startEntry.subjectId}' at " +
                "P$startPeriod is immediately before a break — this would cross a boundary",
            )

            // The continuation entry must exist in the very next period.
            val continuationExists = result.entries.any { e ->
                e.isDoubleContinuation &&
                e.subjectId  == startEntry.subjectId &&
                e.streamId   == startEntry.streamId  &&
                e.day        == startEntry.day        &&
                e.period     == startPeriod + 1
            }
            assertTrue(
                continuationExists,
                "HC10 violated: no isDoubleContinuation entry follows the double start " +
                "for '${startEntry.subjectId}' at P$startPeriod on ${startEntry.day}",
            )
        }
    }

    @Test
    fun `HC10 - double period requirement holds with two streams`() {
        val streams = listOf(stream("sA", name = "Blue"), stream("sB", name = "White"))
        val result = solver.solve(
            streams          = streams,
            teachers         = oneTeacherPerSubject(),
            subjects         = allSubjects,
            timeLimitSeconds = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")

        val doubleSubjects = allSubjects.filter { it.requiresDoubledPeriod && !it.isPpiFixed }
        for (stream in streams) {
            for (subject in doubleSubjects) {
                val hasDouble = result.entries.any {
                    it.isDoubleStart &&
                    it.subjectId == subject.id &&
                    it.streamId  == stream.id
                }
                assertTrue(
                    hasDouble,
                    "HC10 violated: stream '${stream.id}' subject '${subject.id}' " +
                    "has no double-period lesson",
                )
            }
        }
    }

    // ── SC1: Core subjects prefer morning ────────────────────────────────────

    /**
     * SC1: Core subjects (LANGUAGE, MATHEMATICS, SCIENCE) incur a penalty
     * when placed in afternoon slots (P5–P8, 1-based). Under optimal
     * conditions the majority of core slots should appear in the morning.
     *
     * Since SC1 is a soft constraint, "majority" is defined as at least half
     * of all core subject slots appearing in periods 1–4.
     */
    @Test
    fun `SC1 - core subjects are predominantly scheduled in morning periods`() {
        val result = solver.solve(
            streams          = listOf(stream()),
            teachers         = oneTeacherPerSubject(),
            subjects         = allSubjects,
            timeLimitSeconds = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")

        val coreSubjectIds = setOf(
            KenyaCurriculumConfig.ENG,
            KenyaCurriculumConfig.KIS,
            KenyaCurriculumConfig.MAT,
            KenyaCurriculumConfig.SCI,
        )
        val coreEntries = result.entries.filter { it.subjectId in coreSubjectIds }
        assertTrue(coreEntries.isNotEmpty(), "Precondition: no core subject entries found")

        val morningCount   = coreEntries.count { (it.period ?: 0) <= 4 }
        val afternoonCount = coreEntries.count { (it.period ?: 0) > 4  }

        assertTrue(
            morningCount >= afternoonCount,
            "SC1 not satisfied: more core subjects in afternoon ($afternoonCount) " +
            "than morning ($morningCount). SC1 should push core subjects into P1–P4.",
        )
    }

    // ── SC2: CAS prefers slots before breaks ──────────────────────────────────

    /**
     * SC2: Creative Arts & Sports (CAS) incurs a penalty when placed outside
     * pre-break slots (1-based P2, P4, P6). At least one CAS slot in the week
     * should be placed at a pre-break position under optimal conditions.
     */
    @Test
    fun `SC2 - at least one CAS period is scheduled immediately before a break`() {
        val result = solver.solve(
            streams          = listOf(stream()),
            teachers         = oneTeacherPerSubject(),
            subjects         = allSubjects,
            timeLimitSeconds = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")

        val casEntries = result.entries.filter { it.subjectId == KenyaCurriculumConfig.CAS }
        assertTrue(casEntries.isNotEmpty(), "Precondition: no CAS entries in timetable")

        val hasPreBreakSlot = casEntries.any { it.period in BREAK_AFTER_PERIODS }
        assertTrue(
            hasPreBreakSlot,
            "SC2 not satisfied: no CAS period appears immediately before a break. " +
            "CAS periods: ${casEntries.map { it.period }}",
        )
    }

    // ── SC3: Subject spreading across days ────────────────────────────────────

    /**
     * SC3: Subjects with more than one period per week should be distributed
     * across multiple days rather than clustered on the same day.
     *
     * For a well-spread timetable every multi-period subject should appear
     * on at least two distinct days per week.
     */
    @Test
    fun `SC3 - multi-period subjects are spread across at least two days`() {
        val result = solver.solve(
            streams          = listOf(stream()),
            teachers         = oneTeacherPerSubject(),
            subjects         = allSubjects,
            timeLimitSeconds = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")

        val teachingSubjects = allSubjects.filterNot { it.isPpiFixed }
        val multiPeriodSubjects = teachingSubjects.filter { it.periodsPerWeek > 1 }

        for (subject in multiPeriodSubjects) {
            val distinctDays = result.entries
                .filter { it.subjectId == subject.id }
                .mapNotNull { it.day }
                .toSet()

            assertTrue(
                distinctDays.size >= 2,
                "SC3 not satisfied: subject '${subject.id}' appears on only " +
                "${distinctDays.size} day(s): $distinctDays. " +
                "Expected spread over at least 2 days.",
            )
        }
    }

    // ── Combined: solver output is self-consistent ────────────────────────────

    /**
     * Holistic sanity check: the full single-stream output passes all
     * entry-level invariants simultaneously.
     *
     * This test complements the individual constraint tests by confirming no
     * constraint regression slips through an untested combination.
     */
    @Test
    fun `all hard constraint invariants hold simultaneously in a single-stream solve`() {
        val teachers = oneTeacherPerSubject()
        val result = solver.solve(
            streams          = listOf(stream()),
            teachers         = teachers,
            subjects         = allSubjects,
            timeLimitSeconds = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")

        val teacherById      = teachers.associateBy { it.id }
        val teachingSubjects = allSubjects.filterNot { it.isPpiFixed }
        val subjectCap       = teachingSubjects.associate { it.id to it.maxPeriodsPerDay }

        // HC1: no duplicate slots
        val slotKeys = result.entries.map { "${it.streamId}_${it.day}_${it.period}" }
        assertEquals(slotKeys.size, slotKeys.toSet().size, "HC1: duplicate slot keys")

        // HC2: no teacher in two streams at the same time
        result.entries
            .groupBy { "${it.teacherId}_${it.day}_${it.period}" }
            .forEach { (key, group) ->
                assertEquals(1, group.size, "HC2: teacher conflict at '$key'")
            }

        // HC3: subject frequency
        for (sub in teachingSubjects) {
            val count = result.entries.count { it.subjectId == sub.id }
            assertEquals(sub.periodsPerWeek, count, "HC3: wrong count for '${sub.id}'")
        }

        // HC4: teacher qualification
        result.entries.forEach { e ->
            val qualifiedIds = teacherById[e.teacherId]?.subjectIds ?: emptyList()
            assertTrue(e.subjectId in qualifiedIds, "HC4: '${e.teacherId}' unqualified for '${e.subjectId}'")
        }

        // HC6: workload limits (weekly)
        result.entries
            .groupBy { it.teacherId }
            .forEach { (tid, entries) ->
                val max = teacherById[tid]?.maxPeriodsPerWeek ?: 23
                assertTrue(entries.size <= max, "HC6: '$tid' exceeds weekly cap")
            }

        // HC8: daily subject cap
        result.entries
            .groupBy { "${it.day}_${it.subjectId}" }
            .forEach { (key, entries) ->
                val subId = entries.first().subjectId ?: return@forEach
                val cap   = subjectCap[subId] ?: 1
                assertTrue(entries.size <= cap, "HC8: '$key' exceeds daily cap $cap")
            }

        // HC9: no break spanning
        val breakPairs = listOf(2 to 3, 4 to 5, 6 to 7)
        result.entries.groupBy { "${it.streamId}_${it.day}" }.forEach { (_, dayEntries) ->
            val pToSub = dayEntries.associate { it.period to it.subjectId }
            for ((before, after) in breakPairs) {
                if (pToSub[before] != null && pToSub[after] != null) {
                    assertFalse(pToSub[before] == pToSub[after], "HC9: subject spans break P$before→P$after")
                }
            }
        }

        // HC11: no consecutive different-subject same-teacher
        assertHc11NotViolated(result.entries)
    }
}
