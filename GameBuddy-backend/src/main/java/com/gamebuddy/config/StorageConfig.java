package com.gamebuddy.config;

import com.gamebuddy.shared.storage.LocalObjectStorage;
import com.gamebuddy.shared.storage.ObjectStorage;
import com.gamebuddy.shared.storage.R2ObjectStorage;
import java.nio.file.Path;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses where uploaded images are stored.
 *
 * <p>The switch is whether an R2 access key was configured, and nothing else. With one, R2;
 * without one, the local disk. Deliberately not a Spring profile: a contributor who clones
 * this and runs {@code docker compose up} gets a working avatar upload with no accounts to
 * create and no secrets to obtain, and both environments run the same code path. A profile
 * would make the local implementation the one nobody exercises by accident, which is how
 * it rots.
 *
 * <p>The choice is an {@code if} rather than {@code @ConditionalOnProperty} on purpose.
 * That annotation treats an empty value as present, and {@code R2_ACCESS_KEY_ID=} with
 * nothing after it is exactly what an unconfigured {@code .env} looks like — so the
 * annotation would select R2 and fail at the first upload with a signing error, instead of
 * quietly working on disk.
 *
 * <p>The failure mode worth guarding is the reverse: a production deployment falling back
 * to local disk because the variable was missed, where avatars work until the container
 * restarts and then vanish. {@link LocalObjectStorage} logs at WARN on construction and
 * names the variable to set.
 */
@Configuration
@ConfigurationProperties(prefix = "gamebuddy.storage")
@Setter
public class StorageConfig {

    private R2 r2 = new R2();
    private Local local = new Local();

    @Bean
    ObjectStorage objectStorage() {
        if (r2.accessKeyId != null && !r2.accessKeyId.isBlank()) {
            return new R2ObjectStorage(
                    r2.endpoint, r2.accessKeyId, r2.secretAccessKey, r2.uploadsBucket, r2.mediaBucket, r2.publicUrl);
        }
        return new LocalObjectStorage(Path.of(local.directory), local.publicUrl);
    }

    /**
     * Present only when uploads go to the local disk. {@code MediaController} is
     * conditional on it, so the endpoint that serves images out of this process cannot
     * exist in a deployment that has R2 configured.
     */
    @Bean
    @Conditional(LocalStorageCondition.class)
    Object localMediaMarker() {
        return new Object();
    }

    @Setter
    public static class R2 {
        private String endpoint = "";
        private String accessKeyId = "";
        private String secretAccessKey = "";
        private String uploadsBucket = "gamebuddy-uploads";
        private String mediaBucket = "gamebuddy-media";
        private String publicUrl = "";
    }

    @Setter
    public static class Local {
        private String directory = "./.media";
        private String publicUrl = "http://localhost:8080/media";
    }
}
