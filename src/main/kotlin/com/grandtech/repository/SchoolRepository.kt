package com.grandtech.repository

import com.grandtech.model.School
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.neo4j.driver.Driver
import org.neo4j.driver.Record

/** Executes all Neo4j read and write operations specific to [School] nodes. */
@ApplicationScoped
class SchoolRepository {

    /** Neo4j driver injected by Quarkus CDI; manages connection pooling and sessions. */
    @Inject
    lateinit var driver: Driver

    /**
     * Looks up a [School] node by its Firebase UID.
     * Returns null if no `School` node with that UID exists.
     */
    fun getSchoolByFedUid(fedUid: String): School? =
        driver.session().use { session ->
            val result = session.run(
                "MATCH (s:School {fedUid: \$fedUid}) " +
                    "RETURN s.fedUid AS fedUid, s.name AS name, s.email AS email, " +
                    "s.phoneNumber AS phoneNumber, s.county AS county, s.subCounty AS subCounty",
                mapOf("fedUid" to fedUid),
            )
            if (result.hasNext()) mapToSchool(result.next()) else null
        }

    /**
     * Updates the mutable fields of a [School] node identified by [fedUid].
     * Identity fields (`fedUid`, `email`) are never overwritten.
     * Returns null if no matching node exists.
     */
    fun updateSchool(fedUid: String, school: School): School? =
        driver.session().use { session ->
            val result = session.run(
                """
                MATCH (s:School {fedUid: ${'$'}fedUid})
                SET s.name        = COALESCE(${'$'}name, s.name),
                    s.phoneNumber = COALESCE(${'$'}phoneNumber, s.phoneNumber),
                    s.county      = COALESCE(${'$'}county, s.county),
                    s.subCounty   = COALESCE(${'$'}subCounty, s.subCounty)
                RETURN s.fedUid AS fedUid, s.name AS name, s.email AS email,
                       s.phoneNumber AS phoneNumber, s.county AS county, s.subCounty AS subCounty
                """.trimIndent(),
                mapOf(
                    "fedUid"      to fedUid,
                    "name"        to school.name,
                    "phoneNumber" to school.phoneNumber,
                    "county"      to school.county,
                    "subCounty"   to school.subCounty,
                ),
            )
            if (result.hasNext()) mapToSchool(result.next()) else null
        }

    private fun mapToSchool(record: Record) = School(
        fedUid      = record["fedUid"].asString(),
        name        = record["name"].takeUnless { it.isNull }?.asString(),
        email       = record["email"].takeUnless { it.isNull }?.asString(),
        phoneNumber = record["phoneNumber"].takeUnless { it.isNull }?.asString(),
        county      = record["county"].takeUnless { it.isNull }?.asString(),
        subCounty   = record["subCounty"].takeUnless { it.isNull }?.asString(),
    )
}