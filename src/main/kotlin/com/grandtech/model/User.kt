package com.grandtech.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Base class for every user account in the Zentro system.
 *
 * Concrete subtypes are [School] and [Teacher], distinguished by the [getType] value.
 * Jackson uses [getType] as the polymorphic discriminator so the correct subtype is
 * selected when the JSON is deserialised.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type",
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    visible = true,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = School::class, name = "SCHOOL"),
    JsonSubTypes.Type(value = Teacher::class, name = "TEACHER"),
)
abstract class User {

    /** Firebase Authentication UID — unique identifier shared with the auth provider. */
    abstract val fedUid: String

    /**
     * Returns the discriminator string included in every JSON response.
     *
     * @return `"SCHOOL"` for school accounts, `"TEACHER"` for teacher accounts
     */
    abstract fun getType(): String
}