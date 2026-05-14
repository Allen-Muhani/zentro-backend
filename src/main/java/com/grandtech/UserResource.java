package com.grandtech;

import com.google.firebase.auth.FirebaseAuthException;
import com.grandtech.model.School;
import com.grandtech.model.Teacher;
import com.grandtech.model.User;
import com.grandtech.service.UserService;
import com.grandtech.utils.ApiResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

/**
 * REST resource exposing user account endpoints for the Zentro API.
 *
 * <p>All endpoints require a valid Firebase ID token in the
 * {@code Authorization} header (e.g. {@code Bearer <token>}).
 */
@Path("/users")
public class UserResource {

    /** Service that handles user lookup and registration logic. */
    @Inject
    UserService userService;

    /**
     * Returns the authenticated user's profile.
     *
     * @param authHeader the {@code Authorization} header containing the Firebase ID token
     * @return an {@link ApiResponse} carrying the {@link User} on success,
     *         or a 401 response if the token is missing or invalid
     */
    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    public ApiResponse<User> getCurrentUser(
            @HeaderParam(HttpHeaders.AUTHORIZATION) final String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return new ApiResponse<>(401, "Unauthorized: missing Authorization header", null);
        }
        try {
            return new ApiResponse<>(200, "Success", userService.getUserFromToken(authHeader));
        } catch (FirebaseAuthException e) {
            return new ApiResponse<>(401, "Unauthorized: " + e.getMessage(), null);
        }
    }

    /**
     * Registers a new teacher account for the authenticated Firebase user.
     *
     * <p>Creates the base user record and an empty teacher profile. Profile
     * fields (name, email, TSC number) must be completed via a separate
     * profile-update request.
     *
     * @param authHeader the {@code Authorization} header containing the Firebase ID token
     * @return an {@link ApiResponse} carrying the new {@link Teacher} on success,
     *         or a 401 if the Authorization header is missing
     */
    @POST
    @Path("/register/teacher")
    @Produces(MediaType.APPLICATION_JSON)
    public ApiResponse<Teacher> registerTeacher(
            @HeaderParam(HttpHeaders.AUTHORIZATION) final String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return new ApiResponse<>(401, "Unauthorized: missing Authorization header", null);
        }
        return userService.registerTeacher(authHeader);
    }

    /**
     * Registers a new school account for the authenticated Firebase user.
     *
     * <p>Creates the base user record and an empty school profile. Profile
     * fields (name, email, phone, county, sub-county) must be completed via
     * a separate profile-update request.
     *
     * @param authHeader the {@code Authorization} header containing the Firebase ID token
     * @return an {@link ApiResponse} carrying the new {@link School} on success,
     *         or a 401 if the Authorization header is missing
     */
    @POST
    @Path("/register/school")
    @Produces(MediaType.APPLICATION_JSON)
    public ApiResponse<School> registerSchool(
            @HeaderParam(HttpHeaders.AUTHORIZATION) final String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return new ApiResponse<>(401, "Unauthorized: missing Authorization header", null);
        }
        return userService.registerSchool(authHeader);
    }
}
