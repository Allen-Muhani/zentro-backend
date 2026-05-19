package com.grandtech.service

import com.grandtech.model.School
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.neo4j.driver.Driver

/**
 * Executes Neo4j read operations specific to [School] nodes.
 *
 * Queries target the `School` label directly rather than going through the
 * generic [UserRepository], so only school properties are fetched and mapped.
 */
@ApplicationScoped
class SchoolService {

    /** Neo4j driver injected by Quarkus CDI; manages connection pooling and sessions. */
    @Inject
    lateinit var driver: Driver

    /**
     * Looks up a [School] node by its Firebase UID.
     *
     * Returns null if no `School` node exists with the given UID. A `Teacher`
     * node with the same UID will not match because the query targets the
     * `School` label specifically.
     *
     * @param fedUid the Firebase Authentication UID to search for
     * @return the matching [School], or null if no school node is found
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
                    fedUid = record["fedUid"].asString(),
                    name = record["name"].takeUnless { it.isNull }?.asString(),
                    email = record["email"].takeUnless { it.isNull }?.asString(),
                    phoneNumber = record["phoneNumber"].takeUnless { it.isNull }?.asString(),
                    county = record["county"].takeUnless { it.isNull }?.asString(),
                    subCounty = record["subCounty"].takeUnless { it.isNull }?.asString(),
                )
            } else null
        }
}