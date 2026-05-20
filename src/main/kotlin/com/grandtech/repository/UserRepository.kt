package com.grandtech.repository

import com.grandtech.model.School
import com.grandtech.model.Teacher
import com.grandtech.model.User
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.neo4j.driver.Driver
import org.neo4j.driver.types.Node

/**
 * Executes all Neo4j read and write operations for [User] nodes.
 *
 * Users are stored with two labels — `User` plus either `Teacher` or `School` — so a
 * single `MATCH (u:User)` query returns every account regardless of sub-type.
 * The second label is used by [mapNodeToUser] to reconstruct the correct Kotlin type.
 */
@ApplicationScoped
class UserRepository {

    /** Neo4j driver injected by Quarkus CDI; manages connection pooling and sessions. */
    @Inject
    lateinit var driver: Driver

    /** Handles persistence and lookup operations specific to [com.grandtech.model.Teacher] nodes. */
    @Inject
    lateinit var teacherRepository: TeacherRepository

    /**
     * Looks up a user by their Firebase UID and returns the typed model, or null.
     *
     * @param fedUid the Firebase Authentication UID to search for
     * @return the matching [User] subtype, or null if no node exists with that UID
     */
    fun findByFedUid(fedUid: String): User? =
        driver.session().use { session ->
            val result = session.run(
                "MATCH (u:User {fedUid: \$fedUid}) " +
                    "RETURN u, [l IN labels(u) WHERE l <> 'User'] AS role",
                mapOf("fedUid" to fedUid),
            )
            if (result.hasNext()) mapNodeToUser(result.next()) else null
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
     * Returns true if a `Teacher` node with the given email already exists.
     *
     * @param email the email address to check
     * @return true if a Teacher node with that email exists, false otherwise
     */
    fun teacherExistsByEmail(email: String): Boolean =
        teacherRepository.teacherExistsByEmail(email)

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
     * Returns true if a `User` node with the given email already exists.
     *
     * @param email the email address to check
     * @return true if a User node with that email exists, false otherwise
     */
    fun userExistsByEmail(email: String): Boolean =
        driver.session().use { session ->
            session.run(
                "MATCH (u:User {email: \$email}) RETURN count(u) > 0 AS exists",
                mapOf("email" to email),
            ).single()["exists"].asBoolean()
        }

    /**
     * Creates a new `(:User:Teacher)` node and returns the persisted model.
     *
     * @param teacher the teacher to persist; its [Teacher.fedUid] must be unique
     * @return the same [Teacher] instance passed in
     */
    fun saveTeacher(teacher: Teacher): Teacher =
        teacherRepository.saveTeacher(teacher)

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
                    "fedUid" to school.fedUid,
                    "name" to school.name,
                    "email" to school.email,
                    "phoneNumber" to school.phoneNumber,
                    "county" to school.county,
                    "subCounty" to school.subCounty,
                ),
            )
        }
        return school
    }

    /**
     * Maps a raw Neo4j record (containing a `u` node and a `role` list) to the
     * correct [User] subtype.
     *
     * @param record a Neo4j [org.neo4j.driver.Record] with keys `u` and `role`
     * @return the concrete [School] or [Teacher] instance
     * @throws IllegalStateException if the role list contains neither `"Teacher"` nor `"School"`
     */
    private fun mapNodeToUser(record: org.neo4j.driver.Record): User {
        val node: Node = record["u"].asNode()
        val roles: List<String> = record["role"].asList { it.asString() }
        val fedUid = node["fedUid"].asString()
        return when {
            "Teacher" in roles -> Teacher(
                fedUid = fedUid,
                name = node["name"].takeUnless { it.isNull }?.asString(),
                email = node["email"].takeUnless { it.isNull }?.asString(),
                tscNumber = node["tscNumber"].takeUnless { it.isNull }?.asString(),
            )
            "School" in roles -> School(
                fedUid = fedUid,
                name = node["name"].takeUnless { it.isNull }?.asString(),
                email = node["email"].takeUnless { it.isNull }?.asString(),
                phoneNumber = node["phoneNumber"].takeUnless { it.isNull }?.asString(),
                county = node["county"].takeUnless { it.isNull }?.asString(),
                subCounty = node["subCounty"].takeUnless { it.isNull }?.asString(),
            )
            else -> throw IllegalStateException("Unknown user role labels for fedUid=$fedUid: $roles")
        }
    }
}