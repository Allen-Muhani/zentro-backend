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
 * Integration tests for the update path of [com.grandtech.service.StreamService.upsertStream]
 * (i.e. when [Stream.id] is provided).
 */
@QuarkusTest
class UpsertStreamUpdateTest : StreamServiceTestBase() {

    @Test
    fun `upsertStream returns 200 with updated message on update`() {
        trackSchool("usu-school-0")
        userRepository.saveSchool(School(fedUid = "usu-school-0"))
        val created = upsertStream("usu-school-0", Stream(gradeLevel = 7, name = "Blue"))

        val response = streamService.upsertStream("usu-school-0", Stream(id = created.id, name = "White"))

        assertEquals(200, response.status)
        assertEquals("Stream updated", response.message)
        assertNotNull(response.payload)
    }

    @Test
    fun `upsertStream updates scalar fields when id is supplied`() {
        trackSchool("usu-school-1")
        userRepository.saveSchool(School(fedUid = "usu-school-1"))
        val created = upsertStream("usu-school-1", Stream(gradeLevel = 7, name = "Blue", studentCount = 20, reStrand = "CRE"))

        val updated = upsertStream(
            "usu-school-1",
            Stream(id = created.id, gradeLevel = 8, name = "White", studentCount = 35, reStrand = "IRE"),
        )

        assertEquals(created.id, updated.id)
        assertEquals(8, updated.gradeLevel)
        assertEquals("White", updated.name)
        assertEquals(35, updated.studentCount)
        assertEquals("IRE", updated.reStrand)
    }

    @Test
    fun `upsertStream leaves null scalar fields unchanged`() {
        trackSchool("usu-school-2")
        userRepository.saveSchool(School(fedUid = "usu-school-2"))
        val created = upsertStream("usu-school-2", Stream(gradeLevel = 7, name = "Red", studentCount = 28, reStrand = "HRE"))

        val updated = upsertStream("usu-school-2", Stream(id = created.id, name = "Renamed"))

        assertEquals("Renamed", updated.name)
        assertEquals(7, updated.gradeLevel)
        assertEquals(28, updated.studentCount)
        assertEquals("HRE", updated.reStrand)
    }

    @Test
    fun `upsertStream does not change the stream id`() {
        trackSchool("usu-school-3")
        userRepository.saveSchool(School(fedUid = "usu-school-3"))
        val created = upsertStream("usu-school-3", Stream(gradeLevel = 9, name = "Green"))

        val updated = upsertStream("usu-school-3", Stream(id = created.id, name = "Updated"))

        assertEquals(created.id, updated.id)
    }

    @Test
    fun `upsertStream replaces home room when a new room is provided`() {
        trackSchool("usu-school-4")
        userRepository.saveSchool(School(fedUid = "usu-school-4"))
        val roomA = roomService.createRoom("usu-school-4", Room(name = "Room A", capacity = 30, isStandardClassroom = true))
        val roomB = roomService.createRoom("usu-school-4", Room(name = "Room B", capacity = 25, isStandardClassroom = true))
        val created = upsertStream("usu-school-4", Stream(gradeLevel = 7, name = "Blue", homeRoom = Room(id = roomA!!.id)))

        val updated = upsertStream("usu-school-4", Stream(id = created.id, homeRoom = Room(id = roomB!!.id)))

        assertEquals(roomB.id, updated.homeRoom?.id)
        assertEquals("Room B", updated.homeRoom?.name)
    }

    @Test
    fun `upsertStream keeps existing home room when homeRoom is not provided`() {
        trackSchool("usu-school-5")
        userRepository.saveSchool(School(fedUid = "usu-school-5"))
        val room = roomService.createRoom("usu-school-5", Room(name = "Room C", capacity = 30, isStandardClassroom = true))
        val created = upsertStream("usu-school-5", Stream(gradeLevel = 8, name = "White", homeRoom = Room(id = room!!.id)))

        val updated = upsertStream("usu-school-5", Stream(id = created.id, name = "Renamed"))

        assertEquals(room.id, updated.homeRoom?.id)
    }

