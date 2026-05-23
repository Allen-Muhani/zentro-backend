package com.grandtech.service.stream

import com.grandtech.model.School
import com.grandtech.model.Stream
import com.grandtech.model.Teacher
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Integration tests for [com.grandtech.repository.StreamRepository.listStreams].
 *
 * Each test provisions its own isolated school and streams so runs are independent.
 * Relationship hydration (formTeacher) is tested here because the repository
 * owns the Cypher query that joins Teacher nodes.
 */
@QuarkusTest
class ListStreamsRepoTest : StreamServiceTestBase() {

    @Test
    fun `listStreams returns empty list when school has no streams`() {
        trackSchool("lsr-school-1")
        userRepository.saveSchool(School(fedUid = "lsr-school-1"))

        val result = streamRepository.listStreams("lsr-school-1")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `listStreams returns all created streams for a school`() {
        trackSchool("lsr-school-2")
        userRepository.saveSchool(School(fedUid = "lsr-school-2"))
        upsertStream("lsr-school-2", Stream(gradeLevel = 7, name = "Blue"))
        upsertStream("lsr-school-2", Stream(gradeLevel = 8, name = "Red"))

        val ids = streamRepository.listStreams("lsr-school-2").map { it.id }.toSet()

        assertEquals(2, ids.size)
    }

    @Test
    fun `listStreams results are ordered by gradeLevel then name`() {
        trackSchool("lsr-school-3")
        userRepository.saveSchool(School(fedUid = "lsr-school-3"))
        upsertStream("lsr-school-3", Stream(gradeLevel = 8, name = "Zebra"))
        upsertStream("lsr-school-3", Stream(gradeLevel = 7, name = "Alpha"))
        upsertStream("lsr-school-3", Stream(gradeLevel = 8, name = "Apple"))

        val streams = streamRepository.listStreams("lsr-school-3")

        assertEquals(7, streams[0].gradeLevel)
        assertEquals("Apple", streams[1].name)
        assertEquals("Zebra", streams[2].name)
    }

    @Test
    fun `listStreams hydrates formTeacher from FORM_TEACHER relationship`() {
        trackSchool("lsr-school-5")
        userRepository.saveSchool(School(fedUid = "lsr-school-5"))
        val teacher = createTeacher("lsr-school-5", "Mr Kamau", "kamau@school.ke")
        upsertStream("lsr-school-5", Stream(gradeLevel = 9, name = "Purple", formTeacher = Teacher(id = teacher.id)))

        val stream = streamRepository.listStreams("lsr-school-5").first()

        assertNotNull(stream.formTeacher)
        assertEquals(teacher.id, stream.formTeacher?.id)
        assertEquals("Mr Kamau", stream.formTeacher?.name)
        assertEquals("kamau@school.ke", stream.formTeacher?.email)
    }

    @Test
    fun `listStreams does not return streams belonging to a different school`() {
        trackSchool("lsr-school-7a", "lsr-school-7b")
        userRepository.saveSchool(School(fedUid = "lsr-school-7a"))
        userRepository.saveSchool(School(fedUid = "lsr-school-7b"))
        upsertStream("lsr-school-7a", Stream(gradeLevel = 7, name = "Stream A"))
        upsertStream("lsr-school-7b", Stream(gradeLevel = 7, name = "Stream B"))

        val streams = streamRepository.listStreams("lsr-school-7a")

        assertEquals(1, streams.size)
        assertEquals("Stream A", streams[0].name)
    }
}