package com.grandtech.service.teacher

import com.grandtech.model.School
import com.grandtech.repository.TeacherRepository
import com.grandtech.repository.UserRepository
import com.grandtech.service.TeacherService
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.neo4j.driver.Driver

/**
 * Shared test infrastructure for [TeacherService] and [TeacherRepository] integration tests.
 * All tests run against the configured Neo4j instance — no mocking.
 */
@QuarkusTest
abstract class TeacherServiceTestBase {

    @Inject
    lateinit var teacherService: TeacherService

    @Inject
    lateinit var teacherRepository: TeacherRepository

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var driver: Driver

    private val trackedSchoolUids = mutableSetOf<String>()

    protected fun trackSchool(vararg uids: String) {
        trackedSchoolUids.addAll(uids.toList())
    }

    protected fun saveSchool(fedUid: String) {
        userRepository.saveSchool(School(fedUid = fedUid))
    }

    @AfterEach
    fun cleanUp() {
        if (trackedSchoolUids.isNotEmpty()) {
            driver.session().use { session ->
                session.run(
                    """
                    MATCH (s:School) WHERE s.fedUid IN ${'$'}uids
                    OPTIONAL MATCH (s)-[:HAS_TEACHER]->(t:Teacher)
                    DETACH DELETE s, t
                    """.trimIndent(),
                    mapOf("uids" to trackedSchoolUids.toList()),
                )
            }
            trackedSchoolUids.clear()
        }
    }
}