package com.grandtech.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;

/**
 * Initializes the Firebase Admin SDK once at application startup.
 *
 * <p>Reads the service-account credentials from the path configured via
 * {@code firebase.credentials.path} (or the {@code FIREBASE_CREDENTIALS_PATH}
 * environment variable). The project ID is inferred from the credentials file
 * but can be overridden with the {@code FIREBASE_PROJECT_ID} environment variable.
 */
@ApplicationScoped
public class FirebaseConfig {

    /** Filesystem path to the Firebase service-account JSON credentials file. */
    @ConfigProperty(name = "firebase.credentials.path", defaultValue = "firebase-service-account.json")
    String credentialsPath;

    /** Optional Firebase project ID; inferred from the credentials file when absent. */
    @ConfigProperty(name = "firebase.project-id")
    Optional<String> projectId;

    /**
     * Loads Firebase credentials and initialises the {@link FirebaseApp} singleton.
     * If the app is already initialised this method is a no-op.
     *
     * @param event the Quarkus startup event (injected by CDI)
     */
    void onStart(@Observes final StartupEvent event) {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }
        try {
            final FileInputStream credentials = new FileInputStream(credentialsPath);
            final FirebaseOptions.Builder builder = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials));
            projectId.ifPresent(builder::setProjectId);
            FirebaseApp.initializeApp(builder.build());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialise Firebase Admin SDK: " + e.getMessage(), e);
        }
    }
}
