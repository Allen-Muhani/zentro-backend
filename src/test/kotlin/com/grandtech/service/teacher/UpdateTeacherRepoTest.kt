package com.grandtech.service.teacher

import com.grandtech.model.Teacher
import com.grandtech.service.CbcDataSeeder
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Integration tests for [com.grandtech.repository.TeacherRepository.updateTeacher]
 * and [com.grandtech.repository.TeacherRepository.existsByEmailExcluding].
 */
@QuarkusTest
class UpdateTeacherRepoTest : TeacherServiceTestBase() {

    private val subjectId1 = CbcDataSeeder.SUBJECTS[0].id
    private val subjectId2 = CbcDataSeeder.SUBJECTS[1].id
    private val subjectId3 = CbcDataSeeder.SUBJECTS[2].id

    private fun createTeacher(schoolFedUid: String, name: String, email: String): Teacher =
        checkNotNull(
            teacherRepository.createTeacher(
                schoolFedUid,
                Teacher(name = name, email = email, subjectIds = listOf(subjectId1)),
            ),
        )

    // ── updateTeacher ─────────────────────────────────────────────────────────

    @Test
    fun `updateTeacher returns null when teacher id does not exist`() {
        trackSchool("utr-school-1")
        saveSchool("utr-school-1")

        val result = teacherRepository.updateTeacher(
            "utr-school-1",
            Teacher(id = "00000000-0000-0000-0000-000000000000", name = "Ghost"),
        )

        assertNull(result)
    }

    @Test
    fun `updateTeacher returns null when teacher belongs to a different school`() {
        trackSchool("utr-school-2a", "utr-school-2b")
        saveSchool("utr-school-2a")
        saveSchool("utr-school-2b")
        val teacher = createTeacher("utr-school-2a", "Ms Njeri", "njeri@utr.ke")

        val result = teacherRepository.updateTeacher(
            "utr-school-2b",
            Teacher(id = teacher.id, name = "Renamed"),
        )

        assertNull(result)
    }

    @Test
    fun `updateTeacher updates name`() {
        trackSchool("utr-school-3")
        saveSchool("utr-school-3")
        val teacher = createTeacher("utr-school-3", "Ms Old", "old@utr.ke")

        val updated = teacherRepository.updateTeacher(
            "utr-school-3",
            Teacher(id = teacher.id, name = "Ms New"),
        )

        assertNotNull(updated)
        assertEquals("Ms New", updated!!.name)
        assertEquals("old@utr.ke", updated.email)
    }

    @Test
    fun `updateTeacher updates email`() {
        trackSchool("utr-school-4")
        saveSchool("utr-school-4")
        val teacher = createTeacher("utr-school-4", "Mr Otieno", "old@utr.ke")

        val updated = teacherRepository.updateTeacher(
            "utr-school-4",
            Teacher(id = teacher.id, email = "new@utr.ke"),
        )

        assertNotNull(updated)
        assertEquals("new@utr.ke", updated!!.email)
        assertEquals("Mr Otieno", updated.name)
    }

    @Test
    fun `updateTeacher updates optional scalar fields`() {
        trackSchool("utr-school-5")
        saveSchool("utr-school-5")
        val teacher = createTeacher("utr-school-5", "Ms Kamau", "kamau@utr.ke")

        val updated = teacherRepository.updateTeacher(
            "utr-school-5",
            Teacher(
                id = teacher.id,
                phone = "0711111111",
                tscNumber = "TSC-999",
                maxPeriodsPerWeek = 15,
                maxPeriodsPerDay = 3,
            ),
        )

        assertNotNull(updated)
        assertEquals("0711111111", updated!!.phone)
        assertEquals("TSC-999", updated.tscNumber)
        assertEquals(15, updated.maxPeriodsPerWeek)
        assertEquals(3, updated.maxPeriodsPerDay)
    }

