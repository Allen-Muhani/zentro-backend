package com.grandtech.repository.subject

import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Integration tests for [com.grandtech.repository.SubjectRepository.listWithTeacherCount].
 *
 * Subjects are identified by IDs prefixed `"T-TC-"` and schools/teachers by UIDs/IDs
 * prefixed `"tc-"` so [cleanUp] can target them without touching seeded data.
 */
@QuarkusTest
class ListWithTeacherCountRepoTest : SubjectRepositoryTestBase() {

    @AfterEach
    fun cleanUpSchoolsAndTeachers() {
        driver.session().use { session ->
            session.run("MATCH (s:School) WHERE s.fedUid STARTS WITH 'tc-' DETACH DELETE s")
            session.run("MATCH (t:Teacher) WHERE t.id STARTS WITH 'tc-' DETACH DELETE t")
        }
    }

    @Test
    fun `all subjects are returned even when school has no teachers`() {
        val results = subjectRepository.listWithTeacherCount("tc-empty-school")
        assertTrue(results.size >= 10)
        assertTrue(results.all { it.teacherCount == 0 })
    }

    @Test
    fun `counts one teacher assigned to a subject`() {
        track("T-TC-1")
        subjectRepository.saveSubject(testSubject("T-TC-1", name = "Zzz Count Subject"))

        driver.session().use { session ->
            session.run(
                """
                MATCH (sub:Subject {id: 'T-TC-1'})
                CREATE (s:User:School {fedUid: 'tc-school-1'})
                CREATE (t:Teacher {id: 'tc-teacher-1', name: 'Teacher One'})
                CREATE (s)-[:HAS_TEACHER]->(t)
                CREATE (t)-[:TEACHES]->(sub)
                """.trimIndent(),
            )
        }

        val result = subjectRepository.listWithTeacherCount("tc-school-1")
        assertEquals(1, result.find { it.subject.id == "T-TC-1" }?.teacherCount)
    }

    @Test
    fun `counts multiple teachers assigned to the same subject`() {
        track("T-TC-2")
        subjectRepository.saveSubject(testSubject("T-TC-2", name = "Zzz Multi Teacher Subject"))

        driver.session().use { session ->
            session.run(
                """
                MATCH (sub:Subject {id: 'T-TC-2'})
                CREATE (s:User:School {fedUid: 'tc-school-2'})
                CREATE (t1:Teacher {id: 'tc-teacher-2a', name: 'Teacher 2A'})
                CREATE (t2:Teacher {id: 'tc-teacher-2b', name: 'Teacher 2B'})
                CREATE (s)-[:HAS_TEACHER]->(t1)
                CREATE (s)-[:HAS_TEACHER]->(t2)
                CREATE (t1)-[:TEACHES]->(sub)
                CREATE (t2)-[:TEACHES]->(sub)
                """.trimIndent(),
            )
        }

        val result = subjectRepository.listWithTeacherCount("tc-school-2")
        assertEquals(2, result.find { it.subject.id == "T-TC-2" }?.teacherCount)
    }

    @Test
    fun `does not count teachers belonging to a different school`() {
        track("T-TC-3")
        subjectRepository.saveSubject(testSubject("T-TC-3", name = "Zzz Isolation Subject"))

        driver.session().use { session ->
            session.run(
                """
                MATCH (sub:Subject {id: 'T-TC-3'})
                CREATE (sa:User:School {fedUid: 'tc-school-3a'})
                CREATE (sb:User:School {fedUid: 'tc-school-3b'})
                CREATE (ta:Teacher {id: 'tc-teacher-3a', name: 'Teacher 3A'})
                CREATE (tb:Teacher {id: 'tc-teacher-3b', name: 'Teacher 3B'})
                CREATE (sa)-[:HAS_TEACHER]->(ta)
                CREATE (sb)-[:HAS_TEACHER]->(tb)
                CREATE (ta)-[:TEACHES]->(sub)
                CREATE (tb)-[:TEACHES]->(sub)
                """.trimIndent(),
            )
        }

        val resultA = subjectRepository.listWithTeacherCount("tc-school-3a")
        assertEquals(1, resultA.find { it.subject.id == "T-TC-3" }?.teacherCount)

        val resultB = subjectRepository.listWithTeacherCount("tc-school-3b")
        assertEquals(1, resultB.find { it.subject.id == "T-TC-3" }?.teacherCount)
    }

    @Test
    fun `unassigned subjects in the same school return teacherCount zero`() {
        track("T-TC-4", "T-TC-5")
        subjectRepository.saveSubject(testSubject("T-TC-4", name = "Zzz Assigned Subject"))
        subjectRepository.saveSubject(testSubject("T-TC-5", name = "Zzz Unassigned Subject"))

        driver.session().use { session ->
            session.run(
                """
                MATCH (sub:Subject {id: 'T-TC-4'})
                CREATE (s:User:School {fedUid: 'tc-school-4'})
                CREATE (t:Teacher {id: 'tc-teacher-4', name: 'Teacher Four'})
                CREATE (s)-[:HAS_TEACHER]->(t)
                CREATE (t)-[:TEACHES]->(sub)
                """.trimIndent(),
            )
        }

        val results = subjectRepository.listWithTeacherCount("tc-school-4")
        assertEquals(1, results.find { it.subject.id == "T-TC-4" }?.teacherCount)
        assertEquals(0, results.find { it.subject.id == "T-TC-5" }?.teacherCount)
    }

    @Test
    fun `results are ordered alphabetically by subject name`() {
        val results = subjectRepository.listWithTeacherCount("tc-empty-school")
        val names = results.map { it.subject.name }
        assertEquals(names.sorted(), names)
    }
}