package com.grandtech.model

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntity
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorColumn
import jakarta.persistence.DiscriminatorType
import jakarta.persistence.Entity
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.Table

/**
 * Base entity for all user accounts in the Zentro system.
 *
 * Uses JPA joined-table inheritance: the `users` table holds the shared columns
 * (`id`, `fed_uid`, `type`) while [School] and [Teacher] each own a separate table.
 */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
abstract class User : PanacheEntity() {

    /** Firebase Authentication UID — unique identifier shared with the auth provider. */
    @Column(name = "fed_uid", unique = true, nullable = false)
    lateinit var fedUid: String

    /** Returns the discriminator value (`"SCHOOL"` or `"TEACHER"`), included in every JSON response. */
    abstract fun getType(): String

    companion object : PanacheCompanion<User> {
        /** Finds a user by their Firebase Authentication UID, or null if not found. */
        fun findByFedUid(fedUid: String): User? = find("fedUid", fedUid).firstResult()
    }
}
