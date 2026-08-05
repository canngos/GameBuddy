package com.gamebuddy.billing.config;

import com.gamebuddy.billing.domain.AppleReceiptVerifier;
import com.gamebuddy.billing.domain.GooglePlayReceiptVerifier;
import com.gamebuddy.billing.domain.ReceiptVerifier;
import com.gamebuddy.billing.domain.SandboxReceiptVerifier;
import com.gamebuddy.billing.infrastructure.entity.PurchasePlatform;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Wires the receipt verifiers.
 *
 * <p>Nothing is registered by default, and that is the important property:
 * {@code PurchaseService} rejects any platform it has no verifier for, so a deployment that
 * has not been configured refuses to sell rather than giving product away. Every path here
 * has to be switched on deliberately.
 *
 * <p>Three modes, each explicitly enabled:
 * <ul>
 *   <li>{@code gamebuddy.billing.google.enabled} — real Play verification;
 *   <li>{@code gamebuddy.billing.apple.enabled} — real App Store verification;
 *   <li>{@code gamebuddy.billing.sandbox} — verifies nothing, for local work only.
 * </ul>
 */
@Slf4j
@Configuration
public class BillingConfig {

    /** Shared by the verifiers; connection reuse matters on the purchase path. */
    @Bean
    public HttpClient billingHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * Google Play, verified against the Android Publisher API.
     *
     * <p>Needs a service-account JSON with the "View financial data" permission on the Play
     * Console, and the app's package name.
     */
    @Bean
    @ConditionalOnProperty(name = "gamebuddy.billing.google.enabled", havingValue = "true")
    public ReceiptVerifier googlePlayReceiptVerifier(Environment env, HttpClient http, Clock clock) {
        String packageName = required(env, "gamebuddy.billing.google.package-name");
        Path key = Path.of(required(env, "gamebuddy.billing.google.service-account-file"));
        if (!Files.isReadable(key)) {
            throw new IllegalStateException("Google service account file is not readable: " + key);
        }
        log.info("Google Play receipt verification is enabled for {}", packageName);
        return new GooglePlayReceiptVerifier(packageName, key, http, clock);
    }

    /**
     * Apple, verified by checking the StoreKit 2 signature.
     *
     * <p>Needs the bundle id and Apple's root certificates on disk. The roots are shipped
     * with the deployment rather than fetched: a trust anchor downloaded at runtime is not
     * a trust anchor.
     */
    @Bean
    @ConditionalOnProperty(name = "gamebuddy.billing.apple.enabled", havingValue = "true")
    public ReceiptVerifier appleReceiptVerifier(Environment env, Clock clock) {
        String bundleId = required(env, "gamebuddy.billing.apple.bundle-id");
        Path dir = Path.of(required(env, "gamebuddy.billing.apple.root-certificate-dir"));

        List<byte[]> roots = new ArrayList<>();
        try (var files = Files.list(dir)) {
            for (Path p : files.toList()) {
                if (Files.isRegularFile(p)) {
                    roots.add(Files.readAllBytes(p));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read Apple root certificates from " + dir, e);
        }
        log.info("Apple receipt verification is enabled for {} with {} root certificate(s)", bundleId, roots.size());
        return new AppleReceiptVerifier(bundleId, roots, clock);
    }

    // -- local development only ----------------------------------------------

    /**
     * Verifies nothing. Anyone who can reach the endpoint can grant themselves Gold.
     *
     * <p>Registered only when {@code gamebuddy.billing.sandbox=true}, which defaults to
     * false, and it warns on every startup and every call so an environment running it
     * cannot look normal in the logs.
     */
    @Bean
    @ConditionalOnProperty(name = "gamebuddy.billing.sandbox", havingValue = "true")
    public ReceiptVerifier sandboxAppleVerifier(Clock clock) {
        return new SandboxReceiptVerifier(PurchasePlatform.APPLE_APP_STORE, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "gamebuddy.billing.sandbox", havingValue = "true")
    public ReceiptVerifier sandboxGoogleVerifier(Clock clock) {
        return new SandboxReceiptVerifier(PurchasePlatform.GOOGLE_PLAY, clock);
    }

    private static String required(Environment env, String key) {
        String value = env.getProperty(key);
        if (value == null || value.isBlank()) {
            // Fail at startup. A verifier missing half its configuration would either
            // reject every purchase or, worse, behave unpredictably.
            throw new IllegalStateException(key + " must be set when the matching verifier is enabled");
        }
        return value;
    }
}
