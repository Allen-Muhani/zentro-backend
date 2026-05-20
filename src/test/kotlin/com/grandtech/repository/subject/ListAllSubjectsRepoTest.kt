package com.grandtech.repository.subject

import com.grandtech.service.CbcDataSeeder
import io.quarkus.test.junit.QuarkusTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Integration tests for [com.grandtech.repository.SubjectRepository.listAll].
 *
 * Field mapping correctness is covered by [SaveSubjectRepoTest] which saves a subject
 * with known values and reads it back. These tests focus on the structural guarantees:
 * all seeded subjects are present, the list is ordered, and newly saved subjects appear.
 */
@QuarkusTest
class ListAllSubjectsRepoTest : SubjectRepositoryTestBase() {

    @Test
    fun `listAll contains all 10 seeded subject IDs`() {
        val returnedIds = subjectRepository.listAll().map { it.id }.toSet()
        val seededIds = CbcDataSeeder.SUBJECTS.map { it.id }
        assertTrue(returnedIds.containsAll(seededIds))
    }

    @Test
    fun `listAll results are ordered alphabetically by name`() {
        val names = subjectRepository.listAll().map { it.name }
        assertEquals(names.sorted(), names)
    }

    @Test
    fun `listAll includes a newly saved subject`() {
        track("T-LIST-1")
        subjectRepository.saveSubject(testSubject("T-LIST-1", name = "Zzz Test Subject"))

        val found = subjectRepository.listAll().find { it.id == "T-LIST-1" }
        assertNotNull(found)
        assertEquals("Zzz Test Subject", found?.name)
    }

    @Test
    fun `listAll places a newly saved subject in correct alphabetical position`() {
        track("T-LIST-2")
        subjectRepository.saveSubject(testSubject("T-LIST-2", name = "Aaaaa First Subject"))

        val names = subjectRepository.listAll().map { it.name }
        assertEquals("Aaaaa First Subject", names.first())
    }
}