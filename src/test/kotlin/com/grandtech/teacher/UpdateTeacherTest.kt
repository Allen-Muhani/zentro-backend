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
 * Integration tests for `PATCH /school/teacher/update`.
 *
 * Only [FirebaseAuthService] is mocked — token verification requires a live Firebase
 * connection unavailable in CI. All other beans run against the real AuraDB instance.
 */
@QuarkusTest
class UpdateTeacherTest {

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
    private val subjectId2 = CbcDataSeeder.SUBJECTS[1].id
    private val subjectId3 = CbcDataSeeder.SUBJECTS[2].id

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
            .contentType(ContentType.JSON)
            .body("""{"id":"some-id","name":"Ms Njeri"}""")
            .`when`().patch("/school/teacher/update")
            .then()
                .statusCode(401)
                .body("status",  `is`(401))
                .body("payload", nullValue())
    }

    @Test
    fun `unregistered uid returns 401`() {
        stubToken("utt-unknown-uid", "Bearer utt-unknown-token")

        given()
            .header("Authorization", "Bearer utt-unknown-token")
            .contentType(ContentType.JSON)
            .body("""{"id":"some-id","name":"Ms Njeri"}""")
            .`when`().patch("/school/teacher/update")
            .then()
                .statusCode(401)
                .body("status",  `is`(401))
                .body("payload", nullValue())
    }

    @Test
    fun `non-school account returns 403`() {
        driver.session().use { session ->
            session.run("CREATE (:User {fedUid: \$fedUid})", mapOf("fedUid" to "utt-user-1"))
        }
        stubToken("utt-user-1", "Bearer utt-user-token-1")

        try {
            given()
                .header("Authorization", "Bearer utt-user-token-1")
                .contentType(ContentType.JSON)
                .body("""{"id":"some-id","name":"Ms Njeri"}""")
                .`when`().patch("/school/teacher/update")
                .then()
                    .statusCode(200)
                    .body("status",  `is`(403))
                    .body("payload", nullValue())
        } finally {
            driver.session().use { session ->
                session.run("MATCH (u:User {fedUid: \$fedUid}) DETACH DELETE u", mapOf("fedUid" to "utt-user-1"))
            }
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    fun `missing id returns 400`() {
        trackSchool("utt-school-1")
        userRepository.saveSchool(School(fedUid = "utt-school-1"))
        stubToken("utt-school-1", "Bearer utt-token-1")

        given()
            .header("Authorization", "Bearer utt-token-1")
            .contentType(ContentType.JSON)
            .body("""{"name":"Ms Njeri"}""")
            .`when`().patch("/school/teacher/update")
            .then()
                .statusCode(200)
                .body("status",  `is`(400))
                .body("message", `is`("Teacher id is required"))
                .body("payload", nullValue())
    }

    @Test
    fun `blank name returns 400`() {
        trackSchool("utt-school-2")
        userRepository.saveSchool(School(fedUid = "utt-school-2"))
        val teacher = createTeacher("utt-school-2", "Ms Njeri", "njeri2@utt.ke")
        stubToken("utt-school-2", "Bearer utt-token-2")

        given()
            .header("Authorization", "Bearer utt-token-2")
            .contentType(ContentType.JSON)
            .body("""{"id":"${teacher.id}","name":"   "}""")
            .`when`().patch("/school/teacher/update")
            .then()
                .statusCode(200)
                .body("status",  `is`(400))
                .body("message", `is`("Teacher name must not be blank"))
                .body("payload", nullValue())
    }

    @Test
    fun `blank email returns 400`() {
        trackSchool("utt-school-3")
        userRepository.saveSchool(School(fedUid = "utt-school-3"))
        val teacher = createTeacher("utt-school-3", "Ms Njeri", "njeri3@utt.ke")
        stubToken("utt-school-3", "Bearer utt-token-3")

        given()
            .header("Authorization", "Bearer utt-token-3")
            .contentType(ContentType.JSON)
            .body("""{"id":"${teacher.id}","email":""}""")
            .`when`().patch("/school/teacher/update")
            .then()
                .statusCode(200)
                .body("status",  `is`(400))
                .body("message", `is`("Teacher email must not be blank"))
                .body("payload", nullValue())
    }

    @Test
    fun `duplicate email returns 409`() {
        trackSchool("utt-school-4")
        userRepository.saveSchool(School(fedUid = "utt-school-4"))
        createTeacher("utt-school-4", "Ms First", "first4@utt.ke")
        val teacher2 = createTeacher("utt-school-4", "Ms Second", "second4@utt.ke")
        stubToken("utt-school-4", "Bearer utt-token-4")

        given()
            .header("Authorization", "Bearer utt-token-4")
            .contentType(ContentType.JSON)
            .body("""{"id":"${teacher2.id}","email":"first4@utt.ke"}""")
            .`when`().patch("/school/teacher/update")
            .then()
                .statusCode(200)
                .body("status",  `is`(409))
                .body("message", `is`("A teacher with that email already exists"))
                .body("payload", nullValue())
    }

    @Test
    fun `empty subjectIds returns 400`() {
        trackSchool("utt-school-5")
        userRepository.saveSchool(School(fedUid = "utt-school-5"))
        val teacher = createTeacher("utt-school-5", "Ms Wanjiku", "wanjiku5@utt.ke")
        stubToken("utt-school-5", "Bearer utt-token-5")

        given()
            .header("Authorization", "Bearer utt-token-5")
            .contentType(ContentType.JSON)
            .body("""{"id":"${teacher.id}","subjectIds":[]}""")
            .`when`().patch("/school/teacher/update")
            .then()
                .statusCode(200)
                .body("status",  `is`(400))
                .body("message", `is`("At least one subject is required"))
                .body("payload", nullValue())
    }

    @Test
    fun `more than two subjects returns 400`() {
        trackSchool("utt-school-6")
        userRepository.saveSchool(School(fedUid = "utt-school-6"))
        val teacher = createTeacher("utt-school-6", "Mr Kiprop", "kiprop6@utt.ke")
        stubToken("utt-school-6", "Bearer utt-token-6")

        given()
            .header("Authorization", "Bearer utt-token-6")
            .contentType(ContentType.JSON)
            .body("""{"id":"${teacher.id}","subjectIds":["$subjectId1","$subjectId2","$subjectId3"]}""")
            .`when`().patch("/school/teacher/update")
            .then()
                .statusCode(200)
                .body("status",  `is`(400))
                .body("message", `is`("A teacher may teach at most 2 subjects"))
                .body("payload", nullValue())
    }

    @Test
    fun `non-existent teacher id returns 404`() {
        trackSchool("utt-school-7")
        userRepository.saveSchool(School(fedUid = "utt-school-7"))
        stubToken("utt-school-7", "Bearer utt-token-7")

        given()
            .header("Authorization", "Bearer utt-token-7")
            .contentType(ContentType.JSON)
            .body("""{"id":"00000000-0000-0000-0000-000000000000","name":"Ghost"}""")
            .`when`().patch("/school/teacher/update")
            .then()
                .statusCode(200)
                .body("status",  `is`(404))
                .body("message", `is`("Teacher not found"))
                .body("payload", nullValue())
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Test
    fun `updates name and returns full updated payload`() {
        trackSchool("utt-school-8")
        userRepository.saveSchool(School(fedUid = "utt-school-8"))
        val teacher = createTeacher("utt-school-8", "Ms Old", "old8@utt.ke")
        stubToken("utt-school-8", "Bearer utt-token-8")

        given()
            .header("Authorization", "Bearer utt-token-8")
            .contentType(ContentType.JSON)
            .body("""{"id":"${teacher.id}","name":"Ms New"}""")
            .`when`().patch("/school/teacher/update")
            .then()
                .statusCode(200)
                .body("status",        `is`(200))
                .body("message",       `is`("Teacher updated"))
                .body("payload.id",    `is`(teacher.id))
                .body("payload.name",  `is`("Ms New"))
                .body("payload.email", `is`("old8@utt.ke"))
    }

    @Test
    fun `updates optional scalar fields`() {
        trackSchool("utt-school-9")
        userRepository.saveSchool(School(fedUid = "utt-school-9"))
        val teacher = createTeacher("utt-school-9", "Mr Otieno", "otieno9@utt.ke")
        stubToken("utt-school-9", "Bearer utt-token-9")

        given()
            .header("Authorization", "Bearer utt-token-9")
            .contentType(ContentType.JSON)
            .body(
                """{"id":"${teacher.id}","phone":"0799999999","tscNumber":"TSC-42",
                   |"maxPeriodsPerWeek":16,"maxPeriodsPerDay":4}""".trimMargin(),
            )
            .`when`().patch("/school/teacher/update")
            .then()
                .statusCode(200)
                .body("payload.phone",             `is`("0799999999"))
                .body("payload.tscNumber",         `is`("TSC-42"))
                .body("payload.maxPeriodsPerWeek", `is`(16))
                .body("payload.maxPeriodsPerDay",  `is`(4))
    }

    @Test
    fun `replaces subjects and returns updated subjects in payload`() {
        trackSchool("utt-school-10")
        userRepository.saveSchool(School(fedUid = "utt-school-10"))
        val teacher = createTeacher("utt-school-10", "Ms Auma", "auma10@utt.ke")
        stubToken("utt-school-10", "Bearer utt-token-10")

        given()
            .header("Authorization", "Bearer utt-token-10")
            .contentType(ContentType.JSON)
            .body("""{"id":"${teacher.id}","subjectIds":["$subjectId2"]}""")
            .`when`().patch("/school/teacher/update")
            .then()
                .statusCode(200)
                .body("status",                   `is`(200))
                .body("payload.subjects.size()",  `is`(1))
                .body("payload.subjects[0].id",   `is`(subjectId2))
                .body("payload.subjects[0].id",   notNullValue())
    }

    @Test
    fun `omitting subjectIds leaves subjects unchanged`() {
        trackSchool("utt-school-11")
        userRepository.saveSchool(School(fedUid = "utt-school-11"))
        val teacher = checkNotNull(
            teacherRepository.createTeacher(
                "utt-school-11",
                Teacher(name = "Ms Chebet", email = "chebet11@utt.ke", subjectIds = listOf(subjectId1, subjectId2)),
            ),
        )
        stubToken("utt-school-11", "Bearer utt-token-11")

        given()
            .header("Authorization", "Bearer utt-token-11")
            .contentType(ContentType.JSON)
            .body("""{"id":"${teacher.id}","name":"Ms Chebet Updated"}""")
            .`when`().patch("/school/teacher/update")
            .then()
                .statusCode(200)
                .body("payload.subjects.size()", `is`(2))
    }
}