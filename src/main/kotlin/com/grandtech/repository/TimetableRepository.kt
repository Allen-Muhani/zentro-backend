package com.grandtech.repository

import com.grandtech.model.TimetableEntry
import com.grandtech.model.TimetableRun
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.neo4j.driver.Driver
import java.time.OffsetDateTime

/**
 * Executes all Neo4j read and write operations for [TimetableRun] and [TimetableEntry] nodes.
 *
 * Graph schema:
 * ```
 * (:School {fedUid})-[:HAS_TIMETABLE_RUN]->(:TimetableRun {id, ...})
 * (:TimetableRun)-[:HAS_ENTRY]->(:TimetableEntry {id, day, period, ...})
 * (:TimetableEntry)-[:FOR_STREAM]->(:Stream {id})
 * (:TimetableEntry)-[:FOR_SUBJECT]->(:Subject {id})
 * (:TimetableEntry)-[:TAUGHT_BY]->(:Teacher {id})  // omitted when teacherId is null
 * ```
 */
@ApplicationScoped
class TimetableRepository {

    /** CDI-injected Neo4j driver used for all database sessions. */
    @Inject
    lateinit var driver: Driver

    /**
     * Creates a `(:TimetableRun)` node linked to the school,
     * sets status to RUNNING, and returns the generated UUID.
     *
     * @param schoolFedUid    fedUid of the owning school
     * @param academicYear    academic year label (e.g. "2024")
     * @param term            term label (e.g. "Term 1")
     * @param timeLimitSeconds solver time cap stored on the node
     * @return the UUID assigned to the newly created run node
     */
    fun createRun(schoolFedUid: String, academicYear: String, term: String, timeLimitSeconds: Int): String =
        driver.session().use { session ->
            session.run(
                """
                MATCH (s:School {fedUid: ${'$'}fedUid})
                CREATE (r:TimetableRun {
                    id:               randomUUID(),
                    schoolFedUid:     ${'$'}fedUid,
                    academicYear:     ${'$'}academicYear,
                    term:             ${'$'}term,
                    status:           'RUNNING',
                    timeLimitSeconds: ${'$'}timeLimitSeconds,
                    generatedAt:      ${'$'}now
                })
                CREATE (s)-[:HAS_TIMETABLE_RUN]->(r)
                RETURN r.id AS id
                """.trimIndent(),
                mapOf(
                    "fedUid" to schoolFedUid,
                    "academicYear" to academicYear,
                    "term" to term,
                    "timeLimitSeconds" to timeLimitSeconds,
                    "now" to OffsetDateTime.now().toString(),
                ),
            ).single()["id"].asString()
        }

    /**
     * Marks a run as COMPLETED and writes solver statistics onto the node.
     *
     * @param runId          UUID of the run to update
     * @param solverStatus   solver status name (e.g. "FEASIBLE", "INFEASIBLE")
     * @param objectiveValue cumulative soft-constraint penalty from the solver
     * @param wallTimeMs     wall-clock solve time in milliseconds
     * @param violations     soft-constraint or structural violations
     */
    fun updateRunCompleted(
        runId: String,
        solverStatus: String,
        objectiveValue: Long,
        wallTimeMs: Long,
        violations: List<String>,
    ) {
        driver.session().use { session ->
            session.run(
                """
                MATCH (r:TimetableRun {id: ${'$'}runId})
                SET r.status          = 'COMPLETED',
                    r.solverStatus    = ${'$'}solverStatus,
                    r.objectiveValue  = ${'$'}objectiveValue,
                    r.solverWallTimeMs = ${'$'}wallTimeMs,
                    r.violations      = ${'$'}violations,
                    r.completedAt     = ${'$'}now
                """.trimIndent(),
                mapOf(
                    "runId" to runId,
                    "solverStatus" to solverStatus,
                    "objectiveValue" to objectiveValue,
                    "wallTimeMs" to wallTimeMs,
                    "violations" to violations,
                    "now" to OffsetDateTime.now().toString(),
                ),
            )
        }
    }

    /**
     * Marks a run as FAILED and stores the diagnostic report JSON.
     *
     * @param runId            UUID of the run to mark as failed
     * @param diagnosticReport JSON-serialised [DiagnosticReport]
     */
    fun updateRunFailed(runId: String, diagnosticReport: String) {
        driver.session().use { session ->
            session.run(
                """
                MATCH (r:TimetableRun {id: ${'$'}runId})
                SET r.status           = 'FAILED',
                    r.diagnosticReport = ${'$'}report,
                    r.completedAt      = ${'$'}now
                """.trimIndent(),
                mapOf(
                    "runId" to runId,
                    "report" to diagnosticReport,
                    "now" to OffsetDateTime.now().toString(),
                ),
            )
        }
    }

