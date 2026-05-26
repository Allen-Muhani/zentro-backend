package com.grandtech.timetable

import com.grandtech.auth.FirebaseAuthService
import com.grandtech.model.School
import com.grandtech.repository.TimetableRepository
import com.grandtech.repository.UserRepository
import com.grandtech.school.GetSchoolProfileTest
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
 * Integration tests for:
 * - `GET /school/timetable/runs`
 * - `GET /school/timetable/runs/{runId}`
 *
 * Only [FirebaseAuthService] is mocked. All other beans use the real Neo4j instance.
 */
@QuarkusTest
class TimetableRunsTest {

    @InjectMock
    lateinit var firebaseAuthService: FirebaseAuthService

    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var timetableRepository: TimetableRepository
    @Inject lateinit var driver: Driver

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
                DETACH DELETE s, r
                """.trimIndent(),
                mapOf("uids" to trackedSchoolUids.toList()),
            )
        }
        trackedSchoolUids.clear()
    }

    // ── GET /runs — auth ──────────────────────────────────────────────────────

    @Test
    fun `list runs - missing auth returns 401`() {
        given()
            .`when`().get("/school/timetable/runs")
            .then()
                .statusCode(401)
                .body("status", `is`(401))
    }

    @Test
    fun `list runs - non-school account returns 403`() {
        driver.session().use { s ->
            s.run("CREATE (:User {fedUid: \$uid})", mapOf("uid" to "trt-user-lr-1"))
        }
        stubToken("trt-user-lr-1", "Bearer trt-user-lr-token-1")
        try {
            given()
                .header("Authorization", "Bearer trt-user-lr-token-1")
                .`when`().get("/school/timetable/runs")
                .then()
                    .statusCode(200)
                    .body("status", `is`(403))
        } finally {
            driver.session().use { s ->
                s.run("MATCH (u:User {fedUid: \$uid}) DETACH DELETE u", mapOf("uid" to "trt-user-lr-1"))
            }
        }
    }

    // ── GET /runs — list ──────────────────────────────────────────────────────

    @Test
    fun `list runs - returns empty list when school has no runs`() {
        trackSchool("trt-school-lr-1")
        userRepository.saveSchool(School(fedUid = "trt-school-lr-1"))
        stubToken("trt-school-lr-1", "Bearer trt-token-lr-1")

        given()
            .header("Authorization", "Bearer trt-token-lr-1")
            .`when`().get("/school/timetable/runs")
            .then()
                .statusCode(200)
                .body("status",        `is`(200))
                .body("payload",       notNullValue())
                .body("payload.size()", `is`(0))
    }

    @Test
    fun `list runs - returns runs for the authenticated school`() {
        trackSchool("trt-school-lr-2")
        userRepository.saveSchool(School(fedUid = "trt-school-lr-2"))
        timetableRepository.createRun("trt-school-lr-2", "2025", "Term 1", 120)
        timetableRepository.createRun("trt-school-lr-2", "2025", "Term 2", 120)
        stubToken("trt-school-lr-2", "Bearer trt-token-lr-2")

        given()
            .header("Authorization", "Bearer trt-token-lr-2")
            .`when`().get("/school/timetable/runs")
            .then()
                .statusCode(200)
                .body("status",        `is`(200))
                .body("payload.size()", `is`(2))
    }

    @Test
    fun `list runs - does not return runs from another school`() {
        trackSchool("trt-school-lr-3a", "trt-school-lr-3b")
        userRepository.saveSchool(School(fedUid = "trt-school-lr-3a"))
        userRepository.saveSchool(School(fedUid = "trt-school-lr-3b"))
        timetableRepository.createRun("trt-school-lr-3a", "2025", "Term 1", 120)
        timetableRepository.createRun("trt-school-lr-3b", "2025", "Term 1", 120)
        stubToken("trt-school-lr-3a", "Bearer trt-token-lr-3a")

        given()
            .header("Authorization", "Bearer trt-token-lr-3a")
            .`when`().get("/school/timetable/runs")
            .then()
                .statusCode(200)
                .body("payload.size()", `is`(1))
    }

    // ── GET /runs/{runId} — auth ──────────────────────────────────────────────

    @Test
    fun `get run - missing auth returns 401`() {
        given()
            .`when`().get("/school/timetable/runs/any-run-id")
            .then()
                .statusCode(401)
                .body("status", `is`(401))
    }

    // ── GET /runs/{runId} — result ────────────────────────────────────────────

    @Test
    fun `get run - returns 404 when run does not exist`() {
        trackSchool("trt-school-gr-1")
        userRepository.saveSchool(School(fedUid = "trt-school-gr-1"))
        stubToken("trt-school-gr-1", "Bearer trt-token-gr-1")

        given()
            .header("Authorization", "Bearer trt-token-gr-1")
            .`when`().get("/school/timetable/runs/non-existent-id")
            .then()
                .statusCode(200)
                .body("status",  `is`(404))
                .body("payload", nullValue())
    }

    @Test
    fun `get run - returns 200 with run when found`() {
        trackSchool("trt-school-gr-2")
        userRepository.saveSchool(School(fedUid = "trt-school-gr-2"))
        val runId = timetableRepository.createRun("trt-school-gr-2", "2025", "Term 1", 120)
        stubToken("trt-school-gr-2", "Bearer trt-token-gr-2")

        given()
            .header("Authorization", "Bearer trt-token-gr-2")
            .`when`().get("/school/timetable/runs/$runId")
            .then()
                .statusCode(200)
                .body("status",              `is`(200))
                .body("payload.id",          `is`(runId))
                .body("payload.status",      `is`("RUNNING"))
                .body("payload.academicYear",`is`("2025"))
                .body("payload.term",        `is`("Term 1"))
    }

    @Test
    fun `get run - returns COMPLETED status and solverStatus after completion`() {
        trackSchool("trt-school-gr-3")
        userRepository.saveSchool(School(fedUid = "trt-school-gr-3"))
        val runId = timetableRepository.createRun("trt-school-gr-3", "2025", "Term 1", 120)
        timetableRepository.updateRunCompleted(runId, "OPTIMAL", 88L, 2000L, emptyList())
        stubToken("trt-school-gr-3", "Bearer trt-token-gr-3")

        given()
            .header("Authorization", "Bearer trt-token-gr-3")
            .`when`().get("/school/timetable/runs/$runId")
            .then()
                .statusCode(200)
                .body("payload.status",       `is`("COMPLETED"))
                .body("payload.solverStatus", `is`("OPTIMAL"))
    }
}
