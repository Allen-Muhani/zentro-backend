package com.grandtech.repository

import com.grandtech.model.School
import com.grandtech.model.User
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.neo4j.driver.Driver
import org.neo4j.driver.types.Node

/**
 * Executes all Neo4j read and write operations for [User] nodes.
 *
 * Users are stored with two labels — `User` plus `School` — so a
 * single `MATCH (u:User)` query returns every account regardless of sub-type.
 */
@ApplicationScoped
class UserRepository {

    /** Neo4j driver injected by Quarkus CDI; manages connection pooling and sessions. */
    @Inject
    lateinit var driver: Driver

    /**
     * Looks up a user by their Firebase UID and returns the typed model, or null.
     *
     * @param fedUid the Firebase Authentication UID to search for
     * @return the matching [User] subtype, or null if no node exists with that UID
     */
    fun findByFedUid(fedUid: String): User? =
        driver.session().use { session ->
            val result = session.run(
                "MATCH (u:User {fedUid: \$fedUid}) RETURN u",
                mapOf("fedUid" to fedUid),
            )
            if (result.hasNext()) mapNodeToUser(result.next()["u"].asNode()) else null
        }

    /**
     * Returns true if a `User` node with the given Firebase UID already exists.
     *
     * @param fedUid the Firebase Authentication UID to check
     * @return true if a node with that UID exists, false otherwise
     */
    fun existsByFedUid(fedUid: String): Boolean =
        driver.session().use { session ->
            session.run(
                "MATCH (u:User {fedUid: \$fedUid}) RETURN count(u) > 0 AS exists",
                mapOf("fedUid" to fedUid),
            ).single()["exists"].asBoolean()
        }

    /**
     * Returns true if a `School` node with the given email already exists.
     *
     * @param email the email address to check
     * @return true if a School node with that email exists, false otherwise
     */
    fun schoolExistsByEmail(email: String): Boolean =
        driver.session().use { session ->
            session.run(
                "MATCH (s:School {email: \$email}) RETURN count(s) > 0 AS exists",
                mapOf("email" to email),
            ).single()["exists"].asBoolean()
        }

    /**
     * Creates a new `(:User:School)` node and returns the persisted model.
     *
     * @param school the school to persist; its [School.fedUid] must be unique
     * @return the same [School] instance passed in
     */
    fun saveSchool(school: School): School {
        driver.session().use { session ->
            session.run(
                "CREATE (:User:School {fedUid: \$fedUid, name: \$name, email: \$email, " +
                    "phoneNumber: \$phoneNumber, county: \$county, subCounty: \$subCounty})",
                mapOf(
                    "fedUid"      to school.fedUid,
                    "name"        to school.name,
                    "email"       to school.email,
                    "phoneNumber" to school.phoneNumber,
                    "county"      to school.county,
                    "subCounty"   to school.subCounty,
                ),
            )
        }
        return school
    }

    /**
     * Maps a raw Neo4j `User` node to the correct [User] subtype.
     *
     * @param node a Neo4j [Node] with at least the `School` label
     * @return the concrete [School] instance
     * @throws IllegalStateException if the node is not a recognised user type
     */
    private fun mapNodeToUser(node: Node): User {
        val fedUid = node["fedUid"].asString()
        return when {
            node.hasLabel("School") -> School(
                fedUid      = fedUid,
                name        = node["name"].takeUnless { it.isNull }?.asString(),
                email       = node["email"].takeUnless { it.isNull }?.asString(),
                phoneNumber = node["phoneNumber"].takeUnless { it.isNull }?.asString(),
                county      = node["county"].takeUnless { it.isNull }?.asString(),
                subCounty   = node["subCounty"].takeUnless { it.isNull }?.asString(),
            )
            else -> throw IllegalStateException("Unknown user type for fedUid=$fedUid")
        }
    }
}