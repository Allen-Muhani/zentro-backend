package com.grandtech

import com.google.firebase.auth.FirebaseToken
import com.grandtech.model.School
import com.grandtech.service.CbcDataSeeder
import com.grandtech.service.FirebaseAuthService
import com.grandtech.service.SchoolService
import com.grandtech.service.SubjectRepository
import com.grandtech.service.UserRepository
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.lang.reflect.Constructor

@QuarkusTest
open class SchoolResourceTest {

    @InjectMock
    lateinit var subjectRepository: SubjectRepository

    @InjectMock
    lateinit var firebaseAuthService: FirebaseAuthService

    @InjectMock
    lateinit var userRepository: UserRepository

    @InjectMock
    lateinit var schoolService: SchoolService

    @BeforeEach
    fun setUp() {
        Mockito.`when`(subjectRepository.listAll()).thenReturn(CbcDataSeeder.SUBJECTS)
    }

    companion object {
        /**
         * Creates a real [FirebaseToken] via reflection — its constructor is package-private.
         *
         * @param uid the Firebase UID to embed in the token
         * @return a [FirebaseToken] whose [FirebaseToken.getUid] returns [uid]
         */
        private fun buildToken(uid: String): FirebaseToken {
            val ctor: Constructor<FirebaseToken> =
                FirebaseToken::class.java.getDeclaredConstructor(Map::class.java)
            ctor.isAccessible = true
            return ctor.newInstance(mapOf("sub" to uid))
        }
    }

    @Test
    fun `get schools returns 200 success envelope`() {
        given()
            .`when`().get("/schools")
            .then()
                .statusCode(200)
                .body("status", `is`(200))
                .body("message", `is`("success"))
                .body("payload", `is`("Schools endpoint is live"))
    }

    @Test
    fun `get subjects endpoint exists and returns 200 success envelope`() {
        given()
            .`when`().get("/schools/subjects")
            .then()
                .statusCode(200)
                .body("status", `is`(200))
                .body("message", `is`("Success"))
    }

    @Test
    fun `get subjects returns all 10 CBC JSS subjects`() {
        val expectedSymbols = CbcDataSeeder.SUBJECTS.map { it.symbol }.toTypedArray()

        given()
            .`when`().get("/schools/subjects")
            .then()
                .statusCode(200)
                .body("payload", hasSize<Any>(10))
                .body("payload.symbol", containsInAnyOrder(*expectedSymbols))
    }

    @Test
    fun `get subjects includes correct periods per week for each subject`() {
        given()
            .`when`().get("/schools/subjects")
            .then()
                .statusCode(200)
                .body("payload.find { it.symbol == 'ENG' }.periodsPerWeek", `is`(5))
                .body("payload.find { it.symbol == 'KIS' }.periodsPerWeek", `is`(4))
                .body("payload.find { it.symbol == 'MAT' }.periodsPerWeek", `is`(5))
                .body("payload.find { it.symbol == 'SST' }.periodsPerWeek", `is`(4))
                .body("payload.find { it.symbol == 'RE' }.periodsPerWeek",  `is`(4))
                .body("payload.find { it.symbol == 'PPI' }.periodsPerWeek", `is`(1))
    }

    @Test
    fun `get subjects marks practical subjects with requiresDoubledPeriod and correct room tags`() {
        given()
            .`when`().get("/schools/subjects")
            .then()
                .statusCode(200)
                .body("payload.find { it.symbol == 'SCI' }.requiresDoubledPeriod", `is`(true))
                .body("payload.find { it.symbol == 'SCI' }.requiresSpecialRoom",   `is`(true))
                .body("payload.find { it.symbol == 'SCI' }.roomCapabilityTag",     `is`("SCIENCE_LAB"))
                .body("payload.find { it.symbol == 'PTS' }.requiresDoubledPeriod", `is`(true))
                .body("payload.find { it.symbol == 'PTS' }.roomCapabilityTag",     `is`("WORKSHOP"))
                .body("payload.find { it.symbol == 'AGR' }.requiresDoubledPeriod", `is`(true))
                .body("payload.find { it.symbol == 'AGR' }.roomCapabilityTag",     `is`("GARDEN"))
                .body("payload.find { it.symbol == 'CAS' }.requiresDoubledPeriod", `is`(true))
                .body("payload.find { it.symbol == 'CAS' }.preferBeforeBreak",     `is`(true))
    }

