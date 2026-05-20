package com.grandtech.service

import com.grandtech.model.Teacher
import com.grandtech.repository.TeacherRepository
import com.grandtech.utils.ApiResponse
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Validates teacher requests and orchestrates calls to [TeacherRepository].
 *
 * Business rules enforced here:
 * - name is required and must not be blank
 * - email is required and must not be blank
 * - at least 1 and at most 2 subjects must be provided via [Teacher.subjectIds]
 */
@ApplicationScoped
class TeacherService {

    /** Repository that executes all Cypher queries for [Teacher] nodes. */
    @Inject
    lateinit var teacherRepository: TeacherRepository

    /**
     * Creates a new teacher linked to [schoolFedUid].
     *
     * Returns an [ApiResponse] with:
     * - 200 and the persisted teacher on success
     * - 400 for missing or invalid fields
     * - 404 if the school does not exist
     */
    fun createTeacher(schoolFedUid: String, teacher: Teacher): ApiResponse<Teacher> {
        if (teacher.name.isNullOrBlank())
            return ApiResponse(400, "Teacher name is required", null)
        if (teacher.email.isNullOrBlank())
            return ApiResponse(400, "Teacher email is required", null)
        if (teacherRepository.existsByEmail(teacher.email))
            return ApiResponse(409, "A teacher with that email already exists", null)
        val subjectIds = teacher.subjectIds ?: emptyList()
        if (subjectIds.isEmpty())
            return ApiResponse(400, "At least one subject is required", null)
        if (subjectIds.size > 2)
            return ApiResponse(400, "A teacher may teach at most 2 subjects", null)

        val created = teacherRepository.createTeacher(schoolFedUid, teacher)
            ?: return ApiResponse(404, "School not found", null)
        return ApiResponse(200, "Teacher created", created)
    }

    /**
     * Returns all teachers belonging to [schoolFedUid], each with their subjects.
     * Returns 200 with an empty list when the school has no teachers.
     */
    fun listTeachers(schoolFedUid: String): ApiResponse<List<Teacher>> =
        ApiResponse(payload = teacherRepository.listTeachers(schoolFedUid))
}