    @Test
    fun `upsertStream replaces form teacher when a new teacher is provided`() {
        trackSchool("usu-school-6")
        userRepository.saveSchool(School(fedUid = "usu-school-6"))
        val teacherA = createTeacher("usu-school-6", "Ms Auma", "auma@usu.ke")
        val teacherB = createTeacher("usu-school-6", "Mr Mwangi", "mwangi@usu.ke")
        val created = upsertStream(
            "usu-school-6",
            Stream(gradeLevel = 7, name = "Red", formTeacher = Teacher(id = teacherA.id)),
        )

        val updated = upsertStream(
            "usu-school-6",
            Stream(id = created.id, formTeacher = Teacher(id = teacherB.id)),
        )

        assertEquals(teacherB.id, updated.formTeacher?.id)
        assertEquals("Mr Mwangi", updated.formTeacher?.name)
    }

    @Test
    fun `upsertStream keeps existing form teacher when formTeacher is not provided`() {
        trackSchool("usu-school-7")
        userRepository.saveSchool(School(fedUid = "usu-school-7"))
        val teacher = createTeacher("usu-school-7", "Ms Chebet", "chebet@usu.ke")
        val created = upsertStream(
            "usu-school-7",
            Stream(gradeLevel = 9, name = "Gold", formTeacher = Teacher(id = teacher.id)),
        )

        val updated = upsertStream("usu-school-7", Stream(id = created.id, name = "Platinum"))

        assertEquals(teacher.id, updated.formTeacher?.id)
    }

    @Test
    fun `upsertStream returns 404 when stream id does not belong to the school`() {
        trackSchool("usu-school-8a", "usu-school-8b")
        userRepository.saveSchool(School(fedUid = "usu-school-8a"))
        userRepository.saveSchool(School(fedUid = "usu-school-8b"))
        val stream = upsertStream("usu-school-8a", Stream(gradeLevel = 7, name = "Blue"))

        val response = streamService.upsertStream("usu-school-8b", Stream(id = stream.id, name = "Hijacked"))

        assertEquals(404, response.status)
        assertNull(response.payload)
    }

    @Test
    fun `upsertStream returns 409 when room is already assigned to another stream`() {
        trackSchool("usu-school-9")
        userRepository.saveSchool(School(fedUid = "usu-school-9"))
        val room = roomService.createRoom("usu-school-9", Room(name = "Taken Room", capacity = 30, isStandardClassroom = true))
        upsertStream("usu-school-9", Stream(gradeLevel = 7, name = "Blue", homeRoom = Room(id = room!!.id)))
        val other = upsertStream("usu-school-9", Stream(gradeLevel = 8, name = "Red"))

        val response = streamService.upsertStream(
            "usu-school-9",
            Stream(id = other.id, homeRoom = Room(id = room.id)),
        )

        assertEquals(409, response.status)
        assertEquals("Room is already assigned to another stream", response.message)
        assertNull(response.payload)
    }

    @Test
    fun `upsertStream returns 409 when teacher is already assigned to another stream`() {
        trackSchool("usu-school-10")
        userRepository.saveSchool(School(fedUid = "usu-school-10"))
        val teacher = createTeacher("usu-school-10", "Mr Kiprop", "kiprop@usu.ke")
        upsertStream(
            "usu-school-10",
            Stream(gradeLevel = 7, name = "Blue", formTeacher = Teacher(id = teacher.id)),
        )
        val other = upsertStream("usu-school-10", Stream(gradeLevel = 8, name = "Red"))

        val response = streamService.upsertStream(
            "usu-school-10",
            Stream(id = other.id, formTeacher = Teacher(id = teacher.id)),
        )

        assertEquals(409, response.status)
        assertEquals("Teacher is already a form teacher for another stream", response.message)
        assertNull(response.payload)
    }

    @Test
    fun `upsertStream returns 400 when gradeLevel is invalid on update`() {
        trackSchool("usu-school-11")
        userRepository.saveSchool(School(fedUid = "usu-school-11"))
        val created = upsertStream("usu-school-11", Stream(gradeLevel = 7, name = "Blue"))

        val response = streamService.upsertStream("usu-school-11", Stream(id = created.id, gradeLevel = 10))

        assertEquals(400, response.status)
        assertEquals("gradeLevel must be 7, 8, or 9", response.message)
    }
}
