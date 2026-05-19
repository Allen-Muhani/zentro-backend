package com.grandtech

import com.grandtech.model.Subject
import com.grandtech.service.SubjectRepository
import com.grandtech.utils.ApiResponse
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

/** REST resource exposing school-related endpoints for the Zentro API. */
@Path("/schools")
class SchoolResource {

    /** Repository that queries Neo4j for subject and subject-type nodes. */
    @Inject
    lateinit var subjectRepository: SubjectRepository

    /**
     * Returns a confirmation that the schools endpoint is reachable.
     *
     * @return an [ApiResponse] with a status message as payload
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getSchools(): ApiResponse<String> = ApiResponse(200, "success", "Schools endpoint is live")

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
}
