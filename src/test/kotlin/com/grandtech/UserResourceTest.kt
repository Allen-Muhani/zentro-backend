package com.grandtech

import com.google.firebase.auth.FirebaseAuthException
import com.grandtech.model.Teacher
import com.grandtech.service.UserService
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Unit tests for [UserResource] HTTP endpoints.
 *
 * [UserService] is replaced with a Mockito mock so that these tests exercise
 * only the HTTP layer (header extraction, status routing) without touching Neo4j
 * or Firebase.
 */
@QuarkusTest
class UserResourceTest {

    @InjectMock
    lateinit var userService: UserService

    /**
     * Verifies that `GET /users/me` returns the user payload when a valid
     * Firebase token is supplied in the Authorization header.
     */
    @Test
    fun `get current user with valid token returns the user payload`() {
        val mockUser = Teacher(
            fedUid = "firebase-uid-123",
            email = "user@example.com",
            name = "Test User",
        )
        Mockito.`when`(userService.getUserFromToken("Bearer valid-token")).thenReturn(mockUser)

        given()
            .header("Authorization", "Bearer valid-token")
            .`when`().get("/users/me")
            .then()
                .statusCode(200)
                .body("status", `is`(200))
                .body("message", `is`("success"))
                .body("payload.fedUid", `is`("firebase-uid-123"))
                .body("payload.email", `is`("user@example.com"))
                .body("payload.name", `is`("Test User"))
    }

    /**
     * Verifies that `GET /users/me` returns a 401 envelope when the service
     * throws a [FirebaseAuthException] for an invalid or expired token.
     */
    @Test
    fun `get current user with invalid token returns 401 envelope`() {
        val authException = Mockito.mock(FirebaseAuthException::class.java)
        Mockito.`when`(authException.message).thenReturn("invalid id token")
        Mockito.`when`(userService.getUserFromToken("Bearer bad-token")).thenThrow(authException)

        given()
            .header("Authorization", "Bearer bad-token")
            .`when`().get("/users/me")
            .then()
                .statusCode(200)
                .body("status", `is`(401))
                .body("message", `is`("Unauthorized: invalid id token"))
                .body("payload", nullValue())
    }

    /**
     * Verifies that `GET /users/me` returns a 401 envelope when the request
     * contains no Authorization header at all.
     */
    @Test
    fun `get current user without token returns 401 unauthorized`() {
        given()
            .`when`().get("/users/me")
            .then()
                .statusCode(200)
                .body("status", `is`(401))
                .body("message", `is`("Unauthorized: missing Authorization header"))
                .body("payload", nullValue())
    }
}