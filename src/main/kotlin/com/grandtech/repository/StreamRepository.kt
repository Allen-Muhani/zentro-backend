package com.grandtech.repository

import com.grandtech.model.Stream
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.neo4j.driver.Driver
import org.neo4j.driver.Record

/** Executes all Neo4j read and write operations specific to [Stream] nodes. */
@ApplicationScoped
class StreamRepository {

    /** Neo4j driver injected by Quarkus CDI; manages connection pooling and sessions. */
    @Inject
    lateinit var driver: Driver

    /** Repository used to map teacher columns from join query results. */
    @Inject
    lateinit var teacherRepository: TeacherRepository

    /**
     * Returns true when [teacherFedUid] is already linked to a stream via `FORM_TEACHER`.
     * Pass [excludeStreamId] when updating so the stream's own current teacher is not
     * treated as a conflict.
     */
    fun isTeacherTaken(teacherId: String, excludeStreamId: String? = null): Boolean =
        driver.session().use { session ->
            session.run(
                """
                MATCH (st:Stream)-[:FORM_TEACHER]->(:Teacher {id: ${'$'}teacherId})
                WHERE ${'$'}excludeStreamId IS NULL OR st.id <> ${'$'}excludeStreamId
                RETURN 1 LIMIT 1
                """.trimIndent(),
                mapOf("teacherId" to teacherId, "excludeStreamId" to excludeStreamId),
            ).hasNext()
        }

    /**
     * Creates a `Stream` node, sets its scalar fields, and links it to the school via
     * `HAS_STREAM`. Returns the generated UUID, or null if the school does not exist.
     */
    fun createStreamNode(schoolFedUid: String, stream: Stream): String? =
        driver.session().use { session ->
            val result = session.run(
                """
                MATCH (s:School {fedUid: ${'$'}fedUid})
                CREATE (st:Stream {
                    id:           randomUUID(),
                    gradeLevel:   ${'$'}gradeLevel,
                    name:         ${'$'}name,
                    studentCount: ${'$'}studentCount,
                    reStrand:     COALESCE(${'$'}reStrand, 'CRE')
                })
                CREATE (s)-[:HAS_STREAM]->(st)
                RETURN st.id AS id
                """.trimIndent(),
                mapOf(
                    "fedUid"       to schoolFedUid,
                    "gradeLevel"   to stream.gradeLevel,
                    "name"         to stream.name,
                    "studentCount" to stream.studentCount,
                    "reStrand"     to stream.reStrand,
                ),
            )
            if (result.hasNext()) result.next()["id"].asString() else null
        }

    /**
     * Updates the scalar fields of an existing `Stream` node owned by [schoolFedUid].
     * Uses COALESCE so null fields in [stream] leave the stored value unchanged.
     * Returns the stream's id, or null if no matching stream exists under this school.
     */
    fun updateStreamNode(schoolFedUid: String, stream: Stream): String? =
        driver.session().use { session ->
            val result = session.run(
                """
                MATCH (:School {fedUid: ${'$'}fedUid})-[:HAS_STREAM]->(st:Stream {id: ${'$'}id})
                SET st.gradeLevel   = COALESCE(${'$'}gradeLevel,   st.gradeLevel),
                    st.name         = COALESCE(${'$'}name,         st.name),
                    st.studentCount = COALESCE(${'$'}studentCount, st.studentCount),
                    st.reStrand     = COALESCE(${'$'}reStrand,     st.reStrand)
                RETURN st.id AS id
                """.trimIndent(),
                mapOf(
                    "fedUid"       to schoolFedUid,
                    "id"           to stream.id,
                    "gradeLevel"   to stream.gradeLevel,
                    "name"         to stream.name,
                    "studentCount" to stream.studentCount,
                    "reStrand"     to stream.reStrand,
                ),
            )
            if (result.hasNext()) result.next()["id"].asString() else null
        }

    /**
     * Deletes the stream with [streamId] owned by [schoolFedUid] via `DETACH DELETE`.
     * Returns false if no matching stream was found under this school.
     */
    fun deleteStream(schoolFedUid: String, streamId: String): Boolean =
        driver.session().use { session ->
            val result = session.run(
                """
                MATCH (:School {fedUid: ${'$'}fedUid})-[:HAS_STREAM]->(st:Stream {id: ${'$'}streamId})
                DETACH DELETE st
                """.trimIndent(),
                mapOf("fedUid" to schoolFedUid, "streamId" to streamId),
            )
            result.consume().counters().nodesDeleted() > 0
        }

    /**
     * Removes any existing `FORM_TEACHER` relationship from the stream and creates a new one
     * pointing to the teacher identified by [teacherFedUid].
     */
    fun replaceFormTeacher(streamId: String, teacherId: String) {
        driver.session().use { session ->
            session.run(
                """
                MATCH (st:Stream {id: ${'$'}streamId})
                OPTIONAL MATCH (st)-[old:FORM_TEACHER]->()
                WITH st, collect(old) AS oldRels
                FOREACH (r IN oldRels | DELETE r)
                WITH st
                MATCH (teacher:Teacher {id: ${'$'}teacherId})
                CREATE (st)-[:FORM_TEACHER]->(teacher)
                """.trimIndent(),
                mapOf("streamId" to streamId, "teacherId" to teacherId),
            )
        }
    }

    /**
     * Returns all streams belonging to [schoolFedUid] with their optional
     * `FORM_TEACHER` relationships, ordered by grade level then name.
     * Returns an empty list when the school has no streams.
     */
    fun listStreams(schoolFedUid: String): List<Stream> =
        driver.session().use { session ->
            session.run(
                """
                MATCH (:School {fedUid: ${'$'}fedUid})-[:HAS_STREAM]->(st:Stream)
                OPTIONAL MATCH (st)-[:FORM_TEACHER]->(t:Teacher)
                RETURN st.id AS id, st.gradeLevel AS gradeLevel, st.name AS name,
                       st.studentCount AS studentCount, st.reStrand AS reStrand,
                       t
                ORDER BY st.gradeLevel, st.name
                """.trimIndent(),
                mapOf("fedUid" to schoolFedUid),
            ).list { mapToStream(it) }
        }

    /**
     * Fetches the stream identified by [streamId] together with its optional
     * `FORM_TEACHER` relationship. Returns null if not found.
     */
    fun fetchStream(streamId: String): Stream? =
        driver.session().use { session ->
            val result = session.run(
                """
                MATCH (st:Stream {id: ${'$'}streamId})
                OPTIONAL MATCH (st)-[:FORM_TEACHER]->(t:Teacher)
                RETURN st.id AS id, st.gradeLevel AS gradeLevel, st.name AS name,
                       st.studentCount AS studentCount, st.reStrand AS reStrand,
                       t
                """.trimIndent(),
                mapOf("streamId" to streamId),
            )
            if (result.hasNext()) mapToStream(result.next()) else null
        }

    private fun mapToStream(record: Record) = Stream(
        id           = record["id"].takeUnless { it.isNull }?.asString(),
        gradeLevel   = record["gradeLevel"].takeUnless { it.isNull }?.asInt(),
        name         = record["name"].takeUnless { it.isNull }?.asString(),
        studentCount = record["studentCount"].takeUnless { it.isNull }?.asInt(),
        reStrand     = record["reStrand"].takeUnless { it.isNull }?.asString(),
        formTeacher  = record["t"].takeUnless { it.isNull }?.asNode()?.let { teacherRepository.mapNodeToTeacher(it) },
    )
}