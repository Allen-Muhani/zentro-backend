package com.grandtech.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.grandtech.model.TimetableRun
import com.grandtech.repository.StreamRepository
import com.grandtech.repository.SubjectRepository
import com.grandtech.repository.TeacherRepository
import com.grandtech.repository.TimetableRepository
import com.grandtech.timetable.service.TimetableValidator
import com.grandtech.timetable.solver.TimetableSolver
import com.grandtech.utils.ApiResponse
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Orchestrates timetable generation for a school.
 *
 * Split into two methods so the HTTP handler can return immediately:
 * - [initRun] creates the run node and returns its ID synchronously.
 * - [executeSolve] loads data, validates, runs the solver, and persists results.
 *   Called from [com.grandtech.timetable.SolverWorker] on a background thread.
 */
@ApplicationScoped
class TimetableRunService {

    /** Persists and reads timetable run and entry nodes. */
    @Inject
    lateinit var timetableRepository: TimetableRepository

    /** Reads stream data for the school under solve. */
    @Inject
    lateinit var streamRepository: StreamRepository

    /** Reads teacher data for the school under solve. */
    @Inject
    lateinit var teacherRepository: TeacherRepository

    /** Reads CBC subject data for to solve. */
    @Inject
    lateinit var subjectRepository: SubjectRepository

    /** CP-SAT solver that produces the timetable entries. */
    @Inject
    lateinit var solver: TimetableSolver

    /** Pre-solve validator that checks staffing before the solver runs. */
    @Inject
    lateinit var validator: TimetableValidator

    /** Jackson mapper used to serialise diagnostic reports to JSON. */
    @Inject
    lateinit var objectMapper: ObjectMapper

    /**
     * Creates a `TimetableRun` node with status RUNNING and returns it.
     * Commits immediately so the run ID is available for polling
     * before the solver starts.
     *
     * @param schoolFedUid    fedUid of the school requesting generation
     * @param academicYear    academic year label (e.g. "2024")
     * @param term            term label (e.g. "Term 1")
     * @param timeLimitSeconds solver wall-clock time cap in seconds
     * @return 200 response with the created [TimetableRun], or 500 on
     *         persistence failure
     */
    fun initRun(schoolFedUid: String, academicYear: String, term: String, timeLimitSeconds: Int): ApiResponse<TimetableRun> {
        val runId = timetableRepository.createRun(schoolFedUid, academicYear, term, timeLimitSeconds)
        val run = timetableRepository.getRun(runId) ?: return ApiResponse(500, "Failed to create timetable run", null)
        return ApiResponse(message = "Timetable generation started", payload = run)
    }

    /**
     * Loads school data, validates staffing, runs the CP-SAT solver,
     * and persists the result.
     * Called asynchronously by [com.grandtech.timetable.SolverWorker].
     *
     * @param runId UUID of the [TimetableRun] node created by [initRun]
     */
    fun executeSolve(runId: String) {
        val run = timetableRepository.getRun(runId)
        if (run == null) {
            Log.errorf("executeSolve: run %s not found — aborting.", runId)
            return
        }
        val schoolFedUid = run.schoolFedUid ?: return
        val timeLimitSeconds = run.timeLimitSeconds ?: 120

        try {
            val streams = streamRepository.listStreams(schoolFedUid)
            val teachers = teacherRepository.listTeachers(schoolFedUid)
            val subjects = subjectRepository.listAll()

            val report = validator.validate(streams, teachers, subjects)
            if (report.hasCriticalIssues) {
                val reportJson = objectMapper.writeValueAsString(report)
                timetableRepository.updateRunFailed(runId, reportJson)
                Log.warnf("Run %s failed pre-solve validation: %d critical issue(s).", runId, report.issues.size)
                return
            }

            val result = solver.solve(streams, teachers, subjects, timeLimitSeconds)

            if (result.isSuccessful()) {
                timetableRepository.saveEntries(runId, result.entries)
                timetableRepository.updateRunCompleted(
                    runId = runId,
                    solverStatus = result.status.name,
                    objectiveValue = result.objectiveValue,
                    wallTimeMs = result.wallTimeMs,
                    violations = result.violations,
                )
                Log.infof("Run %s completed: %s, %d entries.", runId, result.status, result.entries.size)
            } else {
                val violations = result.violations.toMutableList()
                violations.add(0, "Solver status: ${result.status.name}")
                timetableRepository.updateRunCompleted(
                    runId = runId,
                    solverStatus = result.status.name,
                    objectiveValue = 0L,
                    wallTimeMs = result.wallTimeMs,
                    violations = violations,
                )
                Log.warnf("Run %s ended with status %s.", runId, result.status)
            }
        } catch (e: Exception) {
            Log.errorf(e, "Run %s failed with exception.", runId)
            timetableRepository.updateRunFailed(runId, """{"error":"${e.message}"}""")
        }
    }

    /**
     * Returns all timetable runs for [schoolFedUid], newest first.
     *
     * @param schoolFedUid fedUid of the school whose runs to list
     * @return 200 response with the list of runs (may be empty)
     */
    fun listRuns(schoolFedUid: String): ApiResponse<List<TimetableRun>> =
        ApiResponse(payload = timetableRepository.listRuns(schoolFedUid))

    /**
     * Returns a single run by ID, or 404 if not found.
     *
     * @param runId UUID of the run to fetch
     * @return 200 with the run, or 404 if the node does not exist
     */
    fun getRun(runId: String): ApiResponse<TimetableRun> {
        val run = timetableRepository.getRun(runId) ?: return ApiResponse(404, "Timetable run not found", null)
        return ApiResponse(payload = run)
    }
}
