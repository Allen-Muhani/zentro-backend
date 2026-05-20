package com.grandtech.service.teacher

import com.grandtech.model.Teacher
import com.grandtech.service.CbcDataSeeder
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Integration tests for [com.grandtech.repository.TeacherRepository.deleteTeacher].
 *
 * Each test verifies the Neo4j graph state directly after deletion so we can confirm
 * the node and its relationships are truly gone.
 */
@QuarkusTest
class DeleteTeacherRepoTest : TeacherServiceTestBase() {

    private val subjectId1 = CbcDataSeeder.SUBJECTS[0].id
    private val subjectId2 = CbcDataSeeder.SUBJECTS[1].id

    private fun createTeacher(schoolFedUid: String, name: String, email: String): Teacher =
        checkNotNull(
            teacherRepository.createTeacher(
                schoolFedUid,
                Teacher(name = name, email = email, subjectIds = listOf(subjectId1)),
            ),
        )

    // ── deleteTeacher ─────────────────────────────────────────────────────────

    @Test
    fun `deleteTeacher returns false when teacher id does not exist`() {
        trackSchool("dtr-school-1")
        saveSchool("dtr-school-1")

        val deleted = teacherRepository.deleteTeacher("dtr-school-1", "00000000-0000-0000-0000-000000000000")

        assertFalse(deleted)
    }

    @Test
    fun `deleteTeacher returns false when teacher belongs to a different school`() {
        trackSchool("dtr-school-2a", "dtr-school-2b")
        saveSchool("dtr-school-2a")
        saveSchool("dtr-school-2b")
        val teacher = createTeacher("dtr-school-2a", "Ms Njeri", "njeri@dtr.ke")

        val deleted = teacherRepository.deleteTeacher("dtr-school-2b", teacher.id!!)

        assertFalse(deleted)
    }

    @Test
    fun `deleteTeacher returns true when teacher exists and belongs to the school`() {
        trackSchool("dtr-school-3")
        saveSchool("dtr-school-3")
        val teacher = createTeacher("dtr-school-3", "Mr Otieno", "otieno@dtr.ke")

        val deleted = teacherRepository.deleteTeacher("dtr-school-3", teacher.id!!)

        assertTrue(deleted)
    }

    @Test
    fun `deleted teacher node no longer exists in the graph`() {
        trackSchool("dtr-school-4")
        saveSchool("dtr-school-4")
        val teacher = createTeacher("dtr-school-4", "Ms Kamau", "kamau@dtr.ke")

        teacherRepository.deleteTeacher("dtr-school-4", teacher.id!!)

        val found = teacherRepository.fetchTeacher(teacher.id)
        assertNull(found)
    }

    @Test
    fun `TEACHES relationships are removed after deletion`() {
        trackSchool("dtr-school-5")
        saveSchool("dtr-school-5")
        val teacher = checkNotNull(
            teacherRepository.createTeacher(
                "dtr-school-5",
                Teacher(name = "Ms Auma", email = "auma@dtr.ke", subjectIds = listOf(subjectId1, subjectId2)),
            ),
        )

        teacherRepository.deleteTeacher("dtr-school-5", teacher.id!!)

        val relCount = driver.session().use { session ->
            session.run(
                "MATCH (t:Teacher {id: \$id})-[:TEACHES]->() RETURN count(*) AS cnt",
                mapOf("id" to teacher.id),
            ).single()["cnt"].asInt()
        }
        assertTrue(relCount == 0)
    }

    @Test
    fun `HAS_TEACHER relationship is removed after deletion`() {
        trackSchool("dtr-school-6")
        saveSchool("dtr-school-6")
        val teacher = createTeacher("dtr-school-6", "Ms Wanjiku", "wanjiku@dtr.ke")

        teacherRepository.deleteTeacher("dtr-school-6", teacher.id!!)

        val relCount = driver.session().use { session ->
            session.run(
                """
                MATCH (:School {fedUid: ${'$'}fedUid})-[:HAS_TEACHER]->(t:Teacher {id: ${'$'}id})
                RETURN count(*) AS cnt
                """.trimIndent(),
                mapOf("fedUid" to "dtr-school-6", "id" to teacher.id),
            ).single()["cnt"].asInt()
        }
        assertTrue(relCount == 0)
    }

    @Test
    fun `deleting one teacher does not affect other teachers in the same school`() {
        trackSchool("dtr-school-7")
        saveSchool("dtr-school-7")
        val teacher1 = createTeacher("dtr-school-7", "Ms Alpha", "alpha@dtr.ke")
        val teacher2 = createTeacher("dtr-school-7", "Mr Beta", "beta@dtr.ke")

        teacherRepository.deleteTeacher("dtr-school-7", teacher1.id!!)

        val remaining = teacherRepository.listTeachers("dtr-school-7")
        assertTrue(remaining.size == 1)
        assertTrue(remaining.first().id == teacher2.id)
    }

    @Test
    fun `second delete of the same id returns false`() {
        trackSchool("dtr-school-8")
        saveSchool("dtr-school-8")
        val teacher = createTeacher("dtr-school-8", "Ms Solo", "solo@dtr.ke")
        teacherRepository.deleteTeacher("dtr-school-8", teacher.id!!)

        val secondDelete = teacherRepository.deleteTeacher("dtr-school-8", teacher.id)

        assertFalse(secondDelete)
    }
}