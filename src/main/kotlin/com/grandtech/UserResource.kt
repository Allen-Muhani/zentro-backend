package com.grandtech

import com.google.firebase.auth.FirebaseAuthException
import com.grandtech.model.School
import com.grandtech.model.Teacher
import com.grandtech.model.User
import com.grandtech.service.UserService
import com.grandtech.utils.ApiResponse
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType

/**
 * REST resource exposing user account endpoints for the Zentro API.
 *
 * All endpoints require a valid Firebase ID token in the `Authorization` header
 * (e.g. `Bearer <token>`).
 */
@Path("/users")
class UserResource {

    /** Service that handles user lookup and registration logic. */
    @Inject
    lateinit var userService: UserService

    /**
     * Returns the authenticated user's profile.
     *
     * @param authHeader the Authorization header containing the Firebase ID token
     * @return an [ApiResponse] carrying the [User] on success,
     *         or a 401 response if the token is missing or invalid
     */
    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    fun getCurrentUser(@HeaderParam(HttpHeaders.AUTHORIZATION) authHeader: String?): ApiResponse<User> {
        if (authHeader.isNullOrBlank()) {
            return ApiResponse(401, "Unauthorized: missing Authorization header", null)
        }
        return try {
            ApiResponse(200, "success", userService.getUserFromToken(authHeader))
        } catch (e: FirebaseAuthException) {
            ApiResponse(401, "Unauthorized: ${e.message}", null)
        }
    }

    /**
     * Registers a new teacher account for the authenticated Firebase user.
     *
     * @param authHeader the Authorization header containing the Firebase ID token
     * @return an [ApiResponse] carrying the new [Teacher] on success,
     *         or a 401 if the Authorization header is missing
     */
    @POST
    @Path("/register/teacher")
    @Produces(MediaType.APPLICATION_JSON)
    fun registerTeacher(@HeaderParam(HttpHeaders.AUTHORIZATION) authHeader: String?): ApiResponse<Teacher> {
        if (authHeader.isNullOrBlank()) {
            return ApiResponse(401, "Unauthorized: missing Authorization header", null)
        }
        return userService.registerTeacher(authHeader)
    }

    /**
     * Registers a new school account for the authenticated Firebase user.
     *
     * @param authHeader the Authorization header containing the Firebase ID token
     * @return an [ApiResponse] carrying the new [School] on success,
     *         or a 401 if the Authorization header is missing
     */
    @POST
    @Path("/register/school")
    @Produces(MediaType.APPLICATION_JSON)
    fun registerSchool(@HeaderParam(HttpHeaders.AUTHORIZATION) authHeader: String?): ApiResponse<School> {
        if (authHeader.isNullOrBlank()) {
            return ApiResponse(401, "Unauthorized: missing Authorization header", null)
        }
        return userService.registerSchool(authHeader)
    }
}
