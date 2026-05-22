package com.grandtech.model

/**
 * A teaching staff member registered by a school for timetable scheduling.
 *
 * Stored as `(:Teacher)` linked to the school via `HAS_TEACHER` and to
 * their assigned learning areas via `TEACHES`.
 *
 * @property id                Neo4j-generated UUID; null before persistence
 * @property name              full name of the teacher
 * @property email             contact email address
 * @property phone             optional phone number
 * @property tscNumber         Teachers Service Commission registration number (optional)
 * @property maxPeriodsPerWeek upper bound on weekly lessons; defaults to 23
 * @property maxPeriodsPerDay  upper bound on daily lessons; defaults to 6
 * @property subjectIds        subject IDs — supplied on create/update (1–2 entries); returned on all read responses
 */
data class Teacher(
    val id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val tscNumber: String? = null,
    val maxPeriodsPerWeek: Int? = null,
    val maxPeriodsPerDay: Int? = null,
    val subjectIds: List<String>? = null,
)