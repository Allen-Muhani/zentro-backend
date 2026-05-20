package com.grandtech.teacher

import com.grandtech.auth.FirebaseAuthService
import com.grandtech.model.School
import com.grandtech.repository.UserRepository
import com.grandtech.school.GetSchoolProfileTest
import com.grandtech.service.CbcDataSeeder
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.inject.Inject
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.CoreMatchers.nullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.neo4j.driver.Driver

/**
 * Integration tests for `POST /school/teacher/create`.
 *
 * Only [FirebaseAuthService] is mocked — token verification requires a live Firebase
 * connection unavailable in CI. All other beans run against the real AuraDB instance.
 */
@QuarkusTest
class CreateTeacherTest {

    @InjectMock
    lateinit var firebaseAuthService: FirebaseAuthService

    @Inject
    lateinit var userRepository: UserRepository

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
            .contentType(ContentType.JSON)
            .body("""{"name":"Ms Njeri","email":"njeri@test.ke","subjectIds":["$subjectId1"]}""")
            .`when`().post("/school/teacher/create")
            .then()
                .statusCode(401)
                .body("status",  `is`(401))
                .body("payload", nullValue())
    }

    @Test
    fun `unregistered uid returns 401`() {
        stubToken("ctt-unknown-uid", "Bearer ctt-unknown-token")

        given()
            .header("Authorization", "Bearer ctt-unknown-token")
            .contentType(ContentType.JSON)
            .body("""{"name":"Ms Njeri","email":"njeri@test.ke","subjectIds":["$subjectId1"]}""")
            .`when`().post("/school/teacher/create")
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
                mapOf("fedUid" to "ctt-user-1"),
            )
        }
        stubToken("ctt-user-1", "Bearer ctt-user-token-1")

        try {
            given()
                .header("Authorization", "Bearer ctt-user-token-1")
                .contentType(ContentType.JSON)
                .body("""{"name":"Ms Njeri","email":"njeri@test.ke","subjectIds":["$subjectId1"]}""")
                .`when`().post("/school/teacher/create")
                .then()
                    .statusCode(200)
                    .body("status",  `is`(403))
                    .body("payload", nullValue())
        } finally {
            driver.session().use { session ->
                session.run(
                    "MATCH (u:User {fedUid: \$fedUid}) DETACH DELETE u",
                    mapOf("fedUid" to "ctt-user-1"),
                )
            }
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    fun `missing name returns 400`() {
        trackSchool("ctt-school-1")
        userRepository.saveSchool(School(fedUid = "ctt-school-1"))
        stubToken("ctt-school-1", "Bearer ctt-token-1")

        given()
            .header("Authorization", "Bearer ctt-token-1")
            .contentType(ContentType.JSON)
            .body("""{"email":"njeri@test.ke","subjectIds":["$subjectId1"]}""")
            .`when`().post("/school/teacher/create")
            .then()
                .statusCode(200)
                .body("status",  `is`(400))
                .body("message", `is`("Teacher name is required"))
                .body("payload", nullValue())
    }

    @Test
    fun `missing email returns 400`() {
        trackSchool("ctt-school-2")
        userRepository.saveSchool(School(fedUid = "ctt-school-2"))
        stubToken("ctt-school-2", "Bearer ctt-token-2")

        given()
            .header("Authorization", "Bearer ctt-token-2")
            .contentType(ContentType.JSON)
            .body("""{"name":"Ms Njeri","subjectIds":["$subjectId1"]}""")
            .`when`().post("/school/teacher/create")
            .then()
                .statusCode(200)
                .body("status",  `is`(400))
                .body("message", `is`("Teacher email is required"))
                .body("payload", nullValue())
    }

    @Test
    fun `no subjects returns 400`() {
        trackSchool("ctt-school-3")
        userRepository.saveSchool(School(fedUid = "ctt-school-3"))
        stubToken("ctt-school-3", "Bearer ctt-token-3")

        given()
            .header("Authorization", "Bearer ctt-token-3")
            .contentType(ContentType.JSON)
            .body("""{"name":"Ms Njeri","email":"njeri3@test.ke","subjectIds":[]}""")
            .`when`().post("/school/teacher/create")
            .then()
                .statusCode(200)
                .body("status",  `is`(400))
                .body("message", `is`("At least one subject is required"))
                .body("payload", nullValue())
    }

    @Test
    fun `more than two subjects returns 400`() {
        trackSchool("ctt-school-4")
        userRepository.saveSchool(School(fedUid = "ctt-school-4"))
        stubToken("ctt-school-4", "Bearer ctt-token-4")
        val subjectId3 = CbcDataSeeder.SUBJECTS[2].id

        given()
            .header("Authorization", "Bearer ctt-token-4")
            .contentType(ContentType.JSON)
            .body("""{"name":"Ms Njeri","email":"njeri4@test.ke","subjectIds":["$subjectId1","$subjectId2","$subjectId3"]}""")
            .`when`().post("/school/teacher/create")
            .then()
                .statusCode(200)
                .body("status",  `is`(400))
                .body("message", `is`("A teacher may teach at most 2 subjects"))
                .body("payload", nullValue())
    }

    @Test
    fun `duplicate email returns 409`() {
        trackSchool("ctt-school-5")
        userRepository.saveSchool(School(fedUid = "ctt-school-5"))
        stubToken("ctt-school-5", "Bearer ctt-token-5")

        given()
            .header("Authorization", "Bearer ctt-token-5")
            .contentType(ContentType.JSON)
            .body("""{"name":"Ms First","email":"taken@test.ke","subjectIds":["$subjectId1"]}""")
            .`when`().post("/school/teacher/create")
            .then().statusCode(200).body("status", `is`(200))

        given()
            .header("Authorization", "Bearer ctt-token-5")
            .contentType(ContentType.JSON)
            .body("""{"name":"Ms Second","email":"taken@test.ke","subjectIds":["$subjectId1"]}""")
            .`when`().post("/school/teacher/create")
            .then()
                .statusCode(200)
                .body("status",  `is`(409))
                .body("message", `is`("A teacher with that email already exists"))
                .body("payload", nullValue())
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Test
    fun `creates teacher with required fields and returns full payload`() {
        trackSchool("ctt-school-6")
        userRepository.saveSchool(School(fedUid = "ctt-school-6"))
        stubToken("ctt-school-6", "Bearer ctt-token-6")

        given()
            .header("Authorization", "Bearer ctt-token-6")
            .contentType(ContentType.JSON)
            .body("""{"name":"Ms Njeri","email":"njeri6@test.ke","subjectIds":["$subjectId1"]}""")
            .`when`().post("/school/teacher/create")
            .then()
                .statusCode(200)
                .body("status",          `is`(200))
                .body("message",         `is`("Teacher created"))
                .body("payload.id",      notNullValue())
                .body("payload.name",    `is`("Ms Njeri"))
                .body("payload.email",   `is`("njeri6@test.ke"))
    }

    @Test
    fun `creates teacher with all optional fields`() {
        trackSchool("ctt-school-7")
        userRepository.saveSchool(School(fedUid = "ctt-school-7"))
        stubToken("ctt-school-7", "Bearer ctt-token-7")

        given()
            .header("Authorization", "Bearer ctt-token-7")
            .contentType(ContentType.JSON)
            .body(
                """{"name":"Mr Otieno","email":"otieno7@test.ke","phone":"0712345678",
                   |"tscNumber":"TSC001","maxPeriodsPerWeek":20,"maxPeriodsPerDay":5,
                   |"subjectIds":["$subjectId1"]}""".trimMargin(),
            )
            .`when`().post("/school/teacher/create")
            .then()
                .statusCode(200)
                .body("status",                    `is`(200))
                .body("payload.phone",              `is`("0712345678"))
                .body("payload.tscNumber",          `is`("TSC001"))
                .body("payload.maxPeriodsPerWeek",  `is`(20))
                .body("payload.maxPeriodsPerDay",   `is`(5))
    }

    @Test
    fun `creates teacher with two subjects and returns both in payload`() {
        trackSchool("ctt-school-8")
        userRepository.saveSchool(School(fedUid = "ctt-school-8"))
        stubToken("ctt-school-8", "Bearer ctt-token-8")

        given()
            .header("Authorization", "Bearer ctt-token-8")
            .contentType(ContentType.JSON)
            .body("""{"name":"Ms Auma","email":"auma8@test.ke","subjectIds":["$subjectId1","$subjectId2"]}""")
            .`when`().post("/school/teacher/create")
            .then()
                .statusCode(200)
                .body("status",               `is`(200))
                .body("payload.subjects.size()", `is`(2))
    }
}