package com.grandtech.service.stream

import com.grandtech.model.Room
import com.grandtech.model.School
import com.grandtech.model.Stream
import com.grandtech.model.Teacher
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Integration tests for [com.grandtech.service.StreamService.listStreams].
 *
 * Repository-level ordering and relationship hydration are exercised by [ListStreamsRepoTest].
 * These tests focus on the service contract: the response envelope and the passthrough
 * to the repository.
 */
@QuarkusTest
class ListStreamsServiceTest : StreamServiceTestBase() {

    @Test
    fun `listStreams returns 200 with empty list when school has no streams`() {
        trackSchool("lss-school-1")
        userRepository.saveSchool(School(fedUid = "lss-school-1"))

        val response = streamService.listStreams("lss-school-1")

        assertEquals(200, response.status)
        assertNotNull(response.payload)
        assertEquals(0, response.payload!!.size)
    }

    @Test
    fun `listStreams returns 200 with all created streams`() {
        trackSchool("lss-school-2")
        userRepository.saveSchool(School(fedUid = "lss-school-2"))
        upsertStream("lss-school-2", Stream(gradeLevel = 7, name = "Blue"))
        upsertStream("lss-school-2", Stream(gradeLevel = 8, name = "Red"))

        val response = streamService.listStreams("lss-school-2")

        assertEquals(200, response.status)
        assertEquals(2, response.payload!!.size)
    }

    @Test
    fun `listStreams response includes scalar fields`() {
        trackSchool("lss-school-3")
        userRepository.saveSchool(School(fedUid = "lss-school-3"))
        upsertStream("lss-school-3", Stream(gradeLevel = 9, name = "Gold", studentCount = 28, reStrand = "IRE"))

        val stream = streamService.listStreams("lss-school-3").payload!!.first()

        assertNotNull(stream.id)
        assertEquals(9, stream.gradeLevel)
        assertEquals("Gold", stream.name)
        assertEquals(28, stream.studentCount)
        assertEquals("IRE", stream.reStrand)
    }

    @Test
    fun `listStreams response includes homeRoom when present`() {
        trackSchool("lss-school-4")
        userRepository.saveSchool(School(fedUid = "lss-school-4"))
        val room = roomService.createRoom("lss-school-4", Room(name = "Room 4", capacity = 35, isStandardClassroom = true))
        upsertStream("lss-school-4", Stream(gradeLevel = 7, name = "Silver", homeRoom = Room(id = room!!.id)))

        val stream = streamService.listStreams("lss-school-4").payload!!.first()

        assertNotNull(stream.homeRoom)
        assertEquals(room.id, stream.homeRoom?.id)
    }

    @Test
    fun `listStreams response has null homeRoom when stream has no room`() {
        trackSchool("lss-school-5")
        userRepository.saveSchool(School(fedUid = "lss-school-5"))
        upsertStream("lss-school-5", Stream(gradeLevel = 8, name = "Bronze"))

        val stream = streamService.listStreams("lss-school-5").payload!!.first()

        assertNull(stream.homeRoom)
    }

    @Test
    fun `listStreams response includes formTeacher when present`() {
        trackSchool("lss-school-6")
        userRepository.saveSchool(School(fedUid = "lss-school-6"))
        val teacher = createTeacher("lss-school-6", "Mrs Wanjiku", "wanjiku@lss.ke")
        upsertStream("lss-school-6", Stream(gradeLevel = 7, name = "Jade", formTeacher = Teacher(id = teacher.id)))

        val stream = streamService.listStreams("lss-school-6").payload!!.first()

        assertNotNull(stream.formTeacher)
        assertEquals(teacher.id, stream.formTeacher?.id)
        assertEquals("Mrs Wanjiku", stream.formTeacher?.name)
    }
}