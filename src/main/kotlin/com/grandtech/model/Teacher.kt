package com.grandtech.model

/**
 * User account representing a teacher in the Zentro system.
 *
 * Stored in Neo4j as a node with labels `User` and `Teacher`.
 * Profile fields are nullable on creation and populated by a subsequent update request.
 *
 * @property fedUid     Firebase Authentication UID linking this account to the auth provider
 * @property name       full name of the teacher
 * @property email      contact email address of the teacher
 * @property tscNumber  Teachers Service Commission registration number (optional)
 */
data class Teacher(
    override val fedUid: String,
    val name: String? = null,
    val email: String? = null,
    val tscNumber: String? = null,
) : User() {

    /** Returns the discriminator value identifying this user as a teacher. */
    override fun getType() = "TEACHER"
}