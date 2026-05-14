package com.grandtech.service

import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseToken
import com.grandtech.model.School
import com.grandtech.model.Teacher
import com.grandtech.utils.ApiResponse
import io.quarkus.test.InjectMock
import io.quarkus.test.TestTransaction
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.lang.reflect.Constructor

/**
 * Unit tests for [UserService] business logic.
 *
 * [FirebaseAuthService] is replaced with a Mockito mock so token verification
 * is controlled by each test. Database operations use an in-memory H2 database
 * and each test runs inside a rolled-back transaction via [@TestTransaction].
 */
@QuarkusTest
class UserServiceTest {

    @Inject
    lateinit var userService: UserService

    @InjectMock
    lateinit var firebaseAuthService: FirebaseAuthService

    companion object {
        /**
         * Creates a real [FirebaseToken] via reflection — its constructor is package-private.
         *
         * @param uid   Firebase UID to embed in the token claims
         * @param email email address to embed in the token claims
         * @param name  display name to embed in the token claims
         * @return a [FirebaseToken] populated with the supplied claims
         */
        private fun buildToken(uid: String, email: String, name: String): FirebaseToken {
            val claims: Map<String, Any> = mapOf("sub" to uid, "email" to email, "name" to name)
            val ctor: Constructor<FirebaseToken> =
                FirebaseToken::class.java.getDeclaredConstructor(Map::class.java)
            ctor.isAccessible = true
            return ctor.newInstance(claims)
        }
    }

    // ── registerTeacher ──────────────────────────────────────────────────────

    /**
     * Verifies that registering a brand-new teacher returns status 200 and
     * the success message.
     */
    @Test
    @TestTransaction
    fun `register teacher for new user returns 200 and success message`() {
        val tok = buildToken("uid-t1", "t1@test.com", "Teacher One")
        Mockito.`when`(firebaseAuthService.verifyToken("tok")).thenReturn(tok)

        val res: ApiResponse<Teacher> = userService.registerTeacher("tok")

        assertEquals(200, res.status)
        assertEquals("Success", res.message)
        assertNotNull(res.payload)
    }

    /**
     * Verifies that the persisted teacher record carries the name and email
     * extracted from the Firebase token.
     */
    @Test
    @TestTransaction
    fun `register teacher for new user saves name and email from token`() {
        val tok = buildToken("uid-t2", "t2@test.com", "Teacher Two")
        Mockito.`when`(firebaseAuthService.verifyToken("tok")).thenReturn(tok)

        val res: ApiResponse<Teacher> = userService.registerTeacher("tok")

        assertEquals("uid-t2", res.payload?.fedUid)
        assertEquals("t2@test.com", res.payload?.email)
        assertEquals("Teacher Two", res.payload?.name)
    }

    /**
     * Verifies that an expired or invalid token causes [UserService.registerTeacher]
     * to return status 500 with the appropriate error message.
     */
    @Test
    fun `register teacher with expired or invalid token returns 500`() {
        val ex = Mockito.mock(FirebaseAuthException::class.java)
        Mockito.`when`(firebaseAuthService.verifyToken("bad")).thenThrow(ex)

        val res: ApiResponse<Teacher> = userService.registerTeacher("bad")

        assertEquals(500, res.status)
        assertEquals("Invalid bearer token!!", res.message)
        assertNull(res.payload)
    }

    /**
     * Verifies that attempting to register a teacher with a Firebase UID that
     * already exists in the users table returns status 502.
     */
    @Test
    @TestTransaction
    fun `register teacher with duplicate fed uid returns 502`() {
        Teacher().also { it.fedUid = "uid-dup-t"; it.persist() }

        val tok = buildToken("uid-dup-t", "new@test.com", "New")
        Mockito.`when`(firebaseAuthService.verifyToken("tok")).thenReturn(tok)

        val res: ApiResponse<Teacher> = userService.registerTeacher("tok")

        assertEquals(502, res.status)
        assertEquals("User already in the system!!!", res.message)
        assertNull(res.payload)
    }

    /**
     * Verifies that attempting to register a teacher whose email is already
     * present in the teachers table returns status 502.
     */
    @Test
    @TestTransaction
    fun `register teacher with email already in teachers table returns 502`() {
        Teacher().also { it.fedUid = "uid-other-t"; it.email = "shared@test.com"; it.persist() }

        val tok = buildToken("uid-new-t", "shared@test.com", "New")
        Mockito.`when`(firebaseAuthService.verifyToken("tok")).thenReturn(tok)

        val res: ApiResponse<Teacher> = userService.registerTeacher("tok")

        assertEquals(502, res.status)
        assertEquals("User already in the system!!!", res.message)
        assertNull(res.payload)
    }

