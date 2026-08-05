package com.gamebuddy.shared.storage;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Cloudflare R2, through the S3 API.
 *
 * <p>R2 is S3-compatible, so this is the ordinary AWS SDK pointed at a different endpoint.
 * Three details are not obvious and each fails in a way that does not name its cause:
 *
 * <ul>
 *   <li><strong>The region must be set and is ignored.</strong> R2 has no regions, but the
 *       SDK refuses to build a client without one and signs every request with whatever it
 *       is given. {@code auto} is what Cloudflare's own documentation uses.
 *   <li><strong>Path-style addressing.</strong> Virtual-host style would put the bucket in
 *       the hostname, which does not resolve for R2.
 *   <li><strong>The EU jurisdiction has its own endpoint host</strong> — {@code
 *       <account>.eu.r2.cloudflarestorage.com}. Against the default host the buckets exist
 *       but every request is denied, and the error says nothing about jurisdiction. The
 *       endpoint comes from configuration for that reason rather than being derived from
 *       the account id.
 * </ul>
 */
@Slf4j
public class R2ObjectStorage implements ObjectStorage {

    private final S3Client client;
    private final String uploadsBucket;
    private final String mediaBucket;
    private final String publicBaseUrl;

    public R2ObjectStorage(
            String endpoint,
            String accessKeyId,
            String secretAccessKey,
            String uploadsBucket,
            String mediaBucket,
            String publicBaseUrl) {
        this.uploadsBucket = uploadsBucket;
        this.mediaBucket = mediaBucket;
        this.publicBaseUrl =
                publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;

        this.client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("auto"))
                .forcePathStyle(true)
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .build();

        log.info("Object storage: R2 at {} ({} / {})", endpoint, uploadsBucket, mediaBucket);
    }

    @Override
    public void put(Bucket bucket, String key, byte[] data, String contentType) {
        client.putObject(
                PutObjectRequest.builder()
                        .bucket(nameOf(bucket))
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data));
    }

    @Override
    public byte[] get(Bucket bucket, String key) {
        try {
            ResponseBytes<GetObjectResponse> response = client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(nameOf(bucket)).key(key).build());
            return response.asByteArray();
        } catch (NoSuchKeyException e) {
            throw new ObjectNotFoundException(key, e);
        }
    }

    @Override
    public void delete(Bucket bucket, String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(nameOf(bucket))
                    .key(key)
                    .build());
        } catch (S3Exception e) {
            // Deleting something that is already gone is the outcome we wanted. Anything
            // else is worth knowing about but is not worth failing a moderation decision
            // over — the verdict has already been recorded.
            log.warn("Could not delete {}/{}: {}", bucket, key, e.getMessage());
        }
    }

    @Override
    public void move(Bucket from, String fromKey, Bucket to, String toKey) {
        client.copyObject(CopyObjectRequest.builder()
                .sourceBucket(nameOf(from))
                .sourceKey(fromKey)
                .destinationBucket(nameOf(to))
                .destinationKey(toKey)
                .build());
        // Only after the copy has been acknowledged. The other order loses the image if
        // the copy fails, and an approved avatar that vanished is a bug the user sees.
        delete(from, fromKey);
    }

    @Override
    public String publicUrl(String key) {
        return publicBaseUrl + "/" + key;
    }

    private String nameOf(Bucket bucket) {
        return bucket == Bucket.MEDIA ? mediaBucket : uploadsBucket;
    }
}
