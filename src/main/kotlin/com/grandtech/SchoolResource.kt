package com.grandtech

import com.grandtech.auth.Authenticated
import com.grandtech.model.School
import com.grandtech.model.Subject
import com.grandtech.service.SchoolService
import com.grandtech.service.SubjectRepository
import com.grandtech.utils.ApiResponse
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType

/** REST resource exposing school account endpoints for the Zentro API. */
@Path("/school")
class SchoolResource {

    @Inject
    /** Provides access to the static CBC JSS subject catalogue. */
    lateinit var subjectRepository: SubjectRepository

    @Inject
    /** Handles Neo4j read and write operations for [com.grandtech.model.School] nodes. */
    lateinit var schoolService: SchoolService

    /**
     * Returns a confirmation that the school endpoint is reachable.
     *
     * @return an [ApiResponse] with a status message as payload
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getSchool(): ApiResponse<String> = ApiResponse(200, "success", "School endpoint is live")

    /**
     * Lists all CBC JSS subjects.
     *
     * Data is seeded at startup and reflects the subjects defined by the Kenya
     * Institute of Curriculum Development (KICD) for Grades 7–9.
     *
     * @return an [ApiResponse] whose payload is a list of [Subject] objects.
     */
    @GET
    @Path("/subjects")
    @Produces(MediaType.APPLICATION_JSON)
    fun listSubjects(): ApiResponse<List<Subject>> =
        ApiResponse(200, "Success", subjectRepository.listAll())

    /**
     * Returns the authenticated school's profile.
     *
     * The [Authenticated] guard verifies the bearer token and ensures the Firebase
     * UID belongs to a registered user before this handler runs.
     *
     * @param requestContext the JAX-RS request context carrying the `fedUid` property
     *                       set by [com.grandtech.auth.AuthFilter]
     * @return an [ApiResponse] carrying the [School] on success, or 403 if the token
     *         belongs to a non-school account
     */
    @GET
    @Path("/profile")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    fun getSchoolDetails(@Context requestContext: ContainerRequestContext): ApiResponse<School> {
        val fedUid = requestContext.getProperty("fedUid") as String
        val school = schoolService.getSchoolByFedUid(fedUid)
        return if (school != null) {
            ApiResponse(200, "Success", school)
        } else {
            ApiResponse(403, "Forbidden: account is not a school", null)
        }
    }

    /**
     * Updates the mutable fields of the authenticated school's profile.
     *
     * Only [School.name], [School.phoneNumber], [School.county], and [School.subCounty]
     * are applied. Identity fields ([School.fedUid] and [School.email]) are never
     * overwritten. Null fields leave the stored value unchanged.
     *
     * @param requestContext the JAX-RS request context carrying the `fedUid` property
     *                       set by [com.grandtech.auth.AuthFilter]
     * @param school         the [School] object containing the fields to update
     * @return an [ApiResponse] carrying the updated [School] on success, or 404 if
     *         no school node is found for the authenticated UID
     */
    @PATCH
    @Path("/profile")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun updateSchoolDetails(
        @Context requestContext: ContainerRequestContext,
        school: School,
    ): ApiResponse<School> {
        val fedUid = requestContext.getProperty("fedUid") as String
        val updated = schoolService.updateSchool(fedUid, school)
        return if (updated != null) {
            ApiResponse(200, "Success", updated)
        } else {
            ApiResponse(404, "School not found", null)
        }
    }
}