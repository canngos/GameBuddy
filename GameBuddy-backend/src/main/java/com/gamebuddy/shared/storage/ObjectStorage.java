package com.gamebuddy.shared.storage;

/**
 * Where binary assets live.
 *
 * <p>An interface rather than an S3 client passed around, for one reason that matters more
 * than testability: development and CI must not need credentials to a real bucket. A
 * contributor who clones this repository can run the whole application, upload an avatar
 * and see it come back, with nothing configured — {@link LocalObjectStorage} handles it on
 * disk. Nothing leaves the machine and there is no shared bucket accumulating everyone's
 * test uploads.
 *
 * <p>Two buckets, and the split is a safety property rather than tidiness. Everything
 * arrives in {@link Bucket#UPLOADS}, which is private and has no public URL at all; an
 * object only reaches {@link Bucket#MEDIA} once it has been approved. With one bucket,
 * "public" would mean public for unreviewed uploads too — R2's public development URL
 * exposes a whole bucket and cannot be scoped to a prefix.
 */
public interface ObjectStorage {

    enum Bucket {
        /** Private. Uploads awaiting a moderation verdict, and anything rejected pending purge. */
        UPLOADS,
        /** Public. Approved avatars and the cosmetic catalogue. */
        MEDIA
    }

    void put(Bucket bucket, String key, byte[] data, String contentType);

    byte[] get(Bucket bucket, String key);

    /** Silent when the key is absent — deleting something already gone is the desired state. */
    void delete(Bucket bucket, String key);

    /**
     * Promotes an object between buckets.
     *
     * <p>Copy-then-delete rather than a rename, because object stores have no rename. The
     * copy is verified before the delete: losing an approved avatar because the source was
     * removed after a partial write is worse than briefly holding two copies.
     */
    void move(Bucket from, String fromKey, Bucket to, String toKey);

    /**
     * The URL a client can fetch this key from. Only meaningful for {@link Bucket#MEDIA} —
     * the uploads bucket is not publicly readable, which is the point of it.
     */
    String publicUrl(String key);
}
