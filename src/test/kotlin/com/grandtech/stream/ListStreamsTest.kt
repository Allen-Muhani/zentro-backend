package com.grandtech.stream

import com.grandtech.auth.FirebaseAuthService
import com.grandtech.model.School
import com.grandtech.model.Stream
import com.grandtech.model.Teacher
import com.grandtech.repository.TeacherRepository
import com.grandtech.repository.UserRepository
import com.grandtech.school.GetSchoolProfileTest
import com.grandtech.service.CbcDataSeeder
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
    lateinit var streamService: StreamService

    @Inject
    lateinit var teacherRepository: TeacherRepository

    @Inject
    lateinit var driver: Driver

    private val trackedSchoolUids = mutableSetOf<String>()

    private fun trackSchool(vararg uids: String) { trackedSchoolUids.addAll(uids.toList()) }

    private fun createTeacher(schoolFedUid: String, name: String, email: String): Teacher =
        checkNotNull(
            teacherRepository.createTeacher(
                schoolFedUid,
                Teacher(name = name, email = email, subjectIds = listOf(CbcDataSeeder.SUBJECTS.first().id)),
            ),
        ) { "Failed to create teacher for school $schoolFedUid" }

    private fun stubToken(schoolUid: String, bearer: String) {
        Mockito.`when`(firebaseAuthService.verifyToken(bearer))
            .thenReturn(GetSchoolProfileTest.buildToken(schoolUid))
    }

    @AfterEach
    fun cleanUp() {
        if (trackedSchoolUids.isNotEmpty()) {
            driver.session().use { session ->
                session.run(
                    """
                    MATCH (s:School) WHERE s.fedUid IN ${'$'}uids
                    OPTIONAL MATCH (s)-[:HAS_STREAM]->(st:Stream)
                    OPTIONAL MATCH (s)-[:HAS_TEACHER]->(t:Teacher)
                    DETACH DELETE s, st, t
                    """.trimIndent(),
                    mapOf("uids" to trackedSchoolUids.toList()),
                )
                trackedSchoolUids.clear()
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
    fun `returns stream with formTeacher when FORM_TEACHER relationship exists`() {
        trackSchool("lst-school-5")
        userRepository.saveSchool(School(fedUid = "lst-school-5"))
        val teacher = createTeacher("lst-school-5", "Ms Achieng", "achieng@school.ke")
        stubToken("lst-school-5", "Bearer lst-token-5")
        streamService.upsertStream("lst-school-5", Stream(gradeLevel = 8, name = "Silver", formTeacher = Teacher(id = teacher.id)))

        given()
            .header("Authorization", "Bearer lst-token-5")
            .`when`().get("/school/stream/list")
            .then()
                .statusCode(200)
                .body("payload[0].formTeacher.id",    `is`(teacher.id))
                .body("payload[0].formTeacher.name",  `is`("Ms Achieng"))
                .body("payload[0].formTeacher.email", `is`("achieng@school.ke"))
    }
}