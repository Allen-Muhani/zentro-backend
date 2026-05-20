package com.grandtech.service

import com.grandtech.model.School
import com.grandtech.repository.SchoolRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Business operations for [School] accounts.
 * All persistence is delegated to [SchoolRepository].
 */
@ApplicationScoped
class SchoolService {

    /** Repository that executes all Cypher queries for [School] nodes. */
    @Inject
    lateinit var schoolRepository: SchoolRepository

    /** Returns the [School] matching [fedUid], or null if no such node exists. */
    fun getSchoolByFedUid(fedUid: String): School? =
        schoolRepository.getSchoolByFedUid(fedUid)

    /** Updates mutable fields of the [School] identified by [fedUid]; returns null if not found. */
    fun updateSchool(fedUid: String, school: School): School? =
        schoolRepository.updateSchool(fedUid, school)
}