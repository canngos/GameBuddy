package com.gamebuddy.billing.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamebuddy.billing.infrastructure.entity.PurchasePlatform;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Verifies a StoreKit 2 signed transaction.
 *
 * <p>Apple's modern flow hands the client a JWS that Apple itself signed, so verification
 * is a signature check rather than a call to Apple. That is better in every way that
 * matters here: no network dependency on the purchase path, no shared secret to leak, and
 * no rate limit. What has to be right is the checking, and the order of it:
 *
 * <ol>
 *   <li>the certificate chain in the JWS header leads back to a trusted Apple root;
 *   <li>the signature over the header and payload verifies against the leaf certificate;
 *   <li>only then is the payload read.
 * </ol>
 *
 * <p>Reading the payload before validating the signature is the classic mistake — it is
 * base64, so anyone can write one, and a verifier that parses first and checks later has
 * usually already made a decision by the time it checks.
 *
 * <p>The bundle id is compared too, so a signed transaction from a different app cannot be
 * replayed here. Apple signs receipts for every developer; a valid signature only proves
 * the receipt is genuine, not that it is <em>ours</em>.
 */
@Slf4j
public class AppleReceiptVerifier implements ReceiptVerifier {

    private final String bundleId;
    private final Set<TrustAnchor> appleRoots;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * @param appleRootCertificates Apple's root CA certificates, in DER or PEM. Downloaded
     *     from Apple and shipped with the deployment rather than fetched at runtime — a
     *     trust root fetched over the network is not a trust root.
     */
    public AppleReceiptVerifier(String bundleId, List<byte[]> appleRootCertificates, Clock clock) {
        this.bundleId = bundleId;
        this.clock = clock;
        this.appleRoots = new java.util.HashSet<>();
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            for (byte[] der : appleRootCertificates) {
                X509Certificate cert = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(der));
                appleRoots.add(new TrustAnchor(cert, null));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not load the Apple root certificates", e);
        }
        if (appleRoots.isEmpty()) {
            // Failing at startup, because an empty trust store would make every signature
            // check fail open or closed depending on how the code below is read. Neither
            // is something to discover in production.
            throw new IllegalStateException("No Apple root certificates were supplied; refusing to start");
        }
    }

    @Override
    public PurchasePlatform platform() {
        return PurchasePlatform.APPLE_APP_STORE;
    }

    @Override
    public VerifiedPurchase verify(String receipt, Product claimedProduct) {
        String[] parts = receipt.split("\\.");
        if (parts.length != 3) {
            log.warn("Apple receipt is not a well-formed JWS");
            throw new BusinessException(TransactionCode.PURCHASE_VERIFICATION_FAILED);
        }

        JsonNode header = decode(parts[0]);
        List<X509Certificate> chain = certificateChain(header);
        requireTrustedChain(chain);
        requireValidSignature(parts, chain.get(0), header.path("alg").asText(""));

        // Only now is the payload meaningful.
        JsonNode payload = decode(parts[1]);
        return readTransaction(payload, claimedProduct);
    }

    private JsonNode decode(String base64Url) {
        try {
            return json.readTree(new String(Base64.getUrlDecoder().decode(base64Url), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new BusinessException(TransactionCode.PURCHASE_VERIFICATION_FAILED, e);
        }
    }

    /** The x5c header: leaf first, then intermediates. */
    private List<X509Certificate> certificateChain(JsonNode header) {
        JsonNode x5c = header.path("x5c");
        if (!x5c.isArray() || x5c.isEmpty()) {
            log.warn("Apple receipt has no certificate chain");
            throw new BusinessException(TransactionCode.PURCHASE_VERIFICATION_FAILED);
        }
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            List<X509Certificate> chain = new ArrayList<>();
            for (JsonNode node : x5c) {
                byte[] der = Base64.getDecoder().decode(node.asText());
                chain.add((X509Certificate) factory.generateCertificate(new ByteArrayInputStream(der)));
            }
            return chain;
        } catch (Exception e) {
            throw new BusinessException(TransactionCode.PURCHASE_VERIFICATION_FAILED, e);
        }
    }

    private void requireTrustedChain(List<X509Certificate> chain) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            PKIXParameters params = new PKIXParameters(appleRoots);
            // No CRL or OCSP: Apple does not publish revocation for these, and enabling it
            // would make every purchase depend on an external fetch succeeding.
            params.setRevocationEnabled(false);
            params.setDate(java.util.Date.from(clock.instant()));

            CertPathValidator.getInstance("PKIX").validate(factory.generateCertPath(chain), params);
        } catch (Exception e) {
            log.warn("Apple receipt certificate chain did not validate: {}", e.getMessage());
            throw new BusinessException(TransactionCode.PURCHASE_VERIFICATION_FAILED, e);
        }
    }

    private void requireValidSignature(String[] parts, X509Certificate leaf, String alg) {
        // Apple signs with ES256. Pinning it stops an attacker choosing the algorithm —
        // "alg": "none" is the oldest JWT attack there is, and honouring whatever the
        // header asks for is how it works.
        if (!"ES256".equals(alg)) {
            log.warn("Apple receipt declares an unexpected algorithm: {}", alg);
            throw new BusinessException(TransactionCode.PURCHASE_VERIFICATION_FAILED);
        }
        try {
            PublicKey key = leaf.getPublicKey();
            Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
            verifier.initVerify(key);
            verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!verifier.verify(Base64.getUrlDecoder().decode(parts[2]))) {
                log.warn("Apple receipt signature did not verify");
                throw new BusinessException(TransactionCode.PURCHASE_VERIFICATION_FAILED);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(TransactionCode.PURCHASE_VERIFICATION_FAILED, e);
        }
    }

    private VerifiedPurchase readTransaction(JsonNode payload, Product claimed) {
        String receiptBundle = payload.path("bundleId").asText("");
        if (!bundleId.equals(receiptBundle)) {
            // A genuine Apple signature over somebody else's app.
            log.warn("Apple receipt is for bundle {} but this app is {}", receiptBundle, bundleId);
            throw new BusinessException(TransactionCode.PURCHASE_VERIFICATION_FAILED);
        }

        String productId = payload.path("productId").asText("");
        if (!claimed.storeId().equals(productId)) {
            log.warn("Apple receipt is for product {} but {} was claimed", productId, claimed.storeId());
            throw new BusinessException(TransactionCode.PURCHASE_VERIFICATION_FAILED);
        }

        if (payload.has("revocationDate")) {
            // Refunded or revoked by Apple. PurchaseService.refund handles the case where
            // this happens after the fact; this is the case where it already had.
            log.warn("Apple receipt has been revoked");
            throw new BusinessException(TransactionCode.PURCHASE_VERIFICATION_FAILED);
        }

        long purchased = payload.path("purchaseDate").asLong(0L);
        long expires = payload.path("expiresDate").asLong(0L);

        return new VerifiedPurchase(
                // originalTransactionId stays stable across renewals, so transactionId is
                // the one that makes each renewal a distinct, non-replayable purchase.
                payload.path("transactionId").asText(""),
                claimed,
                purchased > 0 ? Instant.ofEpochMilli(purchased) : clock.instant(),
                expires > 0 ? Instant.ofEpochMilli(expires) : null);
    }
}
