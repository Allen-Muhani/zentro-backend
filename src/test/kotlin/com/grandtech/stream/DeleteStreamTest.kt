package com.grandtech.stream

import com.grandtech.auth.FirebaseAuthService
import com.grandtech.model.School
import com.grandtech.model.Stream
import com.grandtech.repository.UserRepository
import com.grandtech.school.GetSchoolProfileTest
import com.grandtech.service.StreamService
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.neo4j.driver.Driver

/**
 * Integration tests for `DELETE /school/stream/delete/{id}`.
 *
 * Only [FirebaseAuthService] is mocked. All other beans run against the real AuraDB instance.
 */
@QuarkusTest
class DeleteStreamTest {

    @InjectMock
    lateinit var firebaseAuthService: FirebaseAuthService

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var streamService: StreamService

    @Inject
    lateinit var driver: Driver

    private val trackedSchoolUids = mutableSetOf<String>()

    private fun trackSchool(vararg uids: String) { trackedSchoolUids.addAll(uids.toList()) }

    private fun stubToken(schoolUid: String, bearer: String) {
        Mockito.`when`(firebaseAuthService.verifyToken(bearer))
            .thenReturn(GetSchoolProfileTest.buildToken(schoolUid))
    }

    @AfterEach
    fun cleanUp() {
        if (trackedSchoolUids.isNotEmpty()) {
            driver.session().use { session ->
                session.run(
                    """
                    MATCH (s:School) WHERE s.fedUid IN ${'$'}uids
                    OPTIONAL MATCH (s)-[:HAS_ROOM]->(r:Room)
                    OPTIONAL MATCH (s)-[:HAS_STREAM]->(st:Stream)
                    OPTIONAL MATCH (s)-[:HAS_TEACHER]->(t:Teacher)
                    DETACH DELETE s, r, st, t
                    """.trimIndent(),
                    mapOf("uids" to trackedSchoolUids.toList()),
                )
                trackedSchoolUids.clear()
            }
        }
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    @Test
    fun `missing auth header returns 401`() {
        given()
            .`when`().delete("/school/stream/delete/some-id")
            .then()
                .statusCode(401)
                .body("status",  `is`(401))
                .body("payload", nullValue())
    }

    @Test
    fun `unregistered uid returns 401`() {
        stubToken("dst-unknown-uid", "Bearer dst-unknown-token")

        given()
            .header("Authorization", "Bearer dst-unknown-token")
            .`when`().delete("/school/stream/delete/some-id")
            .then()
                .statusCode(401)
                .body("status",  `is`(401))
                .body("payload", nullValue())
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    fun `deletes existing stream and returns 200`() {
        trackSchool("dst-school-1")
        userRepository.saveSchool(School(fedUid = "dst-school-1"))
        stubToken("dst-school-1", "Bearer dst-token-1")
        val streamId = streamService
            .upsertStream("dst-school-1", Stream(gradeLevel = 7, name = "7 Blue"))
            .payload!!.id!!

        given()
            .header("Authorization", "Bearer dst-token-1")
            .`when`().delete("/school/stream/delete/$streamId")
            .then()
                .statusCode(200)
                .body("status",  `is`(200))
                .body("message", `is`("Stream deleted"))
                .body("payload", nullValue())
    }

    @Test
    fun `deleted stream no longer appears in list`() {
        trackSchool("dst-school-2")
        userRepository.saveSchool(School(fedUid = "dst-school-2"))
        stubToken("dst-school-2", "Bearer dst-token-2")
        val streamId = streamService
            .upsertStream("dst-school-2", Stream(gradeLevel = 8, name = "8 Red"))
            .payload!!.id!!

        given()
            .header("Authorization", "Bearer dst-token-2")
            .`when`().delete("/school/stream/delete/$streamId")
            .then().statusCode(200)

        given()
            .header("Authorization", "Bearer dst-token-2")
            .`when`().get("/school/stream/list")
            .then()
                .statusCode(200)
                .body("payload.size()", `is`(0))
    }

    @Test
    fun `non-existent stream id returns 404`() {
        trackSchool("dst-school-3")
        userRepository.saveSchool(School(fedUid = "dst-school-3"))
        stubToken("dst-school-3", "Bearer dst-token-3")

        given()
            .header("Authorization", "Bearer dst-token-3")
            .`when`().delete("/school/stream/delete/00000000-0000-0000-0000-000000000000")
            .then()
                .statusCode(200)
                .body("status",  `is`(404))
                .body("payload", nullValue())
    }

    @Test
    fun `cannot delete a stream belonging to a different school`() {
        trackSchool("dst-school-4a", "dst-school-4b")
        userRepository.saveSchool(School(fedUid = "dst-school-4a"))
        userRepository.saveSchool(School(fedUid = "dst-school-4b"))
        val streamId = streamService
            .upsertStream("dst-school-4b", Stream(gradeLevel = 9, name = "9 Green"))
            .payload!!.id!!
        stubToken("dst-school-4a", "Bearer dst-token-4a")

        given()
            .header("Authorization", "Bearer dst-token-4a")
            .`when`().delete("/school/stream/delete/$streamId")
            .then()
                .statusCode(200)
                .body("status",  `is`(404))
                .body("payload", nullValue())
    }
}