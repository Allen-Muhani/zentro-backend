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
import io.restassured.http.ContentType
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Tests for `PATCH /school/rooms/{id}`.
 */
@QuarkusTest
open class UpdateRoomTest {

    @InjectMock
    lateinit var firebaseAuthService: FirebaseAuthService

    @InjectMock
    lateinit var userRepository: UserRepository

    @InjectMock
    lateinit var schoolService: SchoolService

    @InjectMock
    lateinit var roomService: RoomService

    private fun anyRoom(): Room { Mockito.any(Room::class.java); return uninitialized() }
    private fun eqStr(v: String): String { Mockito.eq(v); return v }

    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T

    @Test
    fun `returns 200 with updated room`() {
        val token = GetSchoolProfileTest.buildToken("uid-school-upd-room-1")
        Mockito.`when`(firebaseAuthService.verifyToken("Bearer upd-room-token-1")).thenReturn(token)
        Mockito.`when`(userRepository.existsByFedUid("uid-school-upd-room-1")).thenReturn(true)
        Mockito.`when`(schoolService.getSchoolByFedUid("uid-school-upd-room-1"))
            .thenReturn(School(fedUid = "uid-school-upd-room-1"))
        Mockito.`when`(roomService.updateRoom(eqStr("uid-school-upd-room-1"), eqStr("room-uuid-1"), anyRoom()))
            .thenReturn(Room(id = "room-uuid-1", name = "Renamed Lab", capacity = 35, isStandardClassroom = true))

        given()
            .header("Authorization", "Bearer upd-room-token-1")
            .contentType(ContentType.JSON)
            .body("""{"name":"Renamed Lab","capacity":35}""")
            .`when`().patch("/school/room/update/room-uuid-1")
            .then()
                .statusCode(200)
                .body("status",           `is`(200))
                .body("message",          `is`("Room updated"))
                .body("payload.id",       `is`("room-uuid-1"))
                .body("payload.name",     `is`("Renamed Lab"))
                .body("payload.capacity", `is`(35))
    }

    @Test
    fun `partial update leaves omitted fields unchanged`() {
        val token = GetSchoolProfileTest.buildToken("uid-school-upd-room-2")
        Mockito.`when`(firebaseAuthService.verifyToken("Bearer upd-room-token-2")).thenReturn(token)
        Mockito.`when`(userRepository.existsByFedUid("uid-school-upd-room-2")).thenReturn(true)
        Mockito.`when`(schoolService.getSchoolByFedUid("uid-school-upd-room-2"))
            .thenReturn(School(fedUid = "uid-school-upd-room-2"))
        Mockito.`when`(roomService.updateRoom(eqStr("uid-school-upd-room-2"), eqStr("room-uuid-2"), anyRoom()))
            .thenReturn(
                Room(
                    id                  = "room-uuid-2",
                    name                = "Science Lab 1",
                    capacity            = 50,
                    capabilityTag       = RoomCapabilityTag.SCIENCE_LAB,
                    isStandardClassroom = false,
                ),
            )

        given()
            .header("Authorization", "Bearer upd-room-token-2")
            .contentType(ContentType.JSON)
            .body("""{"capacity":50}""")
            .`when`().patch("/school/room/update/room-uuid-2")
            .then()
                .statusCode(200)
                .body("status",                     `is`(200))
                .body("payload.name",               `is`("Science Lab 1"))
                .body("payload.capacity",           `is`(50))
                .body("payload.capabilityTag",       `is`("SCIENCE_LAB"))
                .body("payload.isStandardClassroom", `is`(false))
    }

    @Test
    fun `returns 404 when room does not belong to school`() {
        val token = GetSchoolProfileTest.buildToken("uid-school-upd-room-3")
        Mockito.`when`(firebaseAuthService.verifyToken("Bearer upd-room-token-3")).thenReturn(token)
        Mockito.`when`(userRepository.existsByFedUid("uid-school-upd-room-3")).thenReturn(true)
        Mockito.`when`(schoolService.getSchoolByFedUid("uid-school-upd-room-3"))
            .thenReturn(School(fedUid = "uid-school-upd-room-3"))
        Mockito.`when`(roomService.updateRoom(eqStr("uid-school-upd-room-3"), eqStr("nonexistent-id"), anyRoom()))
            .thenReturn(null)

        given()
            .header("Authorization", "Bearer upd-room-token-3")
            .contentType(ContentType.JSON)
            .body("""{"name":"Ghost Room"}""")
            .`when`().patch("/school/room/update/nonexistent-id")
            .then()
                .statusCode(200)
                .body("status",  `is`(404))
                .body("payload", nullValue())
    }

    @Test
    fun `without auth header returns 401`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"name":"No Auth Room"}""")
            .`when`().patch("/school/room/update/some-id")
            .then()
                .statusCode(401)
                .body("status",  `is`(401))
                .body("payload", nullValue())
    }

    @Test
    fun `for non-school account returns 403`() {
        val token = GetSchoolProfileTest.buildToken("uid-teacher-upd-room-1")
        Mockito.`when`(firebaseAuthService.verifyToken("Bearer teacher-upd-room-token")).thenReturn(token)
        Mockito.`when`(userRepository.existsByFedUid("uid-teacher-upd-room-1")).thenReturn(true)
        Mockito.`when`(schoolService.getSchoolByFedUid("uid-teacher-upd-room-1")).thenReturn(null)

        given()
            .header("Authorization", "Bearer teacher-upd-room-token")
            .contentType(ContentType.JSON)
            .body("""{"name":"Teacher Room"}""")
            .`when`().patch("/school/room/update/some-id")
            .then()
                .statusCode(200)
                .body("status",  `is`(403))
                .body("payload", nullValue())
    }
}
