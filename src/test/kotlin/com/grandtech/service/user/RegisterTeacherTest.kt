package com.grandtech.service.user

import com.google.firebase.auth.FirebaseAuthException
import com.grandtech.model.School
import com.grandtech.model.Teacher
import com.grandtech.utils.ApiResponse
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Tests for [com.grandtech.service.UserService.registerTeacher].
 */
@QuarkusTest
class RegisterTeacherTest : UserServiceTestBase() {

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

    @Test
    fun `register teacher with expired or invalid token returns 500`() {
        val ex = Mockito.mock(FirebaseAuthException::class.java)
        Mockito.`when`(firebaseAuthService.verifyToken("bad")).thenThrow(ex)

        val res: ApiResponse<Teacher> = userService.registerTeacher("bad")

        assertEquals(500, res.status)
        assertEquals("Invalid bearer token!!", res.message)
        assertNull(res.payload)
    }

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
}