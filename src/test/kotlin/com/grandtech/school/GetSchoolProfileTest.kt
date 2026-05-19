package com.grandtech.school

import com.google.firebase.auth.FirebaseToken
import com.grandtech.model.School
import com.grandtech.service.FirebaseAuthService
import com.grandtech.service.SchoolService
import com.grandtech.service.UserRepository
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.lang.reflect.Constructor

/**
 * Tests for `GET /school/profile`.
 */
@QuarkusTest
open class GetSchoolProfileTest {

    @InjectMock
    lateinit var firebaseAuthService: FirebaseAuthService

    @InjectMock
    lateinit var userRepository: UserRepository

    @InjectMock
    lateinit var schoolService: SchoolService

    companion object {
        /**
         * Creates a real [FirebaseToken] via reflection — its constructor is package-private.
         *
         * @param uid the Firebase UID to embed in the token
         * @return a [FirebaseToken] whose [FirebaseToken.getUid] returns [uid]
         */
        fun buildToken(uid: String): FirebaseToken {
            val ctor: Constructor<FirebaseToken> =
                FirebaseToken::class.java.getDeclaredConstructor(Map::class.java)
            ctor.isAccessible = true
            return ctor.newInstance(mapOf("sub" to uid))
        }
    }

    @Test
    fun `endpoint exists and returns 200 for authenticated school`() {
        val token = buildToken("uid-school-1")
        Mockito.`when`(firebaseAuthService.verifyToken("Bearer valid-token")).thenReturn(token)
        Mockito.`when`(userRepository.existsByFedUid("uid-school-1")).thenReturn(true)
        Mockito.`when`(schoolService.getSchoolByFedUid("uid-school-1"))
            .thenReturn(School(fedUid = "uid-school-1"))

        given()
            .header("Authorization", "Bearer valid-token")
            .`when`().get("/school/profile")
            .then()
                .statusCode(200)
                .body("status",  `is`(200))
                .body("message", `is`("Success"))
    }

    @Test
    fun `returns correct school details from the database`() {
        val token = buildToken("uid-school-2")
        Mockito.`when`(firebaseAuthService.verifyToken("Bearer valid-token-2")).thenReturn(token)
        Mockito.`when`(userRepository.existsByFedUid("uid-school-2")).thenReturn(true)
        Mockito.`when`(schoolService.getSchoolByFedUid("uid-school-2")).thenReturn(
            School(
                fedUid      = "uid-school-2",
                name        = "Sunrise High School",
                email       = "sunrise@school.ke",
                phoneNumber = "0712345678",
                county      = "Nairobi",
                subCounty   = "Westlands",
            ),
        )

        given()
            .header("Authorization", "Bearer valid-token-2")
            .`when`().get("/school/profile")
            .then()
                .statusCode(200)
                .body("payload.fedUid",      `is`("uid-school-2"))
                .body("payload.name",        `is`("Sunrise High School"))
                .body("payload.email",       `is`("sunrise@school.ke"))
                .body("payload.phoneNumber", `is`("0712345678"))
                .body("payload.county",      `is`("Nairobi"))
                .body("payload.subCounty",   `is`("Westlands"))
    }

    @Test
    fun `without auth header returns 401`() {
        given()
            .`when`().get("/school/profile")
            .then()
                .statusCode(401)
                .body("status",  `is`(401))
                .body("payload", nullValue())
    }

    @Test
    fun `for non-school account returns 403`() {
        val token = buildToken("uid-teacher-1")
        Mockito.`when`(firebaseAuthService.verifyToken("Bearer teacher-token")).thenReturn(token)
        Mockito.`when`(userRepository.existsByFedUid("uid-teacher-1")).thenReturn(true)
        Mockito.`when`(schoolService.getSchoolByFedUid("uid-teacher-1")).thenReturn(null)

        given()
            .header("Authorization", "Bearer teacher-token")
            .`when`().get("/school/profile")
            .then()
                .statusCode(200)
                .body("status",  `is`(403))
                .body("payload", nullValue())
    }
}