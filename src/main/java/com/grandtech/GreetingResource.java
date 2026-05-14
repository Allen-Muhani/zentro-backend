package com.grandtech;

import com.grandtech.utils.ApiResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * REST resource exposing greeting endpoints for the Zentro API.
 */
@Path("/hello")
public class GreetingResource {

    /**
     * Returns a greeting wrapped in a standard API response envelope.
     *
     * @return an {@link ApiResponse} carrying the greeting string as payload
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ApiResponse<String> hello() {
        return new ApiResponse<>(200, "success", "Hello from Quarkus REST");
    }
}
