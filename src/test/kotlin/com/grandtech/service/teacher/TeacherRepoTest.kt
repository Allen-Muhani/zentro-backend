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
 * Integration tests for [com.grandtech.repository.TeacherRepository].
 */
@QuarkusTest
class TeacherRepoTest : TeacherServiceTestBase() {

    private val subjectId1 = CbcDataSeeder.SUBJECTS[0].id
    private val subjectId2 = CbcDataSeeder.SUBJECTS[1].id

    // ── createTeacher ─────────────────────────────────────────────────────────

    @Test
    fun `createTeacher returns null when school does not exist`() {
        val result = teacherRepository.createTeacher(
            "tr-nonexistent-school",
            Teacher(name = "Ghost", email = "ghost@tr.ke", subjectIds = listOf(subjectId1)),
        )

        assertNull(result)
    }

    @Test
    fun `createTeacher returns teacher with generated id`() {
        trackSchool("tr-school-1")
        saveSchool("tr-school-1")

        val teacher = teacherRepository.createTeacher(
            "tr-school-1",
            Teacher(name = "Ms Njeri", email = "njeri@tr.ke", subjectIds = listOf(subjectId1)),
        )

        assertNotNull(teacher)
        assertNotNull(teacher!!.id)
    }

    @Test
    fun `createTeacher persists all scalar fields`() {
        trackSchool("tr-school-2")
        saveSchool("tr-school-2")

        val teacher = teacherRepository.createTeacher(
            "tr-school-2",
            Teacher(
                name = "Mr Otieno",
                email = "otieno@tr.ke",
                phone = "0712345678",
                tscNumber = "TSC001",
                maxPeriodsPerWeek = 18,
                maxPeriodsPerDay = 4,
                subjectIds = listOf(subjectId1),
            ),
        )!!

        assertEquals("Mr Otieno", teacher.name)
        assertEquals("otieno@tr.ke", teacher.email)
        assertEquals("0712345678", teacher.phone)
        assertEquals("TSC001", teacher.tscNumber)
        assertEquals(18, teacher.maxPeriodsPerWeek)
        assertEquals(4, teacher.maxPeriodsPerDay)
    }

    @Test
    fun `createTeacher defaults maxPeriodsPerWeek to 23 and maxPeriodsPerDay to 6 when not supplied`() {
        trackSchool("tr-school-3")
        saveSchool("tr-school-3")

        val teacher = teacherRepository.createTeacher(
            "tr-school-3",
            Teacher(name = "Ms Chebet", email = "chebet@tr.ke", subjectIds = listOf(subjectId1)),
        )!!

        assertEquals(23, teacher.maxPeriodsPerWeek)
        assertEquals(6, teacher.maxPeriodsPerDay)
    }

    @Test
    fun `createTeacher links HAS_TEACHER relationship to the school`() {
        trackSchool("tr-school-4")
        saveSchool("tr-school-4")

        val teacher = teacherRepository.createTeacher(
            "tr-school-4",
            Teacher(name = "Mr Kamau", email = "kamau@tr.ke", subjectIds = listOf(subjectId1)),
        )!!

        val linked = driver.session().use { session ->
            session.run(
                """
                MATCH (:School {fedUid: ${'$'}fedUid})-[:HAS_TEACHER]->(:Teacher {id: ${'$'}id})
                RETURN count(*) > 0 AS exists
                """.trimIndent(),
                mapOf("fedUid" to "tr-school-4", "id" to teacher.id!!),
            ).single()["exists"].asBoolean()
        }
        assertTrue(linked)
    }

    @Test
    fun `createTeacher links TEACHES relationships to subjects`() {
        trackSchool("tr-school-5")
        saveSchool("tr-school-5")

        val teacher = teacherRepository.createTeacher(
            "tr-school-5",
            Teacher(name = "Ms Auma", email = "auma@tr.ke", subjectIds = listOf(subjectId1, subjectId2)),
        )!!

        val subjectCount = driver.session().use { session ->
            session.run(
                """
                MATCH (:Teacher {id: ${'$'}id})-[:TEACHES]->(sub:Subject)
                RETURN count(sub) AS cnt
                """.trimIndent(),
                mapOf("id" to teacher.id!!),
            ).single()["cnt"].asInt()
        }
        assertEquals(2, subjectCount)
    }

    @Test
    fun `createTeacher returns subjectIds in payload`() {
        trackSchool("tr-school-6")
        saveSchool("tr-school-6")

        val teacher = teacherRepository.createTeacher(
            "tr-school-6",
            Teacher(name = "Mr Mwangi", email = "mwangi@tr.ke", subjectIds = listOf(subjectId1)),
        )!!

        assertEquals(1, teacher.subjectIds?.size)
        assertEquals(subjectId1, teacher.subjectIds?.first())
    }

