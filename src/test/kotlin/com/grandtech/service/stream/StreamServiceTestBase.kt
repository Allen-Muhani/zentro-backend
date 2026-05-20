package com.grandtech.service.stream

import com.grandtech.model.Stream
import com.grandtech.repository.StreamRepository
import com.grandtech.repository.UserRepository
import com.grandtech.service.RoomService
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
    lateinit var roomService: RoomService

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var driver: Driver

    private val trackedSchoolUids = mutableSetOf<String>()
    private val trackedTeacherUids = mutableSetOf<String>()

    /** Registers school UIDs so [cleanUp] deletes them along with their rooms and streams. */
    protected fun trackSchool(vararg uids: String) {
        trackedSchoolUids.addAll(uids.toList())
    }

    /** Registers teacher UIDs so [cleanUp] deletes their nodes after the test. */
    protected fun trackTeacher(vararg uids: String) {
        trackedTeacherUids.addAll(uids.toList())
    }

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
}