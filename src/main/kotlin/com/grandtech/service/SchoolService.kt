package com.grandtech.service

import com.grandtech.model.School
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.neo4j.driver.Driver

/**
 * Executes Neo4j read and write operations specific to [School] nodes.
 *
 * Immutable identity fields (`fedUid`, `email`) are never overwritten by updates.
 */
@ApplicationScoped
class SchoolService {

    @Inject
    lateinit var driver: Driver

    /**
     * Looks up a [School] node by its Firebase UID.
     *
     * Returns null if no `School` node exists with the given UID. A `Teacher`
     * node with the same UID will not match because the query targets the
     * `School` label specifically.
     */
    fun getSchoolByFedUid(fedUid: String): School? =
        driver.session().use { session ->
            val result = session.run(
                "MATCH (s:School {fedUid: \$fedUid}) " +
                    "RETURN s.fedUid AS fedUid, s.name AS name, s.email AS email, " +
                    "s.phoneNumber AS phoneNumber, s.county AS county, s.subCounty AS subCounty",
                mapOf("fedUid" to fedUid),
            )
            if (result.hasNext()) {
                val record = result.next()
                School(
                    fedUid      = record["fedUid"].asString(),
                    name        = record["name"].takeUnless { it.isNull }?.asString(),
                    email       = record["email"].takeUnless { it.isNull }?.asString(),
                    phoneNumber = record["phoneNumber"].takeUnless { it.isNull }?.asString(),
                    county      = record["county"].takeUnless { it.isNull }?.asString(),
                    subCounty   = record["subCounty"].takeUnless { it.isNull }?.asString(),
                )
            } else null
        }

    /**
     * Updates the mutable fields of a [School] node identified by [fedUid].
     *
     * Only [School.name], [School.phoneNumber], [School.county], and
     * [School.subCounty] are written. Identity fields are never modified.
     * Each field uses COALESCE so a null value leaves the stored value unchanged.
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
            if (result.hasNext()) {
                val record = result.next()
                School(
                    fedUid      = record["fedUid"].asString(),
                    name        = record["name"].takeUnless { it.isNull }?.asString(),
                    email       = record["email"].takeUnless { it.isNull }?.asString(),
                    phoneNumber = record["phoneNumber"].takeUnless { it.isNull }?.asString(),
                    county      = record["county"].takeUnless { it.isNull }?.asString(),
                    subCounty   = record["subCounty"].takeUnless { it.isNull }?.asString(),
                )
            } else null
        }
}