    // ── existsByEmail ─────────────────────────────────────────────────────────

    @Test
    fun `existsByEmail returns false when no teacher has the email`() {
        assertFalse(teacherRepository.existsByEmail("nobody@tr.ke"))
    }

    @Test
    fun `existsByEmail returns true after teacher with that email is created`() {
        trackSchool("tr-school-7")
        saveSchool("tr-school-7")
        teacherRepository.createTeacher(
            "tr-school-7",
            Teacher(name = "Ms Wanjiku", email = "wanjiku@tr.ke", subjectIds = listOf(subjectId1)),
        )

        assertTrue(teacherRepository.existsByEmail("wanjiku@tr.ke"))
    }

    // ── fetchTeacher ──────────────────────────────────────────────────────────

    @Test
    fun `fetchTeacher returns null when id does not exist`() {
        assertNull(teacherRepository.fetchTeacher("00000000-0000-0000-0000-000000000000"))
    }

    @Test
    fun `fetchTeacher returns the teacher with subjects by id`() {
        trackSchool("tr-school-8")
        saveSchool("tr-school-8")
        val created = teacherRepository.createTeacher(
            "tr-school-8",
            Teacher(name = "Ms Odhiambo", email = "odhiambo@tr.ke", subjectIds = listOf(subjectId1)),
        )!!

        val fetched = teacherRepository.fetchTeacher(created.id!!)

        assertNotNull(fetched)
        assertEquals(created.id, fetched!!.id)
        assertEquals("Ms Odhiambo", fetched.name)
        assertEquals(1, fetched.subjectIds?.size)
    }

    // ── listTeachers ──────────────────────────────────────────────────────────

    @Test
    fun `listTeachers returns empty list when school has no teachers`() {
        trackSchool("tr-school-9")
        saveSchool("tr-school-9")

        assertTrue(teacherRepository.listTeachers("tr-school-9").isEmpty())
    }

    @Test
    fun `listTeachers returns all teachers for the school`() {
        trackSchool("tr-school-10")
        saveSchool("tr-school-10")
        teacherRepository.createTeacher("tr-school-10", Teacher(name = "Ms Alpha", email = "alpha@tr.ke", subjectIds = listOf(subjectId1)))
        teacherRepository.createTeacher("tr-school-10", Teacher(name = "Mr Beta", email = "beta@tr.ke", subjectIds = listOf(subjectId2)))

        assertEquals(2, teacherRepository.listTeachers("tr-school-10").size)
    }

    @Test
    fun `listTeachers does not return teachers belonging to a different school`() {
        trackSchool("tr-school-11a", "tr-school-11b")
        saveSchool("tr-school-11a")
        saveSchool("tr-school-11b")
        teacherRepository.createTeacher("tr-school-11a", Teacher(name = "Ms Solo", email = "solo@tr.ke", subjectIds = listOf(subjectId1)))
        teacherRepository.createTeacher("tr-school-11b", Teacher(name = "Mr Other", email = "other@tr.ke", subjectIds = listOf(subjectId1)))

        val teachers = teacherRepository.listTeachers("tr-school-11a")

        assertEquals(1, teachers.size)
        assertEquals("Ms Solo", teachers.first().name)
    }

    @Test
    fun `listTeachers returns teachers ordered by name`() {
        trackSchool("tr-school-12")
        saveSchool("tr-school-12")
        teacherRepository.createTeacher("tr-school-12", Teacher(name = "Zebra Teacher", email = "zebra@tr.ke", subjectIds = listOf(subjectId1)))
        teacherRepository.createTeacher("tr-school-12", Teacher(name = "Apple Teacher", email = "apple@tr.ke", subjectIds = listOf(subjectId1)))

        val names = teacherRepository.listTeachers("tr-school-12").map { it.name }

        assertEquals(listOf("Apple Teacher", "Zebra Teacher"), names)
    }

    @Test
    fun `listTeachers populates subjectIds for each teacher`() {
        trackSchool("tr-school-13")
        saveSchool("tr-school-13")
        teacherRepository.createTeacher(
            "tr-school-13",
            Teacher(name = "Ms Subjects", email = "subjects@tr.ke", subjectIds = listOf(subjectId1, subjectId2)),
        )

        val teacher = teacherRepository.listTeachers("tr-school-13").first()

        assertEquals(2, teacher.subjectIds?.size)
    }
}