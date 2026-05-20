package com.grandtech.service.user

import com.google.firebase.auth.FirebaseToken
import com.grandtech.auth.FirebaseAuthService
import com.grandtech.repository.UserRepository
import com.grandtech.service.UserService
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.neo4j.driver.Driver
import java.lang.reflect.Constructor

/**
 * Shared test infrastructure for [UserService] integration tests.
 *
 * Subclasses inherit injected fields, the UID tracker, and [cleanUp].
 * [FirebaseAuthService] is replaced with a Mockito mock so token verification
 * is controlled by each test. All tests run against the configured Neo4j instance.
 */
@QuarkusTest
abstract class UserServiceTestBase {

    /** The service under test. */
    @Inject
    lateinit var userService: UserService

    /** Direct repository access for seeding pre-conditions. */
    @Inject
    lateinit var userRepository: UserRepository

    /** Raw driver used for teardown queries. */
    @Inject
    lateinit var driver: Driver

    /** Mockito-controlled stand-in for Firebase token verification. */
    @InjectMock
    lateinit var firebaseAuthService: FirebaseAuthService

    private val trackedUids = mutableSetOf<String>()

    companion object {
        /**
         * Creates a real [FirebaseToken] via reflection — its constructor is package-private.
         *
         * @param uid   Firebase UID to embed in the token claims
         * @param email email address to embed in the token claims
         * @param name  display name to embed in the token claims
         * @return a [FirebaseToken] populated with the supplied claims
         */
        fun buildToken(uid: String, email: String, name: String): FirebaseToken {
            val claims: Map<String, Any> = mapOf("sub" to uid, "email" to email, "name" to name)
            val ctor: Constructor<FirebaseToken> =
                FirebaseToken::class.java.getDeclaredConstructor(Map::class.java)
            ctor.isAccessible = true
            return ctor.newInstance(claims)
        }
    }

    /**
     * Registers Firebase UIDs that the current test may write to the graph.
     * All registered UIDs are removed by [cleanUp] after the test finishes.
     *
     * @param uids one or more Firebase UIDs expected to be touched by this test
     */
    protected fun track(vararg uids: String) {
        trackedUids.addAll(uids.toList())
    }

    /**
     * Deletes only the User nodes whose `fedUid` was registered via [track],
     * leaving all other graph data untouched.
     */
    @AfterEach
    fun cleanUp() {
        if (trackedUids.isNotEmpty()) {
            driver.session().use { session ->
                session.run(
                    "MATCH (u:User) WHERE u.fedUid IN \$uids DETACH DELETE u",
                    mapOf("uids" to trackedUids.toList()),
                )
            }
            trackedUids.clear()
        }
    }
}
