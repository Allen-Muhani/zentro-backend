package com.grandtech.service;

import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.grandtech.model.School;
import com.grandtech.model.Teacher;
import com.grandtech.model.User;
import com.grandtech.utils.ApiResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Business logic for user account management.
 *
 * <p>Handles token verification, user lookup, and registration of both
 * school and teacher accounts. Profile updates are handled separately.
 */
@ApplicationScoped
public class UserService {

    /** Service used to verify Firebase Authentication ID tokens. */
    @Inject
    FirebaseAuthService firebaseAuthService;

    /**
     * Verifies the supplied Firebase ID token and returns the matching user.
     *
     * @param idToken the raw Firebase ID token from the Authorization header
     * @return the {@link User} whose {@code fedUid} matches the token,
     *         or {@code null} if the account has not been registered yet
     * @throws FirebaseAuthException if the token is invalid, expired, or revoked
     */
    public User getUserFromToken(final String idToken) throws FirebaseAuthException {
        final FirebaseToken token = firebaseAuthService.verifyToken(idToken);
        return User.findByFedUid(token.getUid());
    }

    /**
     * Registers a new teacher account linked to the supplied Firebase token.
     *
     * <p>Creates a row in both the {@code users} and {@code teachers} tables.
     * All profile fields are left empty and must be completed via a
     * subsequent profile-update request.
     *
     * @param idToken the raw Firebase ID token from the Authorization header
     * @return status 200 with the new {@link Teacher} as payload on success,
     *         status 502 with "User already in the system!!!" if the fedUid or email is taken,
     *         or status 500 with "Invalid bearer token!!" if the token is invalid or expired
     */
    @Transactional
    public ApiResponse<Teacher> registerTeacher(final String idToken) {
        final FirebaseToken token;
        try {
            token = firebaseAuthService.verifyToken(idToken);
        } catch (FirebaseAuthException e) {
            return new ApiResponse<>(500, "Invalid bearer token!!", null);
        }
        final String fedUid = token.getUid();
        final String email = token.getEmail();
        if (User.findByFedUid(fedUid) != null
                || Teacher.findByEmail(email) != null
                || School.findByEmail(email) != null) {
            return new ApiResponse<>(502, "User already in the system!!!", null);
        }
        final Teacher teacher = new Teacher();
        teacher.fedUid = fedUid;
        teacher.name = token.getName();
        teacher.email = email;
        teacher.persist();
        return new ApiResponse<>(200, "Success", teacher);
    }

    /**
     * Registers a new school account linked to the supplied Firebase token.
     *
     * <p>Creates a row in both the {@code users} and {@code schools} tables.
     * All profile fields are left empty and must be completed via a
     * subsequent profile-update request.
     *
     * @param idToken the raw Firebase ID token from the Authorization header
     * @return status 200 with the new {@link School} as payload on success,
     *         status 502 with "User already in the system!!!" if the fedUid or email is taken,
     *         or status 500 with "Invalid bearer token!!" if the token is invalid or expired
     */
    @Transactional
    public ApiResponse<School> registerSchool(final String idToken) {
        final FirebaseToken token;
        try {
            token = firebaseAuthService.verifyToken(idToken);
        } catch (FirebaseAuthException e) {
            return new ApiResponse<>(500, "Invalid bearer token!!", null);
        }
        final String fedUid = token.getUid();
        final String email = token.getEmail();
        if (User.findByFedUid(fedUid) != null
                || Teacher.findByEmail(email) != null
                || School.findByEmail(email) != null) {
            return new ApiResponse<>(502, "School already in the system", null);
        }
        final School school = new School();
        school.fedUid = fedUid;
        school.persist();
        return new ApiResponse<>(200, "Success", school);
    }
}
