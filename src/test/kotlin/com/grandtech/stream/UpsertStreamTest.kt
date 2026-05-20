package com.grandtech.stream

import com.grandtech.auth.FirebaseAuthService
import com.grandtech.school.GetSchoolProfileTest
import com.grandtech.model.School
import com.grandtech.model.Stream
import com.grandtech.model.Teacher
import com.grandtech.repository.UserRepository
import com.grandtech.service.StreamService
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.neo4j.driver.Driver

/**
 * Integration tests for `POST /school/stream`.
 *
 * Only [FirebaseAuthService] is mocked — token verification requires a live Firebase
 * connection unavailable in CI. All other beans ([UserRepository], [com.grandtech.service.SchoolService],
 * [StreamService]) run against the real AuraDB instance so the full create/update flow
 * is exercised end-to-end without stubbing service results.
 */
@QuarkusTest
class UpsertStreamTest {

    @InjectMock
    lateinit var firebaseAuthService: FirebaseAuthService

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var streamService: StreamService

    @Inject
    lateinit var driver: Driver

    private val trackedSchoolUids = mutableSetOf<String>()
    private val trackedTeacherUids = mutableSetOf<String>()

    private fun trackSchool(vararg uids: String) { trackedSchoolUids.addAll(uids.toList()) }
    private fun trackTeacher(vararg uids: String) { trackedTeacherUids.addAll(uids.toList()) }

    /** Stubs [FirebaseAuthService.verifyToken] to return a token containing [schoolUid]. */
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
            .contentType(ContentType.JSON)
            .body("""{"gradeLevel":7,"name":"7 Blue"}""")
            .`when`().post("/school/stream")
            .then()
                .statusCode(401)
                .body("status",  `is`(401))
                .body("payload", nullValue())
    }

    @Test
    fun `unregistered uid returns 401`() {
        // Token is valid but no User node exists in Neo4j for this UID
        stubToken("ust-unknown-uid", "Bearer ust-unknown-token")

        given()
            .header("Authorization", "Bearer ust-unknown-token")
            .contentType(ContentType.JSON)
            .body("""{"gradeLevel":7,"name":"7 Blue"}""")
            .`when`().post("/school/stream")
            .then()
                .statusCode(401)
                .body("status",  `is`(401))
                .body("payload", nullValue())
    }

    @Test
    fun `teacher account returns 403`() {
        trackTeacher("ust-teacher-1")
        userRepository.saveTeacher(Teacher(fedUid = "ust-teacher-1", name = "Test Teacher"))
        stubToken("ust-teacher-1", "Bearer ust-teacher-token-1")

        given()
            .header("Authorization", "Bearer ust-teacher-token-1")
            .contentType(ContentType.JSON)
            .body("""{"gradeLevel":7,"name":"7 Blue"}""")
            .`when`().post("/school/stream")
            .then()
                .statusCode(200)
                .body("status",  `is`(403))
                .body("payload", nullValue())
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Test
    fun `creates stream with valid body and returns full payload`() {
        trackSchool("ust-school-1")
        userRepository.saveSchool(School(fedUid = "ust-school-1"))
        stubToken("ust-school-1", "Bearer ust-token-1")

        given()
            .header("Authorization", "Bearer ust-token-1")
            .contentType(ContentType.JSON)
            .body("""{"gradeLevel":7,"name":"7 Blue","studentCount":35}""")
            .`when`().post("/school/stream")
            .then()
                .statusCode(200)
                .body("status",               `is`(200))
                .body("message",              `is`("Stream created"))
                .body("payload.id",           notNullValue())
                .body("payload.gradeLevel",   `is`(7))
                .body("payload.name",         `is`("7 Blue"))
                .body("payload.studentCount", `is`(35))
                .body("payload.reStrand",     `is`("CRE"))
    }

    @Test
    fun `missing gradeLevel on create returns 400`() {
        trackSchool("ust-school-2")
        userRepository.saveSchool(School(fedUid = "ust-school-2"))
        stubToken("ust-school-2", "Bearer ust-token-2")

        given()
            .header("Authorization", "Bearer ust-token-2")
            .contentType(ContentType.JSON)
            .body("""{"name":"7 Blue"}""")
            .`when`().post("/school/stream")
            .then()
                .statusCode(200)
                .body("status",  `is`(400))
                .body("payload", nullValue())
    }

    @Test
    fun `gradeLevel out of range returns 400 with message`() {
        trackSchool("ust-school-3")
        userRepository.saveSchool(School(fedUid = "ust-school-3"))
        stubToken("ust-school-3", "Bearer ust-token-3")

        given()
            .header("Authorization", "Bearer ust-token-3")
            .contentType(ContentType.JSON)
            .body("""{"gradeLevel":6,"name":"6 Blue"}""")
            .`when`().post("/school/stream")
            .then()
                .statusCode(200)
                .body("status",  `is`(400))
                .body("message", `is`("gradeLevel must be 7, 8, or 9"))
                .body("payload", nullValue())
    }

    @Test
    fun `blank name on create returns 400`() {
        trackSchool("ust-school-4")
        userRepository.saveSchool(School(fedUid = "ust-school-4"))
        stubToken("ust-school-4", "Bearer ust-token-4")

        given()
            .header("Authorization", "Bearer ust-token-4")
            .contentType(ContentType.JSON)
            .body("""{"gradeLevel":8,"name":"   "}""")
            .`when`().post("/school/stream")
            .then()
                .statusCode(200)
                .body("status",  `is`(400))
                .body("payload", nullValue())
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Test
    fun `updates existing stream and returns updated payload`() {
        trackSchool("ust-school-5")
        userRepository.saveSchool(School(fedUid = "ust-school-5"))
        stubToken("ust-school-5", "Bearer ust-token-5")
        val streamId = streamService
            .upsertStream("ust-school-5", Stream(gradeLevel = 7, name = "7 Red"))
            .payload!!.id!!

        given()
            .header("Authorization", "Bearer ust-token-5")
            .contentType(ContentType.JSON)
            .body("""{"id":"$streamId","name":"7 Green","gradeLevel":8}""")
            .`when`().post("/school/stream")
            .then()
                .statusCode(200)
                .body("status",             `is`(200))
                .body("message",            `is`("Stream updated"))
                .body("payload.id",         `is`(streamId))
                .body("payload.name",       `is`("7 Green"))
                .body("payload.gradeLevel", `is`(8))
    }

    @Test
    fun `updating non-existent stream id returns 404`() {
        trackSchool("ust-school-6")
        userRepository.saveSchool(School(fedUid = "ust-school-6"))
        stubToken("ust-school-6", "Bearer ust-token-6")

        given()
            .header("Authorization", "Bearer ust-token-6")
            .contentType(ContentType.JSON)
            .body("""{"id":"00000000-0000-0000-0000-000000000000","name":"Ghost Stream"}""")
            .`when`().post("/school/stream")
            .then()
                .statusCode(200)
                .body("status",  `is`(404))
                .body("payload", nullValue())
    }
}