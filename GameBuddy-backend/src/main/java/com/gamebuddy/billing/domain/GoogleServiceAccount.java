package com.gamebuddy.billing.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;

/**
 * Exchanges a Google service-account key for an access token.
 *
 * <p>The Android Publisher API needs an OAuth bearer token, and a service account gets one
 * by signing a short-lived assertion with its own private key and trading that in. Written
 * against the JDK HTTP client rather than pulling in the Google API client, which brings a
 * large transitive tree to make one request.
 *
 * <p>Tokens are cached until shortly before they expire. Google issues them for an hour;
 * requesting a fresh one per purchase would add a round trip to every redemption and would
 * eventually get rate limited.
 */
@Slf4j
class GoogleServiceAccount {

    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String SCOPE = "https://www.googleapis.com/auth/androidpublisher";
    private static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";

    /** Renew this far before expiry so a token never expires mid-request. */
    private static final Duration RENEW_BEFORE = Duration.ofMinutes(5);

    private final String clientEmail;
    private final PrivateKey privateKey;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    private volatile String cachedToken;
    private volatile Instant cachedUntil = Instant.EPOCH;

    GoogleServiceAccount(Path serviceAccountJson, HttpClient http) {
        this.http = http;
        try {
            JsonNode key = json.readTree(Files.readString(serviceAccountJson, StandardCharsets.UTF_8));
            this.clientEmail = key.path("client_email").asText(null);
            String pem = key.path("private_key").asText(null);
            if (clientEmail == null || pem == null) {
                throw new IllegalStateException(
                        "Service account JSON at " + serviceAccountJson + " has no client_email or private_key");
            }
            this.privateKey = parsePkcs8(pem);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the Google service account at " + serviceAccountJson, e);
        }
    }

    private static PrivateKey parsePkcs8(String pem) {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (Exception e) {
            throw new IllegalStateException("The service account private key is not a valid PKCS#8 RSA key", e);
        }
    }

    /** A valid access token, from cache when one is still good. */
    String accessToken(Instant now) {
        String token = cachedToken;
        if (token != null && now.isBefore(cachedUntil)) {
            return token;
        }
        synchronized (this) {
            // Re-checked: several redemptions can arrive at once and only one should mint.
            if (cachedToken != null && now.isBefore(cachedUntil)) {
                return cachedToken;
            }
            Fetched fetched = fetch(now);
            cachedToken = fetched.token();
            cachedUntil = now.plusSeconds(fetched.expiresIn()).minus(RENEW_BEFORE);
            return cachedToken;
        }
    }

    private Fetched fetch(Instant now) {
        String assertion = Jwts.builder()
                .issuer(clientEmail)
                .audience()
                .add(TOKEN_URI)
                .and()
                .claim("scope", SCOPE)
                .issuedAt(Date.from(now))
                // Google rejects assertions valid for more than an hour.
                .expiration(Date.from(now.plus(Duration.ofMinutes(30))))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();

        String form = "grant_type=" + URLEncoder.encode(GRANT_TYPE, StandardCharsets.UTF_8) + "&assertion="
                + URLEncoder.encode(assertion, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URI))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // Never log the body: it can echo parts of the assertion.
                throw new IllegalStateException(
                        "Google refused the service account assertion: HTTP " + response.statusCode());
            }
            JsonNode body = json.readTree(response.body());
            String token = body.path("access_token").asText(null);
            if (token == null) {
                throw new IllegalStateException("Google returned no access_token");
            }
            return new Fetched(token, body.path("expires_in").asLong(3600L));
        } catch (IOException e) {
            throw new IllegalStateException("Could not reach Google to exchange the service account assertion", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while exchanging the service account assertion", e);
        }
    }

    private record Fetched(String token, long expiresIn) {}
}
