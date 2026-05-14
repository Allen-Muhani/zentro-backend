package com.grandtech

import com.grandtech.utils.ApiResponse
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

/** REST resource exposing school-related endpoints for the Zentro API. */
@Path("/schools")
class SchoolResource {

    /**
     * Returns a confirmation that the schools endpoint is reachable.
     *
     * @return an [ApiResponse] with a status message as payload
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getSchools(): ApiResponse<String> = ApiResponse(200, "success", "Schools endpoint is live")
}
