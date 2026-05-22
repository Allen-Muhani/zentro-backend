package com.grandtech.model

import com.fasterxml.jackson.annotation.JsonUnwrapped

/**
 * A [Subject] enriched with school-specific context.
 *
 * [subject] fields are unwrapped into the JSON root so the response looks like
 * a flat Subject object with an extra [teacherCount] field.
 *
 * @property subject      the underlying CBC learning area
 * @property teacherCount number of teachers in the authenticated school that teach this subject
 */
data class SchoolSubject(
    @field:JsonUnwrapped
    val subject: Subject,
    val teacherCount: Int = 0,
)
