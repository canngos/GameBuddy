package com.gamebuddy.profile.domain.coin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Proves that a rewarded-ad callback really came from AdMob.
 *
 * <p><b>This class is the whole security model for rewarded video.</b> The callback URL is
 * public and carries no session — AdMob's servers call it, not the app, so there is no
 * token to send. Anyone on the internet can request it. What nobody else can do is produce
 * Google's signature over the query string, so that signature is the authentication, and if
 * this verification is wrong the endpoint is an unlimited coin faucet for anyone who reads
 * the URL out of a proxy.
 *
 * <p>The signed content is a specific, easily-mistaken thing: the raw query string from the
 * first parameter up to but <em>not including</em> {@code &signature=}. Not the decoded
 * parameters, not a re-serialisation of them in a different order, and not including the
 * signature or key id. Re-encoding a parsed map here would produce a different byte string
 * from the one Google signed and every callback would fail — or, worse, a lenient
 * implementation would be built that accepted things it should not.
 *
 * @see <a href="https://developers.google.com/admob/android/ssv">AdMob SSV</a>
 */
@Slf4j
@Component
public class RewardedAdVerifier {

    /**
     * Where Google publishes the public halves of its signing keys.
     *
     * <p>Overridable so a test can point it at a local fixture; there is no other reason to
     * change it, and pointing it anywhere else in production would mean trusting whoever
     * runs that host to mint rewards.
     */
    private final String keysUrl;

    /**
     * How long a fetched key set is trusted before being fetched again.
     *
     * <p>Google rotates these. Caching forever means a rotation silently breaks every
     * reward until a redeploy; not caching at all means an outbound HTTPS request on a path
     * that anybody can hammer for free, which is a denial-of-service amplifier pointed at
     * ourselves.
     */
    private static final Duration CACHE_TTL = Duration.ofHours(6);

    private final RestClient http = RestClient.create();
    private final ObjectMapper json = new ObjectMapper();

    private volatile Map<String, PublicKey> keys = Map.of();
    private volatile Instant fetchedAt = Instant.EPOCH;

    public RewardedAdVerifier(
            @Value("${gamebuddy.ads.verifier-keys-url:https://gstatic.com/admob/reward/verifier-keys.json}")
                    String keysUrl) {
        this.keysUrl = keysUrl;
    }

    /**
     * Whether this callback was signed by Google.
     *
     * @param signedContent the query string up to but excluding {@code &signature=}
     * @param signatureBase64Url the {@code signature} parameter, still URL-safe base64
     * @param keyId the {@code key_id} parameter
     * @return true only if the signature verifies. Any failure — unknown key, malformed
     *     base64, network trouble reaching Google — is false rather than an exception,
     *     because every one of them means "cannot prove this is genuine", and the caller's
     *     answer to that is the same in each case.
     */
    public boolean verify(String signedContent, String signatureBase64Url, String keyId) {
        if (signedContent == null || signatureBase64Url == null || keyId == null) {
            return false;
        }

        PublicKey key = keyFor(keyId);
        if (key == null) {
            log.warn("Rewarded-ad callback signed with unknown key {}", keyId);
            return false;
        }

        try {
            // URL-safe alphabet, and Google omits the padding. Decoding with the standard
            // alphabet silently fails on any signature containing - or _, which is most of
            // them.
            byte[] signature = Base64.getUrlDecoder().decode(signatureBase64Url);

            Signature ecdsa = Signature.getInstance("SHA256withECDSA");
            ecdsa.initVerify(key);
            ecdsa.update(signedContent.getBytes(StandardCharsets.UTF_8));
            return ecdsa.verify(signature);
        } catch (Exception e) {
            // Includes a malformed signature, which is what a probe looks like. Debug
            // rather than warn: this endpoint is public and anyone may send rubbish to it,
            // so a warn here is a log-flooding lever handed to a stranger.
            log.debug("Rewarded-ad signature did not verify", e);
            return false;
        }
    }

    /** The key with this id, refreshing the set if it is stale or the id is unknown. */
    private PublicKey keyFor(String keyId) {
        Map<String, PublicKey> current = keys;
        boolean stale = fetchedAt.plus(CACHE_TTL).isBefore(Instant.now());

        // Also refetches when the id is simply absent, which is what a rotation looks like
        // from here: keys we have never seen appear before the cache would have expired.
        if (stale || !current.containsKey(keyId)) {
            current = refresh(current);
        }
        return current.get(keyId);
    }

    private synchronized Map<String, PublicKey> refresh(Map<String, PublicKey> fallback) {
        try {
            String body = http.get().uri(keysUrl).retrieve().body(String.class);
            JsonNode root = json.readTree(body);

            Map<String, PublicKey> parsed = new HashMap<>();
            KeyFactory ec = KeyFactory.getInstance("EC");
            for (JsonNode node : root.path("keys")) {
                String id = node.path("keyId").asText(null);
                String base64 = node.path("base64").asText(null);
                if (id == null || base64 == null) {
                    continue;
                }
                byte[] der = Base64.getDecoder().decode(base64);
                parsed.put(id, ec.generatePublic(new X509EncodedKeySpec(der)));
            }

            if (parsed.isEmpty()) {
                // An empty answer would otherwise replace a working key set with nothing
                // and refuse every reward until the next fetch.
                log.warn("Verifier key set at {} parsed to nothing; keeping the previous one", keysUrl);
                return fallback;
            }

            keys = Map.copyOf(parsed);
            fetchedAt = Instant.now();
            log.info("Loaded {} AdMob verifier keys", parsed.size());
            return keys;
        } catch (Exception e) {
            // Keeps serving from the old set. Google being unreachable must not turn into
            // every gamer's reward silently vanishing.
            log.warn("Could not refresh AdMob verifier keys from {}", keysUrl, e);
            return fallback;
        }
    }
}
