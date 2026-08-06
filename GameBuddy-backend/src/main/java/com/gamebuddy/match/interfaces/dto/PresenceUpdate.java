package com.gamebuddy.match.interfaces.dto;

import java.time.Instant;

/**
 * Whether somebody is online, pushed when it changes and returned when it is asked for.
 *
 * <p>A wire payload; nothing about presence is stored. See {@code PresenceRegistry} for why
 * it cannot be, and why {@code lastSeenAt} is often null even for somebody who is offline.
 *
 * @param userId who this is about
 * @param online whether they have a live connection right now
 * @param lastSeenAt when their last connection closed; null while online, and also null if
 *     they have not connected since this process started
 */
public record PresenceUpdate(String userId, boolean online, Instant lastSeenAt) {}
