package com.grandtech

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.junit.jupiter.api.Test

/**
 * Integration tests for [UserResource] that run against a packaged Quarkus application.
 *
 * These tests verify the full request-response cycle including CDI wiring, HTTP
 * routing, and JSON serialisation — without mocking any internal dependencies.
 */
@QuarkusIntegrationTest
class UserResourceIT {

    /**
     * Verifies that `GET /users/me` returns a 401 envelope when no Authorization
     * header is present, exercising the packaged application end-to-end.
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