package com.grandtech.service.stream

import com.grandtech.model.Room
import com.grandtech.model.School
import com.grandtech.model.Stream
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Integration tests for [com.grandtech.repository.StreamRepository.isRoomTaken].
 */
@QuarkusTest
class IsRoomTakenRepoTest : StreamServiceTestBase() {

    @Test
    fun `isRoomTaken returns false when room has no stream assigned`() {
        trackSchool("irt-school-1")
        userRepository.saveSchool(School(fedUid = "irt-school-1"))
        val room = roomService.createRoom("irt-school-1", Room(name = "Lab A", capacity = 30, isStandardClassroom = false))

        assertFalse(streamRepository.isRoomTaken(room!!.id!!))
    }

    @Test
    fun `isRoomTaken returns true when room is assigned to a stream`() {
        trackSchool("irt-school-2")
        userRepository.saveSchool(School(fedUid = "irt-school-2"))
        val room = roomService.createRoom("irt-school-2", Room(name = "Lab B", capacity = 30, isStandardClassroom = true))
        upsertStream("irt-school-2", Stream(gradeLevel = 7, name = "Blue", homeRoom = Room(id = room!!.id)))

        assertTrue(streamRepository.isRoomTaken(room.id!!))
    }

    @Test
    fun `isRoomTaken returns false when the only assignment belongs to the excluded stream`() {
        trackSchool("irt-school-3")
        userRepository.saveSchool(School(fedUid = "irt-school-3"))
        val room = roomService.createRoom("irt-school-3", Room(name = "Lab C", capacity = 30, isStandardClassroom = true))
        val stream = upsertStream("irt-school-3", Stream(gradeLevel = 8, name = "White", homeRoom = Room(id = room!!.id)))

        assertFalse(streamRepository.isRoomTaken(room.id!!, excludeStreamId = stream.id))
    }

    @Test
    fun `isRoomTaken returns true when room is taken by a different stream even with excludeStreamId`() {
        trackSchool("irt-school-4")
        userRepository.saveSchool(School(fedUid = "irt-school-4"))
        val room = roomService.createRoom("irt-school-4", Room(name = "Lab D", capacity = 30, isStandardClassroom = true))
        upsertStream("irt-school-4", Stream(gradeLevel = 7, name = "Red", homeRoom = Room(id = room!!.id)))
        val otherStream = upsertStream("irt-school-4", Stream(gradeLevel = 8, name = "Blue"))

        assertTrue(streamRepository.isRoomTaken(room.id!!, excludeStreamId = otherStream.id))
    }
}