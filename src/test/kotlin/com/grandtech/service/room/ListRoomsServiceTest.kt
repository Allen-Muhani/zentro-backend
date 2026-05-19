package com.grandtech.service.room

import com.grandtech.model.Room
import com.grandtech.model.School
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Integration tests for [com.grandtech.service.RoomService.listRooms].
 */
@QuarkusTest
class ListRoomsServiceTest : RoomServiceTestBase() {

    @Test
    fun `listRooms returns all rooms belonging to the school`() {
        track("lr-school-1")
        userRepository.saveSchool(School(fedUid = "lr-school-1"))
        roomService.createRoom("lr-school-1", Room(name = "Room A", capacity = 30, isStandardClassroom = true))
        roomService.createRoom("lr-school-1", Room(name = "Room B", capacity = 25, isStandardClassroom = true))
        roomService.createRoom("lr-school-1", Room(name = "Room C", capacity = 20, isStandardClassroom = true))

        val result = roomService.listRooms("lr-school-1")

        assertEquals(3, result.size)
    }

    @Test
    fun `listRooms returns rooms ordered by name`() {
        track("lr-school-2")
        userRepository.saveSchool(School(fedUid = "lr-school-2"))
        roomService.createRoom("lr-school-2", Room(name = "Zebra Room", capacity = 30, isStandardClassroom = true))
        roomService.createRoom("lr-school-2", Room(name = "Apple Room", capacity = 25, isStandardClassroom = true))
        roomService.createRoom("lr-school-2", Room(name = "Mango Room", capacity = 20, isStandardClassroom = true))

        val result = roomService.listRooms("lr-school-2")

        assertEquals(listOf("Apple Room", "Mango Room", "Zebra Room"), result.map { it.name })
    }

    @Test
    fun `listRooms returns empty list when school has no rooms`() {
        track("lr-school-3")
        userRepository.saveSchool(School(fedUid = "lr-school-3"))

        val result = roomService.listRooms("lr-school-3")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `listRooms returns empty list when school does not exist`() {
        val result = roomService.listRooms("non-existent-school")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `listRooms returns only rooms belonging to the requested school`() {
        track("lr-school-4", "lr-school-5")
        userRepository.saveSchool(School(fedUid = "lr-school-4"))
        userRepository.saveSchool(School(fedUid = "lr-school-5"))
        roomService.createRoom("lr-school-4", Room(name = "School 4 Room", capacity = 30, isStandardClassroom = true))
        roomService.createRoom("lr-school-5", Room(name = "School 5 Room", capacity = 30, isStandardClassroom = true))

        val result = roomService.listRooms("lr-school-4")

        assertEquals(1, result.size)
        assertEquals("School 4 Room", result[0].name)
    }
}