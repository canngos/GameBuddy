package com.gamebuddy.match.domain.service.chat;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Who is online right now, and when everyone else was last seen.
 *
 * <p>Derived from live STOMP sessions rather than from {@code last_active_at}. That column
 * exists and looks tempting, but {@code LastActiveTracker} deliberately writes it at most
 * once an hour — it feeds re-engagement, where the thresholds are measured in days. Reading
 * it as presence would report somebody online for an hour after they closed the app, which
 * is worse than showing nothing.
 *
 * <p>Counted, not flagged. A gamer with a phone and a tablet has two sessions, and a
 * boolean would mark them offline when either one closed. The count also survives the
 * reconnect that follows a network change, which would otherwise flicker offline.
 *
 * @see LocalPresenceRegistry one process's sockets, and the default
 * @see RedisPresenceRegistry every instance's, once a Redis host is configured
 */
public interface PresenceRegistry {

    /** @return true if this is the gamer's first session, so presence has actually changed */
    boolean connected(String userId);

    /** @return true if this was the gamer's last session, so presence has actually changed */
    boolean disconnected(String userId);

    boolean isOnline(String userId);

    /** Empty when they are online, or when nobody has seen them since presence began. */
    Optional<Instant> lastSeenAt(String userId);

    /** Used to keep presence fan-out to people who can actually receive it. */
    Set<String> onlineAmong(Set<String> userIds);
}
