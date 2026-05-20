package com.grandtech.school

import com.grandtech.service.CbcDataSeeder
import com.grandtech.repository.SubjectRepository
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

/**
 * Tests for `GET /school/subjects`.
 */
@QuarkusTest
open class ListSubjectsTest {

    @InjectMock
    lateinit var subjectRepository: SubjectRepository

    @BeforeEach
    fun setUp() {
        Mockito.`when`(subjectRepository.listAll()).thenReturn(CbcDataSeeder.SUBJECTS)
    }

    @Test
    fun `endpoint exists and returns 200 success envelope`() {
        given()
            .`when`().get("/school/subjects")
            .then()
                .statusCode(200)
                .body("status",  `is`(200))
                .body("message", `is`("Success"))
    }

    @Test
    fun `returns all 10 CBC JSS subjects`() {
        val expectedSymbols = CbcDataSeeder.SUBJECTS.map { it.symbol }.toTypedArray()

        given()
            .`when`().get("/school/subjects")
            .then()
                .statusCode(200)
                .body("payload", hasSize<Any>(10))
                .body("payload.symbol", containsInAnyOrder(*expectedSymbols))
    }

    @Test
    fun `includes correct periods per week for each subject`() {
        given()
            .`when`().get("/school/subjects")
            .then()
                .statusCode(200)
                .body("payload.find { it.symbol == 'ENG' }.periodsPerWeek", `is`(5))
                .body("payload.find { it.symbol == 'KIS' }.periodsPerWeek", `is`(4))
                .body("payload.find { it.symbol == 'MAT' }.periodsPerWeek", `is`(5))
                .body("payload.find { it.symbol == 'SST' }.periodsPerWeek", `is`(4))
                .body("payload.find { it.symbol == 'RE'  }.periodsPerWeek", `is`(4))
                .body("payload.find { it.symbol == 'PPI' }.periodsPerWeek", `is`(1))
    }

    @Test
    fun `marks practical subjects with requiresDoubledPeriod and correct room tags`() {
        given()
            .`when`().get("/school/subjects")
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
    fun `marks PPI as fixed and non-practical subjects correctly`() {
        given()
            .`when`().get("/school/subjects")
            .then()
                .statusCode(200)
                .body("payload.find { it.symbol == 'PPI' }.isPpiFixed",            `is`(true))
                .body("payload.find { it.symbol == 'ENG' }.requiresDoubledPeriod", `is`(false))
                .body("payload.find { it.symbol == 'MAT' }.requiresDoubledPeriod", `is`(false))
                .body("payload.find { it.symbol == 'SST' }.requiresSpecialRoom",   `is`(false))
    }
}