    /**
     * Batch-creates all [TimetableEntry] nodes for a run and links them
     * to their run, stream, subject, and teacher (teacher link omitted
     * when [TimetableEntry.teacherId] is null).
     *
     * @param runId   UUID of the parent [TimetableRun] node
     * @param entries entries produced by the solver to persist
     */
    fun saveEntries(runId: String, entries: List<TimetableEntry>) {
        if (entries.isEmpty()) return
        entries.chunked(50).forEach { batch ->
        driver.session().use { session ->
            // Create entry nodes and run→entry relationships in bulk via UNWIND
            session.run(
                """
                MATCH (r:TimetableRun {id: ${'$'}runId})
                UNWIND ${'$'}batch AS e
                CREATE (en:TimetableEntry {
                    id:                 randomUUID(),
                    day:                e.day,
                    period:             e.period,
                    startTime:          e.startTime,
                    endTime:            e.endTime,
                    isDoubleStart:      e.isDoubleStart,
                    isDoubleContinuation: e.isDoubleContinuation
                })
                CREATE (r)-[:HAS_ENTRY]->(en)
                WITH en, e
                MATCH (st:Stream  {id: e.streamId})
                MATCH (sub:Subject {id: e.subjectId})
                CREATE (en)-[:FOR_STREAM]->(st)
                CREATE (en)-[:FOR_SUBJECT]->(sub)
                WITH en, e
                WHERE e.teacherId IS NOT NULL
                MATCH (t:Teacher {id: e.teacherId})
                CREATE (en)-[:TAUGHT_BY]->(t)
                """.trimIndent(),
                mapOf(
                    "runId" to runId,
                    "batch" to batch.map { e ->
                        mapOf(
                            "streamId" to e.streamId,
                            "subjectId" to e.subjectId,
                            "teacherId" to e.teacherId,
                            "day" to e.day,
                            "period" to e.period,
                            "startTime" to e.startTime,
                            "endTime" to e.endTime,
                            "isDoubleStart" to e.isDoubleStart,
                            "isDoubleContinuation" to e.isDoubleContinuation,
                        )
                    },
                ),
            )
        }
        }
    }

    /**
     * Returns all timetable runs for [schoolFedUid], ordered by
     * generation time descending.
     *
     * @param schoolFedUid fedUid of the school whose runs to list
     * @return list of runs, newest first; empty if none exist
     */
    fun listRuns(schoolFedUid: String): List<TimetableRun> =
        driver.session().use { session ->
            session.run(
                """
                MATCH (:School {fedUid: ${'$'}fedUid})-[:HAS_TIMETABLE_RUN]->(r:TimetableRun)
                RETURN r
                ORDER BY r.generatedAt DESC
                """.trimIndent(),
                mapOf("fedUid" to schoolFedUid),
            ).list { record ->
                val r = record["r"].asNode()
                mapToRun(r)
            }
        }

    /**
     * Returns a single timetable run by [runId], or null if not found.
     *
     * @param runId UUID of the run to fetch
     * @return the matching [TimetableRun], or null if the node does not exist
     */
    fun getRun(runId: String): TimetableRun? =
        driver.session().use { session ->
            val result = session.run(
                "MATCH (r:TimetableRun {id: \$runId}) RETURN r",
                mapOf("runId" to runId),
            )
            if (result.hasNext()) mapToRun(result.next()["r"].asNode()) else null
        }

    /**
     * Returns all timetable entries for a specific stream within a run,
     * ordered by day and period. Includes subject and teacher IDs
     * resolved from relationships.
     *
     * @param runId    UUID of the parent run
     * @param streamId ID of the stream whose entries to fetch
     * @return entries for the stream, sorted by day then period
     */
    fun getEntriesForStream(runId: String, streamId: String): List<TimetableEntry> =
        driver.session().use { session ->
            session.run(
                """
                MATCH (:TimetableRun {id: ${'$'}runId})-[:HAS_ENTRY]->(en:TimetableEntry)
                      -[:FOR_STREAM]->(st:Stream {id: ${'$'}streamId})
                MATCH (en)-[:FOR_SUBJECT]->(sub:Subject)
                OPTIONAL MATCH (en)-[:TAUGHT_BY]->(t:Teacher)
                RETURN en,
                       st.id AS streamId, st.name AS streamName,
                       sub.id AS subjectId, sub.name AS subjectName, sub.symbol AS subjectSymbol,
                       t.id AS teacherId, t.name AS teacherName
                ORDER BY en.day, en.period
                """.trimIndent(),
                mapOf("runId" to runId, "streamId" to streamId),
            ).list { mapToEntry(it) }
        }

