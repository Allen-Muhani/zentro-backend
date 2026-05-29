package com.grandtech.timetable

import com.grandtech.auth.FirebaseAuthService
import com.grandtech.model.School
import com.grandtech.model.TimetableEntry
import com.grandtech.repository.TimetableRepository
import com.grandtech.repository.UserRepository
import com.grandtech.school.GetSchoolProfileTest
import com.grandtech.service.CbcDataSeeder
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.neo4j.driver.Driver

/**
 * Integration tests for:
 * - `GET /school/timetable/runs/{runId}/stream/{streamId}`
 * - `GET /school/timetable/runs/{runId}/teacher/{teacherId}`
 *
 * Stream and teacher nodes are created directly via Cypher because no public
 * repository API manages them in these tests. Only [FirebaseAuthService] is
 * mocked; all other beans use the real Neo4j instance.
 */
@QuarkusTest
class TimetableViewsTest {

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
                OPTIONAL MATCH (r)-[:HAS_ENTRY]->(e:TimetableEntry)
                OPTIONAL MATCH (s)-[:HAS_STREAM]->(st:Stream)
                OPTIONAL MATCH (s)-[:HAS_TEACHER]->(t:Teacher)
                DETACH DELETE s, r, e, st, t
                """.trimIndent(),
                mapOf("uids" to trackedSchoolUids.toList()),
            )
        }
        trackedSchoolUids.clear()
    }

    /** Creates a Stream node attached to the school and returns its id. */
    private fun createStream(schoolFedUid: String): String =
        driver.session().use { session ->
            session.run(
                """
                MATCH (s:School {fedUid: ${'$'}fedUid})
                CREATE (st:Stream {id: randomUUID(), gradeLevel: 7, name: 'Blue'})
                CREATE (s)-[:HAS_STREAM]->(st)
                RETURN st.id AS id
                """.trimIndent(),
                mapOf("fedUid" to schoolFedUid),
            ).single()["id"].asString()
        }

    /** Creates a Teacher node attached to the school and returns its id. */
    private fun createTeacher(schoolFedUid: String): String =
        driver.session().use { session ->
            session.run(
                """
                MATCH (s:School {fedUid: ${'$'}fedUid})
                CREATE (t:Teacher {id: randomUUID(), name: 'Test Teacher',
                                   email: ${'$'}email,
                                   maxPeriodsPerWeek: 23, maxPeriodsPerDay: 6})
                CREATE (s)-[:HAS_TEACHER]->(t)
                RETURN t.id AS id
                """.trimIndent(),
                mapOf("fedUid" to schoolFedUid, "email" to "$schoolFedUid@test.ke"),
            ).single()["id"].asString()
        }

    // ── GET /runs/{runId}/stream/{streamId} — auth ────────────────────────────

    @Test
    fun `get stream timetable - missing auth returns 401`() {
        given()
            .`when`().get("/school/timetable/runs/any-run/stream/any-stream")
            .then()
                .statusCode(401)
                .body("status", `is`(401))
    }

    @Test
    fun `get stream timetable - non-school account returns 403`() {
        driver.session().use { s ->
            s.run("CREATE (:User {fedUid: \$uid})", mapOf("uid" to "tvt-user-ss-1"))
        }
        stubToken("tvt-user-ss-1", "Bearer tvt-user-ss-token-1")
        try {
            given()
                .header("Authorization", "Bearer tvt-user-ss-token-1")
                .`when`().get("/school/timetable/runs/any-run/stream/any-stream")
                .then()
                    .statusCode(200)
                    .body("status", `is`(403))
        } finally {
            driver.session().use { s ->
                s.run("MATCH (u:User {fedUid: \$uid}) DETACH DELETE u", mapOf("uid" to "tvt-user-ss-1"))
            }
        }
    }

    // ── GET /runs/{runId}/stream/{streamId} — results ─────────────────────────

    @Test
    fun `get stream timetable - returns empty list when run has no entries`() {
        trackSchool("tvt-school-ss-1")
        userRepository.saveSchool(School(fedUid = "tvt-school-ss-1"))
        val streamId = createStream("tvt-school-ss-1")
        val runId    = timetableRepository.createRun("tvt-school-ss-1", "2025", "Term 1", 120)
        stubToken("tvt-school-ss-1", "Bearer tvt-token-ss-1")

        given()
            .header("Authorization", "Bearer tvt-token-ss-1")
            .`when`().get("/school/timetable/runs/$runId/stream/$streamId")
            .then()
                .statusCode(200)
                .body("status",        `is`(200))
                .body("payload",       notNullValue())
                .body("payload.size()", `is`(0))
    }

    @Test
    fun `get stream timetable - returns entries for the requested stream`() {
        trackSchool("tvt-school-ss-2")
        userRepository.saveSchool(School(fedUid = "tvt-school-ss-2"))
        val streamId  = createStream("tvt-school-ss-2")
        val teacherId = createTeacher("tvt-school-ss-2")
        val runId     = timetableRepository.createRun("tvt-school-ss-2", "2025", "Term 1", 120)
        val subjectId = CbcDataSeeder.SUBJECTS.first { !it.isPpiFixed }.id

        timetableRepository.saveEntries(
            runId,
            listOf(
                TimetableEntry(
                    streamId  = streamId,
                    subjectId = subjectId,
                    teacherId = teacherId,
                    day       = "MONDAY",
                    period    = 1,
                    startTime = "08:20",
                    endTime   = "09:00",
                ),
            ),
        )
        stubToken("tvt-school-ss-2", "Bearer tvt-token-ss-2")

        given()
            .header("Authorization", "Bearer tvt-token-ss-2")
            .`when`().get("/school/timetable/runs/$runId/stream/$streamId")
            .then()
                .statusCode(200)
                .body("status",          `is`(200))
                .body("payload.size()",  `is`(1))
                .body("payload[0].day",  `is`("MONDAY"))
                .body("payload[0].period", `is`(1))
    }

    @Test
    fun `get stream timetable - does not return entries from another stream`() {
        trackSchool("tvt-school-ss-3")
        userRepository.saveSchool(School(fedUid = "tvt-school-ss-3"))
        val streamA   = createStream("tvt-school-ss-3")
        val teacherId = createTeacher("tvt-school-ss-3")
        val runId     = timetableRepository.createRun("tvt-school-ss-3", "2025", "Term 1", 120)
        val subjectId = CbcDataSeeder.SUBJECTS.first { !it.isPpiFixed }.id

        timetableRepository.saveEntries(
            runId,
            listOf(
                TimetableEntry(
                    streamId  = streamA,
                    subjectId = subjectId,
                    teacherId = teacherId,
                    day       = "MONDAY",
                    period    = 1,
                ),
                // Entry for a non-existent stream — must not appear in streamA response.
                TimetableEntry(
                    streamId  = "other-stream-id",
                    subjectId = subjectId,
                    teacherId = teacherId,
                    day       = "TUESDAY",
                    period    = 2,
                ),
            ),
        )
        stubToken("tvt-school-ss-3", "Bearer tvt-token-ss-3")

        given()
            .header("Authorization", "Bearer tvt-token-ss-3")
            .`when`().get("/school/timetable/runs/$runId/stream/$streamA")
            .then()
                .statusCode(200)
                .body("payload.size()", `is`(1))
                .body("payload[0].day", `is`("MONDAY"))
    }

    @Test
    fun `get stream timetable - returns entries ordered by day and period`() {
        trackSchool("tvt-school-ss-4")
        userRepository.saveSchool(School(fedUid = "tvt-school-ss-4"))
        val streamId  = createStream("tvt-school-ss-4")
        val teacherId = createTeacher("tvt-school-ss-4")
        val runId     = timetableRepository.createRun("tvt-school-ss-4", "2025", "Term 1", 120)
        val subjectId = CbcDataSeeder.SUBJECTS.first { !it.isPpiFixed }.id

        timetableRepository.saveEntries(
            runId,
            listOf(
                TimetableEntry(streamId = streamId, subjectId = subjectId,
                    teacherId = teacherId, day = "WEDNESDAY", period = 3),
                TimetableEntry(streamId = streamId, subjectId = subjectId,
                    teacherId = teacherId, day = "MONDAY", period = 2),
                TimetableEntry(streamId = streamId, subjectId = subjectId,
                    teacherId = teacherId, day = "MONDAY", period = 1),
            ),
        )
        stubToken("tvt-school-ss-4", "Bearer tvt-token-ss-4")

        // Day ordering is alphabetical: MONDAY < WEDNESDAY.
        given()
            .header("Authorization", "Bearer tvt-token-ss-4")
            .`when`().get("/school/timetable/runs/$runId/stream/$streamId")
            .then()
                .statusCode(200)
                .body("payload[0].day",    `is`("MONDAY"))
                .body("payload[0].period", `is`(1))
                .body("payload[1].day",    `is`("MONDAY"))
                .body("payload[1].period", `is`(2))
                .body("payload[2].day",    `is`("WEDNESDAY"))
    }

    // ── GET /runs/{runId}/teacher/{teacherId} — auth ──────────────────────────

    @Test
    fun `get teacher timetable - missing auth returns 401`() {
        given()
            .`when`().get("/school/timetable/runs/any-run/teacher/any-teacher")
            .then()
                .statusCode(401)
                .body("status", `is`(401))
    }

    @Test
    fun `get teacher timetable - non-school account returns 403`() {
        driver.session().use { s ->
            s.run("CREATE (:User {fedUid: \$uid})", mapOf("uid" to "tvt-user-gt-1"))
        }
        stubToken("tvt-user-gt-1", "Bearer tvt-user-gt-token-1")
        try {
            given()
                .header("Authorization", "Bearer tvt-user-gt-token-1")
                .`when`().get("/school/timetable/runs/any-run/teacher/any-teacher")
                .then()
                    .statusCode(200)
                    .body("status", `is`(403))
        } finally {
            driver.session().use { s ->
                s.run("MATCH (u:User {fedUid: \$uid}) DETACH DELETE u", mapOf("uid" to "tvt-user-gt-1"))
            }
        }
    }

    // ── GET /runs/{runId}/teacher/{teacherId} — results ───────────────────────

    @Test
    fun `get teacher timetable - returns empty list when run has no entries`() {
        trackSchool("tvt-school-gt-1")
        userRepository.saveSchool(School(fedUid = "tvt-school-gt-1"))
        val teacherId = createTeacher("tvt-school-gt-1")
        val runId     = timetableRepository.createRun("tvt-school-gt-1", "2025", "Term 1", 120)
        stubToken("tvt-school-gt-1", "Bearer tvt-token-gt-1")

        given()
            .header("Authorization", "Bearer tvt-token-gt-1")
            .`when`().get("/school/timetable/runs/$runId/teacher/$teacherId")
            .then()
                .statusCode(200)
                .body("status",        `is`(200))
                .body("payload",       notNullValue())
                .body("payload.size()", `is`(0))
    }

    @Test
    fun `get teacher timetable - returns only entries for the requested teacher`() {
        trackSchool("tvt-school-gt-2")
        userRepository.saveSchool(School(fedUid = "tvt-school-gt-2"))
        val streamId   = createStream("tvt-school-gt-2")
        val teacherA   = createTeacher("tvt-school-gt-2")
        val runId      = timetableRepository.createRun("tvt-school-gt-2", "2025", "Term 1", 120)
        val subjectId  = CbcDataSeeder.SUBJECTS.first { !it.isPpiFixed }.id

        timetableRepository.saveEntries(
            runId,
            listOf(
                TimetableEntry(
                    streamId  = streamId,
                    subjectId = subjectId,
                    teacherId = teacherA,
                    day       = "MONDAY",
                    period    = 1,
                ),
                // Entry for a different teacher — must not appear in teacherA response.
                TimetableEntry(
                    streamId  = streamId,
                    subjectId = subjectId,
                    teacherId = "other-teacher-id",
                    day       = "TUESDAY",
                    period    = 2,
                ),
            ),
        )
        stubToken("tvt-school-gt-2", "Bearer tvt-token-gt-2")

        given()
            .header("Authorization", "Bearer tvt-token-gt-2")
            .`when`().get("/school/timetable/runs/$runId/teacher/$teacherA")
            .then()
                .statusCode(200)
                .body("payload.size()", `is`(1))
                .body("payload[0].day", `is`("MONDAY"))
    }

    @Test
    fun `get teacher timetable - returns multiple entries across streams and days`() {
        trackSchool("tvt-school-gt-3")
        userRepository.saveSchool(School(fedUid = "tvt-school-gt-3"))
        val streamId  = createStream("tvt-school-gt-3")
        val teacherId = createTeacher("tvt-school-gt-3")
        val runId     = timetableRepository.createRun("tvt-school-gt-3", "2025", "Term 1", 120)
        val subjectId = CbcDataSeeder.SUBJECTS.first { !it.isPpiFixed }.id

        timetableRepository.saveEntries(
            runId,
            listOf(
                TimetableEntry(streamId = streamId, subjectId = subjectId,
                    teacherId = teacherId, day = "MONDAY",    period = 1),
                TimetableEntry(streamId = streamId, subjectId = subjectId,
                    teacherId = teacherId, day = "WEDNESDAY", period = 3),
                TimetableEntry(streamId = streamId, subjectId = subjectId,
                    teacherId = teacherId, day = "FRIDAY",    period = 5),
            ),
        )
        stubToken("tvt-school-gt-3", "Bearer tvt-token-gt-3")

        given()
            .header("Authorization", "Bearer tvt-token-gt-3")
            .`when`().get("/school/timetable/runs/$runId/teacher/$teacherId")
            .then()
                .statusCode(200)
                .body("payload.size()", `is`(3))
    }
}
