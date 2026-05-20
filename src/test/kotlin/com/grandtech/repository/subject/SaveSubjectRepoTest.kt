package com.grandtech.repository.subject

import com.grandtech.model.RoomCapabilityTag
import com.grandtech.model.Subject
import com.grandtech.model.SubjectType
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Integration tests for [com.grandtech.repository.SubjectRepository.saveSubject].
 */
@QuarkusTest
class SaveSubjectRepoTest : SubjectRepositoryTestBase() {

    @Test
    fun `saveSubject creates a node with all scalar fields persisted`() {
        track("T-SAVE-1")
        subjectRepository.saveSubject(
            Subject(
                id = "T-SAVE-1",
                symbol = "TSV",
                name = "Test Save Subject",
                type = SubjectType.SCIENCE,
                periodsPerWeek = 5,
                requiresDoubledPeriod = true,
                requiresSpecialRoom = true,
                roomCapabilityTag = RoomCapabilityTag.SCIENCE_LAB,
                isPpiFixed = false,
                preferBeforeBreak = false,
                maxPeriodsPerDay = 2,
            ),
        )

        val saved = subjectRepository.listAll().first { it.id == "T-SAVE-1" }
        assertEquals("TSV", saved.symbol)
        assertEquals("Test Save Subject", saved.name)
        assertEquals(SubjectType.SCIENCE, saved.type)
        assertEquals(5, saved.periodsPerWeek)
        assertEquals(true, saved.requiresDoubledPeriod)
        assertEquals(true, saved.requiresSpecialRoom)
        assertEquals(RoomCapabilityTag.SCIENCE_LAB, saved.roomCapabilityTag)
        assertEquals(2, saved.maxPeriodsPerDay)
    }

    @Test
    fun `saveSubject stores null roomCapabilityTag correctly`() {
        track("T-SAVE-2")
        subjectRepository.saveSubject(testSubject("T-SAVE-2"))

        val saved = subjectRepository.listAll().first { it.id == "T-SAVE-2" }
        assertNull(saved.roomCapabilityTag)
    }

    @Test
    fun `saveSubject stores non-null roomCapabilityTag correctly`() {
        track("T-SAVE-3")
        subjectRepository.saveSubject(
            testSubject("T-SAVE-3").copy(
                requiresSpecialRoom = true,
                roomCapabilityTag = RoomCapabilityTag.WORKSHOP,
            ),
        )

        val saved = subjectRepository.listAll().first { it.id == "T-SAVE-3" }
        assertEquals(RoomCapabilityTag.WORKSHOP, saved.roomCapabilityTag)
    }

    @Test
    fun `saveSubject is idempotent and overwrites fields on re-save`() {
        track("T-SAVE-4")
        subjectRepository.saveSubject(testSubject("T-SAVE-4", name = "Original Name", periodsPerWeek = 3))
        subjectRepository.saveSubject(testSubject("T-SAVE-4", name = "Updated Name", periodsPerWeek = 5))

        val results = subjectRepository.listAll().filter { it.id == "T-SAVE-4" }
        assertEquals(1, results.size)
        assertEquals("Updated Name", results.first().name)
        assertEquals(5, results.first().periodsPerWeek)
    }

    @Test
    fun `saveSubject persists boolean flags correctly`() {
        track("T-SAVE-5", "T-SAVE-6")
        subjectRepository.saveSubject(
            testSubject("T-SAVE-5").copy(
                isPpiFixed = true,
                preferBeforeBreak = true,
            ),
        )
        subjectRepository.saveSubject(testSubject("T-SAVE-6"))

        val withFlags = subjectRepository.listAll().first { it.id == "T-SAVE-5" }
        assertEquals(true, withFlags.isPpiFixed)
        assertEquals(true, withFlags.preferBeforeBreak)

        val withDefaults = subjectRepository.listAll().first { it.id == "T-SAVE-6" }
        assertEquals(false, withDefaults.isPpiFixed)
        assertEquals(false, withDefaults.preferBeforeBreak)
    }
}