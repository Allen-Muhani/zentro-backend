package com.grandtech.model

/**
 * Classifies CBC learning areas into curriculum groups (e.g. CORE, OPTIONAL).
 *
 * Stored in Neo4j as `(:SubjectType)` nodes.
 * Subjects link to their type via an `[:OF_TYPE]` relationship.
 *
 * @property name        unique identifier for this group, e.g. `"CORE"` or `"OPTIONAL"`
 * @property description human-readable explanation of what this classification means
 */
data class SubjectType(
    val name: String,
    val description: String? = null,
)