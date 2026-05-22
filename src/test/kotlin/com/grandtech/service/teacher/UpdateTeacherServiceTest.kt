package com.grandtech.service.teacher

import com.grandtech.model.Teacher
import com.grandtech.service.CbcDataSeeder
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Integration tests for [com.grandtech.service.TeacherService.updateTeacher].
 */
@QuarkusTest
class UpdateTeacherServiceTest : TeacherServiceTestBase() {

    private val subjectId1 = CbcDataSeeder.SUBJECTS[0].id
    private val subjectId2 = CbcDataSeeder.SUBJECTS[1].id
    private val subjectId3 = CbcDataSeeder.SUBJECTS[2].id

    private fun createTeacher(schoolFedUid: String, name: String, email: String): Teacher =
        teacherService.createTeacher(
            schoolFedUid,
            Teacher(name = name, email = email, subjectIds = listOf(subjectId1)),
        ).payload!!

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    fun `updateTeacher returns 400 when id is missing`() {
        val response = teacherService.updateTeacher("any-school", Teacher(name = "Ms Njeri"))

        assertEquals(400, response.status)
        assertEquals("Teacher id is required", response.message)
        assertNull(response.payload)
    }

    @Test
    fun `updateTeacher returns 400 when name is blank`() {
        trackSchool("uts-school-1")
        saveSchool("uts-school-1")
        val teacher = createTeacher("uts-school-1", "Ms Njeri", "njeri@uts.ke")

        val response = teacherService.updateTeacher(
            "uts-school-1",
            Teacher(id = teacher.id, name = "   "),
        )

        assertEquals(400, response.status)
        assertEquals("Teacher name must not be blank", response.message)
        assertNull(response.payload)
    }

    @Test
    fun `updateTeacher returns 400 when email is blank`() {
        trackSchool("uts-school-2")
        saveSchool("uts-school-2")
        val teacher = createTeacher("uts-school-2", "Ms Njeri", "njeri@uts.ke")

        val response = teacherService.updateTeacher(
            "uts-school-2",
            Teacher(id = teacher.id, email = ""),
        )

        assertEquals(400, response.status)
        assertEquals("Teacher email must not be blank", response.message)
        assertNull(response.payload)
    }

    @Test
    fun `updateTeacher returns 409 when email is taken by another teacher`() {
        trackSchool("uts-school-3")
        saveSchool("uts-school-3")
        createTeacher("uts-school-3", "Ms First", "first@uts.ke")
        val teacher2 = createTeacher("uts-school-3", "Ms Second", "second@uts.ke")

        val response = teacherService.updateTeacher(
            "uts-school-3",
            Teacher(id = teacher2.id, email = "first@uts.ke"),
        )

        assertEquals(409, response.status)
        assertEquals("A teacher with that email already exists", response.message)
        assertNull(response.payload)
    }

    @Test
    fun `updateTeacher allows updating email to own current email`() {
        trackSchool("uts-school-4")
        saveSchool("uts-school-4")
        val teacher = createTeacher("uts-school-4", "Ms Njeri", "njeri@uts.ke")

        val response = teacherService.updateTeacher(
            "uts-school-4",
            Teacher(id = teacher.id, email = "njeri@uts.ke", name = "Ms Njeri Updated"),
        )

        assertEquals(200, response.status)
        assertEquals("njeri@uts.ke", response.payload?.email)
    }

    @Test
    fun `updateTeacher returns 400 when subjectIds is empty`() {
        trackSchool("uts-school-5")
        saveSchool("uts-school-5")
        val teacher = createTeacher("uts-school-5", "Ms Wanjiku", "wanjiku@uts.ke")

        val response = teacherService.updateTeacher(
            "uts-school-5",
            Teacher(id = teacher.id, subjectIds = emptyList()),
        )

        assertEquals(400, response.status)
        assertEquals("At least one subject is required", response.message)
        assertNull(response.payload)
    }

    @Test
    fun `updateTeacher returns 400 when more than two subjects are provided`() {
        trackSchool("uts-school-6")
        saveSchool("uts-school-6")
        val teacher = createTeacher("uts-school-6", "Mr Kiprop", "kiprop@uts.ke")

        val response = teacherService.updateTeacher(
            "uts-school-6",
            Teacher(id = teacher.id, subjectIds = listOf(subjectId1, subjectId2, subjectId3)),
        )

        assertEquals(400, response.status)
        assertEquals("A teacher may teach at most 2 subjects", response.message)
        assertNull(response.payload)
    }

    @Test
    fun `updateTeacher returns 404 when teacher does not exist`() {
        trackSchool("uts-school-7")
        saveSchool("uts-school-7")

        val response = teacherService.updateTeacher(
            "uts-school-7",
            Teacher(id = "00000000-0000-0000-0000-000000000000", name = "Ghost"),
        )

        assertEquals(404, response.status)
        assertEquals("Teacher not found", response.message)
        assertNull(response.payload)
    }

    // ── Success ───────────────────────────────────────────────────────────────

    @Test
    fun `updateTeacher returns 200 with updated record on success`() {
        trackSchool("uts-school-8")
        saveSchool("uts-school-8")
        val teacher = createTeacher("uts-school-8", "Ms Auma", "auma@uts.ke")

        val response = teacherService.updateTeacher(
            "uts-school-8",
            Teacher(id = teacher.id, name = "Ms Auma Updated"),
        )

        assertEquals(200, response.status)
        assertEquals("Teacher updated", response.message)
        assertNotNull(response.payload)
        assertEquals(teacher.id, response.payload!!.id)
        assertEquals("Ms Auma Updated", response.payload.name)
    }

    @Test
    fun `updateTeacher replaces subjects and returns updated subjects in payload`() {
        trackSchool("uts-school-9")
        saveSchool("uts-school-9")
        val teacher = createTeacher("uts-school-9", "Ms Chebet", "chebet@uts.ke")

        val response = teacherService.updateTeacher(
            "uts-school-9",
            Teacher(id = teacher.id, subjectIds = listOf(subjectId2)),
        )

        assertEquals(200, response.status)
        assertEquals(1, response.payload?.subjectIds?.size)
        assertEquals(subjectId2, response.payload?.subjectIds?.first())
    }

    @Test
    fun `updateTeacher does not affect subjects when subjectIds is omitted`() {
        trackSchool("uts-school-10")
        saveSchool("uts-school-10")
        val teacher = teacherService.createTeacher(
            "uts-school-10",
            Teacher(name = "Mr Mwangi", email = "mwangi@uts.ke", subjectIds = listOf(subjectId1, subjectId2)),
        ).payload!!

        val response = teacherService.updateTeacher(
            "uts-school-10",
            Teacher(id = teacher.id, name = "Mr Mwangi Updated"),
        )

        assertEquals(200, response.status)
        assertEquals(2, response.payload?.subjectIds?.size)
    }
}
