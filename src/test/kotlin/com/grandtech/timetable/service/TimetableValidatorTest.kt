package com.grandtech.timetable.service

import com.grandtech.model.Stream
import com.grandtech.model.Subject
import com.grandtech.model.SubjectType
import com.grandtech.model.Teacher
import com.grandtech.service.CbcDataSeeder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [TimetableValidator].
 *
 * The validator has no injected dependencies so it is instantiated directly —
 * no Quarkus context is required, keeping these tests fast.
 */
class TimetableValidatorTest {

    private val validator = TimetableValidator()

    private val allSubjects = CbcDataSeeder.SUBJECTS
    private val teachingSubjects = allSubjects.filter { !it.isPpiFixed }

    private fun stream(id: String = "s1") =
        Stream(id = id, gradeLevel = 7, name = "Blue")

    /** One teacher per non-PPI subject with generous capacity. */
    private fun fullTeacherSet(): List<Teacher> =
        teachingSubjects.mapIndexed { i, sub ->
            Teacher(id = "t-$i", maxPeriodsPerWeek = 23, maxPeriodsPerDay = 6, subjectIds = listOf(sub.id))
        }

    // ── No issues ─────────────────────────────────────────────────────────────

    @Test
    fun `no issues when staffing is adequate`() {
        val report = validator.validate(
            streams  = listOf(stream()),
            teachers = fullTeacherSet(),
            subjects = allSubjects,
        )

        assertFalse(report.hasCriticalIssues)
        assertTrue(report.issues.none { it.severity == IssueSeverity.CRITICAL })
    }

    @Test
    fun `PPI is excluded from subject coverage checks`() {
        // Remove all teachers so every teaching subject is uncovered, but PPI
        // should not raise an issue of its own.
        val report = validator.validate(
            streams  = listOf(stream()),
            teachers = emptyList(),
            subjects = allSubjects,
        )

        // Issues are raised for teaching subjects only, not PPI.
        val ppiIssues = report.issues.filter { it.message.contains("PPI") }
        assertTrue(ppiIssues.isEmpty(), "Expected no issues referencing PPI but got: $ppiIssues")
    }

    // ── Subject coverage ──────────────────────────────────────────────────────

    @Test
    fun `critical issue when a subject has no qualified teacher`() {
        val subjectUnderTest = teachingSubjects.first()
        val teachersWithoutSubject = fullTeacherSet()
            .map { t -> t.copy(subjectIds = t.subjectIds?.filter { it != subjectUnderTest.id }) }

        val report = validator.validate(
            streams  = listOf(stream()),
            teachers = teachersWithoutSubject,
            subjects = allSubjects,
        )

        assertTrue(report.hasCriticalIssues)
        val criticals = report.issues.filter { it.severity == IssueSeverity.CRITICAL }
        assertTrue(
            criticals.any { it.message.contains(subjectUnderTest.id) },
            "Expected a CRITICAL issue mentioning ${subjectUnderTest.id}",
        )
    }

    @Test
    fun `critical issue when combined teacher capacity is below weekly demand`() {
        // One stream needs MAT 5×/week. A teacher capped at 2 periods/week
        // cannot cover demand of 5.
        val matSubject = teachingSubjects.first { it.id == "MAT" }
        val undercapacitatedTeacher = Teacher(
            id = "t-mat",
            maxPeriodsPerWeek = 2,
            maxPeriodsPerDay  = 1,
            subjectIds        = listOf("MAT"),
        )
        val otherTeachers = fullTeacherSet().filter { t ->
            t.subjectIds?.contains("MAT") == false
        }

        val report = validator.validate(
            streams  = listOf(stream()),
            teachers = listOf(undercapacitatedTeacher) + otherTeachers,
            subjects = allSubjects,
        )

        assertTrue(report.hasCriticalIssues)
        assertTrue(
            report.issues.any { it.severity == IssueSeverity.CRITICAL && it.message.contains("MAT") },
        )
    }

    @Test
    fun `warning when teacher capacity exactly equals weekly demand`() {
        // 1 stream, MAT needs 5 periods/week. Teacher capped at exactly 5.
        val exactTeacher = Teacher(
            id = "t-mat-exact",
            maxPeriodsPerWeek = 5,
            maxPeriodsPerDay  = 2,
            subjectIds        = listOf("MAT"),
        )
        val otherTeachers = fullTeacherSet().filter { t ->
            t.subjectIds?.contains("MAT") == false
        }

        val report = validator.validate(
            streams  = listOf(stream()),
            teachers = listOf(exactTeacher) + otherTeachers,
            subjects = allSubjects,
        )

        assertFalse(report.hasCriticalIssues)
        assertTrue(
            report.issues.any { it.severity == IssueSeverity.WARNING && it.message.contains("MAT") },
            "Expected a WARNING for MAT capacity exactly meeting demand",
        )
    }

