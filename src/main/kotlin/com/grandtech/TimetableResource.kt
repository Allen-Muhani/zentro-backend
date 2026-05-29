package com.grandtech

import com.grandtech.auth.Authenticated
import com.grandtech.model.TimetableEntry
import com.grandtech.model.TimetableRun
import com.grandtech.repository.TimetableRepository
import com.grandtech.service.SchoolService
import com.grandtech.service.TimetableRunService
import com.grandtech.utils.ApiResponse
import io.quarkus.logging.Log
import io.vertx.mutiny.core.eventbus.EventBus
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType

/** REST resource for timetable generation and retrieval. */
@Path("/school/timetable")
class TimetableResource {

    /** Looks up the school entity from the authenticated fedUid. */
    @Inject
    lateinit var schoolService: SchoolService

    /** Orchestrates run creation and solve execution. */
    @Inject
    lateinit var timetableRunService: TimetableRunService

    /** Reads timetable entries directly for stream and teacher views. */
    @Inject
    lateinit var timetableRepository: TimetableRepository

    /** Vert.x event bus used to dispatch the solve job asynchronously. */
    @Inject
    lateinit var eventBus: EventBus

    /**
     * Triggers async timetable generation for the authenticated school.
     * Returns immediately with the run (status=RUNNING);
     * poll [getRun] for completion.
     *
     * @param requestContext JAX-RS context carrying the authenticated fedUid
     * @param body           generation parameters (year, term, time limit)
     * @return 200 with the newly created run, 403 if the account is not
     *         a school, or 500 on persistence failure
     */
    @POST
    @Path("/generate")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun generate(
        @Context requestContext: ContainerRequestContext,
        body: GenerateRequest,
    ): ApiResponse<TimetableRun> {
        Log.info("generate()---->")
        val fedUid = requestContext.getProperty("fedUid") as String
        schoolService.getSchoolByFedUid(fedUid)
            ?: return ApiResponse(403, "Forbidden: account is not a school", null)

        val response = timetableRunService.initRun(
            schoolFedUid = fedUid,
            academicYear = body.academicYear,
            term = body.term,
            timeLimitSeconds = body.timeLimitSeconds ?: 120,
        )
        val runId = response.payload?.id ?: return response

        eventBus.send("timetable.solve", runId)
        return response
    }

    /**
     * Returns all timetable runs for the authenticated school, newest first.
     *
     * @param requestContext JAX-RS context carrying the authenticated fedUid
     * @return 200 with the list of runs, or 403 if not a school account
     */
    @GET
    @Path("/runs")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    fun listRuns(
        @Context requestContext: ContainerRequestContext,
    ): ApiResponse<List<TimetableRun>> {
        Log.info("listRuns()---->")
        val fedUid = requestContext.getProperty("fedUid") as String
        schoolService.getSchoolByFedUid(fedUid)
            ?: return ApiResponse(403, "Forbidden: account is not a school", null)
        return timetableRunService.listRuns(fedUid)
    }

    /**
     * Returns a single timetable run including diagnostic report
     * and violations.
     *
     * @param requestContext JAX-RS context carrying the authenticated fedUid
     * @param runId          UUID of the run to fetch
     * @return 200 with the run, 403 if not a school account, or 404 if
     *         the run does not exist
     */
    @GET
    @Path("/runs/{runId}")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    fun getRun(
        @Context requestContext: ContainerRequestContext,
        @PathParam("runId") runId: String,
    ): ApiResponse<TimetableRun> {
        Log.info("getRun()---->")
        val fedUid = requestContext.getProperty("fedUid") as String
        schoolService.getSchoolByFedUid(fedUid)
            ?: return ApiResponse(403, "Forbidden: account is not a school", null)
        return timetableRunService.getRun(runId)
    }

    /**
     * Returns all timetable entries for a specific stream within a
     * completed run (41 per week).
     *
     * @param requestContext JAX-RS context carrying the authenticated fedUid
     * @param runId          UUID of the completed run
     * @param streamId       ID of the stream whose timetable to retrieve
     * @return 200 with the stream's entries, or 403 if not a school account
     */
    @GET
    @Path("/runs/{runId}/stream/{streamId}")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    fun getStreamTimetable(
        @Context requestContext: ContainerRequestContext,
        @PathParam("runId") runId: String,
        @PathParam("streamId") streamId: String,
    ): ApiResponse<List<TimetableEntry>> {
        Log.info("getStreamTimetable()---->")
        val fedUid = requestContext.getProperty("fedUid") as String
        schoolService.getSchoolByFedUid(fedUid)
            ?: return ApiResponse(403, "Forbidden: account is not a school", null)
        val entries = timetableRepository.getEntriesForStream(runId, streamId)
        return ApiResponse(payload = entries)
    }

    /**
     * Returns all timetable entries for a specific teacher within a
     * completed run.
     *
     * @param requestContext JAX-RS context carrying the authenticated fedUid
     * @param runId          UUID of the completed run
     * @param teacherId      ID of the teacher whose schedule to retrieve
     * @return 200 with the teacher's entries, or 403 if not a school account
     */
    @GET
    @Path("/runs/{runId}/teacher/{teacherId}")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    fun getTeacherTimetable(
        @Context requestContext: ContainerRequestContext,
        @PathParam("runId") runId: String,
        @PathParam("teacherId") teacherId: String,
    ): ApiResponse<List<TimetableEntry>> {
        Log.info("getTeacherTimetable()---->")
        val fedUid = requestContext.getProperty("fedUid") as String
        schoolService.getSchoolByFedUid(fedUid)
            ?: return ApiResponse(403, "Forbidden: account is not a school", null)
        val entries = timetableRepository.getEntriesForTeacher(runId, teacherId)
        return ApiResponse(payload = entries)
    }

    /** Request body for the generate endpoint. */
    data class GenerateRequest(
        /** Academic year label (e.g. "2024"). */
        val academicYear: String,
        /** Term label (e.g. "Term 1"). */
        val term: String,
        /** Solver time limit in seconds. Defaults to 120 if omitted. */
        val timeLimitSeconds: Int? = null,
    )
}