    /**
     * Verifies that attempting to register a teacher whose email is already
     * present in the schools table returns status 502.
     */
    @Test
    @TestTransaction
    fun `register teacher with email already in schools table returns 502`() {
        School().also { it.fedUid = "uid-sch-t"; it.email = "sch@test.com"; it.persist() }

        val tok = buildToken("uid-new-tt", "sch@test.com", "New")
        Mockito.`when`(firebaseAuthService.verifyToken("tok")).thenReturn(tok)

        val res: ApiResponse<Teacher> = userService.registerTeacher("tok")

        assertEquals(502, res.status)
        assertEquals("User already in the system!!!", res.message)
        assertNull(res.payload)
    }

    // ── registerSchool ───────────────────────────────────────────────────────

    /**
     * Verifies that registering a brand-new school returns status 200 and
     * the success message.
     */
    @Test
    @TestTransaction
    fun `register school for new user returns 200 and success message`() {
        val tok = buildToken("uid-s1", "s1@test.com", "School One")
        Mockito.`when`(firebaseAuthService.verifyToken("tok")).thenReturn(tok)

        val res: ApiResponse<School> = userService.registerSchool("tok")

        assertEquals(200, res.status)
        assertEquals("Success", res.message)
        assertNotNull(res.payload)
    }

    /**
     * Verifies that the persisted school record carries the Firebase UID
     * extracted from the token.
     */
    @Test
    @TestTransaction
    fun `register school for new user saves fed uid from token`() {
        val tok = buildToken("uid-s2", "s2@test.com", "School Two")
        Mockito.`when`(firebaseAuthService.verifyToken("tok")).thenReturn(tok)

        val res: ApiResponse<School> = userService.registerSchool("tok")

        assertEquals("uid-s2", res.payload?.fedUid)
    }

    /**
     * Verifies that an expired or invalid token causes [UserService.registerSchool]
     * to return status 500 with the appropriate error message.
     */
    @Test
    fun `register school with expired or invalid token returns 500`() {
        val ex = Mockito.mock(FirebaseAuthException::class.java)
        Mockito.`when`(firebaseAuthService.verifyToken("bad")).thenThrow(ex)

        val res: ApiResponse<School> = userService.registerSchool("bad")

        assertEquals(500, res.status)
        assertEquals("Invalid bearer token!!", res.message)
        assertNull(res.payload)
    }

    /**
     * Verifies that attempting to register a school with a Firebase UID that
     * already exists in the users table returns status 502.
     */
    @Test
    @TestTransaction
    fun `register school with duplicate fed uid returns 502`() {
        School().also { it.fedUid = "uid-dup-s"; it.persist() }

        val tok = buildToken("uid-dup-s", "new-s@test.com", "New")
        Mockito.`when`(firebaseAuthService.verifyToken("tok")).thenReturn(tok)

        val res: ApiResponse<School> = userService.registerSchool("tok")

        assertEquals(502, res.status)
        assertEquals("School already in the system", res.message)
        assertNull(res.payload)
    }

    /**
     * Verifies that attempting to register a school whose email is already
     * present in the schools table returns status 502.
     */
    @Test
    @TestTransaction
    fun `register school with email already in schools table returns 502`() {
        School().also { it.fedUid = "uid-other-s"; it.email = "taken-s@test.com"; it.persist() }

        val tok = buildToken("uid-new-s", "taken-s@test.com", "New")
        Mockito.`when`(firebaseAuthService.verifyToken("tok")).thenReturn(tok)

        val res: ApiResponse<School> = userService.registerSchool("tok")

        assertEquals(502, res.status)
        assertEquals("School already in the system", res.message)
        assertNull(res.payload)
    }

    /**
     * Verifies that attempting to register a school whose email is already
     * present in the teachers table returns status 502.
     */
    @Test
    @TestTransaction
    fun `register school with email already in teachers table returns 502`() {
        Teacher().also { it.fedUid = "uid-teach-s"; it.email = "teach-s@test.com"; it.persist() }

        val tok = buildToken("uid-new-ss", "teach-s@test.com", "New")
        Mockito.`when`(firebaseAuthService.verifyToken("tok")).thenReturn(tok)

        val res: ApiResponse<School> = userService.registerSchool("tok")

        assertEquals(502, res.status)
        assertEquals("School already in the system", res.message)
        assertNull(res.payload)
    }
}