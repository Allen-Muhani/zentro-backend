package com.grandtech.timetable

import com.grandtech.auth.FirebaseAuthService
import com.grandtech.model.School
import com.grandtech.repository.UserRepository
import com.grandtech.school.GetSchoolProfileTest
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
 * Integration tests for `POST /school/timetable/generate`.
 *
 * Only [FirebaseAuthService] is mocked — token verification requires a live
 * Firebase connection unavailable in CI. All other beans run against the real
 * Neo4j instance.
 *
 * The generate endpoint returns immediately with the run in RUNNING status.
 * The background solver execution is not awaited in these tests.
 */
@QuarkusTest
class GenerateTimetableTest {

    @InjectMock
    lateinit var firebaseAuthService: FirebaseAuthService

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var driver: Driver

    private val trackedSchoolUids = mutableSetOf<String>()

    private fun trackSchool(vararg uids: String) { trackedSchoolUids.addAll(uids.toList()) }

    private fun stubToken(uid: String, bearer: String) {
        Mockito.`when`(firebaseAuthService.verifyToken(bearer))
            .thenReturn(GetSchoolProfileTest.buildToken(uid))
    }

    @AfterEach
    fun cleanUp() {
        if (trackedSchoolUids.isEmpty()) return
        driver.session().use { session ->
            session.run(
                """
                MATCH (s:School) WHERE s.fedUid IN ${'$'}uids
                OPTIONAL MATCH (s)-[:HAS_TIMETABLE_RUN]->(r:TimetableRun)
                OPTIONAL MATCH (r)-[:HAS_ENTRY]->(e:TimetableEntry)
                DETACH DELETE s, r, e
                """.trimIndent(),
                mapOf("uids" to trackedSchoolUids.toList()),
            )
        }
        trackedSchoolUids.clear()
    }

    // ── Auth guards ───────────────────────────────────────────────────────────

    @Test
    fun `missing auth header returns 401`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"academicYear":"2025","term":"Term 1"}""")
            .`when`().post("/school/timetable/generate")
            .then()
                .statusCode(401)
                .body("status", `is`(401))
    }

    @Test
    fun `non-school account returns 403`() {
        driver.session().use { session ->
            session.run(
                "CREATE (:User {fedUid: \$uid})",
                mapOf("uid" to "gt-user-1"),
            )
        }
        stubToken("gt-user-1", "Bearer gt-user-token-1")

        try {
            given()
                .header("Authorization", "Bearer gt-user-token-1")
                .contentType(ContentType.JSON)
                .body("""{"academicYear":"2025","term":"Term 1"}""")
                .`when`().post("/school/timetable/generate")
                .then()
                    .statusCode(200)
                    .body("status",  `is`(403))
                    .body("payload", nullValue())
        } finally {
            driver.session().use { session ->
                session.run(
                    "MATCH (u:User {fedUid: \$uid}) DETACH DELETE u",
                    mapOf("uid" to "gt-user-1"),
                )
            }
        }
    }

    // ── Successful generation ─────────────────────────────────────────────────

    @Test
    fun `valid request returns 200 with a RUNNING run`() {
        trackSchool("gt-school-1")
        userRepository.saveSchool(School(fedUid = "gt-school-1"))
        stubToken("gt-school-1", "Bearer gt-token-1")

        given()
            .header("Authorization", "Bearer gt-token-1")
            .contentType(ContentType.JSON)
            .body("""{"academicYear":"2025","term":"Term 1"}""")
            .`when`().post("/school/timetable/generate")
            .then()
                .statusCode(200)
                .body("status",          `is`(200))
                .body("payload",         notNullValue())
                .body("payload.id",      notNullValue())
                .body("payload.status",  `is`("RUNNING"))
    }

    @Test
    fun `run is created with the supplied academicYear and term`() {
        trackSchool("gt-school-2")
        userRepository.saveSchool(School(fedUid = "gt-school-2"))
        stubToken("gt-school-2", "Bearer gt-token-2")

        given()
            .header("Authorization", "Bearer gt-token-2")
            .contentType(ContentType.JSON)
            .body("""{"academicYear":"2026","term":"Term 3","timeLimitSeconds":30}""")
            .`when`().post("/school/timetable/generate")
            .then()
                .statusCode(200)
                .body("payload.academicYear",     `is`("2026"))
                .body("payload.term",             `is`("Term 3"))
                .body("payload.timeLimitSeconds", `is`(30))
    }

    @Test
    fun `timeLimitSeconds defaults to 120 when not supplied`() {
        trackSchool("gt-school-3")
        userRepository.saveSchool(School(fedUid = "gt-school-3"))
        stubToken("gt-school-3", "Bearer gt-token-3")

        given()
            .header("Authorization", "Bearer gt-token-3")
            .contentType(ContentType.JSON)
            .body("""{"academicYear":"2025","term":"Term 1"}""")
            .`when`().post("/school/timetable/generate")
            .then()
                .statusCode(200)
                .body("payload.timeLimitSeconds", `is`(120))
    }
}
