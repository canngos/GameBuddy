package com.gamebuddy.notif.domain.service.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Initialises the Firebase Admin SDK at startup.
 *
 * <p>Three changes:
 *
 * <ul>
 *   <li>{@code javax.annotation.PostConstruct} became {@code jakarta.annotation.PostConstruct}.
 *       The {@code javax} namespace is gone in Spring Boot 3+, and the old annotation is
 *       not recognised — this method would simply never have run;
 *   <li>an {@link IOException} was caught and printed to stdout, leaving the service to
 *       start with no Firebase app configured. Every notification then failed at runtime
 *       while the pod reported itself healthy. Startup fails loudly now;
 *   <li>credentials may come from a filesystem path as well as the classpath. Baking a
 *       service-account key into the jar puts the key in the image, in the registry and
 *       in every build directory; in a deployment it should be a mounted secret.
 * </ul>
 */
@Slf4j
@Component
public class FCMInitializer {

    @Value("${firebase.config.path:}")
    private String firebaseConfigPath;

    @Value("${firebase.enabled:true}")
    private boolean enabled;

    @PostConstruct
    public void initialize() {
        if (!enabled) {
            log.warn("Firebase is disabled; notifications will not be delivered");
            return;
        }
        if (firebaseConfigPath == null || firebaseConfigPath.isBlank()) {
            throw new IllegalStateException(
                    "firebase.config.path must point at a service-account JSON file, or set firebase.enabled=false");
        }
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        try (InputStream credentials = openCredentials()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("Firebase application initialised");
        } catch (IOException e) {
            // Failing here stops the container, which is the point: a notif-service that
            // starts without Firebase looks healthy and delivers nothing.
            throw new IllegalStateException("Could not initialise Firebase from " + firebaseConfigPath, e);
        }
    }

    /** Filesystem first, so a Kubernetes secret can be mounted over the configured path. */
    private InputStream openCredentials() throws IOException {
        Path path = Path.of(firebaseConfigPath);
        if (Files.isReadable(path)) {
            return Files.newInputStream(path);
        }
        InputStream fromClasspath = getClass().getClassLoader().getResourceAsStream(firebaseConfigPath);
        if (fromClasspath == null) {
            throw new IOException("Firebase config not found on the filesystem or the classpath");
        }
        return fromClasspath;
    }
}
