package com.grandtech.repository

import com.grandtech.model.Teacher
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.neo4j.driver.Driver
import org.neo4j.driver.Record
import org.neo4j.driver.types.Node

/** Executes all Neo4j read and write operations specific to [Teacher] nodes. */
@ApplicationScoped
class TeacherRepository {

    /** Neo4j driver injected by Quarkus CDI; manages connection pooling and sessions. */
    @Inject
    lateinit var driver: Driver

    /**
     * Creates a `(:User:Teacher)` node and returns the persisted model.
     * The caller must ensure [Teacher.fedUid] is unique before calling this.
     */
    fun saveTeacher(teacher: Teacher): Teacher {
        driver.session().use { session ->
            session.run(
                """
                CREATE (:User:Teacher {
                    fedUid:    ${'$'}fedUid,
                    name:      ${'$'}name,
                    email:     ${'$'}email,
                    tscNumber: ${'$'}tscNumber
                })
                """.trimIndent(),
                mapOf(
                    "fedUid"    to teacher.fedUid,
                    "name"      to teacher.name,
                    "email"     to teacher.email,
                    "tscNumber" to teacher.tscNumber,
                ),
            )
        }
        return teacher
    }

    /** Returns true if a `Teacher` node with the given [email] already exists. */
    fun teacherExistsByEmail(email: String): Boolean =
        driver.session().use { session ->
            session.run(
                "MATCH (t:Teacher {email: \$email}) RETURN count(t) > 0 AS exists",
                mapOf("email" to email),
            ).single()["exists"].asBoolean()
        }

    /**
     * Maps a Neo4j [Record] to a [Teacher], or returns null when no teacher was present.
     *
     * [prefix] supports reuse across queries that alias teacher columns differently:
     * - `prefix = ""`        → columns `fedUid`, `name`, `email`, `tscNumber`
     * - `prefix = "teacher"` → columns `teacherFedUid`, `teacherName`, … (join queries)
     *
     * Returns null when the sentinel column `<prefix>fedUid` is null, which indicates that
     * no teacher relationship was present in the result row.
     */
    internal fun mapToTeacher(record: Record, prefix: String = ""): Teacher? =
        record[col(prefix, "fedUid")].takeUnless { it.isNull }?.asString()?.let { fedUid ->
            Teacher(
                fedUid    = fedUid,
                name      = record[col(prefix, "name")].takeUnless { it.isNull }?.asString(),
                email     = record[col(prefix, "email")].takeUnless { it.isNull }?.asString(),
                tscNumber = record[col(prefix, "tscNumber")].takeUnless { it.isNull }?.asString(),
            )
        }

    /**
     * Maps a Neo4j [Node] (returned as a whole node via `RETURN t`) to a [Teacher].
     * Returns null if `fedUid` is missing on the node.
     */
    internal fun mapNodeToTeacher(node: Node): Teacher? =
        node["fedUid"].takeUnless { it.isNull }?.asString()?.let { fedUid ->
            Teacher(
                fedUid    = fedUid,
                name      = node["name"].takeUnless { it.isNull }?.asString(),
                email     = node["email"].takeUnless { it.isNull }?.asString(),
                tscNumber = node["tscNumber"].takeUnless { it.isNull }?.asString(),
            )
        }

    /** Builds a column alias: empty prefix → `"fedUid"`, prefix `"teacher"` → `"teacherFedUid"`. */
    private fun col(prefix: String, field: String) =
        if (prefix.isEmpty()) field else prefix + field.replaceFirstChar { it.uppercase() }
}