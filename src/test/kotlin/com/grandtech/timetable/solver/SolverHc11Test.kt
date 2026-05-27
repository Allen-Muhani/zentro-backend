package com.grandtech.timetable.solver

import com.grandtech.model.Teacher
import com.grandtech.timetable.config.KenyaCurriculumConfig
import com.grandtech.timetable.model.SolverStatus
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for HC11 — no consecutive same-teacher appearances in a stream.
 *
 * Each test runs the full Timefold solver with a crafted input, then inspects
 * the output for violations. No database interaction occurs; CDI is the only
 * Quarkus facility required.
 *
 * The time limit for each solve is capped at [SOLVER_TIME_LIMIT_SECONDS].
 * Simple single-stream configurations typically solve in under 5 seconds.
 */
@QuarkusTest
class SolverHc11Test : TimetableSolverTestBase() {

    companion object {
        /** Wall-clock seconds given to each solve. Kept short for CI speed. */
        private const val SOLVER_TIME_LIMIT_SECONDS = 30
    }

    // ── Baseline ─────────────────────────────────────────────────────────────

    /**
     * With one dedicated teacher per subject and a single stream the model
     * is straightforward. This test verifies the solver still finds a valid
     * solution after HC11 was introduced, confirming the new constraint does
     * not inadvertently make a well-formed school configuration infeasible.
     */
    @Test
    fun `solver finds OPTIMAL or FEASIBLE with one teacher per subject`() {
        val result = solver.solve(
            streams           = listOf(stream()),
            teachers          = oneTeacherPerSubject(),
            subjects          = allSubjects,
            timeLimitSeconds  = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(
            result.isSuccessful(),
            "Expected OPTIMAL or FEASIBLE but got ${result.status}. " +
            "HC11 may have made a well-formed configuration infeasible. " +
            "Violations: ${result.violations}",
        )
    }

    // ── HC11 core property ────────────────────────────────────────────────────

    /**
     * Core property test for HC11 with a single stream.
     *
     * Scans every entry in the solved timetable and asserts that no teacher
     * appears in two consecutive within-block periods for the same stream
     * while teaching different subjects.
     */
    @Test
    fun `HC11 - no teacher appears consecutively with different subjects in a single stream`() {
        val result = solver.solve(
            streams           = listOf(stream()),
            teachers          = oneTeacherPerSubject(),
            subjects          = allSubjects,
            timeLimitSeconds  = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")
        assertHc11NotViolated(result.entries)
    }

    /**
     * HC11 most-relevant scenario: a single teacher is qualified for two
     * subjects (Mathematics and Integrated Science) and teaches both to the
     * same stream. Without HC11 the solver might schedule Math at P3 and
     * Science at P4 for the same stream on the same day. HC11 must prevent
     * this while still finding a feasible timetable.
     */
    @Test
    fun `HC11 - teacher qualified for two subjects never appears consecutively with different subjects`() {
        val teacherMatSci = Teacher(
            id                = "teacher-mat-sci",
            maxPeriodsPerWeek = 23,
            maxPeriodsPerDay  = 6,
            subjectIds        = listOf(KenyaCurriculumConfig.MAT, KenyaCurriculumConfig.SCI),
        )
        val remainingTeachers = allSubjects
            .filterNot { it.isPpiFixed || it.id in listOf(KenyaCurriculumConfig.MAT, KenyaCurriculumConfig.SCI) }
            .mapIndexed { i, sub ->
                Teacher(
                    id                = "teacher-$i",
                    maxPeriodsPerWeek = 23,
                    maxPeriodsPerDay  = 6,
                    subjectIds        = listOf(sub.id),
                )
            }

        val result = solver.solve(
            streams           = listOf(stream()),
            teachers          = listOf(teacherMatSci) + remainingTeachers,
            subjects          = allSubjects,
            timeLimitSeconds  = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")
        assertHc11NotViolated(result.entries)
    }

    /**
     * HC11 must hold across every stream independently. With two streams the
     * solver must schedule separate timetables for each and HC11 must not be
     * violated in either.
     */
    @Test
    fun `HC11 - no consecutive same-teacher different-subject appearances across multiple streams`() {
        val result = solver.solve(
            streams           = listOf(stream("s1", name = "Blue"), stream("s2", name = "White")),
            teachers          = oneTeacherPerSubject(),
            subjects          = allSubjects,
            timeLimitSeconds  = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")
        assertHc11NotViolated(result.entries)
    }

    // ── Valid exception: double periods ───────────────────────────────────────

    /**
     * HC11 skips same-subject consecutive pairs (s1 == s2), which is the
     * definition of a double lesson. Several subjects in the CBC curriculum
     * require a double period (SCI, PTS, AGR, CAS). This test verifies that
     * HC11 does not prevent the solver from honouring HC10 — at least one
     * double-period lesson must still appear in the output.
     */
    @Test
    fun `HC11 - valid double periods for practical subjects are still produced`() {
        val result = solver.solve(
            streams           = listOf(stream()),
            teachers          = oneTeacherPerSubject(),
            subjects          = allSubjects,
            timeLimitSeconds  = SOLVER_TIME_LIMIT_SECONDS,
        )

        assertTrue(result.isSuccessful(), "Solver did not find a solution: ${result.status}")
        assertTrue(
            result.entries.any { it.isDoubleStart },
            "No double-period lesson found in the output. " +
            "HC11 may be over-constraining and blocking valid same-subject consecutive pairs.",
        )
        assertHc11NotViolated(result.entries)
    }

    // ── Break boundary ────────────────────────────────────────────────────────

    /**
     * HC11 intentionally does not restrict teacher appearances across break
     * boundaries (P2→P3, P4→P5, P6→P7). This test verifies that the
     * [assertHc11NotViolated] helper correctly excludes break-crossing pairs
     * from the violation check, confirming the boundary logic in both the
     * implementation and the test helper is consistent.
     *
     * The check is indirect: a solved timetable from a feasible configuration
     * may contain a teacher teaching different subjects on either side of a
     * break. The helper must not flag that as a violation.
     */
    @Test
    fun `HC11 - assertion helper does not flag teacher appearances across break boundaries`() {
        // Manually construct entries that cross every break boundary with the
        // same teacher and different subjects. assertHc11NotViolated must pass.
        val crossBreakEntries = listOf(
            // P2 → P3  (first break boundary, 1-based)
            entry(teacherId = "t1", subjectId = "MAT", streamId = "s1", day = "MONDAY", period = 2),
            entry(teacherId = "t1", subjectId = "SCI", streamId = "s1", day = "MONDAY", period = 3),
            // P4 → P5  (second break boundary)
            entry(teacherId = "t1", subjectId = "ENG", streamId = "s1", day = "TUESDAY", period = 4),
            entry(teacherId = "t1", subjectId = "KIS", streamId = "s1", day = "TUESDAY", period = 5),
            // P6 → P7  (lunch boundary)
            entry(teacherId = "t1", subjectId = "SST", streamId = "s1", day = "WEDNESDAY", period = 6),
            entry(teacherId = "t1", subjectId = "RE",  streamId = "s1", day = "WEDNESDAY", period = 7),
        )

        // Must not throw — all pairs cross a break and are exempt from HC11.
        assertHc11NotViolated(crossBreakEntries)
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun entry(
        teacherId: String,
        subjectId: String,
        streamId: String,
        day: String,
        period: Int,
    ) = com.grandtech.model.TimetableEntry(
        teacherId = teacherId,
        subjectId = subjectId,
        streamId  = streamId,
        day       = day,
        period    = period,
    )
}
