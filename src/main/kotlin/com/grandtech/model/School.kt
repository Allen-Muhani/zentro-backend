package com.grandtech.model

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * Entity representing a school user account.
 *
 * Stored in the `schools` table, joined to the `users` table via the shared primary key.
 * Profile fields are nullable on creation and filled in by a subsequent profile-update request.
 */
@Entity
@Table(name = "schools")
@DiscriminatorValue("SCHOOL")
class School : User() {

    /** Registered name of the school. */
    @Column
    var name: String? = null

    /** Official contact email address of the school. */
    @Column
    var email: String? = null

    /** Primary phone number for the school. */
    @Column(name = "phone_number")
    var phoneNumber: String? = null

    /** County in which the school is located. */
    @Column
    var county: String? = null

    /** Sub-county in which the school is located. */
    @Column(name = "sub_county")
    var subCounty: String? = null

    /** Returns the discriminator value identifying this user as a school. */
    override fun getType() = "SCHOOL"

    companion object : PanacheCompanion<School> {
        /** Finds a school by email address, or null if not found. */
        fun findByEmail(email: String): School? = find("email", email).firstResult()
    }
}
