package com.grandtech.service.teacher

import com.grandtech.model.Teacher
import com.grandtech.service.CbcDataSeeder
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Integration tests for [com.grandtech.service.TeacherService.createTeacher].
 */
@QuarkusTest
class CreateTeacherServiceTest : TeacherServiceTestBase() {

    private val subjectId1 = CbcDataSeeder.SUBJECTS[0].id
    private val subjectId2 = CbcDataSeeder.SUBJECTS[1].id

    @Test
    fun `createTeacher returns 200 with generated id on success`() {
        trackSchool("ct-school-1")
        saveSchool("ct-school-1")

        val response = teacherService.createTeacher(
            "ct-school-1",
            Teacher(name = "Ms Njeri", email = "njeri@ct.ke", subjectIds = listOf(subjectId1)),
        )

        assertEquals(200, response.status)
        assertEquals("Teacher created", response.message)
        assertNotNull(response.payload?.id)
    }

    @Test
    fun `createTeacher stores scalar fields correctly`() {
        trackSchool("ct-school-2")
        saveSchool("ct-school-2")

        val teacher = teacherService.createTeacher(
            "ct-school-2",
            Teacher(
                name = "Mr Otieno",
                email = "otieno@ct.ke",
                phone = "0712345678",
                tscNumber = "TSC001",
                maxPeriodsPerWeek = 20,
                maxPeriodsPerDay = 5,
                subjectIds = listOf(subjectId1),
            ),
        ).payload!!

        assertEquals("Mr Otieno", teacher.name)
        assertEquals("otieno@ct.ke", teacher.email)
        assertEquals("0712345678", teacher.phone)
        assertEquals("TSC001", teacher.tscNumber)
        assertEquals(20, teacher.maxPeriodsPerWeek)
        assertEquals(5, teacher.maxPeriodsPerDay)
    }

    @Test
    fun `createTeacher returns subjectIds in payload`() {
        trackSchool("ct-school-3")
        saveSchool("ct-school-3")

        val teacher = teacherService.createTeacher(
            "ct-school-3",
            Teacher(name = "Ms Auma", email = "auma@ct.ke", subjectIds = listOf(subjectId1, subjectId2)),
        ).payload!!

        assertEquals(2, teacher.subjectIds?.size)
        assertEquals(setOf(subjectId1, subjectId2), teacher.subjectIds!!.toSet())
    }

    @Test
    fun `createTeacher returns 400 when name is blank`() {
        trackSchool("ct-school-4")
        saveSchool("ct-school-4")

        val response = teacherService.createTeacher(
            "ct-school-4",
            Teacher(name = "", email = "blank@ct.ke", subjectIds = listOf(subjectId1)),
        )

        assertEquals(400, response.status)
        assertEquals("Teacher name is required", response.message)
        assertNull(response.payload)
    }

    @Test
    fun `createTeacher returns 400 when email is blank`() {
        trackSchool("ct-school-5")
        saveSchool("ct-school-5")

        val response = teacherService.createTeacher(
            "ct-school-5",
            Teacher(name = "Ms Kamau", email = "", subjectIds = listOf(subjectId1)),
        )

        assertEquals(400, response.status)
        assertEquals("Teacher email is required", response.message)
        assertNull(response.payload)
    }

    @Test
    fun `createTeacher returns 409 when email is already taken`() {
        trackSchool("ct-school-6")
        saveSchool("ct-school-6")
        teacherService.createTeacher(
            "ct-school-6",
            Teacher(name = "Ms First", email = "taken@ct.ke", subjectIds = listOf(subjectId1)),
        )

        val response = teacherService.createTeacher(
            "ct-school-6",
            Teacher(name = "Ms Second", email = "taken@ct.ke", subjectIds = listOf(subjectId1)),
        )

        assertEquals(409, response.status)
        assertEquals("A teacher with that email already exists", response.message)
        assertNull(response.payload)
    }

    @Test
    fun `createTeacher returns 409 when email is taken by a teacher in a different school`() {
        trackSchool("ct-school-7a", "ct-school-7b")
        saveSchool("ct-school-7a")
        saveSchool("ct-school-7b")
        teacherService.createTeacher(
            "ct-school-7a",
            Teacher(name = "Ms Alpha", email = "crossschool@ct.ke", subjectIds = listOf(subjectId1)),
        )

        val response = teacherService.createTeacher(
            "ct-school-7b",
            Teacher(name = "Ms Beta", email = "crossschool@ct.ke", subjectIds = listOf(subjectId1)),
        )

        assertEquals(409, response.status)
        assertNull(response.payload)
    }

    @Test
    fun `createTeacher returns 400 when no subjects are provided`() {
        trackSchool("ct-school-8")
        saveSchool("ct-school-8")

        val response = teacherService.createTeacher(
            "ct-school-8",
            Teacher(name = "Ms Wanjiku", email = "wanjiku@ct.ke", subjectIds = emptyList()),
        )

        assertEquals(400, response.status)
        assertEquals("At least one subject is required", response.message)
        assertNull(response.payload)
    }

    @Test
    fun `createTeacher returns 400 when more than two subjects are provided`() {
        trackSchool("ct-school-9")
        saveSchool("ct-school-9")

        val response = teacherService.createTeacher(
            "ct-school-9",
            Teacher(
                name = "Mr Kiprop",
                email = "kiprop@ct.ke",
                subjectIds = listOf(subjectId1, subjectId2, CbcDataSeeder.SUBJECTS[2].id),
            ),
        )

        assertEquals(400, response.status)
        assertEquals("A teacher may teach at most 2 subjects", response.message)
        assertNull(response.payload)
    }

    @Test
    fun `createTeacher returns 404 when school does not exist`() {
        val response = teacherService.createTeacher(
            "non-existent-school",
            Teacher(name = "Mr Ghost", email = "ghost@ct.ke", subjectIds = listOf(subjectId1)),
        )

        assertEquals(404, response.status)
        assertEquals("School not found", response.message)
        assertNull(response.payload)
    }
}