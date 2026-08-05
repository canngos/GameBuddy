package com.gamebuddy.shared.storage;

/**
 * The key is not there.
 *
 * <p>Unchecked, and distinct from the storage being unreachable. A missing object is
 * usually a consistency problem — a row pointing at something a purge already removed —
 * and the caller wants to fall back to a default, not retry.
 */
public class ObjectNotFoundException extends RuntimeException {

    public ObjectNotFoundException(String key, Throwable cause) {
        super("No object at key " + key, cause);
    }
}
