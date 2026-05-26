package com.grandtech.repository.timetable

import com.grandtech.model.School
import com.grandtech.model.Stream
import com.grandtech.model.Teacher
import com.grandtech.model.TimetableEntry
import com.grandtech.repository.TimetableRepository
import com.grandtech.repository.UserRepository
import com.grandtech.service.CbcDataSeeder
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.neo4j.driver.Driver

/**
 * Integration tests for [TimetableRepository].
 *
 * All tests run against the real Neo4j instance. Each test registers its
 * school fedUid in [trackedSchoolUids] so [cleanUp] tears down all created
 * nodes after every test.
 */
@QuarkusTest
class TimetableRepositoryTest {

    @Inject lateinit var timetableRepository: TimetableRepository
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var driver: Driver

    private val trackedSchoolUids = mutableSetOf<String>()

    private fun trackSchool(vararg uids: String) { trackedSchoolUids.addAll(uids.toList()) }

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

    // ── createRun ─────────────────────────────────────────────────────────────

    @Test
    fun `createRun returns a non-blank UUID`() {
        trackSchool("trt-school-1")
        userRepository.saveSchool(School(fedUid = "trt-school-1"))

        val runId = timetableRepository.createRun("trt-school-1", "2025", "Term 1", 120)

        assertTrue(runId.isNotBlank())
    }

    @Test
    fun `createRun creates a RUNNING run linked to the school`() {
        trackSchool("trt-school-2")
        userRepository.saveSchool(School(fedUid = "trt-school-2"))

        val runId = timetableRepository.createRun("trt-school-2", "2025", "Term 2", 60)
        val run = timetableRepository.getRun(runId)

        assertNotNull(run)
        assertEquals("RUNNING", run!!.status)
        assertEquals("2025", run.academicYear)
        assertEquals("Term 2", run.term)
        assertEquals(60, run.timeLimitSeconds)
        assertEquals("trt-school-2", run.schoolFedUid)
    }

    @Test
    fun `createRun links run to school via HAS_TIMETABLE_RUN relationship`() {
        trackSchool("trt-school-3")
        userRepository.saveSchool(School(fedUid = "trt-school-3"))

        val runId = timetableRepository.createRun("trt-school-3", "2025", "Term 3", 120)

        val linked = driver.session().use { session ->
            session.run(
                """
                MATCH (:School {fedUid: ${'$'}fedUid})-[:HAS_TIMETABLE_RUN]->(:TimetableRun {id: ${'$'}runId})
                RETURN count(*) > 0 AS exists
                """.trimIndent(),
                mapOf("fedUid" to "trt-school-3", "runId" to runId),
            ).single()["exists"].asBoolean()
        }
        assertTrue(linked)
    }

    // ── getRun ────────────────────────────────────────────────────────────────

    @Test
    fun `getRun returns null for an unknown id`() {
        val run = timetableRepository.getRun("non-existent-run-id")
        assertNull(run)
    }

    @Test
    fun `getRun returns correct fields on a freshly created run`() {
        trackSchool("trt-school-4")
        userRepository.saveSchool(School(fedUid = "trt-school-4"))

        val runId = timetableRepository.createRun("trt-school-4", "2024", "Term 1", 90)
        val run = timetableRepository.getRun(runId)

        assertNotNull(run)
        assertEquals(runId, run!!.id)
        assertEquals("trt-school-4", run.schoolFedUid)
        assertEquals("2024", run.academicYear)
        assertEquals("Term 1", run.term)
        assertEquals("RUNNING", run.status)
        assertEquals(90, run.timeLimitSeconds)
        assertNotNull(run.generatedAt)
        assertNull(run.completedAt)
        assertNull(run.solverStatus)
    }

    // ── updateRunCompleted ────────────────────────────────────────────────────

    @Test
    fun `updateRunCompleted sets status to COMPLETED with solver statistics`() {
        trackSchool("trt-school-5")
        userRepository.saveSchool(School(fedUid = "trt-school-5"))

        val runId = timetableRepository.createRun("trt-school-5", "2025", "Term 1", 120)
        timetableRepository.updateRunCompleted(
            runId          = runId,
            solverStatus   = "OPTIMAL",
            objectiveValue = 42L,
            wallTimeMs     = 3500L,
            violations     = listOf("SC1: 2 penalties"),
        )

        val run = timetableRepository.getRun(runId)!!
        assertEquals("COMPLETED", run.status)
        assertEquals("OPTIMAL", run.solverStatus)
        assertEquals(42L, run.objectiveValue)
        assertEquals(3500L, run.solverWallTimeMs)
        assertEquals(listOf("SC1: 2 penalties"), run.violations)
        assertNotNull(run.completedAt)
    }

