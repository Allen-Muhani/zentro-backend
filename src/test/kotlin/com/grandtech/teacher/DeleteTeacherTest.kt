package com.grandtech.teacher

import com.grandtech.auth.FirebaseAuthService
import com.grandtech.model.School
import com.grandtech.model.Teacher
import com.grandtech.repository.TeacherRepository
import com.grandtech.repository.UserRepository
import com.grandtech.school.GetSchoolProfileTest
import com.grandtech.service.CbcDataSeeder
import com.grandtech.service.TeacherService
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
 * Integration tests for `DELETE /school/teacher/delete/{id}`.
 *
 * Only [FirebaseAuthService] is mocked. All other beans run against the real AuraDB instance.
 */
@QuarkusTest
class DeleteTeacherTest {

    @InjectMock
    lateinit var firebaseAuthService: FirebaseAuthService

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var teacherRepository: TeacherRepository

    @Inject
    lateinit var teacherService: TeacherService

    @Inject
    lateinit var driver: Driver

    private val subjectId1 = CbcDataSeeder.SUBJECTS[0].id

    private val trackedSchoolUids = mutableSetOf<String>()

    private fun trackSchool(vararg uids: String) { trackedSchoolUids.addAll(uids.toList()) }

    private fun stubToken(schoolUid: String, bearer: String) {
        Mockito.`when`(firebaseAuthService.verifyToken(bearer))
            .thenReturn(GetSchoolProfileTest.buildToken(schoolUid))
    }

    private fun createTeacher(schoolFedUid: String, name: String, email: String): Teacher =
        checkNotNull(
            teacherRepository.createTeacher(
                schoolFedUid,
                Teacher(name = name, email = email, subjectIds = listOf(subjectId1)),
            ),
        )

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
                trackedSchoolUids.clear()
            }
        }
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    @Test
    fun `missing auth header returns 401`() {
        given()
            .`when`().delete("/school/teacher/delete/some-id")
            .then()
                .statusCode(401)
                .body("status",  `is`(401))
                .body("payload", nullValue())
    }

    @Test
    fun `unregistered uid returns 401`() {
        stubToken("dtt-unknown-uid", "Bearer dtt-unknown-token")

        given()
            .header("Authorization", "Bearer dtt-unknown-token")
            .`when`().delete("/school/teacher/delete/some-id")
            .then()
                .statusCode(401)
                .body("status",  `is`(401))
                .body("payload", nullValue())
    }

    @Test
    fun `non-school account returns 403`() {
        driver.session().use { session ->
            session.run("CREATE (:User {fedUid: \$fedUid})", mapOf("fedUid" to "dtt-user-1"))
        }
        stubToken("dtt-user-1", "Bearer dtt-user-token-1")

        try {
            given()
                .header("Authorization", "Bearer dtt-user-token-1")
                .`when`().delete("/school/teacher/delete/some-id")
                .then()
                    .statusCode(200)
                    .body("status",  `is`(403))
                    .body("payload", nullValue())
        } finally {
            driver.session().use { session ->
                session.run("MATCH (u:User {fedUid: \$fedUid}) DETACH DELETE u", mapOf("fedUid" to "dtt-user-1"))
            }
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    fun `deletes existing teacher and returns 200`() {
        trackSchool("dtt-school-1")
        userRepository.saveSchool(School(fedUid = "dtt-school-1"))
        val teacher = createTeacher("dtt-school-1", "Ms Njeri", "njeri1@dtt.ke")
        stubToken("dtt-school-1", "Bearer dtt-token-1")

        given()
            .header("Authorization", "Bearer dtt-token-1")
            .`when`().delete("/school/teacher/delete/${teacher.id}")
            .then()
                .statusCode(200)
                .body("status",  `is`(200))
                .body("message", `is`("Teacher deleted"))
                .body("payload", nullValue())
    }

    @Test
    fun `deleted teacher no longer appears in list`() {
        trackSchool("dtt-school-2")
        userRepository.saveSchool(School(fedUid = "dtt-school-2"))
        val teacher = createTeacher("dtt-school-2", "Mr Otieno", "otieno2@dtt.ke")
        stubToken("dtt-school-2", "Bearer dtt-token-2")

        given()
            .header("Authorization", "Bearer dtt-token-2")
            .`when`().delete("/school/teacher/delete/${teacher.id}")
            .then().statusCode(200)

        given()
            .header("Authorization", "Bearer dtt-token-2")
            .`when`().get("/school/teacher/list")
            .then()
                .statusCode(200)
                .body("payload.size()", `is`(0))
    }

    @Test
    fun `non-existent teacher id returns 404`() {
        trackSchool("dtt-school-3")
        userRepository.saveSchool(School(fedUid = "dtt-school-3"))
        stubToken("dtt-school-3", "Bearer dtt-token-3")

        given()
            .header("Authorization", "Bearer dtt-token-3")
            .`when`().delete("/school/teacher/delete/00000000-0000-0000-0000-000000000000")
            .then()
                .statusCode(200)
                .body("status",  `is`(404))
                .body("message", `is`("Teacher not found"))
                .body("payload", nullValue())
    }

    @Test
    fun `cannot delete a teacher belonging to a different school`() {
        trackSchool("dtt-school-4a", "dtt-school-4b")
        userRepository.saveSchool(School(fedUid = "dtt-school-4a"))
        userRepository.saveSchool(School(fedUid = "dtt-school-4b"))
        val teacher = createTeacher("dtt-school-4b", "Ms Kamau", "kamau4@dtt.ke")
        stubToken("dtt-school-4a", "Bearer dtt-token-4a")

        given()
            .header("Authorization", "Bearer dtt-token-4a")
            .`when`().delete("/school/teacher/delete/${teacher.id}")
            .then()
                .statusCode(200)
                .body("status",  `is`(404))
                .body("payload", nullValue())
    }

    @Test
    fun `deleting one teacher does not affect others in the same school`() {
        trackSchool("dtt-school-5")
        userRepository.saveSchool(School(fedUid = "dtt-school-5"))
        val teacher1 = createTeacher("dtt-school-5", "Ms Alpha", "alpha5@dtt.ke")
        val teacher2 = createTeacher("dtt-school-5", "Mr Beta", "beta5@dtt.ke")
        stubToken("dtt-school-5", "Bearer dtt-token-5")

        given()
            .header("Authorization", "Bearer dtt-token-5")
            .`when`().delete("/school/teacher/delete/${teacher1.id}")
            .then().statusCode(200).body("status", `is`(200))

        given()
            .header("Authorization", "Bearer dtt-token-5")
            .`when`().get("/school/teacher/list")
            .then()
                .statusCode(200)
                .body("payload.size()", `is`(1))
                .body("payload[0].id",  `is`(teacher2.id))
    }
}