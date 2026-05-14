package com.grandtech;

import com.grandtech.utils.ApiResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * REST resource exposing school-related endpoints for the Zentro API.
 */
@Path("/schools")
public class SchoolResource {

    /**
     * Returns a confirmation that the schools endpoint is reachable.
     *
     * @return an {@link ApiResponse} with a status message as payload
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ApiResponse<String> getSchools() {
        return new ApiResponse<>(200, "success", "Schools endpoint is live");
    }
}