    @Test
    fun `updateTeacher preserves fields not included in the update`() {
        trackSchool("utr-school-6")
        saveSchool("utr-school-6")
        val teacher = checkNotNull(
            teacherRepository.createTeacher(
                "utr-school-6",
                Teacher(
                    name = "Ms Achieng",
                    email = "achieng@utr.ke",
                    phone = "0700000001",
                    tscNumber = "TSC-001",
                    maxPeriodsPerWeek = 20,
                    maxPeriodsPerDay = 5,
                    subjectIds = listOf(subjectId1),
                ),
            ),
        )

        val updated = teacherRepository.updateTeacher(
            "utr-school-6",
            Teacher(id = teacher.id, name = "Ms Achieng Updated"),
        )!!

        assertEquals("Ms Achieng Updated", updated.name)
        assertEquals("achieng@utr.ke", updated.email)
        assertEquals("0700000001", updated.phone)
        assertEquals("TSC-001", updated.tscNumber)
        assertEquals(20, updated.maxPeriodsPerWeek)
        assertEquals(5, updated.maxPeriodsPerDay)
    }

    @Test
    fun `updateTeacher replaces subjects when subjectIds is provided`() {
        trackSchool("utr-school-7")
        saveSchool("utr-school-7")
        val teacher = createTeacher("utr-school-7", "Ms Wanjiku", "wanjiku@utr.ke")

        val updated = teacherRepository.updateTeacher(
            "utr-school-7",
            Teacher(id = teacher.id, subjectIds = listOf(subjectId2, subjectId3)),
        )!!

        assertEquals(2, updated.subjects?.size)
        val ids = updated.subjects!!.map { it.id }.toSet()
        assertTrue(ids.contains(subjectId2))
        assertTrue(ids.contains(subjectId3))
        assertFalse(ids.contains(subjectId1))
    }

    @Test
    fun `updateTeacher does not touch subjects when subjectIds is null`() {
        trackSchool("utr-school-8")
        saveSchool("utr-school-8")
        val teacher = checkNotNull(
            teacherRepository.createTeacher(
                "utr-school-8",
                Teacher(name = "Mr Kiprop", email = "kiprop@utr.ke", subjectIds = listOf(subjectId1, subjectId2)),
            ),
        )

        val updated = teacherRepository.updateTeacher(
            "utr-school-8",
            Teacher(id = teacher.id, name = "Mr Kiprop Updated"),
        )!!

        assertEquals(2, updated.subjects?.size)
    }

    @Test
    fun `updateTeacher returns teacher with full subjects in payload`() {
        trackSchool("utr-school-9")
        saveSchool("utr-school-9")
        val teacher = createTeacher("utr-school-9", "Ms Alpha", "alpha@utr.ke")

        val updated = teacherRepository.updateTeacher(
            "utr-school-9",
            Teacher(id = teacher.id, subjectIds = listOf(subjectId2)),
        )!!

        assertEquals(1, updated.subjects?.size)
        assertNotNull(updated.subjects!!.first().name)
    }

    // ── existsByEmailExcluding ────────────────────────────────────────────────

    @Test
    fun `existsByEmailExcluding returns false when no other teacher has the email`() {
        trackSchool("utr-school-10")
        saveSchool("utr-school-10")
        val teacher = createTeacher("utr-school-10", "Ms Solo", "solo@utr.ke")

        assertFalse(teacherRepository.existsByEmailExcluding("solo@utr.ke", teacher.id!!))
    }

    @Test
    fun `existsByEmailExcluding returns true when a different teacher has the email`() {
        trackSchool("utr-school-11")
        saveSchool("utr-school-11")
        val teacher1 = createTeacher("utr-school-11", "Ms First", "first@utr.ke")
        val teacher2 = createTeacher("utr-school-11", "Ms Second", "second@utr.ke")

        assertTrue(teacherRepository.existsByEmailExcluding("second@utr.ke", teacher1.id!!))
    }

    @Test
    fun `existsByEmailExcluding returns false for unrelated email`() {
        trackSchool("utr-school-12")
        saveSchool("utr-school-12")
        val teacher = createTeacher("utr-school-12", "Ms Alone", "alone@utr.ke")

        assertFalse(teacherRepository.existsByEmailExcluding("nobody@utr.ke", teacher.id!!))
    }
}
