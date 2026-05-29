package com.grandtech.timetable

import com.grandtech.service.TimetableRunService
import io.quarkus.vertx.ConsumeEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Consumes timetable solve requests from the Vert.x event bus and executes them
 * on a blocking worker thread so the HTTP handler can return immediately.
 *
 * Publish a run ID to the `"timetable.solve"` address to trigger solving.
 */
@ApplicationScoped
class SolverWorker {

    /** Service that orchestrates data loading, validation, and solve execution. */
    @Inject
    lateinit var timetableRunService: TimetableRunService

    /** Receives a run ID and delegates to [TimetableRunService.executeSolve] on a worker thread. */
    @ConsumeEvent(value = "timetable.solve", blocking = true)
    fun solve(runId: String) {
        timetableRunService.executeSolve(runId)
    }
}
