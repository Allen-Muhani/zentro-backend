package com.grandtech.teacher

import com.grandtech.auth.FirebaseAuthService
import com.grandtech.model.School
import com.grandtech.model.Teacher
import com.grandtech.repository.TeacherRepository
import com.grandtech.repository.UserRepository
import com.grandtech.school.GetSchoolProfileTest
import com.grandtech.service.CbcDataSeeder
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.neo4j.driver.Driver

/**
 * Integration tests for `GET /school/teacher/list`.
 *
 * Only [FirebaseAuthService] is mocked — token verification requires a live Firebase
 * connection unavailable in CI. All other beans run against the real AuraDB instance.
 */
@QuarkusTest
class ListTeachersTest {

    @InjectMock
    lateinit var firebaseAuthService: FirebaseAuthService

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var teacherRepository: TeacherRepository

    @Inject
    lateinit var driver: Driver

    private val subjectId1 = CbcDataSeeder.SUBJECTS[0].id
    private val subjectId2 = CbcDataSeeder.SUBJECTS[1].id

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
        ) { "Failed to create teacher for school $schoolFedUid" }

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
            .`when`().get("/school/teacher/list")
            .then()
                .statusCode(401)
                .body("status",  `is`(401))
                .body("payload", nullValue())
    }

    @Test
    fun `unregistered uid returns 401`() {
        stubToken("ltt-unknown-uid", "Bearer ltt-unknown-token")

        given()
            .header("Authorization", "Bearer ltt-unknown-token")
            .`when`().get("/school/teacher/list")
            .then()
                .statusCode(401)
                .body("status",  `is`(401))
                .body("payload", nullValue())
    }

    @Test
    fun `non-school account returns 403`() {
        // Create a plain (:User) node without a School label so existsByFedUid passes
        // but getSchoolByFedUid returns null, triggering the 403 guard in TeacherResource.
        driver.session().use { session ->
            session.run(
                "CREATE (:User {fedUid: \$fedUid})",
                mapOf("fedUid" to "ltt-user-1"),
            )
        }
        stubToken("ltt-user-1", "Bearer ltt-user-token-1")

        try {
            given()
                .header("Authorization", "Bearer ltt-user-token-1")
                .`when`().get("/school/teacher/list")
                .then()
                    .statusCode(200)
                    .body("status",  `is`(403))
                    .body("payload", nullValue())
        } finally {
            driver.session().use { session ->
                session.run(
                    "MATCH (u:User {fedUid: \$fedUid}) DETACH DELETE u",
                    mapOf("fedUid" to "ltt-user-1"),
                )
            }
        }
    }

    // ── List ─────────────────────────────────────────────────────────────────

    @Test
    fun `returns empty list when school has no teachers`() {
        trackSchool("ltt-school-1")
        userRepository.saveSchool(School(fedUid = "ltt-school-1"))
        stubToken("ltt-school-1", "Bearer ltt-token-1")

        given()
            .header("Authorization", "Bearer ltt-token-1")
            .`when`().get("/school/teacher/list")
            .then()
                .statusCode(200)
                .body("status",        `is`(200))
                .body("payload",       notNullValue())
                .body("payload.size()", `is`(0))
    }

    @Test
    fun `returns all teachers ordered by name`() {
        trackSchool("ltt-school-2")
        userRepository.saveSchool(School(fedUid = "ltt-school-2"))
        createTeacher("ltt-school-2", "Ms Wanjiku", "wanjiku@ltt.ke")
        createTeacher("ltt-school-2", "Mr Otieno", "otieno@ltt.ke")
        stubToken("ltt-school-2", "Bearer ltt-token-2")

        given()
            .header("Authorization", "Bearer ltt-token-2")
            .`when`().get("/school/teacher/list")
            .then()
                .statusCode(200)
                .body("status",          `is`(200))
                .body("payload.size()",  `is`(2))
                .body("payload[0].name", `is`("Mr Otieno"))
                .body("payload[1].name", `is`("Ms Wanjiku"))
    }

    @Test
    fun `returns teacher with scalar fields`() {
        trackSchool("ltt-school-3")
        userRepository.saveSchool(School(fedUid = "ltt-school-3"))
        teacherRepository.createTeacher(
            "ltt-school-3",
            Teacher(
                name = "Ms Kamau",
                email = "kamau@ltt.ke",
                phone = "0700000001",
                tscNumber = "TSC999",
                maxPeriodsPerWeek = 18,
                maxPeriodsPerDay = 4,
                subjectIds = listOf(subjectId1),
            ),
        )
        stubToken("ltt-school-3", "Bearer ltt-token-3")

        given()
            .header("Authorization", "Bearer ltt-token-3")
            .`when`().get("/school/teacher/list")
            .then()
                .statusCode(200)
                .body("payload[0].email",             `is`("kamau@ltt.ke"))
                .body("payload[0].phone",             `is`("0700000001"))
                .body("payload[0].tscNumber",         `is`("TSC999"))
                .body("payload[0].maxPeriodsPerWeek", `is`(18))
                .body("payload[0].maxPeriodsPerDay",  `is`(4))
    }

    @Test
    fun `returns teacher with their subject ids`() {
        trackSchool("ltt-school-4")
        userRepository.saveSchool(School(fedUid = "ltt-school-4"))
        teacherRepository.createTeacher(
            "ltt-school-4",
            Teacher(name = "Ms Achieng", email = "achieng@ltt.ke", subjectIds = listOf(subjectId1, subjectId2)),
        )
        stubToken("ltt-school-4", "Bearer ltt-token-4")

        given()
            .header("Authorization", "Bearer ltt-token-4")
            .`when`().get("/school/teacher/list")
            .then()
                .statusCode(200)
                .body("payload[0].subjectIds.size()", `is`(2))
                .body("payload[0].subjectIds[0]",     notNullValue())
    }

    @Test
    fun `only returns teachers belonging to the authenticated school`() {
        trackSchool("ltt-school-5a", "ltt-school-5b")
        userRepository.saveSchool(School(fedUid = "ltt-school-5a"))
        userRepository.saveSchool(School(fedUid = "ltt-school-5b"))
        createTeacher("ltt-school-5a", "Ms Alpha", "alpha@ltt.ke")
        createTeacher("ltt-school-5b", "Mr Beta", "beta@ltt.ke")
        stubToken("ltt-school-5a", "Bearer ltt-token-5a")

        given()
            .header("Authorization", "Bearer ltt-token-5a")
            .`when`().get("/school/teacher/list")
            .then()
                .statusCode(200)
                .body("payload.size()",  `is`(1))
                .body("payload[0].name", `is`("Ms Alpha"))
    }
}