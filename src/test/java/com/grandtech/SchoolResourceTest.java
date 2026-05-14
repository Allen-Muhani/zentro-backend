package com.grandtech;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class SchoolResourceTest {

    @Test
    void testGetSchoolsReturnsSuccess() {
        given()
            .when().get("/schools")
            .then()
                .statusCode(200)
                .body("status", is(200))
                .body("message", is("success"))
                .body("payload", is("Schools endpoint is live"));
    }
}
