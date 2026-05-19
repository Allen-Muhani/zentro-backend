package com.grandtech.service.room

import com.grandtech.model.Room
import com.grandtech.model.RoomCapabilityTag
import com.grandtech.model.School
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Integration tests for [com.grandtech.service.RoomService.updateRoom].
 */
@QuarkusTest
class UpdateRoomServiceTest : RoomServiceTestBase() {

    @Test
    fun `updateRoom updates all mutable fields`() {
        track("ur-school-1")
        userRepository.saveSchool(School(fedUid = "ur-school-1"))
        val room = roomService.createRoom(
            "ur-school-1",
            Room(name = "Old Name", capacity = 20, isStandardClassroom = true),
        )

        val result = roomService.updateRoom(
            "ur-school-1",
            room!!.id!!,
            Room(name = "New Name", capacity = 50, capabilityTag = RoomCapabilityTag.SCIENCE_LAB, isStandardClassroom = false),
        )

        assertNotNull(result)
        assertEquals("New Name", result?.name)
        assertEquals(50, result?.capacity)
        assertEquals(RoomCapabilityTag.SCIENCE_LAB, result?.capabilityTag)
        assertEquals(false, result?.isStandardClassroom)
    }

    @Test
    fun `updateRoom leaves stored values unchanged when fields are null`() {
        track("ur-school-2")
        userRepository.saveSchool(School(fedUid = "ur-school-2"))
        val room = roomService.createRoom(
            "ur-school-2",
            Room(name = "Keep Name", capacity = 30, isStandardClassroom = true),
        )

        val result = roomService.updateRoom(
            "ur-school-2",
            room!!.id!!,
            Room(capacity = 45),
        )

        assertNotNull(result)
        assertEquals("Keep Name", result?.name)
        assertEquals(45, result?.capacity)
        assertEquals(true, result?.isStandardClassroom)
    }

    @Test
    fun `updateRoom does not change the room id`() {
        track("ur-school-3")
        userRepository.saveSchool(School(fedUid = "ur-school-3"))
        val room = roomService.createRoom(
            "ur-school-3",
            Room(name = "Original", capacity = 20, isStandardClassroom = true),
        )

        val result = roomService.updateRoom(
            "ur-school-3",
            room!!.id!!,
            Room(name = "Updated"),
        )

        assertEquals(room.id, result?.id)
    }

    @Test
    fun `updateRoom returns null when room does not exist`() {
        track("ur-school-4")
        userRepository.saveSchool(School(fedUid = "ur-school-4"))

        val result = roomService.updateRoom(
            "ur-school-4",
            "non-existent-room-id",
            Room(name = "Ghost Room"),
        )

        assertNull(result)
    }

    @Test
    fun `updateRoom returns null when room belongs to a different school`() {
        track("ur-school-5", "ur-school-6")
        userRepository.saveSchool(School(fedUid = "ur-school-5"))
        userRepository.saveSchool(School(fedUid = "ur-school-6"))
        val room = roomService.createRoom(
            "ur-school-5",
            Room(name = "School 5 Room", capacity = 20, isStandardClassroom = true),
        )

        val result = roomService.updateRoom("ur-school-6", room!!.id!!, Room(name = "Hijacked"))

        assertNull(result)
    }
}