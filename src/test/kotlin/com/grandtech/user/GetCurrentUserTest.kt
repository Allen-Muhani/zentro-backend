package com.grandtech.user

import com.google.firebase.auth.FirebaseAuthException
import com.grandtech.model.School
import com.grandtech.service.UserService
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Tests for `GET /users/me`.
 */
@QuarkusTest
class GetCurrentUserTest {

    @InjectMock
    lateinit var userService: UserService

    @Test
    fun `with valid token returns the user payload`() {
        val mockUser = School(fedUid = "firebase-uid-123", email = "school@example.com", name = "Test School")
        Mockito.`when`(userService.getUserFromToken("Bearer valid-token")).thenReturn(mockUser)

        given()
            .header("Authorization", "Bearer valid-token")
            .`when`().get("/users/me")
            .then()
                .statusCode(200)
                .body("status",         `is`(200))
                .body("message",        `is`("success"))
                .body("payload.fedUid", `is`("firebase-uid-123"))
                .body("payload.email",  `is`("school@example.com"))
                .body("payload.name",   `is`("Test School"))
    }

    @Test
    fun `with invalid token returns 401 envelope`() {
        val authException = Mockito.mock(FirebaseAuthException::class.java)
        Mockito.`when`(authException.message).thenReturn("invalid id token")
        Mockito.`when`(userService.getUserFromToken("Bearer bad-token")).thenThrow(authException)

        given()
            .header("Authorization", "Bearer bad-token")
            .`when`().get("/users/me")
            .then()
                .statusCode(200)
                .body("status",  `is`(401))
                .body("message", `is`("Unauthorized: invalid id token"))
                .body("payload", nullValue())
    }

    @Test
    fun `without auth header returns 401 unauthorized`() {
        given()
            .`when`().get("/users/me")
            .then()
                .statusCode(200)
                .body("status",  `is`(401))
                .body("message", `is`("Unauthorized: missing Authorization header"))
                .body("payload", nullValue())
    }
}