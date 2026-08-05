package com.gamebuddy.shared.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;

/**
 * The same contract, on the local filesystem.
 *
 * <p>What development and CI use. Without it, running the application at all would require
 * credentials to a real bucket, which means every contributor shares one namespace and
 * every test run leaves rubbish in it — and it would mean the test suite could not run
 * offline.
 *
 * <p>{@link #publicUrl} points back at this application's own {@code /media/**} endpoint
 * rather than at a file path, so the client code is identical in both environments: it is
 * handed a URL and fetches it. See {@code MediaController}, which only exists when this
 * implementation is active.
 */
@Slf4j
public class LocalObjectStorage implements ObjectStorage {

    private final Path root;
    private final String publicBaseUrl;

    public LocalObjectStorage(Path root, String publicBaseUrl) {
        this.root = root;
        this.publicBaseUrl =
                publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create the local media directory " + root, e);
        }
        log.warn("Object storage: LOCAL DISK at {}. Set R2_ACCESS_KEY_ID to use R2.", root.toAbsolutePath());
    }

    @Override
    public void put(Bucket bucket, String key, byte[] data, String contentType) {
        Path target = resolve(bucket, key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + target, e);
        }
    }

    @Override
    public byte[] get(Bucket bucket, String key) {
        Path source = resolve(bucket, key);
        if (!Files.exists(source)) {
            throw new ObjectNotFoundException(key, null);
        }
        try {
            return Files.readAllBytes(source);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + source, e);
        }
    }

    @Override
    public void delete(Bucket bucket, String key) {
        try {
            Files.deleteIfExists(resolve(bucket, key));
        } catch (IOException e) {
            log.warn("Could not delete {}/{}: {}", bucket, key, e.getMessage());
        }
    }

    @Override
    public void move(Bucket from, String fromKey, Bucket to, String toKey) {
        Path source = resolve(from, fromKey);
        Path target = resolve(to, toKey);
        try {
            Files.createDirectories(target.getParent());
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not move " + source + " to " + target, e);
        }
    }

    @Override
    public String publicUrl(String key) {
        return publicBaseUrl + "/" + key;
    }

    /**
     * Resolves a key under the bucket directory, refusing anything that escapes it.
     *
     * <p>Keys are generated server-side, so a traversal should be impossible — but this is
     * the one place where a string becomes a filesystem path, and "should be impossible"
     * is how directory traversal keeps happening. The check costs nothing.
     */
    private Path resolve(Bucket bucket, String key) {
        Path base = root.resolve(bucket.name().toLowerCase()).normalize();
        Path resolved = base.resolve(key).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("Key escapes the storage root: " + key);
        }
        return resolved;
    }
}
