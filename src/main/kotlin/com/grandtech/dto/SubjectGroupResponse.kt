package com.grandtech.dto

/**
 * API response envelope for a single curriculum group and the subjects it contains.
 *
 * Used by the `GET /schools/subjects` endpoint to return subjects grouped by type
 * (e.g. CORE, OPTIONAL) in a single structured response.
 *
 * @property type        the curriculum group name, e.g. `"CORE"` or `"OPTIONAL"`
 * @property description a short explanation of what the group means, or null
 * @property subjects    the list of learning areas that belong to this group
 */
data class SubjectGroupResponse(
    val type: String,
    val description: String?,
    val subjects: List<SubjectSummary>,
)

/**
 * Compact view of a single CBC learning area returned inside a [SubjectGroupResponse].
 *
 * @property name        the full official name, e.g. `"English and Literature"`
 * @property symbol      the short timetable code, e.g. `"ENG"`
 * @property description a brief description of what the learning area covers, or null
 */
data class SubjectSummary(
    val name: String,
    val symbol: String,
    val description: String?,
)