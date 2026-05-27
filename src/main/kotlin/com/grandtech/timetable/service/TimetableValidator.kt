package com.grandtech.timetable.service

import com.grandtech.model.Stream
import com.grandtech.model.Subject
import com.grandtech.model.Teacher
import jakarta.enterprise.context.ApplicationScoped

/** Severity of a pre-solve diagnostic issue. */
enum class IssueSeverity {
    /** Issue prevents the solver from running; the run will be marked FAILED. */
    CRITICAL,
    /** Issue is concerning but the solver can still attempt a solution. */
    WARNING,
    /** Informational finding with no impact on solve feasibility. */
    INFO,
}

/** A single pre-solve finding. */
data class DiagnosticIssue(
    /** How serious this issue is — determines whether the solve can proceed. */
    val severity: IssueSeverity,
    /**
     * Human-readable description of the issue, suitable for
     * display in the API response.
     */
    val message: String,
)

/** Pre-solve analysis result. If [hasCriticalIssues] is true the solver should not run. */
data class DiagnosticReport(
    /**
     * True when at least one issue with [IssueSeverity.CRITICAL]
     * severity is present.
     */
    val hasCriticalIssues: Boolean,
    /** All issues found, ordered by detection (not severity). */
    val issues: List<DiagnosticIssue>,
)

/**
 * Validates a school's staffing and configuration before the Timefold solver runs.
 *
 * Ported from the zentro Java implementation. Rooms are not checked in this
 * version as room nodes are not yet modelled.
 */
@ApplicationScoped
class TimetableValidator {

    /**
     * Runs all pre-solve checks and returns a [DiagnosticReport].
     *
     * @param streams  all streams for the school
     * @param teachers all teachers with their qualified subject IDs and workload limits
     * @param subjects all CBC subjects (including PPI)
     * @return a report containing all findings; check [DiagnosticReport.hasCriticalIssues] before solving
     */
    fun validate(streams: List<Stream>, teachers: List<Teacher>, subjects: List<Subject>): DiagnosticReport {
        val issues = mutableListOf<DiagnosticIssue>()
        val teachingSubjects = subjects.filter { !it.isPpiFixed }

        checkSubjectCoverage(issues, streams, teachers, teachingSubjects)
        checkWorkloadConfig(issues, teachers)

        return DiagnosticReport(
            hasCriticalIssues = issues.any { it.severity == IssueSeverity.CRITICAL },
            issues = issues,
        )
    }

    /** Checks that every teaching subject has at least one qualified teacher and sufficient capacity. */
    private fun checkSubjectCoverage(
        issues: MutableList<DiagnosticIssue>,
        streams: List<Stream>,
        teachers: List<Teacher>,
        teachingSubjects: List<Subject>,
    ) {
        val gradeCount = streams.size

        for (subject in teachingSubjects) {
            val qualifiedTeachers = teachers.filter { t ->
                t.subjectIds?.contains(subject.id) == true
            }

            if (qualifiedTeachers.isEmpty()) {
                issues.add(
                    DiagnosticIssue(
                        severity = IssueSeverity.CRITICAL,
                        message = "No qualified teacher for ${subject.name} (${subject.id}). " +
                            "Timetable cannot be generated.",
                    ),
                )
                continue
            }

            val weeklyDemand = subject.periodsPerWeek * gradeCount
            val weeklyCapacity = qualifiedTeachers.sumOf { it.maxPeriodsPerWeek ?: 23 }

            when {
                weeklyCapacity < weeklyDemand -> issues.add(
                    DiagnosticIssue(
                        severity = IssueSeverity.CRITICAL,
                        message = "${subject.name}: demand=${weeklyDemand} periods/week, " +
                            "capacity=${weeklyCapacity}. Add more ${subject.id} teachers.",
                    ),
                )
                weeklyCapacity == weeklyDemand -> issues.add(
                    DiagnosticIssue(
                        severity = IssueSeverity.WARNING,
                        message = "${subject.name} (${subject.id}): capacity exactly meets demand " +
                            "(${weeklyDemand} periods/week). No scheduling slack.",
                    ),
                )
                weeklyCapacity.toDouble() / weeklyDemand < 1.15 -> issues.add(
                    DiagnosticIssue(
                        severity = IssueSeverity.INFO,
                        message = "${subject.name} (${subject.id}): ${qualifiedTeachers.size} teacher(s), " +
                            "capacity utilisation ${weeklyDemand * 100 / weeklyCapacity}%.",
                    ),
                )
            }
        }
    }

    /** Checks that each teacher's workload config is internally consistent. */
    private fun checkWorkloadConfig(issues: MutableList<DiagnosticIssue>, teachers: List<Teacher>) {
        for (teacher in teachers) {
            val weekly = teacher.maxPeriodsPerWeek ?: 23
            val daily = teacher.maxPeriodsPerDay ?: 6
            if (weekly > 5 * daily) {
                issues.add(
                    DiagnosticIssue(
                        severity = IssueSeverity.CRITICAL,
                        message = "Teacher '${teacher.id}': maxPeriodsPerWeek ($weekly) > " +
                            "5 × maxPeriodsPerDay ($daily). Constraint is unsatisfiable.",
                    ),
                )
            }
        }
    }
}
