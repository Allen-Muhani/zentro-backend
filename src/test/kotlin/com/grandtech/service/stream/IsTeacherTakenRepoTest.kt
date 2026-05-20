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
        trackSchool("itt-school-1")
        userRepository.saveSchool(School(fedUid = "itt-school-1"))
        val teacher = createTeacher("itt-school-1", "Ms Wanjiku", "wanjiku@itt.ke")

        assertFalse(streamRepository.isTeacherTaken(teacher.id!!))
    }

    @Test
    fun `isTeacherTaken returns true when teacher is a form teacher for a stream`() {
        trackSchool("itt-school-2")
        userRepository.saveSchool(School(fedUid = "itt-school-2"))
        val teacher = createTeacher("itt-school-2", "Mr Otieno", "otieno@itt.ke")
        upsertStream(
            "itt-school-2",
            Stream(gradeLevel = 7, name = "Green", formTeacher = Teacher(id = teacher.id)),
        )

        assertTrue(streamRepository.isTeacherTaken(teacher.id!!))
    }

    @Test
    fun `isTeacherTaken returns false when the only assignment belongs to the excluded stream`() {
        trackSchool("itt-school-3")
        userRepository.saveSchool(School(fedUid = "itt-school-3"))
        val teacher = createTeacher("itt-school-3", "Ms Achieng", "achieng@itt.ke")
        val stream = upsertStream(
            "itt-school-3",
            Stream(gradeLevel = 9, name = "Yellow", formTeacher = Teacher(id = teacher.id)),
        )

        assertFalse(streamRepository.isTeacherTaken(teacher.id!!, excludeStreamId = stream.id))
    }

    @Test
    fun `isTeacherTaken returns true when teacher is taken by a different stream even with excludeStreamId`() {
        trackSchool("itt-school-4")
        userRepository.saveSchool(School(fedUid = "itt-school-4"))
        val teacher = createTeacher("itt-school-4", "Mr Kamau", "kamau@itt.ke")
        upsertStream(
            "itt-school-4",
            Stream(gradeLevel = 7, name = "Blue", formTeacher = Teacher(id = teacher.id)),
        )
        val otherStream = upsertStream("itt-school-4", Stream(gradeLevel = 8, name = "Red"))

        assertTrue(streamRepository.isTeacherTaken(teacher.id!!, excludeStreamId = otherStream.id))
    }
}