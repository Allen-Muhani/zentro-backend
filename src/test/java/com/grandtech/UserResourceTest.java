package com.grandtech;

import com.google.firebase.auth.FirebaseAuthException;
import com.grandtech.model.Teacher;
import com.grandtech.service.UserService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;

@QuarkusTest
class UserResourceTest {

    @InjectMock
    UserService userService;

    @Test
    void testGetCurrentUserWithValidToken() throws FirebaseAuthException {
        Teacher mockUser = new Teacher();
        mockUser.fedUid = "firebase-uid-123";
        mockUser.email = "user@example.com";
        mockUser.name = "Test User";

        Mockito.when(userService.getUserFromToken("Bearer valid-token"))
                .thenReturn(mockUser);

        given()
            .header("Authorization", "Bearer valid-token")
            .when().get("/users/me")
            .then()
                .statusCode(200)
                .body("status", is(200))
                .body("message", is("success"))
                .body("payload.fedUid", is("firebase-uid-123"))
                .body("payload.email", is("user@example.com"))
                .body("payload.name", is("Test User"));
    }

    @Test
    void testGetCurrentUserWithInvalidToken() throws FirebaseAuthException {
        FirebaseAuthException authException = Mockito.mock(FirebaseAuthException.class);
        Mockito.when(authException.getMessage()).thenReturn("invalid id token");
        Mockito.when(userService.getUserFromToken("Bearer bad-token"))
                .thenThrow(authException);

        given()
            .header("Authorization", "Bearer bad-token")
            .when().get("/users/me")
            .then()
                .statusCode(200)
                .body("status", is(401))
                .body("message", is("Unauthorized: invalid id token"))
                .body("payload", nullValue());
    }

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
