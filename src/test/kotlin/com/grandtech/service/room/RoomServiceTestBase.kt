package com.grandtech.service.room

import com.grandtech.service.RoomService
import com.grandtech.service.UserRepository
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.neo4j.driver.Driver

/**
 * Shared test infrastructure for [RoomService] integration tests.
 *
 * Subclasses inherit injected fields, the UID tracker, and [cleanUp].
 * All tests run against the configured Neo4j instance — no mocking.
 */
@QuarkusTest
abstract class RoomServiceTestBase {

    @Inject
    lateinit var roomService: RoomService

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var driver: Driver

    private val trackedUids = mutableSetOf<String>()

    /**
     * Registers school UIDs touched by the current test. [cleanUp] deletes
     * these school nodes and any rooms linked to them after the test finishes.
     */
    protected fun track(vararg uids: String) {
        trackedUids.addAll(uids.toList())
    }

    @AfterEach
    fun cleanUp() {
        if (trackedUids.isNotEmpty()) {
            driver.session().use { session ->
                session.run(
                    """
                    MATCH (s:School) WHERE s.fedUid IN ${'$'}uids
                    OPTIONAL MATCH (s)-[:HAS_ROOM]->(r:Room)
                    DETACH DELETE s, r
                    """.trimIndent(),
                    mapOf("uids" to trackedUids.toList()),
                )
            }
            trackedUids.clear()
        }
    }
}
