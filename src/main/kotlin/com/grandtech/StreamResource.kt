package com.grandtech

import com.grandtech.auth.Authenticated
import com.grandtech.model.Stream
import com.grandtech.service.SchoolService
import com.grandtech.service.StreamService
import com.grandtech.utils.ApiResponse
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
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
     * Creates or updates a stream belonging to the authenticated school.
     *
     * Omit [Stream.id] to create; supply it to update. All business-rule validation
     * (grade level range, name presence, room/teacher uniqueness) is performed by
     * [StreamService] and reflected in the response status.
     */
    @POST
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun upsertStream(
        @Context requestContext: ContainerRequestContext,
        stream: Stream,
    ): ApiResponse<Stream> {
        val fedUid = requestContext.getProperty("fedUid") as String
        schoolService.getSchoolByFedUid(fedUid)
            ?: return ApiResponse(403, "Forbidden: account is not a school", null)
        return streamService.upsertStream(fedUid, stream)
    }
}