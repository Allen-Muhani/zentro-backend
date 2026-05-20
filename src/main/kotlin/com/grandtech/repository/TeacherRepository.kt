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

    /** Used to map subject nodes returned from join queries. */
    @Inject
    lateinit var subjectRepository: SubjectRepository

    /** Returns true if any `(:Teacher)` node already has the given email. */
    fun existsByEmail(email: String): Boolean =
        driver.session().use { session ->
            session.run(
                "MATCH (t:Teacher {email: \$email}) RETURN count(t) > 0 AS exists",
                mapOf("email" to email),
            ).single()["exists"].asBoolean()
        }

    /**
     * Creates a `(:Teacher)` node linked to the school via `HAS_TEACHER`, then links
     * it to each subject in [Teacher.subjectIds] via `TEACHES`.
     * Returns the full teacher with subjects, or null if the school does not exist.
     */
    fun createTeacher(schoolFedUid: String, teacher: Teacher): Teacher? {
        val subjectIds = teacher.subjectIds ?: emptyList()

        val teacherId = driver.session().use { session ->
            val result = session.run(
                """
                MATCH (s:School {fedUid: ${'$'}fedUid})
                CREATE (t:Teacher {
                    id:                randomUUID(),
                    name:              ${'$'}name,
                    email:             ${'$'}email,
                    phone:             ${'$'}phone,
                    tscNumber:         ${'$'}tscNumber,
                    maxPeriodsPerWeek: COALESCE(${'$'}maxPeriodsPerWeek, 23),
                    maxPeriodsPerDay:  COALESCE(${'$'}maxPeriodsPerDay, 6)
                })
                CREATE (s)-[:HAS_TEACHER]->(t)
                RETURN t.id AS id
                """.trimIndent(),
                mapOf(
                    "fedUid"            to schoolFedUid,
                    "name"              to teacher.name,
                    "email"             to teacher.email,
                    "phone"             to teacher.phone,
                    "tscNumber"         to teacher.tscNumber,
                    "maxPeriodsPerWeek" to teacher.maxPeriodsPerWeek,
                    "maxPeriodsPerDay"  to teacher.maxPeriodsPerDay,
                ),
            )
            if (result.hasNext()) result.next()["id"].asString() else null
        } ?: return null

        if (subjectIds.isNotEmpty()) {
            driver.session().use { session ->
                session.run(
                    """
                    MATCH (t:Teacher {id: ${'$'}teacherId})
                    UNWIND ${'$'}subjectIds AS subjectId
                    MATCH (sub:Subject {id: subjectId})
                    CREATE (t)-[:TEACHES]->(sub)
                    """.trimIndent(),
                    mapOf("teacherId" to teacherId, "subjectIds" to subjectIds),
                )
            }
        }

        return fetchTeacher(teacherId)
    }

    /**
     * Returns all teachers belonging to [schoolFedUid] with their subjects, ordered by name.
     * Returns an empty list when the school has no teachers.
     */
    fun listTeachers(schoolFedUid: String): List<Teacher> =
        driver.session().use { session ->
            session.run(
                """
                MATCH (:School {fedUid: ${'$'}fedUid})-[:HAS_TEACHER]->(t:Teacher)
                OPTIONAL MATCH (t)-[:TEACHES]->(sub:Subject)
                WITH t, collect(sub) AS subjects
                RETURN t, subjects
                ORDER BY t.name
                """.trimIndent(),
                mapOf("fedUid" to schoolFedUid),
            ).list { mapToTeacher(it) }.filterNotNull()
        }

    /**
     * Fetches a single teacher by [teacherId] with their subjects. Returns null if not found.
     */
    fun fetchTeacher(teacherId: String): Teacher? =
        driver.session().use { session ->
            val result = session.run(
                """
                MATCH (t:Teacher {id: ${'$'}teacherId})
                OPTIONAL MATCH (t)-[:TEACHES]->(sub:Subject)
                WITH t, collect(sub) AS subjects
                RETURN t, subjects
                """.trimIndent(),
                mapOf("teacherId" to teacherId),
            )
            if (result.hasNext()) mapToTeacher(result.next()) else null
        }

    /**
     * Maps a [Record] containing a `t` node and a `subjects` node list to a [Teacher].
     * Returns null when the `t` column is null.
     */
    private fun mapToTeacher(record: Record): Teacher? =
        record["t"].takeUnless { it.isNull }?.asNode()?.let { node ->
            Teacher(
                id                = node["id"].takeUnless { it.isNull }?.asString(),
                name              = node["name"].takeUnless { it.isNull }?.asString(),
                email             = node["email"].takeUnless { it.isNull }?.asString(),
                phone             = node["phone"].takeUnless { it.isNull }?.asString(),
                tscNumber         = node["tscNumber"].takeUnless { it.isNull }?.asString(),
                maxPeriodsPerWeek = node["maxPeriodsPerWeek"].takeUnless { it.isNull }?.asInt(),
                maxPeriodsPerDay  = node["maxPeriodsPerDay"].takeUnless { it.isNull }?.asInt(),
                subjects          = record["subjects"].asList { subjectRepository.mapNodeToSubject(it.asNode()) },
            )
        }

    /**
     * Maps a Neo4j [Node] (returned via `RETURN t` in stream join queries) to a [Teacher]
     * without subjects. Returns null if the node's `id` is absent.
     */
    internal fun mapNodeToTeacher(node: Node): Teacher? =
        node["id"].takeUnless { it.isNull }?.asString()?.let { id ->
            Teacher(
                id                = id,
                name              = node["name"].takeUnless { it.isNull }?.asString(),
                email             = node["email"].takeUnless { it.isNull }?.asString(),
                phone             = node["phone"].takeUnless { it.isNull }?.asString(),
                tscNumber         = node["tscNumber"].takeUnless { it.isNull }?.asString(),
                maxPeriodsPerWeek = node["maxPeriodsPerWeek"].takeUnless { it.isNull }?.asInt(),
                maxPeriodsPerDay  = node["maxPeriodsPerDay"].takeUnless { it.isNull }?.asInt(),
            )
        }
}
