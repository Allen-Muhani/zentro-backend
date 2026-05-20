package com.grandtech.service.school

import com.grandtech.model.School
import com.grandtech.repository.UserRepository
import com.grandtech.service.SchoolService
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.neo4j.driver.Driver

/**
 * Integration tests for [SchoolService] that run against the configured Neo4j instance.
 *
 * Each test seeds its own School node via [UserRepository.saveSchool] and registers
 * the UID with [track] so [cleanUp] can delete it after the test finishes.
 */
@QuarkusTest
class SchoolServiceTest {

    @Inject
    lateinit var schoolService: SchoolService

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var driver: Driver

    private val trackedUids = mutableSetOf<String>()

    private fun track(vararg uids: String) {
        trackedUids.addAll(uids.toList())
    }

    @AfterEach
    fun cleanUp() {
        if (trackedUids.isNotEmpty()) {
            driver.session().use { session ->
                session.run(
                    "MATCH (u:User) WHERE u.fedUid IN \$uids DETACH DELETE u",
                    mapOf("uids" to trackedUids.toList()),
                )
            }
            trackedUids.clear()
        }
    }

    // region getSchoolByFedUid

    @Test
    fun `getSchoolByFedUid returns school when it exists`() {
        track("svc-get-1")
        userRepository.saveSchool(
            School(
                fedUid = "svc-get-1",
                name = "Sunrise Academy",
                email = "sunrise@test.com",
                phoneNumber = "0700000001",
                county = "Nairobi",
                subCounty = "Westlands",
            ),
        )

        val result = schoolService.getSchoolByFedUid("svc-get-1")

        assertNotNull(result)
        assertEquals("svc-get-1", result?.fedUid)
        assertEquals("Sunrise Academy", result?.name)
        assertEquals("sunrise@test.com", result?.email)
        assertEquals("0700000001", result?.phoneNumber)
        assertEquals("Nairobi", result?.county)
        assertEquals("Westlands", result?.subCounty)
    }

    @Test
    fun `getSchoolByFedUid returns null when school does not exist`() {
        val result = schoolService.getSchoolByFedUid("non-existent-uid")

        assertNull(result)
    }

    @Test
    fun `getSchoolByFedUid returns school with null optional fields`() {
        track("svc-get-2")
        userRepository.saveSchool(School(fedUid = "svc-get-2", email = "min@test.com"))

        val result = schoolService.getSchoolByFedUid("svc-get-2")

        assertNotNull(result)
        assertEquals("svc-get-2", result?.fedUid)
        assertNull(result?.name)
        assertNull(result?.phoneNumber)
        assertNull(result?.county)
        assertNull(result?.subCounty)
    }

    // endregion

    // region updateSchool

    @Test
    fun `updateSchool updates all mutable fields`() {
        track("svc-upd-1")
        userRepository.saveSchool(
            School(
                fedUid = "svc-upd-1",
                email = "upd1@test.com",
                name = "Old Name",
                phoneNumber = "0700000000",
                county = "Mombasa",
                subCounty = "Mvita",
            ),
        )

        val result = schoolService.updateSchool(
            "svc-upd-1",
            School(
                fedUid = "svc-upd-1",
                name = "New Name",
                phoneNumber = "0711111111",
                county = "Kisumu",
                subCounty = "Kisumu Central",
            ),
        )

        assertNotNull(result)
        assertEquals("New Name", result?.name)
        assertEquals("0711111111", result?.phoneNumber)
        assertEquals("Kisumu", result?.county)
        assertEquals("Kisumu Central", result?.subCounty)


        assertEquals("svc-upd-1", result?.fedUid)
        assertEquals("upd1@test.com", result?.email)
    }

    @Test
    fun `updateSchool does not overwrite email or fedUid`() {
        track("svc-upd-2")
        userRepository.saveSchool(
            School(fedUid = "svc-upd-2", email = "original@test.com"),
        )

        val result = schoolService.updateSchool(
            "svc-upd-2",
            School(fedUid = "svc-upd-2", name = "Updated Name"),
        )

        assertNotNull(result)
        assertEquals("svc-upd-2", result?.fedUid)
        assertEquals("original@test.com", result?.email)
    }

    @Test
    fun `updateSchool leaves stored values unchanged when fields are null`() {
        track("svc-upd-3")
        userRepository.saveSchool(
            School(
                fedUid = "svc-upd-3",
                email = "upd3@test.com",
                name = "Keep This Name",
                phoneNumber = "0722222222",
                county = "Nakuru",
                subCounty = "Nakuru East",
            ),
        )

        val result = schoolService.updateSchool(
            "svc-upd-3",
            School(fedUid = "svc-upd-3", county = "Eldoret"),
        )

        assertNotNull(result)
        assertEquals("Keep This Name", result?.name)
        assertEquals("0722222222", result?.phoneNumber)
        assertEquals("Eldoret", result?.county)
        assertEquals("Nakuru East", result?.subCounty)
    }

    @Test
    fun `updateSchool returns null when school does not exist`() {
        val result = schoolService.updateSchool(
            "non-existent-uid",
            School(fedUid = "non-existent-uid", name = "Ghost School"),
        )

        assertNull(result)
    }

    // endregion
}
