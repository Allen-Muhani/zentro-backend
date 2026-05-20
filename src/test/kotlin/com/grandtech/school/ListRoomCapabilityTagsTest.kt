package com.grandtech.school

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.hasItems
import org.junit.jupiter.api.Test

/**
 * Tests for `GET /school/rooms/capability-tags`.
 */
@QuarkusTest
class ListRoomCapabilityTagsTest {

    @Test
    fun `returns 200 with all capability tags`() {
        given()
            .`when`().get("/school/room/capability-tags")
            .then()
                .statusCode(200)
                .body("status",  `is`(200))
                .body("message", `is`("Success"))
                .body("payload", hasItems("SCIENCE_LAB", "WORKSHOP", "GARDEN", "COMPUTER_LAB", "GAMES_FIELD"))
    }

    @Test
    fun `does not require authentication`() {
        given()
            .`when`().get("/school/room/capability-tags")
            .then()
                .statusCode(200)
    }
}