package com.grandtech.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Entity representing a school user account.
 *
 * <p>Stored in the {@code schools} table, joined to the {@code users} table
 * via the shared primary key. Profile fields are nullable on creation and
 * filled in by a subsequent profile-update request.
 */
@Entity
@Table(name = "schools")
@DiscriminatorValue("SCHOOL")
public class School extends User {

    /** Registered name of the school. */
    @Column
    public String name;

    /** Official contact email address of the school. */
    @Column
    public String email;

    /** Primary phone number for the school. */
    @Column(name = "phone_number")
    public String phoneNumber;

    /** County in which the school is located. */
    @Column
    public String county;

    /** Sub-county in which the school is located. */
    @Column(name = "sub_county")
    public String subCounty;

    /**
     * Finds a school by email address.
     *
     * @param email the email address to look up
     * @return the matching {@link School}, or {@code null} if not found
     */
    public static School findByEmail(final String email) {
        return find("email", email).firstResult();
    }

    /**
     * Returns the discriminator value identifying this user as a school.
     *
     * @return {@code "SCHOOL"}
     */
    @Override
    public String getType() {
        return "SCHOOL";
    }
}
