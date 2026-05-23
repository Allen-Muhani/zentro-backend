package com.grandtech

import com.grandtech.auth.Authenticated
import com.grandtech.model.Stream
import com.grandtech.service.SchoolService
import com.grandtech.service.StreamService
import com.grandtech.utils.ApiResponse
import io.quarkus.logging.Log
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType

/** REST resource exposing stream management endpoints for the Zentro API. */
@Path("/school/stream")
class StreamResource {

    /** Verifies that the authenticated account belongs to a school. */
    @Inject
    lateinit var schoolService: SchoolService

    /** Validates and persists stream create/update requests. */
    @Inject
    lateinit var streamService: StreamService

    /**
     * Returns all streams belonging to the authenticated school, ordered by grade level then name.
     * Each stream includes its optional `FORM_TEACHER` relationship.
     */
    @GET
    @Path("/list")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    fun listStreams(
        @Context requestContext: ContainerRequestContext,
    ): ApiResponse<List<Stream>> {
        Log.info("listStreams()---->")
        val fedUid = requestContext.getProperty("fedUid") as String
        schoolService.getSchoolByFedUid(fedUid)
            ?: return ApiResponse(403, "Forbidden: account is not a school", null)
        return streamService.listStreams(fedUid)
    }

    /**
     * Creates or updates a stream belonging to the authenticated school.
     *
     * Omit [Stream.id] to create; supply it to update. All business-rule validation
     * (grade level range, name presence, teacher uniqueness) is performed by
     * [StreamService] and reflected in the response status.
     */
    @POST
    @Path("/upsert")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun upsertStream(
        @Context requestContext: ContainerRequestContext,
        stream: Stream,
    ): ApiResponse<Stream> {
        Log.info("upsertStream()---->")
        val fedUid = requestContext.getProperty("fedUid") as String
        schoolService.getSchoolByFedUid(fedUid)
            ?: return ApiResponse(403, "Forbidden: account is not a school", null)
        return streamService.upsertStream(fedUid, stream)
    }

    /** Deletes the stream identified by [id] belonging to the authenticated school. */
    @DELETE
    @Path("/delete/{id}")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    fun deleteStream(
        @Context requestContext: ContainerRequestContext,
        @PathParam("id") id: String,
    ): ApiResponse<Nothing> {
        Log.info("deleteStream()---->")
        val fedUid = requestContext.getProperty("fedUid") as String
        schoolService.getSchoolByFedUid(fedUid)
            ?: return ApiResponse(403, "Forbidden: account is not a school", null)
        return streamService.deleteStream(fedUid, id)
    }
}