    /**
     * Returns all timetable entries for a specific teacher within a run,
     * ordered by day and period.
     *
     * @param runId     UUID of the parent run
     * @param teacherId ID of the teacher whose entries to fetch
     * @return entries taught by the teacher, sorted by day then period
     */
    fun getEntriesForTeacher(runId: String, teacherId: String): List<TimetableEntry> =
        driver.session().use { session ->
            session.run(
                """
                MATCH (:TimetableRun {id: ${'$'}runId})-[:HAS_ENTRY]->(en:TimetableEntry)
                      -[:TAUGHT_BY]->(t:Teacher {id: ${'$'}teacherId})
                MATCH (en)-[:FOR_SUBJECT]->(sub:Subject)
                MATCH (en)-[:FOR_STREAM]->(st:Stream)
                RETURN en,
                       st.id AS streamId, st.name AS streamName,
                       sub.id AS subjectId, sub.name AS subjectName, sub.symbol AS subjectSymbol,
                       t.id AS teacherId, t.name AS teacherName
                ORDER BY en.day, en.period
                """.trimIndent(),
                mapOf("runId" to runId, "teacherId" to teacherId),
            ).list { mapToEntry(it) }
        }

    private fun mapToRun(node: org.neo4j.driver.types.Node) = TimetableRun(
        id = node["id"].takeUnless { it.isNull }?.asString(),
        schoolFedUid = node["schoolFedUid"].takeUnless { it.isNull }?.asString(),
        academicYear = node["academicYear"].takeUnless { it.isNull }?.asString(),
        term = node["term"].takeUnless { it.isNull }?.asString(),
        status = node["status"].takeUnless { it.isNull }?.asString(),
        solverStatus = node["solverStatus"].takeUnless { it.isNull }?.asString(),
        objectiveValue = node["objectiveValue"].takeUnless { it.isNull }?.asLong(),
        solverWallTimeMs = node["solverWallTimeMs"].takeUnless { it.isNull }?.asLong(),
        timeLimitSeconds = node["timeLimitSeconds"].takeUnless { it.isNull }?.asInt(),
        diagnosticReport = node["diagnosticReport"].takeUnless { it.isNull }?.asString(),
        violations = node["violations"].takeUnless { it.isNull }?.asList { it.asString() },
        generatedAt = node["generatedAt"].takeUnless { it.isNull }?.asString(),
        completedAt = node["completedAt"].takeUnless { it.isNull }?.asString(),
    )

    private fun mapToEntry(record: org.neo4j.driver.Record): TimetableEntry {
        val en = record["en"].asNode()
        return TimetableEntry(
            id = en["id"].takeUnless { it.isNull }?.asString(),
            streamId = record["streamId"].takeUnless { it.isNull }?.asString(),
            streamName = record["streamName"].takeUnless { it.isNull }?.asString(),
            subjectId = record["subjectId"].takeUnless { it.isNull }?.asString(),
            subjectName = record["subjectName"].takeUnless { it.isNull }?.asString(),
            subjectSymbol = record["subjectSymbol"].takeUnless { it.isNull }?.asString(),
            teacherId = record["teacherId"].takeUnless { it.isNull }?.asString(),
            teacherName = record["teacherName"].takeUnless { it.isNull }?.asString(),
            day = en["day"].takeUnless { it.isNull }?.asString(),
            period = en["period"].takeUnless { it.isNull }?.asInt(),
            startTime = en["startTime"].takeUnless { it.isNull }?.asString(),
            endTime = en["endTime"].takeUnless { it.isNull }?.asString(),
            isDoubleStart = en["isDoubleStart"].takeUnless { it.isNull }?.asBoolean() ?: false,
            isDoubleContinuation = en["isDoubleContinuation"].takeUnless { it.isNull }?.asBoolean() ?: false,
        )
    }
}