    @Test
    fun `updateRunCompleted persists an empty violations list`() {
        trackSchool("trt-school-6")
        userRepository.saveSchool(School(fedUid = "trt-school-6"))

        val runId = timetableRepository.createRun("trt-school-6", "2025", "Term 2", 120)
        timetableRepository.updateRunCompleted(
            runId          = runId,
            solverStatus   = "OPTIMAL",
            objectiveValue = 0L,
            wallTimeMs     = 1000L,
            violations     = emptyList(),
        )

        val run = timetableRepository.getRun(runId)!!
        assertEquals("COMPLETED", run.status)
        assertTrue(run.violations?.isEmpty() ?: false)
    }

    // ── updateRunFailed ───────────────────────────────────────────────────────

    @Test
    fun `updateRunFailed sets status to FAILED with diagnostic report`() {
        trackSchool("trt-school-7")
        userRepository.saveSchool(School(fedUid = "trt-school-7"))

        val runId = timetableRepository.createRun("trt-school-7", "2025", "Term 1", 120)
        timetableRepository.updateRunFailed(runId, """{"hasCriticalIssues":true}""")

        val run = timetableRepository.getRun(runId)!!
        assertEquals("FAILED", run.status)
        assertEquals("""{"hasCriticalIssues":true}""", run.diagnosticReport)
        assertNotNull(run.completedAt)
    }

    // ── listRuns ──────────────────────────────────────────────────────────────

    @Test
    fun `listRuns returns empty list when school has no runs`() {
        trackSchool("trt-school-8")
        userRepository.saveSchool(School(fedUid = "trt-school-8"))

        val runs = timetableRepository.listRuns("trt-school-8")

        assertTrue(runs.isEmpty())
    }

    @Test
    fun `listRuns returns all runs for the school`() {
        trackSchool("trt-school-9")
        userRepository.saveSchool(School(fedUid = "trt-school-9"))

        timetableRepository.createRun("trt-school-9", "2025", "Term 1", 120)
        timetableRepository.createRun("trt-school-9", "2025", "Term 2", 120)

        val runs = timetableRepository.listRuns("trt-school-9")
        assertEquals(2, runs.size)
    }

    @Test
    fun `listRuns returns runs newest first`() {
        trackSchool("trt-school-10")
        userRepository.saveSchool(School(fedUid = "trt-school-10"))

        val firstRunId  = timetableRepository.createRun("trt-school-10", "2025", "Term 1", 120)
        Thread.sleep(10) // ensure different generatedAt timestamps
        val secondRunId = timetableRepository.createRun("trt-school-10", "2025", "Term 2", 120)

        val runs = timetableRepository.listRuns("trt-school-10")
        assertEquals(secondRunId, runs[0].id)
        assertEquals(firstRunId,  runs[1].id)
    }

    @Test
    fun `listRuns only returns runs belonging to the requested school`() {
        trackSchool("trt-school-11a", "trt-school-11b")
        userRepository.saveSchool(School(fedUid = "trt-school-11a"))
        userRepository.saveSchool(School(fedUid = "trt-school-11b"))

        timetableRepository.createRun("trt-school-11a", "2025", "Term 1", 120)
        timetableRepository.createRun("trt-school-11b", "2025", "Term 1", 120)

        val runs = timetableRepository.listRuns("trt-school-11a")
        assertEquals(1, runs.size)
        assertEquals("trt-school-11a", runs[0].schoolFedUid)
    }

    // ── saveEntries / getEntriesForStream / getEntriesForTeacher ─────────────

    private fun seedSchoolWithStreamAndTeacher(fedUid: String): Pair<String, String> {
        userRepository.saveSchool(School(fedUid = fedUid))
        val streamId  = driver.session().use { session ->
            session.run(
                """
                MATCH (s:School {fedUid: ${'$'}fedUid})
                CREATE (st:Stream {id: randomUUID(), gradeLevel: 7, name: 'Blue', reStrand: 'CRE'})
                CREATE (s)-[:HAS_STREAM]->(st)
                RETURN st.id AS id
                """.trimIndent(),
                mapOf("fedUid" to fedUid),
            ).single()["id"].asString()
        }
        val teacherId = driver.session().use { session ->
            session.run(
                """
                MATCH (s:School {fedUid: ${'$'}fedUid})
                CREATE (t:Teacher {id: randomUUID(), name: 'Test Teacher',
                                   email: ${'$'}email,
                                   maxPeriodsPerWeek: 23, maxPeriodsPerDay: 6})
                CREATE (s)-[:HAS_TEACHER]->(t)
                RETURN t.id AS id
                """.trimIndent(),
                mapOf("fedUid" to fedUid, "email" to "$fedUid@test.ke"),
            ).single()["id"].asString()
        }
        return streamId to teacherId
    }

