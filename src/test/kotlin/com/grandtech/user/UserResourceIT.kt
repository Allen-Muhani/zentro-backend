package com.grandtech.user

import io.quarkus.test.junit.QuarkusIntegrationTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.junit.jupiter.api.Test

/**
 * Integration tests for user endpoints that run against the packaged Quarkus application.
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
                .body("status",  `is`(401))
                .body("message", `is`("Unauthorized: missing Authorization header"))
                .body("payload", nullValue())
    }
}