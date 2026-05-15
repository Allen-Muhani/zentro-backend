package com.grandtech.model

/**
 * A CBC JSS learning area with its symbol, name, and curriculum classification.
 *
 * Stored in Neo4j as a `(:Subject)` node connected to its [SubjectType] via
 * an `[:OF_TYPE]` relationship.
 * All subjects are seeded at startup by [com.grandtech.service.CbcDataSeeder].
 *
 * @property symbol      short unique code used in timetables and reports, e.g. `"ENG"`, `"MAT"`
 * @property name        full official name of the learning area
 * @property description brief summary of what the learning area covers
 * @property subjectType the curriculum group this subject belongs to, or null if not loaded
 */
data class Subject(
    val symbol: String,
    val name: String,
    val description: String? = null,
    val subjectType: SubjectType? = null,
)