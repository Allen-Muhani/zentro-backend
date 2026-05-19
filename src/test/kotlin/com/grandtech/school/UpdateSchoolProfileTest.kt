package com.grandtech.school

import com.grandtech.model.School
import com.grandtech.service.FirebaseAuthService
import com.grandtech.service.SchoolService
import com.grandtech.service.UserRepository
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Tests for `PATCH /schools/profile`.
 */
@QuarkusTest
open class UpdateSchoolProfileTest {

    @InjectMock
    lateinit var firebaseAuthService: FirebaseAuthService

    @InjectMock
    lateinit var userRepository: UserRepository

    @InjectMock
    lateinit var schoolService: SchoolService

    @Test
    fun `returns 200 with updated school`() {
        val token = GetSchoolProfileTest.buildToken("uid-school-upd-1")
        Mockito.`when`(firebaseAuthService.verifyToken("Bearer update-token")).thenReturn(token)
        Mockito.`when`(userRepository.existsByFedUid("uid-school-upd-1")).thenReturn(true)
        Mockito.`when`(
            schoolService.updateSchool(
                "uid-school-upd-1",
                School(fedUid = "uid-school-upd-1", name = "New Name", county = "Mombasa"),
            ),
        ).thenReturn(
            School(fedUid = "uid-school-upd-1", name = "New Name", email = "original@school.ke", county = "Mombasa"),
        )

        given()
            .header("Authorization", "Bearer update-token")
            .contentType(ContentType.JSON)
            .body("""{"type":"SCHOOL","fedUid":"uid-school-upd-1","name":"New Name","county":"Mombasa"}""")
            .`when`().patch("/schools/profile")
            .then()
                .statusCode(200)
                .body("status",  `is`(200))
                .body("message", `is`("Success"))
    }

    @Test
    fun `does not overwrite email or fedUid`() {
        val token = GetSchoolProfileTest.buildToken("uid-school-upd-2")
        Mockito.`when`(firebaseAuthService.verifyToken("Bearer update-token-2")).thenReturn(token)
        Mockito.`when`(userRepository.existsByFedUid("uid-school-upd-2")).thenReturn(true)
        Mockito.`when`(
            schoolService.updateSchool(
                "uid-school-upd-2",
                School(fedUid = "attacker-uid", name = "Updated School", email = "hacker@evil.com", phoneNumber = "0799999999"),
            ),
        ).thenReturn(
            School(fedUid = "uid-school-upd-2", name = "Updated School", email = "original@school.ke", phoneNumber = "0799999999"),
        )

        given()
            .header("Authorization", "Bearer update-token-2")
            .contentType(ContentType.JSON)
            .body("""{"type":"SCHOOL","fedUid":"attacker-uid","name":"Updated School","email":"hacker@evil.com","phoneNumber":"0799999999"}""")
            .`when`().patch("/schools/profile")
            .then()
                .statusCode(200)
                .body("payload.fedUid", `is`("uid-school-upd-2"))
                .body("payload.email",  `is`("original@school.ke"))
    }

    @Test
    fun `without auth header returns 401`() {
        given()
            .contentType(ContentType.JSON)
            .body("""{"name":"No Auth School"}""")
            .`when`().patch("/schools/profile")
            .then()
                .statusCode(401)
                .body("status",  `is`(401))
                .body("payload", nullValue())
    }
}