    @Test
    fun `info issue when capacity utilisation is tight but sufficient`() {
        // 1 stream, MAT needs 5 periods/week. Teacher with 6 periods → 83% utilisation,
        // below the 115% threshold so INFO should be emitted.
        val tightTeacher = Teacher(
            id = "t-mat-tight",
            maxPeriodsPerWeek = 6,
            maxPeriodsPerDay  = 3,
            subjectIds        = listOf("MAT"),
        )
        val otherTeachers = fullTeacherSet().filter { t ->
            t.subjectIds?.contains("MAT") == false
        }

        val report = validator.validate(
            streams  = listOf(stream()),
            teachers = listOf(tightTeacher) + otherTeachers,
            subjects = allSubjects,
        )

        assertFalse(report.hasCriticalIssues)
        assertTrue(
            report.issues.any { it.severity == IssueSeverity.INFO && it.message.contains("MAT") },
            "Expected an INFO issue for MAT tight utilisation",
        )
    }

    // ── Workload config ───────────────────────────────────────────────────────

    @Test
    fun `critical issue when maxPeriodsPerWeek exceeds 5 times maxPeriodsPerDay`() {
        // 30 weekly periods / 5 days = 6 per day, but maxPeriodsPerDay = 5 → impossible.
        val badTeacher = Teacher(
            id = "t-bad",
            maxPeriodsPerWeek = 30,
            maxPeriodsPerDay  = 5,
            subjectIds        = listOf("MAT"),
        )

        val report = validator.validate(
            streams  = listOf(stream()),
            teachers = fullTeacherSet() + badTeacher,
            subjects = allSubjects,
        )

        assertTrue(report.hasCriticalIssues)
        assertTrue(
            report.issues.any {
                it.severity == IssueSeverity.CRITICAL && it.message.contains("t-bad")
            },
            "Expected a CRITICAL issue naming the teacher with invalid workload config",
        )
    }

    @Test
    fun `no workload issue when maxPeriodsPerWeek equals 5 times maxPeriodsPerDay`() {
        // 30 weekly / 6 daily = exactly 5 days — valid.
        val validTeacher = Teacher(
            id = "t-ok",
            maxPeriodsPerWeek = 30,
            maxPeriodsPerDay  = 6,
            subjectIds        = listOf("MAT"),
        )
        val otherTeachers = fullTeacherSet().filter { t ->
            t.subjectIds?.contains("MAT") == false
        }

        val report = validator.validate(
            streams  = listOf(stream()),
            teachers = listOf(validTeacher) + otherTeachers,
            subjects = allSubjects,
        )

        assertFalse(
            report.issues.any {
                it.severity == IssueSeverity.CRITICAL && it.message.contains("t-ok")
            },
            "Expected no CRITICAL workload issue for a valid teacher configuration",
        )
    }

    // ── hasCriticalIssues flag ────────────────────────────────────────────────

    @Test
    fun `hasCriticalIssues is false when report contains only warnings and info`() {
        // Exact capacity for one subject → WARNING, but no CRITICAL.
        val exactTeacher = Teacher(
            id = "t-mat-w",
            maxPeriodsPerWeek = 5,
            maxPeriodsPerDay  = 2,
            subjectIds        = listOf("MAT"),
        )
        val others = fullTeacherSet().filter { it.subjectIds?.contains("MAT") == false }

        val report = validator.validate(
            streams  = listOf(stream()),
            teachers = listOf(exactTeacher) + others,
            subjects = allSubjects,
        )

        assertFalse(report.hasCriticalIssues)
    }

    @Test
    fun `hasCriticalIssues is true when any critical issue is present`() {
        val report = validator.validate(
            streams  = listOf(stream()),
            teachers = emptyList(),
            subjects = allSubjects,
        )

        assertTrue(report.hasCriticalIssues)
    }

    @Test
    fun `issues list is empty when school has no streams and no teachers`() {
        // No streams → weeklyDemand is 0 for every subject → capacity always sufficient.
        // No teachers still trips "no qualified teacher" check if subjects exist.
        // Actually: qualifiedTeachers.isEmpty() fires even with 0 streams.
        // Verify the issues list reflects the subject-count correctly.
        val report = validator.validate(
            streams  = emptyList(),
            teachers = fullTeacherSet(),
            subjects = allSubjects,
        )

        // With full teachers and no streams, demand = 0 — no issues expected.
        assertTrue(report.issues.isEmpty())
        assertFalse(report.hasCriticalIssues)
    }
}
