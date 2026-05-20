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

    /** Repository used to map room columns from join query results. */
    @Inject
    lateinit var roomRepository: RoomRepository

    /** Repository used to map teacher columns from join query results. */
    @Inject
    lateinit var teacherRepository: TeacherRepository

    /**
     * Returns true when [roomId] is already linked to a stream via `HOME_ROOM`.
     * Pass [excludeStreamId] when updating so the stream's own current room is not
     * treated as a conflict.
     */
    fun isRoomTaken(roomId: String, excludeStreamId: String? = null): Boolean =
        driver.session().use { session ->
            session.run(
                """
                MATCH (st:Stream)-[:HOME_ROOM]->(:Room {id: ${'$'}roomId})
                WHERE ${'$'}excludeStreamId IS NULL OR st.id <> ${'$'}excludeStreamId
                RETURN 1 LIMIT 1
                """.trimIndent(),
                mapOf("roomId" to roomId, "excludeStreamId" to excludeStreamId),
            ).hasNext()
        }

    /**
     * Returns true when [teacherFedUid] is already linked to a stream via `FORM_TEACHER`.
     * Pass [excludeStreamId] when updating so the stream's own current teacher is not
     * treated as a conflict.
     */
    fun isTeacherTaken(teacherFedUid: String, excludeStreamId: String? = null): Boolean =
        driver.session().use { session ->
            session.run(
                """
                MATCH (st:Stream)-[:FORM_TEACHER]->(:Teacher {fedUid: ${'$'}fedUid})
                WHERE ${'$'}excludeStreamId IS NULL OR st.id <> ${'$'}excludeStreamId
                RETURN 1 LIMIT 1
                """.trimIndent(),
                mapOf("fedUid" to teacherFedUid, "excludeStreamId" to excludeStreamId),
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
     * Removes any existing `HOME_ROOM` relationship from the stream and creates a new one
     * pointing to the room identified by [roomId].
     */
    fun replaceHomeRoom(streamId: String, roomId: String) {
        driver.session().use { session ->
            session.run(
                """
                MATCH (st:Stream {id: ${'$'}streamId})
                OPTIONAL MATCH (st)-[old:HOME_ROOM]->()
                WITH st, collect(old) AS oldRels
                FOREACH (r IN oldRels | DELETE r)
                WITH st
                MATCH (room:Room {id: ${'$'}roomId})
                CREATE (st)-[:HOME_ROOM]->(room)
                """.trimIndent(),
                mapOf("streamId" to streamId, "roomId" to roomId),
            )
        }
    }

    /**
     * Removes any existing `FORM_TEACHER` relationship from the stream and creates a new one
     * pointing to the teacher identified by [teacherFedUid].
     */
    fun replaceFormTeacher(streamId: String, teacherFedUid: String) {
        driver.session().use { session ->
            session.run(
                """
                MATCH (st:Stream {id: ${'$'}streamId})
                OPTIONAL MATCH (st)-[old:FORM_TEACHER]->()
                WITH st, collect(old) AS oldRels
                FOREACH (r IN oldRels | DELETE r)
                WITH st
                MATCH (teacher:Teacher {fedUid: ${'$'}fedUid})
                CREATE (st)-[:FORM_TEACHER]->(teacher)
                """.trimIndent(),
                mapOf("streamId" to streamId, "fedUid" to teacherFedUid),
            )
        }
    }

    /**
     * Returns all streams belonging to [schoolFedUid] with their optional
     * `HOME_ROOM` and `FORM_TEACHER` relationships, ordered by grade level then name.
     * Returns an empty list when the school has no streams.
     */
    fun listStreams(schoolFedUid: String): List<Stream> =
        driver.session().use { session ->
            session.run(
                """
                MATCH (:School {fedUid: ${'$'}fedUid})-[:HAS_STREAM]->(st:Stream)
                OPTIONAL MATCH (st)-[:HOME_ROOM]->(r:Room)
                OPTIONAL MATCH (st)-[:FORM_TEACHER]->(t:Teacher)
                RETURN st.id AS id, st.gradeLevel AS gradeLevel, st.name AS name,
                       st.studentCount AS studentCount, st.reStrand AS reStrand,
                       r, t
                ORDER BY st.gradeLevel, st.name
                """.trimIndent(),
                mapOf("fedUid" to schoolFedUid),
            ).list { mapToStream(it) }
        }

    /**
     * Fetches the stream identified by [streamId] together with its optional
     * `HOME_ROOM` and `FORM_TEACHER` relationships. Returns null if not found.
     */
    fun fetchStream(streamId: String): Stream? =
        driver.session().use { session ->
            val result = session.run(
                """
                MATCH (st:Stream {id: ${'$'}streamId})
                OPTIONAL MATCH (st)-[:HOME_ROOM]->(r:Room)
                OPTIONAL MATCH (st)-[:FORM_TEACHER]->(t:Teacher)
                RETURN st.id AS id, st.gradeLevel AS gradeLevel, st.name AS name,
                       st.studentCount AS studentCount, st.reStrand AS reStrand,
                       r, t
                """.trimIndent(),
                mapOf("streamId" to streamId),
            )
            if (result.hasNext()) mapToStream(result.next()) else null
        }

    /**
     * Maps a Neo4j [Record] row returned by a stream query to a [Stream] domain object.
     *
     * Scalar stream fields (`id`, `gradeLevel`, `name`, `studentCount`, `reStrand`) are read
     * directly from the record. Optional relationships are returned as whole nodes (`r`, `t`)
     * and delegated to [RoomRepository.mapNodeToRoom] and [TeacherRepository.mapNodeToTeacher].
     * Both resolve to null when no `HOME_ROOM` / `FORM_TEACHER` relationship exists.
     */
    private fun mapToStream(record: Record) = Stream(
        id           = record["id"].takeUnless { it.isNull }?.asString(),
        gradeLevel   = record["gradeLevel"].takeUnless { it.isNull }?.asInt(),
        name         = record["name"].takeUnless { it.isNull }?.asString(),
        studentCount = record["studentCount"].takeUnless { it.isNull }?.asInt(),
        reStrand     = record["reStrand"].takeUnless { it.isNull }?.asString(),
        homeRoom     = record["r"].takeUnless { it.isNull }?.asNode()?.let { roomRepository.mapNodeToRoom(it) },
        formTeacher  = record["t"].takeUnless { it.isNull }?.asNode()?.let { teacherRepository.mapNodeToTeacher(it) },
    )
}