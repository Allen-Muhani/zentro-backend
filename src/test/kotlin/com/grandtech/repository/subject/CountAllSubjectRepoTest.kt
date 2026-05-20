package com.grandtech.repository.subject

import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Integration tests for [com.grandtech.repository.SubjectRepository.countAll].
 */
@QuarkusTest
class CountAllSubjectRepoTest : SubjectRepositoryTestBase() {

    @Test
    fun `countAll returns at least 10 for the seeded subjects`() {
        assertTrue(subjectRepository.countAll() >= 10)
    }

    @Test
    fun `countAll increases by 1 after saving a new subject`() {
        track("T-COUNT-1")
        val before = subjectRepository.countAll()

        subjectRepository.saveSubject(testSubject("T-COUNT-1"))

        assertEquals(before + 1, subjectRepository.countAll())
    }

    @Test
    fun `countAll does not increase when saving an existing subject again`() {
        track("T-COUNT-2")
        subjectRepository.saveSubject(testSubject("T-COUNT-2"))
        val after = subjectRepository.countAll()

        subjectRepository.saveSubject(testSubject("T-COUNT-2", name = "Updated Name"))

        assertEquals(after, subjectRepository.countAll())
    }
}