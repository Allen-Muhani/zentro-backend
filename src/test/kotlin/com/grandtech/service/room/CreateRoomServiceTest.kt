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
 * Integration tests for [com.grandtech.service.RoomService.createRoom].
 */
@QuarkusTest
class CreateRoomServiceTest : RoomServiceTestBase() {

    @Test
    fun `createRoom returns created room with generated id`() {
        track("cr-school-1")
        userRepository.saveSchool(School(fedUid = "cr-school-1"))

        val result = roomService.createRoom(
            "cr-school-1",
            Room(name = "Science Lab 1", capacity = 30, isStandardClassroom = false),
        )

        assertNotNull(result)
        assertNotNull(result?.id)
        assertEquals("Science Lab 1", result?.name)
        assertEquals(30, result?.capacity)
        assertEquals(false, result?.isStandardClassroom)
    }

    @Test
    fun `createRoom stores capability tag correctly`() {
        track("cr-school-2")
        userRepository.saveSchool(School(fedUid = "cr-school-2"))

        val result = roomService.createRoom(
            "cr-school-2",
            Room(name = "Computer Lab", capacity = 40, capabilityTag = RoomCapabilityTag.COMPUTER_LAB, isStandardClassroom = false),
        )

        assertNotNull(result)
        assertEquals(RoomCapabilityTag.COMPUTER_LAB, result?.capabilityTag)
    }

    @Test
    fun `createRoom links room to school in the database`() {
        track("cr-school-3")
        userRepository.saveSchool(School(fedUid = "cr-school-3"))

        val result = roomService.createRoom(
            "cr-school-3",
            Room(name = "Workshop A", capacity = 25, isStandardClassroom = false),
        )

        assertNotNull(result?.id)
        val linked = driver.session().use { session ->
            session.run(
                """
                MATCH (s:School {fedUid: ${'$'}fedUid})-[:HAS_ROOM]->(r:Room {id: ${'$'}roomId})
                RETURN count(r) > 0 AS exists
                """.trimIndent(),
                mapOf("fedUid" to "cr-school-3", "roomId" to result!!.id!!),
            ).single()["exists"].asBoolean()
        }
        assertEquals(true, linked)
    }

    @Test
    fun `createRoom returns null when school does not exist`() {
        val result = roomService.createRoom(
            "non-existent-school",
            Room(name = "Room X", capacity = 20, isStandardClassroom = true),
        )

        assertNull(result)
    }
}