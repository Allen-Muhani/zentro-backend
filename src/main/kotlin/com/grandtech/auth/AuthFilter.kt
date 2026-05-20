package com.grandtech.auth

import com.google.firebase.auth.FirebaseAuthException
import com.grandtech.repository.UserRepository
import com.grandtech.utils.ApiResponse
import jakarta.annotation.Priority
import jakarta.inject.Inject
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider

/**
 * JAX-RS request filter that enforces authentication on endpoints annotated with [Authenticated].
 *
 * Runs at [Priorities.AUTHENTICATION] priority (1000), before any other filters or handlers.
 * On success, the verified Firebase UID is stored as request property `"fedUid"` so
 * downstream handlers can retrieve it without re-verifying the token.
 */
@Authenticated
@Provider
@Priority(Priorities.AUTHENTICATION)
class AuthFilter : ContainerRequestFilter {

    /** Service used to verify Firebase ID tokens. */
    @Inject
    lateinit var firebaseAuthService: FirebaseAuthService

    /** Repository used to confirm the Firebase UID has a registered node in Neo4j. */
    @Inject
    lateinit var userRepository: UserRepository

    /**
     * Intercepts the request and aborts with 401 if authentication fails.
     *
     * Checks performed in order:
     * 1. `Authorization` header is present and non-blank.
     * 2. The bearer token is valid according to Firebase.
     * 3. The resolved Firebase UID belongs to a registered user in Neo4j.
     *
     * @param ctx the JAX-RS request context; [ContainerRequestContext.abortWith] is called on failure
     */
    override fun filter(ctx: ContainerRequestContext) {
        val authHeader = ctx.getHeaderString(HttpHeaders.AUTHORIZATION)

        if (authHeader.isNullOrBlank()) {
            abort(ctx, "Missing Authorization header")
            return
        }

        val token = try {
            firebaseAuthService.verifyToken(authHeader)
        } catch (e: FirebaseAuthException) {
            abort(ctx, "Invalid or expired token: ${e.message}")
            return
        }

        if (!userRepository.existsByFedUid(token.uid)) {
            abort(ctx, "User not registered")
            return
        }

        ctx.setProperty("fedUid", token.uid)
    }

    /**
     * Aborts the request with an HTTP 401 JSON response.
     *
     * @param ctx     the request context to abort
     * @param message the human-readable reason included in the response body
     */
    private fun abort(ctx: ContainerRequestContext, message: String) {
        ctx.abortWith(
            Response.status(Response.Status.UNAUTHORIZED)
                .type(MediaType.APPLICATION_JSON)
                .entity(ApiResponse(401, "Unauthorized: $message", null))
                .build(),
        )
    }
}
