package com.grandtech.service.user

import com.google.firebase.auth.FirebaseAuthException
import com.grandtech.model.School
import com.grandtech.utils.ApiResponse
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Tests for [com.grandtech.service.UserService.registerSchool].
 */
@QuarkusTest
class RegisterSchoolTest : UserServiceTestBase() {

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

    @Test
    fun `register school for new user saves fed uid from token`() {
        track("uid-s2")
        val tok = buildToken("uid-s2", "s2@test.com", "School Two")
        Mockito.`when`(firebaseAuthService.verifyToken("tok")).thenReturn(tok)

        val res: ApiResponse<School> = userService.registerSchool("tok")

        assertEquals("uid-s2", res.payload?.fedUid)
    }

    @Test
    fun `register school with expired or invalid token returns 500`() {
        val ex = Mockito.mock(FirebaseAuthException::class.java)
        Mockito.`when`(firebaseAuthService.verifyToken("bad")).thenThrow(ex)

        val res: ApiResponse<School> = userService.registerSchool("bad")

        assertEquals(500, res.status)
        assertEquals("Invalid bearer token!!", res.message)
        assertNull(res.payload)
    }

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

}