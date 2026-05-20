package com.grandtech.service

import com.google.firebase.auth.FirebaseAuthException
import com.grandtech.auth.FirebaseAuthService
import com.grandtech.model.School
import com.grandtech.model.User
import com.grandtech.repository.UserRepository
import com.grandtech.utils.ApiResponse
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * Business logic for user account management.
 *
 * Handles Firebase token verification, user lookup, and registration of school accounts.
 * All persistence is delegated to [UserRepository].
 */
@ApplicationScoped
class UserService {

    /** Service used to verify Firebase Authentication ID tokens. */
    @Inject
    lateinit var firebaseAuthService: FirebaseAuthService

    /** Repository that executes Neo4j reads and writes for user nodes. */
    @Inject
    lateinit var userRepository: UserRepository

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
        return userRepository.findByFedUid(token.uid)
    }

    /**
     * Registers a new school account linked to the supplied Firebase token.
     *
     * @param idToken the raw Firebase ID token from the Authorization header
     * @return status 200 with the new [School] on success,
     *         502 if the fedUid or email is already taken,
     *         or 500 if the token is invalid or expired
     */
    fun registerSchool(idToken: String): ApiResponse<School> {
        val token = try {
            firebaseAuthService.verifyToken(idToken)
        } catch (e: FirebaseAuthException) {
            return ApiResponse(500, "Invalid bearer token!!", null)
        }
        val fedUid = token.uid
        val email = token.email
        if (userRepository.existsByFedUid(fedUid) || userRepository.schoolExistsByEmail(email)) {
            return ApiResponse(502, "School already in the system", null)
        }
        val school = School(fedUid = fedUid)
        userRepository.saveSchool(school)
        return ApiResponse(200, "Success", school)
    }
}