package com.grandtech.repository.subject

import com.grandtech.model.SubjectType
import com.grandtech.model.Subject
import com.grandtech.repository.SubjectRepository
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.neo4j.driver.Driver

/**
 * Shared infrastructure for [SubjectRepository] integration tests.
 *
 * Tests use IDs prefixed with `"T-"` to avoid colliding with the 10 subjects
 * seeded by [com.grandtech.service.CbcDataSeeder] at startup. [track] registers
 * those IDs so [cleanUp] deletes them after each test.
 */
@QuarkusTest
abstract class SubjectRepositoryTestBase {

    @Inject
    lateinit var subjectRepository: SubjectRepository

    @Inject
    lateinit var driver: Driver

    private val trackedIds = mutableSetOf<String>()

    /** Registers subject IDs so [cleanUp] deletes them after the test. */
    protected fun track(vararg ids: String) {
        trackedIds.addAll(ids.toList())
    }

    /** Convenience builder for a minimal valid test subject with the given [id]. */
    protected fun testSubject(
        id: String,
        name: String = "Test Subject $id",
        type: SubjectType = SubjectType.SOCIAL,
        periodsPerWeek: Int = 3,
    ) = Subject(
        id = id,
        symbol = id,
        name = name,
        type = type,
        periodsPerWeek = periodsPerWeek,
    )

    @AfterEach
    fun cleanUp() {
        if (trackedIds.isNotEmpty()) {
            driver.session().use { session ->
                session.run(
                    "MATCH (s:Subject) WHERE s.id IN \$ids DETACH DELETE s",
                    mapOf("ids" to trackedIds.toList()),
                )
            }
            trackedIds.clear()
        }
    }
}