package com.grandtech.repository

import com.grandtech.model.RoomCapabilityTag
import com.grandtech.model.SchoolSubject
import com.grandtech.model.Subject
import com.grandtech.model.SubjectType
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.neo4j.driver.Driver

/**
 * Executes all Neo4j reads and writes for [Subject] nodes.
 *
 * Graph structure:
 * ```
 * (:Subject {id, symbol, name, type, periodsPerWeek, requiresDoubledPeriod,
 *            requiresSpecialRoom, roomCapabilityTag, isPpiFixed,
 *            preferBeforeBreak, maxPeriodsPerDay})
 * ```
 * Subjects are seeded at startup by [CbcDataSeeder] and treated as read-only
 * at runtime. All writes use MERGE so they are idempotent.
 */
@ApplicationScoped
class SubjectRepository {

    /** Neo4j driver injected by Quarkus CDI; manages connection pooling and sessions. */
    @Inject
    lateinit var driver: Driver

   /**
     * Returns the total number of `Subject` nodes in the graph.
     *
     * Used by [CbcDataSeeder] to decide whether the seed should be skipped.
     *
     * @return count of all Subject nodes
     */
    fun countAll(): Long =
        driver.session().use { session ->
            session.run("MATCH (s:Subject) RETURN count(s) AS count")
                .single()["count"].asLong()
        }

    /**
     * Creates or updates a `Subject` node using MERGE so the operation is idempotent.
     *
     * All solver fields are overwritten on every call, so passing an updated
     * [Subject] is safe even if the node already exists.
     *
     * @param subject the [Subject] whose properties to write
     */
    fun saveSubject(subject: Subject) {
        driver.session().use { session ->
            session.run(
                """
                MERGE (s:Subject {id: ${'$'}id})
                SET s.symbol                = ${'$'}symbol,
                    s.name                  = ${'$'}name,
                    s.description           = ${'$'}description,
                    s.type                  = ${'$'}type,
                    s.periodsPerWeek        = ${'$'}periodsPerWeek,
                    s.requiresDoubledPeriod = ${'$'}requiresDoubledPeriod,
                    s.requiresSpecialRoom   = ${'$'}requiresSpecialRoom,
                    s.roomCapabilityTag     = ${'$'}roomCapabilityTag,
                    s.isPpiFixed            = ${'$'}isPpiFixed,
                    s.preferBeforeBreak     = ${'$'}preferBeforeBreak,
                    s.maxPeriodsPerDay      = ${'$'}maxPeriodsPerDay
                """.trimIndent(),
                mapOf(
                    "id"                    to subject.id,
                    "symbol"                to subject.symbol,
                    "name"                  to subject.name,
                    "description"           to subject.description,
                    "type"                  to subject.type.name,
                    "periodsPerWeek"        to subject.periodsPerWeek,
                    "requiresDoubledPeriod" to subject.requiresDoubledPeriod,
                    "requiresSpecialRoom"   to subject.requiresSpecialRoom,
                    "roomCapabilityTag"     to subject.roomCapabilityTag?.name,
                    "isPpiFixed"            to subject.isPpiFixed,
                    "preferBeforeBreak"     to subject.preferBeforeBreak,
                    "maxPeriodsPerDay"      to subject.maxPeriodsPerDay,
                ),
            )
        }
    }

    /**
     * Returns all `Subject` nodes from the graph, ordered alphabetically by name.
     *
     * @return list of every [Subject], sorted by name
     */
    fun listAll(): List<Subject> =
        driver.session().use { session ->
            session.run("MATCH (s:Subject) RETURN s ORDER BY s.name")
                .list { record -> record["s"].asNode().toSubject() }
        }

    /**
     * Returns all subjects with the count of teachers in [schoolFedUid] that teach each one.
     *
     * @param schoolFedUid the Firebase UID of the authenticated school
     * @return list of [SchoolSubject] ordered alphabetically by name
     */
    fun listWithTeacherCount(schoolFedUid: String): List<SchoolSubject> =
        driver.session().use { session ->
            session.run(
                """
                MATCH (sub:Subject)
                OPTIONAL MATCH (:School {fedUid: ${'$'}fedUid})-[:HAS_TEACHER]->(t:Teacher)-[:TEACHES]->(sub)
                WITH sub, count(t) AS teacherCount
                RETURN sub, teacherCount
                ORDER BY sub.name
                """.trimIndent(),
                mapOf("fedUid" to schoolFedUid),
            ).list { record ->
                SchoolSubject(
                    subject = record["sub"].asNode().toSubject(),
                    teacherCount = record["teacherCount"].asInt(0),
                )
            }
        }

    /**
     * Maps a Neo4j [org.neo4j.driver.types.Node] to a [Subject].
     * Used by repositories that return subject nodes from join queries.
     */
    internal fun mapNodeToSubject(node: org.neo4j.driver.types.Node): Subject = node.toSubject()

    /**
     * Maps a raw Neo4j node to a [Subject] instance by reading its stored properties.
     *
     * @return the [Subject] populated from this node's property map
     */
    private fun org.neo4j.driver.types.Node.toSubject() = Subject(
        id                    = this["id"].asString(),
        symbol                = this["symbol"].asString(),
        name                  = this["name"].asString(),
        description           = this["description"].takeUnless { it.isNull }?.asString(),
        type                  = SubjectType.valueOf(this["type"].asString()),
        periodsPerWeek        = this["periodsPerWeek"].asInt(),
        requiresDoubledPeriod = this["requiresDoubledPeriod"].asBoolean(false),
        requiresSpecialRoom   = this["requiresSpecialRoom"].asBoolean(false),
        roomCapabilityTag     = this["roomCapabilityTag"].takeUnless { it.isNull }
                                    ?.let { RoomCapabilityTag.valueOf(it.asString()) },
        isPpiFixed            = this["isPpiFixed"].asBoolean(false),
        preferBeforeBreak     = this["preferBeforeBreak"].asBoolean(false),
        maxPeriodsPerDay      = this["maxPeriodsPerDay"].asInt(1),
    )
}