package com.grandtech.model

/**
 * User account representing a school in the Zentro system.
 *
 * Stored in Neo4j as a node with labels `User` and `School`.
 * Profile fields are nullable on creation and populated by a subsequent update request.
 *
 * @property fedUid       Firebase Authentication UID linking this account to the auth provider
 * @property name         registered name of the school
 * @property email        official contact email address of the school
 * @property phoneNumber  primary phone number for the school
 * @property county       county in which the school is located
 * @property subCounty    sub-county in which the school is located
 */
data class School(
    override val fedUid: String,
    val name: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val county: String? = null,
    val subCounty: String? = null,
) : User() {

    /** Returns the discriminator value identifying this user as a school. */
    override fun getType() = "SCHOOL"
}