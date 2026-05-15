package com.grandtech.service

import com.grandtech.dto.SubjectGroupResponse
import com.grandtech.dto.SubjectSummary
import com.grandtech.model.Subject
import com.grandtech.model.SubjectType
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.neo4j.driver.Driver

/**
 * Executes all Neo4j read and write operations for [Subject] and [SubjectType] nodes.
 *
 * The graph structure is:
 * ```
 * (:Subject {symbol, name, description})-[:OF_TYPE]->(:SubjectType {name, description})
 * ```
 * Both node types are seeded at startup by [CbcDataSeeder] and are read-only at runtime.
 */
@ApplicationScoped
class SubjectRepository {

    /** Neo4j driver injected by Quarkus CDI; manages connection pooling and sessions. */
    @Inject
    lateinit var driver: Driver

    /**
     * Returns the total number of `Subject` nodes in the graph.
     *
     * Used by [CbcDataSeeder] to decide whether to run the seed.
     *
     * @return count of all Subject nodes
     */
    fun countAll(): Long =
        driver.session().use { session ->
            session.run("MATCH (s:Subject) RETURN count(s) AS count")
                .single()["count"].asLong()
        }

    /**
     * Creates or updates a `SubjectType` node using MERGE so the operation is idempotent.
     *
     * @param type the [SubjectType] whose properties to write
     */
    fun saveSubjectType(type: SubjectType) {
        driver.session().use { session ->
            session.run(
                "MERGE (st:SubjectType {name: \$name}) SET st.description = \$description",
                mapOf("name" to type.name, "description" to type.description),
            )
        }
    }

    /**
     * Creates or updates a `Subject` node and links it to its type via `[:OF_TYPE]`.
     *
     * Uses MERGE on both ends so repeated calls are safe.
     *
     * @param subject  the [Subject] whose properties to write
     * @param typeName the [SubjectType.name] of the owning curriculum group
     */
    fun saveSubject(subject: Subject, typeName: String) {
        driver.session().use { session ->
            session.run(
                """
                MERGE (st:SubjectType {name: ${'$'}typeName})
                MERGE (s:Subject {symbol: ${'$'}symbol})
                SET s.name = ${'$'}name, s.description = ${'$'}description
                MERGE (s)-[:OF_TYPE]->(st)
                """.trimIndent(),
                mapOf(
                    "typeName" to typeName,
                    "symbol" to subject.symbol,
                    "name" to subject.name,
                    "description" to subject.description,
                ),
            )
        }
    }

    /**
     * Fetches all subjects from the graph, grouped by curriculum type.
     *
     * The Cypher query aggregates subjects per `SubjectType` node in a single
     * round-trip and returns them ordered alphabetically by type name.
     *
     * @return list of [SubjectGroupResponse], one per curriculum group, sorted by type name
     */
    fun listGroupedByType(): List<SubjectGroupResponse> =
        driver.session().use { session ->
            session.run(
                """
                MATCH (s:Subject)-[:OF_TYPE]->(st:SubjectType)
                RETURN st.name AS type,
                       st.description AS typeDescription,
                       collect({symbol: s.symbol, name: s.name, description: s.description})
                           AS subjects
                ORDER BY st.name
                """.trimIndent(),
            ).list { record ->
                SubjectGroupResponse(
                    type = record["type"].asString(),
                    description = record["typeDescription"].takeUnless { it.isNull }?.asString(),
                    subjects = record["subjects"].asList { v ->
                        val map = v.asMap()
                        SubjectSummary(
                            name = map["name"] as String,
                            symbol = map["symbol"] as String,
                            description = map["description"] as? String,
                        )
                    },
                )
            }
        }

    /**
     * Finds a single subject by its unique symbol code, or null if not found.
     *
     * @param symbol the KICD subject code, e.g. `"ENG"`
     * @return the matching [Subject] with its type loaded, or null
     */
    fun findBySymbol(symbol: String): Subject? =
        driver.session().use { session ->
            val result = session.run(
                "MATCH (s:Subject {symbol: \$symbol})-[:OF_TYPE]->(st:SubjectType) " +
                    "RETURN s, st",
                mapOf("symbol" to symbol),
            )
            if (result.hasNext()) {
                val record = result.next()
                val sNode = record["s"].asNode()
                val stNode = record["st"].asNode()
                Subject(
                    symbol = sNode["symbol"].asString(),
                    name = sNode["name"].asString(),
                    description = sNode["description"].takeUnless { it.isNull }?.asString(),
                    subjectType = SubjectType(
                        name = stNode["name"].asString(),
                        description = stNode["description"].takeUnless { it.isNull }?.asString(),
                    ),
                )
            } else null
        }
}