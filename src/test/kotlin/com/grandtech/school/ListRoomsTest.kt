package com.grandtech.school

import com.grandtech.model.Room
import com.grandtech.model.RoomCapabilityTag
import com.grandtech.model.School
import com.grandtech.auth.FirebaseAuthService
import com.grandtech.service.RoomService
import com.grandtech.service.SchoolService
import com.grandtech.repository.UserRepository
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Tests for `GET /school/rooms`.
 */
@QuarkusTest
open class ListRoomsTest {

    @InjectMock
    lateinit var firebaseAuthService: FirebaseAuthService

    @InjectMock
    lateinit var userRepository: UserRepository

    @InjectMock
    lateinit var schoolService: SchoolService

    @InjectMock
    lateinit var roomService: RoomService

    @Test
    fun `returns 200 with list of rooms`() {
        val token = GetSchoolProfileTest.buildToken("uid-school-list-1")
        Mockito.`when`(firebaseAuthService.verifyToken("Bearer list-token-1")).thenReturn(token)
        Mockito.`when`(userRepository.existsByFedUid("uid-school-list-1")).thenReturn(true)
        Mockito.`when`(schoolService.getSchoolByFedUid("uid-school-list-1"))
            .thenReturn(School(fedUid = "uid-school-list-1"))
        Mockito.`when`(roomService.listRooms("uid-school-list-1")).thenReturn(
            listOf(
                Room(id = "uuid-1", name = "Classroom 7A", capacity = 45, isStandardClassroom = true),
                Room(id = "uuid-2", name = "Science Lab 1", capacity = 40, capabilityTag = RoomCapabilityTag.SCIENCE_LAB, isStandardClassroom = false),
            ),
        )

        given()
            .header("Authorization", "Bearer list-token-1")
            .`when`().get("/school/rooms")
            .then()
                .statusCode(200)
                .body("status",                         `is`(200))
                .body("message",                        `is`("Success"))
                .body("payload.size()",                 `is`(2))
                .body("payload[0].id",                  `is`("uuid-1"))
                .body("payload[0].name",                `is`("Classroom 7A"))
                .body("payload[0].capacity",            `is`(45))
                .body("payload[1].id",                  `is`("uuid-2"))
                .body("payload[1].capabilityTag",        `is`("SCIENCE_LAB"))
                .body("payload[1].isStandardClassroom",  `is`(false))
    }

    @Test
    fun `returns 200 with empty list when school has no rooms`() {
        val token = GetSchoolProfileTest.buildToken("uid-school-list-2")
        Mockito.`when`(firebaseAuthService.verifyToken("Bearer list-token-2")).thenReturn(token)
        Mockito.`when`(userRepository.existsByFedUid("uid-school-list-2")).thenReturn(true)
        Mockito.`when`(schoolService.getSchoolByFedUid("uid-school-list-2"))
            .thenReturn(School(fedUid = "uid-school-list-2"))
        Mockito.`when`(roomService.listRooms("uid-school-list-2")).thenReturn(emptyList())

        given()
            .header("Authorization", "Bearer list-token-2")
            .`when`().get("/school/rooms")
            .then()
                .statusCode(200)
                .body("status",         `is`(200))
                .body("payload.size()", `is`(0))
    }

    @Test
    fun `without auth header returns 401`() {
        given()
            .`when`().get("/school/rooms")
            .then()
                .statusCode(401)
                .body("status",  `is`(401))
                .body("payload", nullValue())
    }

    @Test
    fun `for non-school account returns 403`() {
        val token = GetSchoolProfileTest.buildToken("uid-teacher-list-1")
        Mockito.`when`(firebaseAuthService.verifyToken("Bearer teacher-list-token")).thenReturn(token)
        Mockito.`when`(userRepository.existsByFedUid("uid-teacher-list-1")).thenReturn(true)
        Mockito.`when`(schoolService.getSchoolByFedUid("uid-teacher-list-1")).thenReturn(null)

        given()
            .header("Authorization", "Bearer teacher-list-token")
            .`when`().get("/school/rooms")
            .then()
                .statusCode(200)
                .body("status",  `is`(403))
                .body("payload", nullValue())
    }
}