    @Test
    fun `get subjects marks PPI as fixed and non-practical subjects correctly`() {
        given()
            .`when`().get("/schools/subjects")
            .then()
                .statusCode(200)
                .body("payload.find { it.symbol == 'PPI' }.isPpiFixed",            `is`(true))
                .body("payload.find { it.symbol == 'ENG' }.requiresDoubledPeriod", `is`(false))
                .body("payload.find { it.symbol == 'MAT' }.requiresDoubledPeriod", `is`(false))
                .body("payload.find { it.symbol == 'SST' }.requiresSpecialRoom",   `is`(false))
    }

    // ── GET /schools/profile ─────────────────────────────────────────────────

    @Test
    fun `get school profile endpoint exists and returns 200 for authenticated school`() {
        val token = buildToken("uid-school-1")
        Mockito.`when`(firebaseAuthService.verifyToken("Bearer valid-token")).thenReturn(token)
        Mockito.`when`(userRepository.existsByFedUid("uid-school-1")).thenReturn(true)
        Mockito.`when`(schoolService.getSchoolByFedUid("uid-school-1"))
            .thenReturn(School(fedUid = "uid-school-1"))

        given()
            .header("Authorization", "Bearer valid-token")
            .`when`().get("/schools/profile")
            .then()
                .statusCode(200)
                .body("status", `is`(200))
                .body("message", `is`("Success"))
    }

    @Test
    fun `get school profile returns correct school details from the database`() {
        val token = buildToken("uid-school-2")
        Mockito.`when`(firebaseAuthService.verifyToken("Bearer valid-token-2")).thenReturn(token)
        Mockito.`when`(userRepository.existsByFedUid("uid-school-2")).thenReturn(true)
        Mockito.`when`(schoolService.getSchoolByFedUid("uid-school-2")).thenReturn(
            School(
                fedUid = "uid-school-2",
                name = "Sunrise High School",
                email = "sunrise@school.ke",
                phoneNumber = "0712345678",
                county = "Nairobi",
                subCounty = "Westlands",
            ),
        )

        given()
            .header("Authorization", "Bearer valid-token-2")
            .`when`().get("/schools/profile")
            .then()
                .statusCode(200)
                .body("payload.fedUid",     `is`("uid-school-2"))
                .body("payload.name",       `is`("Sunrise High School"))
                .body("payload.email",      `is`("sunrise@school.ke"))
                .body("payload.phoneNumber",`is`("0712345678"))
                .body("payload.county",     `is`("Nairobi"))
                .body("payload.subCounty",  `is`("Westlands"))
    }

    @Test
    fun `get school profile without auth header returns 401`() {
        given()
            .`when`().get("/schools/profile")
            .then()
                .statusCode(401)
                .body("status",   `is`(401))
                .body("payload",  nullValue())
    }

    @Test
    fun `get school profile for non-school account returns 403`() {
        val token = buildToken("uid-teacher-1")
        Mockito.`when`(firebaseAuthService.verifyToken("Bearer teacher-token")).thenReturn(token)
        Mockito.`when`(userRepository.existsByFedUid("uid-teacher-1")).thenReturn(true)
        Mockito.`when`(schoolService.getSchoolByFedUid("uid-teacher-1")).thenReturn(null)

        given()
            .header("Authorization", "Bearer teacher-token")
            .`when`().get("/schools/profile")
            .then()
                .statusCode(200)
                .body("status",  `is`(403))
                .body("payload", nullValue())
    }
}