    @Test
    fun `saveEntries creates entry nodes linked to run, stream, subject, and teacher`() {
        trackSchool("trt-school-12")
        val (streamId, teacherId) = seedSchoolWithStreamAndTeacher("trt-school-12")
        val runId = timetableRepository.createRun("trt-school-12", "2025", "Term 1", 120)
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

        val entries = timetableRepository.getEntriesForStream(runId, streamId)
        assertEquals(1, entries.size)
        assertEquals("MONDAY", entries[0].day)
        assertEquals(1, entries[0].period)
        assertEquals(subjectId, entries[0].subjectId)
        assertEquals(teacherId, entries[0].teacherId)
    }

    @Test
    fun `saveEntries is a no-op when the list is empty`() {
        trackSchool("trt-school-13")
        userRepository.saveSchool(School(fedUid = "trt-school-13"))
        val runId = timetableRepository.createRun("trt-school-13", "2025", "Term 1", 120)

        timetableRepository.saveEntries(runId, emptyList())

        val runs = timetableRepository.listRuns("trt-school-13")
        assertEquals(1, runs.size) // run exists, just no entries
    }

    @Test
    fun `getEntriesForStream returns only entries for the requested stream`() {
        trackSchool("trt-school-14")
        val (streamId, teacherId) = seedSchoolWithStreamAndTeacher("trt-school-14")
        val runId     = timetableRepository.createRun("trt-school-14", "2025", "Term 1", 120)
        val subjectId = CbcDataSeeder.SUBJECTS.first { !it.isPpiFixed }.id

        timetableRepository.saveEntries(
            runId,
            listOf(
                TimetableEntry(streamId = streamId, subjectId = subjectId,
                    teacherId = teacherId, day = "MONDAY", period = 1),
                TimetableEntry(streamId = streamId, subjectId = subjectId,
                    teacherId = teacherId, day = "TUESDAY", period = 2),
                // Entry for a non-existent stream — should not be returned.
                TimetableEntry(streamId = "other-stream", subjectId = subjectId,
                    teacherId = teacherId, day = "WEDNESDAY", period = 3),
            ),
        )

        val entries = timetableRepository.getEntriesForStream(runId, streamId)
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.streamId == streamId || it.streamId == null })
    }

    @Test
    fun `getEntriesForTeacher returns only entries for the requested teacher`() {
        trackSchool("trt-school-15")
        val (streamId, teacherId) = seedSchoolWithStreamAndTeacher("trt-school-15")
        val runId     = timetableRepository.createRun("trt-school-15", "2025", "Term 1", 120)
        val subjectId = CbcDataSeeder.SUBJECTS.first { !it.isPpiFixed }.id

        timetableRepository.saveEntries(
            runId,
            listOf(
                TimetableEntry(streamId = streamId, subjectId = subjectId,
                    teacherId = teacherId,  day = "MONDAY",    period = 1),
                TimetableEntry(streamId = streamId, subjectId = subjectId,
                    teacherId = teacherId,  day = "WEDNESDAY", period = 3),
                TimetableEntry(streamId = streamId, subjectId = subjectId,
                    teacherId = "other-teacher", day = "FRIDAY", period = 5),
            ),
        )

        val entries = timetableRepository.getEntriesForTeacher(runId, teacherId)
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.teacherId == teacherId })
    }

    @Test
    fun `getEntriesForStream returns entries ordered by day and period`() {
        trackSchool("trt-school-16")
        val (streamId, teacherId) = seedSchoolWithStreamAndTeacher("trt-school-16")
        val runId     = timetableRepository.createRun("trt-school-16", "2025", "Term 1", 120)
        val subjectId = CbcDataSeeder.SUBJECTS.first { !it.isPpiFixed }.id

        timetableRepository.saveEntries(
            runId,
            listOf(
                TimetableEntry(streamId = streamId, subjectId = subjectId,
                    teacherId = teacherId, day = "WEDNESDAY", period = 2),
                TimetableEntry(streamId = streamId, subjectId = subjectId,
                    teacherId = teacherId, day = "MONDAY",    period = 5),
                TimetableEntry(streamId = streamId, subjectId = subjectId,
                    teacherId = teacherId, day = "MONDAY",    period = 1),
            ),
        )

        val entries = timetableRepository.getEntriesForStream(runId, streamId)
        // Neo4j ORDER BY day, period — day is alphabetical so MONDAY < WEDNESDAY.
        assertEquals("MONDAY",    entries[0].day)
        assertEquals(1,           entries[0].period)
        assertEquals("MONDAY",    entries[1].day)
        assertEquals(5,           entries[1].period)
        assertEquals("WEDNESDAY", entries[2].day)
    }
}
