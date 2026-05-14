package com.grandtech.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Entity representing a teacher user account.
 *
 * <p>Stored in the {@code teachers} table, joined to the {@code users} table
 * via the shared primary key. Profile fields are nullable on creation and
 * filled in by a subsequent profile-update request.
 */
@Entity
@Table(name = "teachers")
@DiscriminatorValue("TEACHER")
public class Teacher extends User {

    /** Full name of the teacher. */
    @Column
    public String name;

    /** Contact email address of the teacher. */
    @Column
    public String email;

    /** Teachers Service Commission registration number (optional). */
    @Column(name = "tsc_number")
    public String tscNumber;

    /**
     * Finds a teacher by email address.
     *
     * @param email the email address to look up
     * @return the matching {@link Teacher}, or {@code null} if not found
     */
    public static Teacher findByEmail(final String email) {
        return find("email", email).firstResult();
    }

    /**
     * Returns the discriminator value identifying this user as a teacher.
     *
     * @return {@code "TEACHER"}
     */
    @Override
    public String getType() {
        return "TEACHER";
    }
}
