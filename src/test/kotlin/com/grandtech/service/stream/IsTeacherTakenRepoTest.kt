package com.grandtech.service.stream

import com.grandtech.model.School
import com.grandtech.model.Stream
import com.grandtech.model.Teacher
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Integration tests for [com.grandtech.repository.StreamRepository.isTeacherTaken].
 */
@QuarkusTest
class IsTeacherTakenRepoTest : StreamServiceTestBase() {

    @Test
    fun `isTeacherTaken returns false when teacher is not assigned to any stream`() {
        trackTeacher("itt-teacher-1")
        userRepository.saveTeacher(Teacher(fedUid = "itt-teacher-1", name = "Ms Wanjiku"))

        assertFalse(streamRepository.isTeacherTaken("itt-teacher-1"))
    }

    @Test
    fun `isTeacherTaken returns true when teacher is a form teacher for a stream`() {
        trackSchool("itt-school-2")
        trackTeacher("itt-teacher-2")
        userRepository.saveSchool(School(fedUid = "itt-school-2"))
        userRepository.saveTeacher(Teacher(fedUid = "itt-teacher-2", name = "Mr Otieno"))
        upsertStream(
            "itt-school-2",
            Stream(gradeLevel = 7, name = "Green", formTeacher = Teacher(fedUid = "itt-teacher-2")),
        )

        assertTrue(streamRepository.isTeacherTaken("itt-teacher-2"))
    }

    @Test
    fun `isTeacherTaken returns false when the only assignment belongs to the excluded stream`() {
        trackSchool("itt-school-3")
        trackTeacher("itt-teacher-3")
        userRepository.saveSchool(School(fedUid = "itt-school-3"))
        userRepository.saveTeacher(Teacher(fedUid = "itt-teacher-3", name = "Ms Achieng"))
        val stream = upsertStream(
            "itt-school-3",
            Stream(gradeLevel = 9, name = "Yellow", formTeacher = Teacher(fedUid = "itt-teacher-3")),
        )

        assertFalse(streamRepository.isTeacherTaken("itt-teacher-3", excludeStreamId = stream.id))
    }

    @Test
    fun `isTeacherTaken returns true when teacher is taken by a different stream even with excludeStreamId`() {
        trackSchool("itt-school-4")
        trackTeacher("itt-teacher-4")
        userRepository.saveSchool(School(fedUid = "itt-school-4"))
        userRepository.saveTeacher(Teacher(fedUid = "itt-teacher-4", name = "Mr Kamau"))
        upsertStream(
            "itt-school-4",
            Stream(gradeLevel = 7, name = "Blue", formTeacher = Teacher(fedUid = "itt-teacher-4")),
        )
        val otherStream = upsertStream("itt-school-4", Stream(gradeLevel = 8, name = "Red"))

        assertTrue(streamRepository.isTeacherTaken("itt-teacher-4", excludeStreamId = otherStream.id))
    }
}