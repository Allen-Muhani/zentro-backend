package com.grandtech

import com.grandtech.auth.Authenticated
import com.grandtech.model.Teacher
import com.grandtech.service.SchoolService
import com.grandtech.service.TeacherService
import com.grandtech.utils.ApiResponse
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType

/** REST resource exposing teacher management endpoints for the Zentro API. */
@Path("/school/teacher")
class TeacherResource {

    /** Verifies that the authenticated account belongs to a school. */
    @Inject
    lateinit var schoolService: SchoolService

    /** Validates and persists teacher requests. */
    @Inject
    lateinit var teacherService: TeacherService

    /**
     * Creates a new teacher linked to the authenticated school.
     *
     * Supply [Teacher.subjectIds] with 1–2 subject IDs. All other fields except
     * [Teacher.name] and [Teacher.email] are optional.
     */
    @POST
    @Path("/create")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun createTeacher(
        @Context requestContext: ContainerRequestContext,
        teacher: Teacher,
    ): ApiResponse<Teacher> {
        val fedUid = requestContext.getProperty("fedUid") as String
        schoolService.getSchoolByFedUid(fedUid)
            ?: return ApiResponse(403, "Forbidden: account is not a school", null)
        return teacherService.createTeacher(fedUid, teacher)
    }

    /**
     * Returns all teachers belonging to the authenticated school, ordered by name.
     * Each teacher includes their assigned subjects.
     */
    @GET
    @Path("/list")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    fun listTeachers(
        @Context requestContext: ContainerRequestContext,
    ): ApiResponse<List<Teacher>> {
        val fedUid = requestContext.getProperty("fedUid") as String
        schoolService.getSchoolByFedUid(fedUid)
            ?: return ApiResponse(403, "Forbidden: account is not a school", null)
        return teacherService.listTeachers(fedUid)
    }
}