package com.grandtech.service;

import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.grandtech.model.School;
import com.grandtech.model.Teacher;
import com.grandtech.utils.ApiResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class UserServiceTest {

    @Inject
    UserService userService;

    @InjectMock
    FirebaseAuthService firebaseAuthService;

    /**
     * Creates a real FirebaseToken via reflection since its constructor is package-private.
     * This avoids Mockito trying to subclass or stub a class it cannot proxy.
     */
    private static FirebaseToken buildToken(String uid, String email, String name) {
        try {
            Map<String, Object> claims = new HashMap<>();
            claims.put("sub", uid);   // getUid() reads "sub"; constructor validates its presence
            claims.put("email", email);
            claims.put("name", name);
            Constructor<FirebaseToken> ctor = FirebaseToken.class.getDeclaredConstructor(Map.class);
            ctor.setAccessible(true);
            return ctor.newInstance(claims);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build test FirebaseToken", e);
        }
    }

    // ── registerTeacher ──────────────────────────────────────────────────────

    @Test
    @TestTransaction
    void registerTeacher_newUser_returns200AndSuccessMessage() throws FirebaseAuthException {
        FirebaseToken tok = buildToken("uid-t1", "t1@test.com", "Teacher One");
        Mockito.when(firebaseAuthService.verifyToken("tok")).thenReturn(tok);

        ApiResponse<Teacher> res = userService.registerTeacher("tok");

        assertEquals(200, res.getStatus());
        assertEquals("Success", res.getMessage());
        assertNotNull(res.getPayload());
    }

    @Test
    @TestTransaction
    void registerTeacher_newUser_savesNameAndEmailFromToken() throws FirebaseAuthException {
        FirebaseToken tok = buildToken("uid-t2", "t2@test.com", "Teacher Two");
        Mockito.when(firebaseAuthService.verifyToken("tok")).thenReturn(tok);

        ApiResponse<Teacher> res = userService.registerTeacher("tok");

        assertEquals("uid-t2", res.getPayload().fedUid);
        assertEquals("t2@test.com", res.getPayload().email);
        assertEquals("Teacher Two", res.getPayload().name);
    }

    @Test
    void registerTeacher_expiredOrInvalidToken_returns500AndMessage() throws FirebaseAuthException {
        FirebaseAuthException ex = Mockito.mock(FirebaseAuthException.class);
        Mockito.when(firebaseAuthService.verifyToken("bad")).thenThrow(ex);

        ApiResponse<Teacher> res = userService.registerTeacher("bad");

        assertEquals(500, res.getStatus());
        assertEquals("Invalid bearer token!!", res.getMessage());
        assertNull(res.getPayload());
    }

    @Test
    @TestTransaction
    void registerTeacher_duplicateFedUid_returns502AndMessage() throws FirebaseAuthException {
        Teacher existing = new Teacher();
        existing.fedUid = "uid-dup-t";
        existing.persist();

        FirebaseToken tok = buildToken("uid-dup-t", "new@test.com", "New");
        Mockito.when(firebaseAuthService.verifyToken("tok")).thenReturn(tok);

        ApiResponse<Teacher> res = userService.registerTeacher("tok");

        assertEquals(502, res.getStatus());
        assertEquals("User already in the system!!!", res.getMessage());
        assertNull(res.getPayload());
    }

    @Test
    @TestTransaction
    void registerTeacher_emailAlreadyInTeachersTable_returns502AndMessage() throws FirebaseAuthException {
        Teacher existing = new Teacher();
        existing.fedUid = "uid-other-t";
        existing.email = "shared@test.com";
        existing.persist();

        FirebaseToken tok = buildToken("uid-new-t", "shared@test.com", "New");
        Mockito.when(firebaseAuthService.verifyToken("tok")).thenReturn(tok);

        ApiResponse<Teacher> res = userService.registerTeacher("tok");

        assertEquals(502, res.getStatus());
        assertEquals("User already in the system!!!", res.getMessage());
        assertNull(res.getPayload());
    }

    @Test
    @TestTransaction
    void registerTeacher_emailAlreadyInSchoolsTable_returns502AndMessage() throws FirebaseAuthException {
        School existing = new School();
        existing.fedUid = "uid-sch-t";
        existing.email = "sch@test.com";
        existing.persist();

        FirebaseToken tok = buildToken("uid-new-tt", "sch@test.com", "New");
        Mockito.when(firebaseAuthService.verifyToken("tok")).thenReturn(tok);

        ApiResponse<Teacher> res = userService.registerTeacher("tok");

        assertEquals(502, res.getStatus());
        assertEquals("User already in the system!!!", res.getMessage());
        assertNull(res.getPayload());
    }

    // ── registerSchool ───────────────────────────────────────────────────────

    @Test
    @TestTransaction
    void registerSchool_newUser_returns200AndSuccessMessage() throws FirebaseAuthException {
        FirebaseToken tok = buildToken("uid-s1", "s1@test.com", "School One");
        Mockito.when(firebaseAuthService.verifyToken("tok")).thenReturn(tok);

        ApiResponse<School> res = userService.registerSchool("tok");

        assertEquals(200, res.getStatus());
        assertEquals("Success", res.getMessage());
        assertNotNull(res.getPayload());
    }

    @Test
    @TestTransaction
    void registerSchool_newUser_savesFedUidFromToken() throws FirebaseAuthException {
        FirebaseToken tok = buildToken("uid-s2", "s2@test.com", "School Two");
        Mockito.when(firebaseAuthService.verifyToken("tok")).thenReturn(tok);

        ApiResponse<School> res = userService.registerSchool("tok");

        assertEquals("uid-s2", res.getPayload().fedUid);
    }

    @Test
    void registerSchool_expiredOrInvalidToken_returns500AndMessage() throws FirebaseAuthException {
        FirebaseAuthException ex = Mockito.mock(FirebaseAuthException.class);
        Mockito.when(firebaseAuthService.verifyToken("bad")).thenThrow(ex);

        ApiResponse<School> res = userService.registerSchool("bad");

        assertEquals(500, res.getStatus());
        assertEquals("Invalid bearer token!!", res.getMessage());
        assertNull(res.getPayload());
    }

    @Test
    @TestTransaction
    void registerSchool_duplicateFedUid_returns502AndMessage() throws FirebaseAuthException {
        School existing = new School();
        existing.fedUid = "uid-dup-s";
        existing.persist();

        FirebaseToken tok = buildToken("uid-dup-s", "new-s@test.com", "New");
        Mockito.when(firebaseAuthService.verifyToken("tok")).thenReturn(tok);

        ApiResponse<School> res = userService.registerSchool("tok");

        assertEquals(502, res.getStatus());
        assertEquals("School already in the system", res.getMessage());
        assertNull(res.getPayload());
    }

    @Test
    @TestTransaction
    void registerSchool_emailAlreadyInSchoolsTable_returns502AndMessage() throws FirebaseAuthException {
        School existing = new School();
        existing.fedUid = "uid-other-s";
        existing.email = "taken-s@test.com";
        existing.persist();

        FirebaseToken tok = buildToken("uid-new-s", "taken-s@test.com", "New");
        Mockito.when(firebaseAuthService.verifyToken("tok")).thenReturn(tok);

        ApiResponse<School> res = userService.registerSchool("tok");

        assertEquals(502, res.getStatus());
        assertEquals("School already in the system", res.getMessage());
        assertNull(res.getPayload());
    }

    @Test
    @TestTransaction
    void registerSchool_emailAlreadyInTeachersTable_returns502AndMessage() throws FirebaseAuthException {
        Teacher existing = new Teacher();
        existing.fedUid = "uid-teach-s";
        existing.email = "teach-s@test.com";
        existing.persist();

        FirebaseToken tok = buildToken("uid-new-ss", "teach-s@test.com", "New");
        Mockito.when(firebaseAuthService.verifyToken("tok")).thenReturn(tok);

        ApiResponse<School> res = userService.registerSchool("tok");

        assertEquals(502, res.getStatus());
        assertEquals("School already in the system", res.getMessage());
        assertNull(res.getPayload());
    }
}
