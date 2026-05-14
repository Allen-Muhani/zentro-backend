package com.grandtech.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

/**
 * Base entity for all user accounts in the Zentro system.
 *
 * <p>Uses JPA joined-table inheritance: the {@code users} table holds the
 * shared columns ({@code id}, {@code fed_uid}, {@code type}) while
 * {@link School} and {@link Teacher} each own a separate table with
 * their type-specific columns.
 */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public abstract class User extends PanacheEntity {

    /** Firebase Authentication UID — unique identifier shared with the auth provider. */
    @Column(name = "fed_uid", unique = true, nullable = false)
    public String fedUid;

    /**
     * Returns the user type discriminator value ({@code "SCHOOL"} or {@code "TEACHER"}).
     * Included automatically in every JSON response as the {@code type} field.
     *
     * @return the string discriminator for this user subtype
     */
    public abstract String getType();

    /**
     * Finds a user by their Firebase Authentication UID.
     *
     * @param fedUid the Firebase UID to look up
     * @return the matching {@link User}, or {@code null} if not found
     */
    public static User findByFedUid(final String fedUid) {
        return find("fedUid", fedUid).firstResult();
    }
}
