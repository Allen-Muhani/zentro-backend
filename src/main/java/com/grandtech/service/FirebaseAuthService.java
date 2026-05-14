package com.grandtech.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Verifies Firebase Authentication ID tokens and extracts session details.
 *
 * <p>Inject this service wherever you need to authenticate an incoming request.
 * The decoded {@link FirebaseToken} exposes the user's {@code uid}, email,
 * display name, and any custom claims set in the Firebase console.
 */
@ApplicationScoped
public class FirebaseAuthService {

    /** Prefix that clients attach to the token in the Authorization header. */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Verifies a Firebase ID token and returns the decoded user details.
     *
     * <p>Accepts the raw token with or without the {@code "Bearer "} prefix so
     * callers can pass the Authorization header value directly.
     *
     * @param idToken the ID token string from the client's Authorization header
     * @return the decoded {@link FirebaseToken} containing {@code uid}, email,
     *         display name, and custom claims
     * @throws FirebaseAuthException if the token is invalid, expired, or revoked
     */
    public FirebaseToken verifyToken(final String idToken) throws FirebaseAuthException {
        final String token = idToken.startsWith(BEARER_PREFIX)
                ? idToken.substring(BEARER_PREFIX.length())
                : idToken;
        return FirebaseAuth.getInstance().verifyIdToken(token);
    }
}
