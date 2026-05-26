package com.grandtech.timetable.solver

import com.grandtech.model.Stream
import com.grandtech.model.Subject
import com.grandtech.model.Teacher
import com.grandtech.model.TimetableEntry
import com.grandtech.service.CbcDataSeeder
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.fail

/**
 * Shared fixtures and assertion helpers for [TimetableSolver] tests.
 *
 * Solver tests do not touch Neo4j — no cleanup is needed. The [QuarkusTest]
 * annotation is required only to satisfy CDI injection of [TimetableSolver]
 * and its [com.grandtech.timetable.config.KenyaCurriculumConfig] dependency.
 */
@QuarkusTest
abstract class TimetableSolverTestBase {

    @Inject
    lateinit var solver: TimetableSolver

    /** All CBC JSS subjects including PPI; the solver filters PPI internally. */
    protected val allSubjects: List<Subject> = CbcDataSeeder.SUBJECTS

    /**
     * Builds a [Stream] with sensible defaults for use in solver tests.
     *
     * @param id         unique string id for this stream
     * @param gradeLevel JSS grade level (7, 8, or 9)
     * @param name       stream name, e.g. "Blue"
     */
    protected fun stream(
        id: String = "stream-1",
        gradeLevel: Int = 7,
        name: String = "Blue",
    ) = Stream(id = id, gradeLevel = gradeLevel, name = name)

    /**
     * Creates one [Teacher] per non-PPI subject, each qualified for exactly
     * that one subject. Produces the minimum teacher set needed to cover all
     * learning areas for a single stream without over-loading any teacher.
     */
    protected fun oneTeacherPerSubject(): List<Teacher> =
        allSubjects
            .filterNot { it.isPpiFixed }
            .mapIndexed { i, sub ->
                Teacher(
                    id                = "teacher-$i",
                    maxPeriodsPerWeek = 23,
                    maxPeriodsPerDay  = 6,
                    subjectIds        = listOf(sub.id),
                )
            }

    /**
     * Scans [entries] for HC11 violations and fails the test if any are found.
     *
     * HC11 rule: a teacher may not appear in two consecutive within-block
     * periods for the same stream while teaching different subjects. A
     * "within-block consecutive pair" is a pair `(P, P+1)` that does not
     * cross a break boundary. Break boundaries in this curriculum fall after
     * periods 2, 4, and 6 (1-based), so the constrained pairs are:
     * `(P1,P2)`, `(P3,P4)`, `(P5,P6)`, `(P7,P8)`.
     *
     * Pairs that cross a break (P2→P3, P4→P5, P6→P7) are deliberately
     * excluded — the intervening break provides natural separation and HC11
     * does not restrict those pairs.
     *
     * @param entries the flat list of [TimetableEntry] objects produced by the solver
     */
    protected fun assertHc11NotViolated(entries: List<TimetableEntry>) {
        // 1-based period numbers after which a break or lunch falls.
        val breakAfterPeriods = setOf(2, 4, 6)

        entries
            .groupBy { "${it.streamId}_${it.day}" }
            .forEach { (_, dayEntries) ->
                dayEntries
                    .sortedBy { it.period ?: 0 }
                    .zipWithNext()
                    .forEach { (curr, next) ->
                        val isConsecutive    = (next.period ?: 0) == (curr.period ?: 0) + 1
                        val crossesBreak     = (curr.period ?: 0) in breakAfterPeriods
                        val sameTeacher      = curr.teacherId != null &&
                                              curr.teacherId == next.teacherId
                        val differentSubject = curr.subjectId != next.subjectId

                        if (isConsecutive && !crossesBreak && sameTeacher && differentSubject) {
                            fail(
                                "HC11 violated: teacher '${curr.teacherId}' appears in " +
                                "consecutive periods P${curr.period} ('${curr.subjectId}') and " +
                                "P${next.period} ('${next.subjectId}') for stream " +
                                "'${curr.streamId}' on ${curr.day}",
                            )
                        }
                    }
            }
    }
}
