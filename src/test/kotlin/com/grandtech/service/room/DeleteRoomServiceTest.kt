package com.grandtech.service.room

import com.grandtech.model.Room
import com.grandtech.model.School
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Integration tests for [com.grandtech.service.RoomService.deleteRoom].
 */
@QuarkusTest
class DeleteRoomServiceTest : RoomServiceTestBase() {

    @Test
    fun `deleteRoom returns true and removes the room node`() {
        track("dr-school-1")
        userRepository.saveSchool(School(fedUid = "dr-school-1"))
        val room = roomService.createRoom(
            "dr-school-1",
            Room(name = "Room To Delete", capacity = 20, isStandardClassroom = true),
        )

        val deleted = roomService.deleteRoom("dr-school-1", room!!.id!!)

        assertTrue(deleted)
        val exists = driver.session().use { session ->
            session.run(
                "MATCH (r:Room {id: \$id}) RETURN count(r) > 0 AS exists",
                mapOf("id" to room.id),
            ).single()["exists"].asBoolean()
        }
        assertFalse(exists)
    }

    @Test
    fun `deleteRoom removes the HAS_ROOM relationship`() {
        track("dr-school-2")
        userRepository.saveSchool(School(fedUid = "dr-school-2"))
        val room = roomService.createRoom(
            "dr-school-2",
            Room(name = "Linked Room", capacity = 30, isStandardClassroom = true),
        )

        roomService.deleteRoom("dr-school-2", room!!.id!!)

        val relationshipExists = driver.session().use { session ->
            session.run(
                """
                MATCH (s:School {fedUid: ${'$'}fedUid})-[:HAS_ROOM]->(r:Room {id: ${'$'}roomId})
                RETURN count(r) > 0 AS exists
                """.trimIndent(),
                mapOf("fedUid" to "dr-school-2", "roomId" to room.id),
            ).single()["exists"].asBoolean()
        }
        assertFalse(relationshipExists)
    }

    @Test
    fun `deleteRoom returns false when room does not exist`() {
        track("dr-school-3")
        userRepository.saveSchool(School(fedUid = "dr-school-3"))

        val deleted = roomService.deleteRoom("dr-school-3", "non-existent-room-id")

        assertFalse(deleted)
    }

    @Test
    fun `deleteRoom returns false when room belongs to a different school`() {
        track("dr-school-4", "dr-school-5")
        userRepository.saveSchool(School(fedUid = "dr-school-4"))
        userRepository.saveSchool(School(fedUid = "dr-school-5"))
        val room = roomService.createRoom(
            "dr-school-4",
            Room(name = "School 4 Room", capacity = 20, isStandardClassroom = true),
        )

        val deleted = roomService.deleteRoom("dr-school-5", room!!.id!!)

        assertFalse(deleted)
        assertEquals(1, roomService.listRooms("dr-school-4").size)
    }
}