package com.grandtech.service.stream

import com.grandtech.model.School
import com.grandtech.model.Stream
import com.grandtech.model.Teacher
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Integration tests for the create path of [com.grandtech.service.StreamService.upsertStream]
 * (i.e. when [Stream.id] is null).
 */
@QuarkusTest
class UpsertStreamCreateTest : StreamServiceTestBase() {

    @Test
    fun `upsertStream returns 200 with generated id on create`() {
        trackSchool("usc-school-1")
        userRepository.saveSchool(School(fedUid = "usc-school-1"))

        val response = streamService.upsertStream("usc-school-1", Stream(gradeLevel = 7, name = "Blue"))

        assertEquals(200, response.status)
        assertEquals("Stream created", response.message)
        assertNotNull(response.payload?.id)
    }

    @Test
    fun `upsertStream stores all scalar fields correctly`() {
        trackSchool("usc-school-2")
        userRepository.saveSchool(School(fedUid = "usc-school-2"))

        val stream = upsertStream(
            "usc-school-2",
            Stream(gradeLevel = 8, name = "White", studentCount = 35, reStrand = "IRE"),
        )

        assertEquals(8, stream.gradeLevel)
        assertEquals("White", stream.name)
        assertEquals(35, stream.studentCount)
        assertEquals("IRE", stream.reStrand)
    }

    @Test
    fun `upsertStream defaults reStrand to CRE when not supplied`() {
        trackSchool("usc-school-3")
        userRepository.saveSchool(School(fedUid = "usc-school-3"))

        val stream = upsertStream("usc-school-3", Stream(gradeLevel = 9, name = "Red"))

        assertEquals("CRE", stream.reStrand)
    }

    @Test
    fun `upsertStream creates HAS_STREAM relationship to the school`() {
        trackSchool("usc-school-4")
        userRepository.saveSchool(School(fedUid = "usc-school-4"))

        val stream = upsertStream("usc-school-4", Stream(gradeLevel = 7, name = "Green"))

        val linked = driver.session().use { session ->
            session.run(
                """
                MATCH (:School {fedUid: ${'$'}fedUid})-[:HAS_STREAM]->(:Stream {id: ${'$'}streamId})
                RETURN count(*) > 0 AS exists
                """.trimIndent(),
                mapOf("fedUid" to "usc-school-4", "streamId" to stream.id!!),
            ).single()["exists"].asBoolean()
        }
        assertEquals(true, linked)
    }

    @Test
    fun `upsertStream creates FORM_TEACHER relationship and returns full teacher`() {
        trackSchool("usc-school-6")
        userRepository.saveSchool(School(fedUid = "usc-school-6"))
        val teacher = createTeacher("usc-school-6", "Ms Njeri", "njeri@usc.ke")

        val stream = upsertStream(
            "usc-school-6",
            Stream(gradeLevel = 8, name = "Blue", formTeacher = Teacher(id = teacher.id)),
        )

        assertNotNull(stream.formTeacher)
        assertEquals(teacher.id, stream.formTeacher?.id)
        assertEquals("Ms Njeri", stream.formTeacher?.name)
        assertEquals("njeri@usc.ke", stream.formTeacher?.email)
    }

    @Test
    fun `upsertStream returns 404 when school does not exist`() {
        val response = streamService.upsertStream("non-existent-school", Stream(gradeLevel = 7, name = "Blue"))

        assertEquals(404, response.status)
        assertNull(response.payload)
    }

    @Test
    fun `upsertStream returns 400 when gradeLevel is out of range`() {
        trackSchool("usc-school-8")
        userRepository.saveSchool(School(fedUid = "usc-school-8"))

        val response = streamService.upsertStream("usc-school-8", Stream(gradeLevel = 6, name = "Blue"))

        assertEquals(400, response.status)
        assertEquals("gradeLevel must be 7, 8, or 9", response.message)
        assertNull(response.payload)
    }

    @Test
    fun `upsertStream returns 400 when name is blank`() {
        trackSchool("usc-school-9")
        userRepository.saveSchool(School(fedUid = "usc-school-9"))

        val response = streamService.upsertStream("usc-school-9", Stream(gradeLevel = 7, name = ""))

        assertEquals(400, response.status)
        assertEquals("Stream name is required", response.message)
        assertNull(response.payload)
    }

    @Test
    fun `upsertStream returns stream with null formTeacher when no teacher is provided`() {
        trackSchool("usc-school-11")
        userRepository.saveSchool(School(fedUid = "usc-school-11"))

        val stream = upsertStream("usc-school-11", Stream(gradeLevel = 9, name = "Gold"))

        assertNull(stream.formTeacher)
    }
}