package com.grandtech;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;

@QuarkusIntegrationTest
class UserResourceIT {

    @Test
    void testGetCurrentUserWithoutTokenReturnsUnauthorized() {
        given()
            .when().get("/users/me")
            .then()
                .statusCode(200)
                .body("status", is(401))
                .body("message", is("Unauthorized: missing Authorization header"))
                .body("payload", nullValue());
    }
}
