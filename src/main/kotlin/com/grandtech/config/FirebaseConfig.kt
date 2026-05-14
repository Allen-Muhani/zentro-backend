package com.grandtech.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.io.FileInputStream
import java.util.Optional

/**
 * Initializes the Firebase Admin SDK once at application startup.
 *
 * Reads the service-account credentials from the path configured via
 * `firebase.credentials.path` (or the `FIREBASE_CREDENTIALS_PATH` environment variable).
 * The project ID is inferred from the credentials file but can be overridden via
 * `FIREBASE_PROJECT_ID`.
 */
@ApplicationScoped
class FirebaseConfig {

    /** Filesystem path to the Firebase service-account JSON credentials file. */
    @ConfigProperty(name = "firebase.credentials.path", defaultValue = "firebase-service-account.json")
    lateinit var credentialsPath: String

    /** Optional Firebase project ID; inferred from the credentials file when absent. */
    @ConfigProperty(name = "firebase.project-id")
    lateinit var projectId: Optional<String>

    /**
     * Loads Firebase credentials and initialises the [FirebaseApp] singleton.
     * If the app is already initialised this method is a no-op.
     *
     * @param event the Quarkus startup event (injected by CDI)
     */
    fun onStart(@Observes event: StartupEvent) {
        if (FirebaseApp.getApps().isNotEmpty()) return
        try {
            val builder = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(FileInputStream(credentialsPath)))
            projectId.ifPresent { builder.setProjectId(it) }
            FirebaseApp.initializeApp(builder.build())
        } catch (e: Exception) {
            throw IllegalStateException("Failed to initialise Firebase Admin SDK: ${e.message}", e)
        }
    }
}
