package com.grandtech.model

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * Entity representing a teacher user account.
 *
 * Stored in the `teachers` table, joined to the `users` table via the shared primary key.
 * Profile fields are nullable on creation and filled in by a subsequent profile-update request.
 */
@Entity
@Table(name = "teachers")
@DiscriminatorValue("TEACHER")
class Teacher : User() {

    /** Full name of the teacher. */
    @Column
    var name: String? = null

    /** Contact email address of the teacher. */
    @Column
    var email: String? = null

    /** Teachers Service Commission registration number (optional). */
    @Column(name = "tsc_number")
    var tscNumber: String? = null

    /** Returns the discriminator value identifying this user as a teacher. */
    override fun getType() = "TEACHER"

    companion object : PanacheCompanion<Teacher> {
        /** Finds a teacher by email address, or null if not found. */
        fun findByEmail(email: String): Teacher? = find("email", email).firstResult()
    }
}
