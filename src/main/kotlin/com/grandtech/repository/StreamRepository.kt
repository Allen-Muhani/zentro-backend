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
                       r.id AS roomId, r.name AS roomName, r.capacity AS roomCapacity,
                       r.capabilityTag AS roomCapabilityTag,
                       r.isStandardClassroom AS roomIsStandardClassroom,
                       t.fedUid AS teacherFedUid, t.name AS teacherName,
                       t.email AS teacherEmail, t.tscNumber AS teacherTscNumber
                """.trimIndent(),
                mapOf("streamId" to streamId),
            )
            if (result.hasNext()) mapToStream(result.next()) else null
        }

    /**
     * Maps a Neo4j [Record] row returned by a stream query to a [Stream] domain object.
     *
     * Scalar stream fields (`id`, `gradeLevel`, `name`, `studentCount`, `reStrand`) are
     * read directly from the record. The two optional relationships are reconstructed from
     * flattened columns:
     * - `roomId`, `roomName`, `roomCapacity`, `roomCapabilityTag`, `roomIsStandardClassroom`
     *   → [Stream.homeRoom]; null when no `HOME_ROOM` relationship exists.
     * - `teacherFedUid`, `teacherName`, `teacherEmail`, `teacherTscNumber`
     *   → [Stream.formTeacher]; null when no `FORM_TEACHER` relationship exists.
     *
     * `takeUnless { it.isNull }` guards every field so that a Neo4j null value becomes a
     * Kotlin null rather than throwing a type-conversion exception.
     */
    private fun mapToStream(record: Record) = Stream(
        id           = record["id"].takeUnless { it.isNull }?.asString(),
        gradeLevel   = record["gradeLevel"].takeUnless { it.isNull }?.asInt(),
        name         = record["name"].takeUnless { it.isNull }?.asString(),
        studentCount = record["studentCount"].takeUnless { it.isNull }?.asInt(),
        reStrand     = record["reStrand"].takeUnless { it.isNull }?.asString(),
        homeRoom     = roomRepository.mapToRoom(record, prefix = "room"),
        formTeacher  = teacherRepository.mapToTeacher(record, prefix = "teacher"),
    )
}