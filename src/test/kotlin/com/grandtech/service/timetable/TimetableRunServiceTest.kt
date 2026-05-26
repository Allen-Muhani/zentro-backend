package com.grandtech.service.timetable

import com.grandtech.model.School
import com.grandtech.repository.TimetableRepository
import com.grandtech.repository.UserRepository
import com.grandtech.service.TimetableRunService
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.neo4j.driver.Driver

/**
 * Integration tests for [TimetableRunService].
 *
 * Tests cover [TimetableRunService.initRun], [TimetableRunService.getRun],
 * and [TimetableRunService.listRuns]. [TimetableRunService.executeSolve] is
 * covered end-to-end by the solver tests and resource integration tests.
 */
@QuarkusTest
class TimetableRunServiceTest {

    @Inject lateinit var timetableRunService: TimetableRunService
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
                DETACH DELETE s, r, e
                """.trimIndent(),
                mapOf("uids" to trackedSchoolUids.toList()),
            )
        }
        trackedSchoolUids.clear()
    }

    // ── initRun ───────────────────────────────────────────────────────────────

    @Test
    fun `initRun returns 200 with a run in RUNNING status`() {
        trackSchool("trst-school-1")
        userRepository.saveSchool(School(fedUid = "trst-school-1"))

        val response = timetableRunService.initRun("trst-school-1", "2025", "Term 1", 120)

        assertEquals(200, response.status)
        assertNotNull(response.payload)
        assertEquals("RUNNING", response.payload!!.status)
    }

    @Test
    fun `initRun stores academicYear and term on the run`() {
        trackSchool("trst-school-2")
        userRepository.saveSchool(School(fedUid = "trst-school-2"))

        val response = timetableRunService.initRun("trst-school-2", "2026", "Term 3", 60)

        val run = response.payload!!
        assertEquals("2026", run.academicYear)
        assertEquals("Term 3", run.term)
        assertEquals(60, run.timeLimitSeconds)
    }

    @Test
    fun `initRun generates a non-blank run id`() {
        trackSchool("trst-school-3")
        userRepository.saveSchool(School(fedUid = "trst-school-3"))

        val response = timetableRunService.initRun("trst-school-3", "2025", "Term 1", 120)

        assertNotNull(response.payload?.id)
        assertTrue(response.payload!!.id!!.isNotBlank())
    }

    @Test
    fun `initRun links run to the correct school`() {
        trackSchool("trst-school-4")
        userRepository.saveSchool(School(fedUid = "trst-school-4"))

        val response = timetableRunService.initRun("trst-school-4", "2025", "Term 1", 120)

        assertEquals("trst-school-4", response.payload!!.schoolFedUid)
    }

    // ── getRun ────────────────────────────────────────────────────────────────

    @Test
    fun `getRun returns 200 with the run when it exists`() {
        trackSchool("trst-school-5")
        userRepository.saveSchool(School(fedUid = "trst-school-5"))

        val runId = timetableRunService.initRun("trst-school-5", "2025", "Term 1", 120).payload!!.id!!

        val response = timetableRunService.getRun(runId)

        assertEquals(200, response.status)
        assertNotNull(response.payload)
        assertEquals(runId, response.payload!!.id)
    }

    @Test
    fun `getRun returns 404 when the run does not exist`() {
        val response = timetableRunService.getRun("non-existent-run-id")

        assertEquals(404, response.status)
        assertNull(response.payload)
    }

    // ── listRuns ──────────────────────────────────────────────────────────────

    @Test
    fun `listRuns returns 200 with empty list when school has no runs`() {
        trackSchool("trst-school-6")
        userRepository.saveSchool(School(fedUid = "trst-school-6"))

        val response = timetableRunService.listRuns("trst-school-6")

        assertEquals(200, response.status)
        assertNotNull(response.payload)
        assertTrue(response.payload!!.isEmpty())
    }

    @Test
    fun `listRuns returns all runs for the school`() {
        trackSchool("trst-school-7")
        userRepository.saveSchool(School(fedUid = "trst-school-7"))

        timetableRunService.initRun("trst-school-7", "2025", "Term 1", 120)
        timetableRunService.initRun("trst-school-7", "2025", "Term 2", 120)

        val response = timetableRunService.listRuns("trst-school-7")

        assertEquals(200, response.status)
        assertEquals(2, response.payload!!.size)
    }

    @Test
    fun `listRuns does not return runs from other schools`() {
        trackSchool("trst-school-8a", "trst-school-8b")
        userRepository.saveSchool(School(fedUid = "trst-school-8a"))
        userRepository.saveSchool(School(fedUid = "trst-school-8b"))

        timetableRunService.initRun("trst-school-8a", "2025", "Term 1", 120)
        timetableRunService.initRun("trst-school-8b", "2025", "Term 1", 120)

        val response = timetableRunService.listRuns("trst-school-8a")

        assertEquals(1, response.payload!!.size)
        assertEquals("trst-school-8a", response.payload!![0].schoolFedUid)
    }
}

private fun assertTrue(condition: Boolean) {
    org.junit.jupiter.api.Assertions.assertTrue(condition)
}

private fun assertTrue(condition: Boolean, message: String) {
    org.junit.jupiter.api.Assertions.assertTrue(condition, message)
}
