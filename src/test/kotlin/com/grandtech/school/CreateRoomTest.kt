package com.grandtech.school

import com.grandtech.model.Room
import com.grandtech.model.RoomCapabilityTag
import com.grandtech.model.School
import com.grandtech.service.FirebaseAuthService
import com.grandtech.service.SchoolService
import com.grandtech.service.UserRepository
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Tests for `POST /schools/rooms`.
 */
@QuarkusTest
open class CreateRoomTest {

    @InjectMock
    lateinit var firebaseAuthService: FirebaseAuthService

    @InjectMock
    lateinit var userRepository: UserRepository

    @InjectMock
    lateinit var schoolService: SchoolService

    // Workaround for Kotlin + Mockito: Mockito's matcher helpers return null, which Kotlin
    // rejects at non-null param sites. We register the matcher then return a safe placeholder.
    // All args in a stubbed call must use matchers — mixing matchers with raw values throws
    // InvalidUseOfMatchersException. Use eqStr() for String args alongside anyRoom().
    private fun anyRoom(): Room { Mockito.any(Room::class.java); return uninitialized() }
    private fun eqStr(v: String): String { Mockito.eq(v); return v }

    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T

    @Test
    fun `returns 200 with created room for standard classroom`() {
        val token = GetSchoolProfileTest.buildToken("uid-school-room-1")
        Mockito.`when`(firebaseAuthService.verifyToken("Bearer room-token-1")).thenReturn(token)
        Mockito.`when`(userRepository.existsByFedUid("uid-school-room-1")).thenReturn(true)
        Mockito.`when`(schoolService.getSchoolByFedUid("uid-school-room-1"))
            .thenReturn(School(fedUid = "uid-school-room-1"))
        Mockito.`when`(schoolService.createRoom(eqStr("uid-school-room-1"), anyRoom()))
            .thenReturn(Room(id = "generated-uuid-1", name = "Classroom 7A", capacity = 45, isStandardClassroom = true))

        given()
            .header("Authorization", "Bearer room-token-1")
            .contentType(ContentType.JSON)
            .body("""{"name":"Classroom 7A","capacity":45,"isStandardClassroom":true}""")
            .`when`().post("/schools/rooms")
            .then()
                .statusCode(200)
                .body("status",                     `is`(200))
                .body("message",                    `is`("Room created"))
                .body("payload.id",                 `is`("generated-uuid-1"))
                .body("payload.name",               `is`("Classroom 7A"))
                .body("payload.capacity",           `is`(45))
                .body("payload.isStandardClassroom", `is`(true))
    }

    @Test
    fun `returns 200 with created specialist room`() {
        val token = GetSchoolProfileTest.buildToken("uid-school-room-2")
        Mockito.`when`(firebaseAuthService.verifyToken("Bearer room-token-2")).thenReturn(token)
        Mockito.`when`(userRepository.existsByFedUid("uid-school-room-2")).thenReturn(true)
        Mockito.`when`(schoolService.getSchoolByFedUid("uid-school-room-2"))
            .thenReturn(School(fedUid = "uid-school-room-2"))
        Mockito.`when`(schoolService.createRoom(eqStr("uid-school-room-2"), anyRoom()))
            .thenReturn(
                Room(
                    id                  = "generated-uuid-2",
                    name                = "Science Lab 1",
                    capacity            = 40,
                    capabilityTag       = RoomCapabilityTag.SCIENCE_LAB,
                    isStandardClassroom = false,
                ),
            )

        given()
            .header("Authorization", "Bearer room-token-2")
            .contentType(ContentType.JSON)
            .body("""{"name":"Science Lab 1","capacity":40,"capabilityTag":"SCIENCE_LAB","isStandardClassroom":false}""")
            .`when`().post("/schools/rooms")
            .then()
                .statusCode(200)
                .body("status",                     `is`(200))
                .body("payload.id",                 notNullValue())
                .body("payload.capabilityTag",       `is`("SCIENCE_LAB"))
                .body("payload.isStandardClassroom", `is`(false))
    }

    @Test
    fun `without auth header returns 401`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"name":"Classroom 8B","capacity":40,"isStandardClassroom":true}""")
            .`when`().post("/schools/rooms")
            .then()
                .statusCode(401)
                .body("status",  `is`(401))
                .body("payload", nullValue())
    }

    @Test
    fun `for non-school account returns 403`() {
        val token = GetSchoolProfileTest.buildToken("uid-teacher-room-1")
        Mockito.`when`(firebaseAuthService.verifyToken("Bearer teacher-room-token")).thenReturn(token)
        Mockito.`when`(userRepository.existsByFedUid("uid-teacher-room-1")).thenReturn(true)
        Mockito.`when`(schoolService.getSchoolByFedUid("uid-teacher-room-1")).thenReturn(null)

        given()
            .header("Authorization", "Bearer teacher-room-token")
            .contentType(ContentType.JSON)
            .body("""{"name":"Classroom 9C","capacity":35,"isStandardClassroom":true}""")
            .`when`().post("/schools/rooms")
            .then()
                .statusCode(200)
                .body("status",  `is`(403))
                .body("payload", nullValue())
    }
}