package com.grandtech.school

import com.grandtech.model.School
import com.grandtech.service.FirebaseAuthService
import com.grandtech.service.RoomService
import com.grandtech.service.SchoolService
import com.grandtech.service.UserRepository
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Tests for `DELETE /school/rooms/{id}`.
 */
@QuarkusTest
open class DeleteRoomTest {

    @InjectMock
    lateinit var firebaseAuthService: FirebaseAuthService

    @InjectMock
    lateinit var userRepository: UserRepository

    @InjectMock
    lateinit var schoolService: SchoolService

    @InjectMock
    lateinit var roomService: RoomService

    @Test
    fun `returns 200 when room is successfully deleted`() {
        val token = GetSchoolProfileTest.buildToken("uid-school-del-1")
        Mockito.`when`(firebaseAuthService.verifyToken("Bearer del-token-1")).thenReturn(token)
        Mockito.`when`(userRepository.existsByFedUid("uid-school-del-1")).thenReturn(true)
        Mockito.`when`(schoolService.getSchoolByFedUid("uid-school-del-1"))
            .thenReturn(School(fedUid = "uid-school-del-1"))
        Mockito.`when`(roomService.deleteRoom("uid-school-del-1", "room-uuid-del-1"))
            .thenReturn(true)

        given()
            .header("Authorization", "Bearer del-token-1")
            .`when`().delete("/school/rooms/room-uuid-del-1")
            .then()
                .statusCode(200)
                .body("status",  `is`(200))
                .body("message", `is`("Room deleted"))
                .body("payload", nullValue())
    }

    @Test
    fun `returns 404 when room does not belong to the school`() {
        val token = GetSchoolProfileTest.buildToken("uid-school-del-2")
        Mockito.`when`(firebaseAuthService.verifyToken("Bearer del-token-2")).thenReturn(token)
        Mockito.`when`(userRepository.existsByFedUid("uid-school-del-2")).thenReturn(true)
        Mockito.`when`(schoolService.getSchoolByFedUid("uid-school-del-2"))
            .thenReturn(School(fedUid = "uid-school-del-2"))
        Mockito.`when`(roomService.deleteRoom("uid-school-del-2", "nonexistent-room"))
            .thenReturn(false)

        given()
            .header("Authorization", "Bearer del-token-2")
            .`when`().delete("/school/rooms/nonexistent-room")
            .then()
                .statusCode(200)
                .body("status",  `is`(404))
                .body("payload", nullValue())
    }

    @Test
    fun `without auth header returns 401`() {
        given()
            .`when`().delete("/school/rooms/some-room-id")
            .then()
                .statusCode(401)
                .body("status",  `is`(401))
                .body("payload", nullValue())
    }

    @Test
    fun `for non-school account returns 403`() {
        val token = GetSchoolProfileTest.buildToken("uid-teacher-del-1")
        Mockito.`when`(firebaseAuthService.verifyToken("Bearer teacher-del-token")).thenReturn(token)
        Mockito.`when`(userRepository.existsByFedUid("uid-teacher-del-1")).thenReturn(true)
        Mockito.`when`(schoolService.getSchoolByFedUid("uid-teacher-del-1")).thenReturn(null)

        given()
            .header("Authorization", "Bearer teacher-del-token")
            .`when`().delete("/school/rooms/some-room-id")
            .then()
                .statusCode(200)
                .body("status",  `is`(403))
                .body("payload", nullValue())
    }
}