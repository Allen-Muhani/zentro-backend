package com.grandtech

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.`is`
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SchoolResource] HTTP endpoints.
 *
 * Verifies that the schools REST layer is wired up correctly and returns the
 * expected JSON envelope for each supported operation.
 */
@QuarkusTest
open class SchoolResourceTest {

    /**
     * Verifies that `GET /schools` returns a 200 envelope with the live-check
     * payload string when the endpoint is reachable.
     */
    @Test
    fun `get schools returns 200 success envelope`() {
        given()
            .`when`().get("/schools")
            .then()
                .statusCode(200)
                .body("status", `is`(200))
                .body("message", `is`("success"))
                .body("payload", `is`("Schools endpoint is live"))
    }
}