package com.grandtech.service.stream

import com.grandtech.model.Stream
import com.grandtech.model.Teacher
import com.grandtech.repository.StreamRepository
import com.grandtech.repository.TeacherRepository
import com.grandtech.repository.UserRepository
import com.grandtech.service.CbcDataSeeder
import com.grandtech.service.StreamService
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.neo4j.driver.Driver

/**
 * Shared test infrastructure for [StreamService] and [StreamRepository] integration tests.
 *
 * [upsertStream] is a convenience helper that unwraps a 200 response so setup code stays
 * terse — call [streamService.upsertStream] directly when the response itself is under test.
 * All tests run against the configured Neo4j instance — no mocking.
 */
@QuarkusTest
abstract class StreamServiceTestBase {

    @Inject
    lateinit var streamService: StreamService

    @Inject
    lateinit var streamRepository: StreamRepository

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var teacherRepository: TeacherRepository

    @Inject
    lateinit var driver: Driver

    private val trackedSchoolUids = mutableSetOf<String>()

    /** Registers school UIDs so [cleanUp] deletes them along with their streams and teachers. */
    protected fun trackSchool(vararg uids: String) {
        trackedSchoolUids.addAll(uids.toList())
    }

    /**
     * Creates a teacher linked to [schoolFedUid] with one seeded subject.
     * Throws if the school does not exist.
     */
    protected fun createTeacher(schoolFedUid: String, name: String, email: String): Teacher =
        checkNotNull(
            teacherRepository.createTeacher(
                schoolFedUid,
                Teacher(name = name, email = email, subjectIds = listOf(CbcDataSeeder.SUBJECTS.first().id)),
            ),
        ) { "Failed to create teacher for school $schoolFedUid" }

    /**
     * Calls [StreamService.upsertStream] and returns the stream payload from a 200 response.
     * Throws if the response status is not 200 so misconfigured test setup surfaces as a
     * clear failure rather than a NullPointerException downstream.
     */
    protected fun upsertStream(schoolFedUid: String, stream: Stream): Stream {
        val response = streamService.upsertStream(schoolFedUid, stream)
        check(response.status == 200) {
            "Expected status 200 but got ${response.status}: ${response.message} — check test setup"
        }
        return response.payload!!
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
            }
            trackedSchoolUids.clear()
        }
    }
}
