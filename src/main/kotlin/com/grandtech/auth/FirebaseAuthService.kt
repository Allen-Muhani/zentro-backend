package com.grandtech.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseToken
import jakarta.enterprise.context.ApplicationScoped

/**
 * Verifies Firebase Authentication ID tokens and extracts session details.
 *
 * Inject this service wherever you need to authenticate an incoming request.
 * The decoded [com.google.firebase.auth.FirebaseToken] exposes the user's uid, email, display name,
 * and any custom claims set in the Firebase console.
 */
@ApplicationScoped
class FirebaseAuthService {

    /**
     * Verifies a Firebase ID token and returns the decoded user details.
     *
     * Accepts the raw token with or without the `Bearer ` prefix so callers
     * can pass the Authorization header value directly.
     *
     * @param idToken the ID token string from the client's Authorization header
     * @return the decoded [com.google.firebase.auth.FirebaseToken]
     * @throws com.google.firebase.auth.FirebaseAuthException if the token is invalid, expired, or revoked
     */
    @Throws(FirebaseAuthException::class)
    fun verifyToken(idToken: String): FirebaseToken =
        FirebaseAuth.getInstance().verifyIdToken(idToken.removePrefix("Bearer "))
}
