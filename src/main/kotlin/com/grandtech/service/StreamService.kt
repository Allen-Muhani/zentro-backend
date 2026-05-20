package com.grandtech.service

import com.grandtech.model.Stream
import com.grandtech.repository.StreamRepository
import com.grandtech.utils.ApiResponse
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Validates stream requests and orchestrates calls to [StreamRepository].
 *
 * This layer owns all business rules:
 * - grade level must be 7, 8, or 9 (required on create, validated if provided on update)
 * - stream name is required on create, must not be blank if provided on update
 * - a room may only be assigned to one stream at a time
 * - a teacher may only be form teacher for one stream at a time
 */
@ApplicationScoped
class StreamService {

    /** Repository that executes all Cypher queries for [com.grandtech.model.Stream] nodes. */
    @Inject
    lateinit var streamRepository: StreamRepository

    /**
     * Creates or updates a stream depending on whether [Stream.id] is present.
     *
     * On create (`id` is null) a new node is generated and linked to the school.
     * On update (`id` is provided) only supplied fields are changed; omitting a field
     * leaves the stored value and existing room/teacher relationships untouched.
     * Supplying a new [Stream.homeRoom] or [Stream.formTeacher] replaces the existing link.
     *
     * Returns an [ApiResponse] with:
     * - 200 on success
     * - 400 for invalid field values
     * - 404 when the school or stream cannot be found
     * - 409 when the requested room or teacher is already assigned to another stream
     */
    fun upsertStream(schoolFedUid: String, stream: Stream): ApiResponse<Stream> {
        val level = stream.gradeLevel
        if (stream.id == null) {
            if (level == null || level !in 7..9)
                return ApiResponse(400, "gradeLevel must be 7, 8, or 9", null)
            if (stream.name.isNullOrBlank())
                return ApiResponse(400, "Stream name is required", null)
        } else {
            if (level != null && level !in 7..9)
                return ApiResponse(400, "gradeLevel must be 7, 8, or 9", null)
            if (stream.name != null && stream.name.isBlank())
                return ApiResponse(400, "Stream name must not be blank", null)
        }

        val homeRoomId = stream.homeRoom?.id
        if (homeRoomId != null && streamRepository.isRoomTaken(homeRoomId, excludeStreamId = stream.id))
            return ApiResponse(409, "Room is already assigned to another stream")

        val teacherFedUid = stream.formTeacher?.fedUid
        if (teacherFedUid != null && streamRepository.isTeacherTaken(teacherFedUid, excludeStreamId = stream.id))
            return ApiResponse(409, "Teacher is already a form teacher for another stream")

        val streamId = if (stream.id == null) {
            streamRepository.createStreamNode(schoolFedUid, stream)
                ?: return ApiResponse(404, "School not found")
        } else {
            streamRepository.updateStreamNode(schoolFedUid, stream)
                ?: return ApiResponse(404, "Stream not found")
        }

        homeRoomId?.let { streamRepository.replaceHomeRoom(streamId, it) }
        teacherFedUid?.let { streamRepository.replaceFormTeacher(streamId, it) }

        val result = streamRepository.fetchStream(streamId)
            ?: return ApiResponse(404, "Stream not found")
        return ApiResponse(message = if (stream.id == null) "Stream created" else "Stream updated", payload = result)
    }

    /**
     * Returns all streams belonging to [schoolFedUid], each with its optional
     * `HOME_ROOM` and `FORM_TEACHER` relationships, ordered by grade level then name.
     * Returns a 200 with an empty list when the school has no streams.
     */
    fun listStreams(schoolFedUid: String): ApiResponse<List<Stream>> =
        ApiResponse(payload = streamRepository.listStreams(schoolFedUid))
}
