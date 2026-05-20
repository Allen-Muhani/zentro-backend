package com.grandtech

import com.grandtech.auth.Authenticated
import com.grandtech.model.Teacher
import com.grandtech.service.SchoolService
import com.grandtech.service.TeacherService
import com.grandtech.utils.ApiResponse
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
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

    /** Deletes the teacher identified by [id] belonging to the authenticated school. */
    @DELETE
    @Path("/delete/{id}")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    fun deleteTeacher(
        @Context requestContext: ContainerRequestContext,
        @PathParam("id") id: String,
    ): ApiResponse<Nothing> {
        val fedUid = requestContext.getProperty("fedUid") as String
        schoolService.getSchoolByFedUid(fedUid)
            ?: return ApiResponse(403, "Forbidden: account is not a school", null)
        return teacherService.deleteTeacher(fedUid, id)
    }

    /**
     * Updates an existing teacher's details for the authenticated school.
     *
     * Supply the teacher's [Teacher.id] plus any fields to change. Omitted fields
     * keep their current values. If [Teacher.subjectIds] is included it fully replaces
     * the current subject assignments (1–2 entries required).
     */
    @PATCH
    @Path("/update")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun updateTeacher(
        @Context requestContext: ContainerRequestContext,
        teacher: Teacher,
    ): ApiResponse<Teacher> {
        val fedUid = requestContext.getProperty("fedUid") as String
        schoolService.getSchoolByFedUid(fedUid)
            ?: return ApiResponse(403, "Forbidden: account is not a school", null)
        return teacherService.updateTeacher(fedUid, teacher)
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