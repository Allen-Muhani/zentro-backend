package com.grandtech.service

import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseToken
import com.grandtech.model.School
import com.grandtech.model.Teacher
import com.grandtech.model.User
import com.grandtech.utils.ApiResponse
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.neo4j.driver.Driver
import java.lang.reflect.Constructor

/**
 * Unit tests for [UserService] business logic.
 *
 * [FirebaseAuthService] is replaced with a Mockito mock so token verification
 * is controlled by each test. All tests run against the configured Neo4j instance.
 * Each test declares the Firebase UIDs it touches via [track]; [cleanUp] removes
 * only those [User] nodes after the test completes so unrelated data is preserved.
 */
@QuarkusTest
class UserServiceTest {

    @Inject
    lateinit var userService: UserService

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var driver: Driver

    @InjectMock
    lateinit var firebaseAuthService: FirebaseAuthService

    private val trackedUids = mutableSetOf<String>()

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

    /**
     * Registers Firebase UIDs that the current test may write to the graph.
     * All registered UIDs are removed by [cleanUp] after the test finishes.
     *
     * @param uids one or more Firebase UIDs expected to be touched by this test
     */
    private fun track(vararg uids: String) {
        trackedUids.addAll(uids.toList())
    }

    /**
     * Deletes only the [User] nodes whose `fedUid` was registered via [track],
     * leaving all other graph data untouched.
     */
    @AfterEach
    fun cleanUp() {
        if (trackedUids.isNotEmpty()) {
            driver.session().use { session ->
                session.run(
                    "MATCH (u:User) WHERE u.fedUid IN \$uids DETACH DELETE u",
                    mapOf("uids" to trackedUids.toList()),
                )
            }
            trackedUids.clear()
        }
    }

    // ── registerTeacher ──────────────────────────────────────────────────────

    /**
     * Verifies that registering a brand-new teacher returns status 200 and
     * the success message.
     */
    @Test
    fun `register teacher for new user returns 200 and success message`() {
        track("uid-t1")
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
    fun `register teacher for new user saves name and email from token`() {
        track("uid-t2")
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
     * already exists in the graph returns status 502.
     */
    @Test
    fun `register teacher with duplicate fed uid returns 502`() {
        track("uid-dup-t")
        userRepository.saveTeacher(Teacher(fedUid = "uid-dup-t"))

        val tok = buildToken("uid-dup-t", "new@test.com", "New")
        Mockito.`when`(firebaseAuthService.verifyToken("tok")).thenReturn(tok)

        val res: ApiResponse<Teacher> = userService.registerTeacher("tok")

        assertEquals(502, res.status)
        assertEquals("User already in the system!!!", res.message)
        assertNull(res.payload)
    }

    /**
     * Verifies that attempting to register a teacher whose email is already
     * present in the graph (on another Teacher node) returns status 502.
     */
    @Test
    fun `register teacher with email already in teachers table returns 502`() {
        track("uid-other-t", "uid-new-t")
        userRepository.saveTeacher(Teacher(fedUid = "uid-other-t", email = "shared@test.com"))

        val tok = buildToken("uid-new-t", "shared@test.com", "New")
        Mockito.`when`(firebaseAuthService.verifyToken("tok")).thenReturn(tok)

        val res: ApiResponse<Teacher> = userService.registerTeacher("tok")

        assertEquals(502, res.status)
        assertEquals("User already in the system!!!", res.message)
        assertNull(res.payload)
    }

    /**
     * Verifies that attempting to register a teacher whose email is already
     * present in the graph (on a School node) returns status 502.
     */
    @Test
    fun `register teacher with email already in schools table returns 502`() {
        track("uid-sch-t", "uid-new-tt")
        userRepository.saveSchool(School(fedUid = "uid-sch-t", email = "sch@test.com"))

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
    fun `register school for new user returns 200 and success message`() {
        track("uid-s1")
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
    fun `register school for new user saves fed uid from token`() {
        track("uid-s2")
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
     * already exists in the graph returns status 502.
     */
    @Test
    fun `register school with duplicate fed uid returns 502`() {
        track("uid-dup-s")
        userRepository.saveSchool(School(fedUid = "uid-dup-s"))

        val tok = buildToken("uid-dup-s", "new-s@test.com", "New")
        Mockito.`when`(firebaseAuthService.verifyToken("tok")).thenReturn(tok)

        val res: ApiResponse<School> = userService.registerSchool("tok")

        assertEquals(502, res.status)
        assertEquals("School already in the system", res.message)
        assertNull(res.payload)
    }

    /**
     * Verifies that attempting to register a school whose email is already
     * present in the graph (on another School node) returns status 502.
     */
    @Test
    fun `register school with email already in schools table returns 502`() {
        track("uid-other-s", "uid-new-s")
        userRepository.saveSchool(School(fedUid = "uid-other-s", email = "taken-s@test.com"))

        val tok = buildToken("uid-new-s", "taken-s@test.com", "New")
        Mockito.`when`(firebaseAuthService.verifyToken("tok")).thenReturn(tok)

        val res: ApiResponse<School> = userService.registerSchool("tok")

        assertEquals(502, res.status)
        assertEquals("School already in the system", res.message)
        assertNull(res.payload)
    }

    /**
     * Verifies that attempting to register a school whose email is already
     * present in the graph (on a Teacher node) returns status 502.
     */
    @Test
    fun `register school with email already in teachers table returns 502`() {
        track("uid-teach-s", "uid-new-ss")
        userRepository.saveTeacher(Teacher(fedUid = "uid-teach-s", email = "teach-s@test.com"))

        val tok = buildToken("uid-new-ss", "teach-s@test.com", "New")
        Mockito.`when`(firebaseAuthService.verifyToken("tok")).thenReturn(tok)

        val res: ApiResponse<School> = userService.registerSchool("tok")

        assertEquals(502, res.status)
        assertEquals("School already in the system", res.message)
        assertNull(res.payload)
    }
}