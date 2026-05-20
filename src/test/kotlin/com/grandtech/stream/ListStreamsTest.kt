package com.grandtech.stream

import com.grandtech.auth.FirebaseAuthService
import com.grandtech.model.Room
import com.grandtech.model.School
import com.grandtech.model.Stream
import com.grandtech.model.Teacher
import com.grandtech.repository.UserRepository
import com.grandtech.school.GetSchoolProfileTest
import com.grandtech.service.RoomService
import com.grandtech.service.StreamService
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.neo4j.driver.Driver

/**
 * Integration tests for `GET /school/stream`.
 *
 * Only [FirebaseAuthService] is mocked — token verification requires a live Firebase
 * connection unavailable in CI. All other beans run against the real AuraDB instance.
 */
@QuarkusTest
class ListStreamsTest {

    @InjectMock
    lateinit var firebaseAuthService: FirebaseAuthService

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var roomService: RoomService

    @Inject
    lateinit var streamService: StreamService

    @Inject
    lateinit var driver: Driver

    private val trackedSchoolUids = mutableSetOf<String>()
    private val trackedTeacherUids = mutableSetOf<String>()

    private fun trackSchool(vararg uids: String) { trackedSchoolUids.addAll(uids.toList()) }
    private fun trackTeacher(vararg uids: String) { trackedTeacherUids.addAll(uids.toList()) }

    private fun stubToken(schoolUid: String, bearer: String) {
        Mockito.`when`(firebaseAuthService.verifyToken(bearer))
            .thenReturn(GetSchoolProfileTest.buildToken(schoolUid))
    }

    @AfterEach
    fun cleanUp() {
        driver.session().use { session ->
            if (trackedSchoolUids.isNotEmpty()) {
                session.run(
                    """
                    MATCH (s:School) WHERE s.fedUid IN ${'$'}uids
                    OPTIONAL MATCH (s)-[:HAS_ROOM]->(r:Room)
                    OPTIONAL MATCH (s)-[:HAS_STREAM]->(st:Stream)
                    DETACH DELETE s, r, st
                    """.trimIndent(),
                    mapOf("uids" to trackedSchoolUids.toList()),
                )
                trackedSchoolUids.clear()
            }
            if (trackedTeacherUids.isNotEmpty()) {
                session.run(
                    "MATCH (t:Teacher) WHERE t.fedUid IN \$uids DETACH DELETE t",
                    mapOf("uids" to trackedTeacherUids.toList()),
                )
                trackedTeacherUids.clear()
            }
        }
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    @Test
    fun `missing auth header returns 401`() {
        given()
            .`when`().get("/school/stream/list")
            .then()
                .statusCode(401)
                .body("status",  `is`(401))
                .body("payload", nullValue())
    }

    @Test
    fun `unregistered uid returns 401`() {
        stubToken("lst-unknown-uid", "Bearer lst-unknown-token")

        given()
            .header("Authorization", "Bearer lst-unknown-token")
            .`when`().get("/school/stream/list")
            .then()
                .statusCode(401)
                .body("status",  `is`(401))
                .body("payload", nullValue())
    }

    @Test
    fun `teacher account returns 403`() {
        trackTeacher("lst-teacher-1")
        userRepository.saveTeacher(Teacher(fedUid = "lst-teacher-1", name = "Test Teacher"))
        stubToken("lst-teacher-1", "Bearer lst-teacher-token-1")

        given()
            .header("Authorization", "Bearer lst-teacher-token-1")
            .`when`().get("/school/stream/list")
            .then()
                .statusCode(200)
                .body("status",  `is`(403))
                .body("payload", nullValue())
    }

    // ── List ─────────────────────────────────────────────────────────────────

    @Test
    fun `returns empty list when school has no streams`() {
        trackSchool("lst-school-1")
        userRepository.saveSchool(School(fedUid = "lst-school-1"))
        stubToken("lst-school-1", "Bearer lst-token-1")

        given()
            .header("Authorization", "Bearer lst-token-1")
            .`when`().get("/school/stream/list")
            .then()
                .statusCode(200)
                .body("status",  `is`(200))
                .body("payload", notNullValue())
                .body("payload.size()", `is`(0))
    }

    @Test
    fun `returns all streams with scalar fields`() {
        trackSchool("lst-school-2")
        userRepository.saveSchool(School(fedUid = "lst-school-2"))
        stubToken("lst-school-2", "Bearer lst-token-2")
        streamService.upsertStream("lst-school-2", Stream(gradeLevel = 7, name = "Blue", studentCount = 30))
        streamService.upsertStream("lst-school-2", Stream(gradeLevel = 8, name = "Red"))

        given()
            .header("Authorization", "Bearer lst-token-2")
            .`when`().get("/school/stream/list")
            .then()
                .statusCode(200)
                .body("status",           `is`(200))
                .body("payload.size()",   `is`(2))
                .body("payload[0].gradeLevel", `is`(7))
                .body("payload[0].name",       `is`("Blue"))
                .body("payload[0].studentCount", `is`(30))
                .body("payload[1].gradeLevel", `is`(8))
                .body("payload[1].name",       `is`("Red"))
    }

    @Test
    fun `returns stream with homeRoom when HOME_ROOM relationship exists`() {
        trackSchool("lst-school-3")
        userRepository.saveSchool(School(fedUid = "lst-school-3"))
        stubToken("lst-school-3", "Bearer lst-token-3")
        val room = roomService.createRoom("lst-school-3", Room(name = "Lab A", capacity = 25, isStandardClassroom = false))
        streamService.upsertStream("lst-school-3", Stream(gradeLevel = 7, name = "Green", homeRoom = Room(id = room!!.id)))

        given()
            .header("Authorization", "Bearer lst-token-3")
            .`when`().get("/school/stream/list")
            .then()
                .statusCode(200)
                .body("payload[0].homeRoom.id",       `is`(room.id))
                .body("payload[0].homeRoom.name",     `is`("Lab A"))
                .body("payload[0].homeRoom.capacity", `is`(25))
    }

    @Test
    fun `returns stream with null homeRoom when no HOME_ROOM relationship`() {
        trackSchool("lst-school-4")
        userRepository.saveSchool(School(fedUid = "lst-school-4"))
        stubToken("lst-school-4", "Bearer lst-token-4")
        streamService.upsertStream("lst-school-4", Stream(gradeLevel = 9, name = "Gold"))

        given()
            .header("Authorization", "Bearer lst-token-4")
            .`when`().get("/school/stream/list")
            .then()
                .statusCode(200)
                .body("payload[0].homeRoom", nullValue())
    }

    @Test
    fun `returns stream with formTeacher when FORM_TEACHER relationship exists`() {
        trackSchool("lst-school-5")
        trackTeacher("lst-teacher-5")
        userRepository.saveSchool(School(fedUid = "lst-school-5"))
        userRepository.saveTeacher(Teacher(fedUid = "lst-teacher-5", name = "Ms Achieng", email = "achieng@school.ke"))
        stubToken("lst-school-5", "Bearer lst-token-5")
        streamService.upsertStream("lst-school-5", Stream(gradeLevel = 8, name = "Silver", formTeacher = Teacher(fedUid = "lst-teacher-5")))

        given()
            .header("Authorization", "Bearer lst-token-5")
            .`when`().get("/school/stream/list")
            .then()
                .statusCode(200)
                .body("payload[0].formTeacher.fedUid", `is`("lst-teacher-5"))
                .body("payload[0].formTeacher.name",   `is`("Ms Achieng"))
                .body("payload[0].formTeacher.email",  `is`("achieng@school.ke"))
    }
}