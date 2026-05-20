package com.grandtech.service.teacher

import com.grandtech.model.Teacher
import com.grandtech.service.CbcDataSeeder
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Integration tests for [com.grandtech.service.TeacherService.deleteTeacher].
 */
@QuarkusTest
class DeleteTeacherServiceTest : TeacherServiceTestBase() {

    private val subjectId1 = CbcDataSeeder.SUBJECTS[0].id

    private fun createTeacher(schoolFedUid: String, name: String, email: String): Teacher =
        teacherService.createTeacher(
            schoolFedUid,
            Teacher(name = name, email = email, subjectIds = listOf(subjectId1)),
        ).payload!!

    @Test
    fun `deleteTeacher returns 200 with message on success`() {
        trackSchool("dts-school-1")
        saveSchool("dts-school-1")
        val teacher = createTeacher("dts-school-1", "Ms Njeri", "njeri@dts.ke")

        val response = teacherService.deleteTeacher("dts-school-1", teacher.id!!)

        assertEquals(200, response.status)
        assertEquals("Teacher deleted", response.message)
        assertNull(response.payload)
    }

    @Test
    fun `deleteTeacher returns 404 when teacher does not exist`() {
        trackSchool("dts-school-2")
        saveSchool("dts-school-2")

        val response = teacherService.deleteTeacher("dts-school-2", "00000000-0000-0000-0000-000000000000")

        assertEquals(404, response.status)
        assertEquals("Teacher not found", response.message)
        assertNull(response.payload)
    }

    @Test
    fun `deleteTeacher returns 404 when teacher belongs to a different school`() {
        trackSchool("dts-school-3a", "dts-school-3b")
        saveSchool("dts-school-3a")
        saveSchool("dts-school-3b")
        val teacher = createTeacher("dts-school-3a", "Mr Otieno", "otieno@dts.ke")

        val response = teacherService.deleteTeacher("dts-school-3b", teacher.id!!)

        assertEquals(404, response.status)
        assertNull(response.payload)
    }

    @Test
    fun `deleted teacher no longer appears in listTeachers`() {
        trackSchool("dts-school-4")
        saveSchool("dts-school-4")
        val teacher = createTeacher("dts-school-4", "Ms Wanjiku", "wanjiku@dts.ke")

        teacherService.deleteTeacher("dts-school-4", teacher.id!!)

        val list = teacherService.listTeachers("dts-school-4").payload!!
        assertTrue(list.isEmpty())
    }

    @Test
    fun `deleting one teacher does not affect others in the same school`() {
        trackSchool("dts-school-5")
        saveSchool("dts-school-5")
        val teacher1 = createTeacher("dts-school-5", "Ms Alpha", "alpha@dts.ke")
        val teacher2 = createTeacher("dts-school-5", "Mr Beta", "beta@dts.ke")

        teacherService.deleteTeacher("dts-school-5", teacher1.id!!)

        val list = teacherService.listTeachers("dts-school-5").payload!!
        assertEquals(1, list.size)
        assertEquals(teacher2.id, list.first().id)
    }
}