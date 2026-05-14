package com.grandtech.service

import com.google.firebase.auth.FirebaseAuthException
import com.grandtech.model.School
import com.grandtech.model.Teacher
import com.grandtech.model.User
import com.grandtech.utils.ApiResponse
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional

/**
 * Business logic for user account management.
 *
 * Handles token verification, user lookup, and registration of both
 * school and teacher accounts. Profile updates are handled separately.
 */
@ApplicationScoped
class UserService {

    /** Service used to verify Firebase Authentication ID tokens. */
    @Inject
    lateinit var firebaseAuthService: FirebaseAuthService

    /**
     * Verifies the supplied Firebase ID token and returns the matching user.
     *
     * @param idToken the raw Firebase ID token from the Authorization header
     * @return the [User] whose `fedUid` matches the token, or null if not registered
     * @throws FirebaseAuthException if the token is invalid, expired, or revoked
     */
    @Throws(FirebaseAuthException::class)
    fun getUserFromToken(idToken: String): User? {
        val token = firebaseAuthService.verifyToken(idToken)
        return User.findByFedUid(token.uid)
    }

    /**
     * Registers a new teacher account linked to the supplied Firebase token.
     *
     * @param idToken the raw Firebase ID token from the Authorization header
     * @return status 200 with the new [Teacher] on success,
     *         502 if the fedUid or email is already taken,
     *         or 500 if the token is invalid or expired
     */
    @Transactional
    fun registerTeacher(idToken: String): ApiResponse<Teacher> {
        val token = try {
            firebaseAuthService.verifyToken(idToken)
        } catch (e: FirebaseAuthException) {
            return ApiResponse(500, "Invalid bearer token!!", null)
        }
        val fedUid = token.uid
        val email = token.email
        if (User.findByFedUid(fedUid) != null
            || Teacher.findByEmail(email) != null
            || School.findByEmail(email) != null
        ) {
            return ApiResponse(502, "User already in the system!!!", null)
        }
        val teacher = Teacher().apply {
            this.fedUid = fedUid
            this.name = token.name
            this.email = email
        }
        teacher.persist()
        return ApiResponse(200, "Success", teacher)
    }

    /**
     * Registers a new school account linked to the supplied Firebase token.
     *
     * @param idToken the raw Firebase ID token from the Authorization header
     * @return status 200 with the new [School] on success,
     *         502 if the fedUid or email is already taken,
     *         or 500 if the token is invalid or expired
     */
    @Transactional
    fun registerSchool(idToken: String): ApiResponse<School> {
        val token = try {
            firebaseAuthService.verifyToken(idToken)
        } catch (e: FirebaseAuthException) {
            return ApiResponse(500, "Invalid bearer token!!", null)
        }
        val fedUid = token.uid
        val email = token.email
        if (User.findByFedUid(fedUid) != null
            || Teacher.findByEmail(email) != null
            || School.findByEmail(email) != null
        ) {
            return ApiResponse(502, "School already in the system", null)
        }
        val school = School().apply { this.fedUid = fedUid }
        school.persist()
        return ApiResponse(200, "Success", school)
    }
}
