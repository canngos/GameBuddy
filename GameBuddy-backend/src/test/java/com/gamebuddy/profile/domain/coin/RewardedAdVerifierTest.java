package com.gamebuddy.profile.domain.coin;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The signature check, against a real key pair rather than a mock.
 *
 * <p>Mocking the verifier would test nothing that matters. This is the only thing standing
 * between a public URL and unlimited coins, so the test signs with a genuine P-256 key,
 * serves the public half the way Google serves theirs, and checks that tampering with any
 * part of the signed content is caught.
 */
@DisplayName("RewardedAdVerifier")
class RewardedAdVerifierTest {

    private static final String KEY_ID = "3335741209";

    private HttpServer server;
    private KeyPair keyPair;
    private RewardedAdVerifier verifier;
    private final AtomicInteger fetches = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();

        String body = """
                {"keys":[{"keyId":%s,"pem":"unused","base64":"%s"}]}
                """.formatted(
                KEY_ID, Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));

        // A real socket rather than a stubbed RestClient: the parsing of Google's document
        // is part of what can be wrong, and a mock would assert our own idea of its shape.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/keys", exchange -> {
            fetches.incrementAndGet();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        // A host that answers 301 to /keys, the way the bare gstatic host redirects to the
        // www one. The client must follow it, or it parses the redirect page as a key set.
        server.createContext("/moved", exchange -> {
            exchange.getResponseHeaders().add("Location", "/keys");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });
        server.start();

        verifier =
                new RewardedAdVerifier("http://127.0.0.1:" + server.getAddress().getPort() + "/keys");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /** Signs exactly as AdMob does: SHA256withECDSA over the raw signed content. */
    private String sign(String signedContent) throws Exception {
        Signature ecdsa = Signature.getInstance("SHA256withECDSA");
        ecdsa.initSign(keyPair.getPrivate());
        ecdsa.update(signedContent.getBytes(StandardCharsets.UTF_8));
        // URL-safe and unpadded, which is the form that arrives in the query string.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(ecdsa.sign());
    }

    private static final String CONTENT =
            "ad_network=5450213213286189855&ad_unit=1234&reward_amount=1&reward_item=coins"
                    + "&timestamp=1786500000000&transaction_id=abc123&user_id=gamer-1";

    @Test
    @DisplayName("a genuine callback verifies")
    void genuineSignatureVerifies() throws Exception {
        assertTrue(verifier.verify(CONTENT, sign(CONTENT), KEY_ID));
    }

    @Test
    @DisplayName("changing the user the reward is for invalidates it")
    void tamperedUserIsRejected() throws Exception {
        String signature = sign(CONTENT);

        // The attack this exists to stop: intercept somebody's callback and redirect the
        // coins. The signature covers user_id, so it cannot survive the edit.
        String redirected = CONTENT.replace("user_id=gamer-1", "user_id=attacker");
        assertFalse(verifier.verify(redirected, signature, KEY_ID));
    }

    @Test
    @DisplayName("changing the transaction id invalidates it")
    void tamperedTransactionIsRejected() throws Exception {
        String signature = sign(CONTENT);

        // Otherwise one genuine callback could be replayed indefinitely by editing the one
        // field the duplicate check keys on.
        String renumbered = CONTENT.replace("transaction_id=abc123", "transaction_id=abc124");
        assertFalse(verifier.verify(renumbered, signature, KEY_ID));
    }

    @Test
    @DisplayName("a signature from the wrong key is rejected")
    void foreignKeyIsRejected() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair attacker = generator.generateKeyPair();

        Signature ecdsa = Signature.getInstance("SHA256withECDSA");
        ecdsa.initSign(attacker.getPrivate());
        ecdsa.update(CONTENT.getBytes(StandardCharsets.UTF_8));
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(ecdsa.sign());

        assertFalse(verifier.verify(CONTENT, forged, KEY_ID));
    }

    @Test
    @DisplayName("an unknown key id is rejected rather than trusted")
    void unknownKeyIdIsRejected() throws Exception {
        assertFalse(verifier.verify(CONTENT, sign(CONTENT), "9999999999"));
    }

    @Test
    @DisplayName("rubbish in the signature parameter is refused, not thrown")
    void malformedSignatureIsRejected() {
        // This endpoint is public, so malformed input is the normal traffic of the open
        // internet. It must be a quiet false rather than a 500.
        assertFalse(verifier.verify(CONTENT, "not base64 !!", KEY_ID));
        assertDoesNotThrow(() -> verifier.verify(CONTENT, null, KEY_ID));
        assertFalse(verifier.verify(null, "sig", KEY_ID));
    }

    @Test
    @DisplayName("the key set is fetched once and reused")
    void keysAreCached() throws Exception {
        String signature = sign(CONTENT);
        for (int i = 0; i < 5; i++) {
            assertTrue(verifier.verify(CONTENT, signature, KEY_ID));
        }

        // The alternative is an outbound request to Google on a path anybody can hammer
        // for free, which is an amplifier pointed at ourselves.
        assertEquals(1, fetches.get());
    }

    @Test
    @DisplayName("Google being unreachable refuses rather than crashes")
    void unreachableKeysRefuse() {
        RewardedAdVerifier offline = new RewardedAdVerifier("http://127.0.0.1:1/nothing");
        assertFalse(offline.verify(CONTENT, "sig", KEY_ID));
    }

    @Test
    @DisplayName("the default key URL is the host that actually answers")
    void defaultUrlIsTheWwwHost() {
        // The bare gstatic host answers 301, and the JDK client does not follow a redirect
        // unless the client is built to. A regression to the bare host silently refuses
        // every reward, so pin the constant.
        assertEquals("https://www.gstatic.com/admob/reward/verifier-keys.json", RewardedAdVerifier.DEFAULT_KEYS_URL);
    }

    @Test
    @DisplayName("a 301 to the key set is followed, not parsed as the key set")
    void redirectIsFollowed() throws Exception {
        RewardedAdVerifier viaRedirect =
                new RewardedAdVerifier("http://127.0.0.1:" + server.getAddress().getPort() + "/moved");
        assertTrue(
                viaRedirect.verify(CONTENT, sign(CONTENT), KEY_ID),
                "the client must follow the redirect to reach the keys");
    }
}
