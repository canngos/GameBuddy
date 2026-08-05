package com.gamebuddy.common.base;

/**
 * Marker supertype for every response body payload.
 *
 * <p>An interface rather than an abstract class: it carries no state and no behaviour,
 * so a class was the wrong shape (it also burned the single inheritance slot of every
 * body that extended it). It is no longer {@code Serializable} either — nothing ever
 * used Java serialisation on these, they exist to become JSON, and the marker produced
 * a {@code java:S1948} warning on every body holding a list of DTOs.
 *
 * <p>Kept rather than deleted because it is what {@link BaseBody} and the service
 * response envelopes are generically bounded by, which stops an arbitrary type being
 * passed as a response payload.
 */
public interface BaseModel {}
