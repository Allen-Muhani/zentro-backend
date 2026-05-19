package com.grandtech.school

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.`is`
import org.junit.jupiter.api.Test

/**
 * Tests for `GET /schools` — liveness check endpoint.
 */
@QuarkusTest
open class GetSchoolsTest {

    @Test
    fun `returns 200 success envelope with live message`() {
        given()
            .`when`().get("/schools")
            .then()
                .statusCode(200)
                .body("status",  `is`(200))
                .body("message", `is`("success"))
                .body("payload", `is`("Schools endpoint is live"))
    }
}