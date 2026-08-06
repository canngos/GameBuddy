package com.gamebuddy.match.domain.service.chat;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Who is online right now, and when everyone else was last seen.
 *
 * <p>Derived from live STOMP sessions rather than from {@code last_active_at}. That column
 * exists and looks tempting, but {@code LastActiveTracker} deliberately writes it at most
 * once an hour — it feeds re-engagement, where the thresholds are measured in days. Reading
 * it as presence would report somebody online for an hour after they closed the app, which
 * is worse than showing nothing.
 *
 * <p><strong>In memory, and that is a deliberate limit.</strong> Presence is true only for
 * as long as the connection it describes, so it cannot outlive this process and there is
 * nothing to persist. The consequence is that it is per-instance: behind a load balancer,
 * an instance only knows about sockets it holds, and would report everyone else offline.
 * That is the same boundary the in-memory broker already has, and both are resolved by the
 * same change — a shared broker or a Redis fan-out — if a second instance is ever run.
 *
 * <p>Counted, not flagged. A gamer with a phone and a tablet has two sessions, and a
 * boolean would mark them offline when either one closed. The count also survives the
 * reconnect that follows a network change, which would otherwise flicker offline.
 */
@Component
@RequiredArgsConstructor
public class PresenceRegistry {

    /** Open sessions per gamer. An entry is removed once it reaches zero. */
    private final Map<String, Integer> sessions = new ConcurrentHashMap<>();

    /**
     * When each gamer's last session closed.
     *
     * <p>Only holds people seen since this process started, so "last seen" is unknown for
     * anybody who has not connected since. Reported as absent rather than guessed — an
     * invented timestamp is worse than saying nothing.
     */
    private final Map<String, Instant> lastSeen = new ConcurrentHashMap<>();

    private final Clock clock;

    /** @return true if this is the gamer's first session, so presence has actually changed */
    public boolean connected(String userId) {
        boolean first = sessions.merge(userId, 1, Integer::sum) == 1;
        if (first) {
            lastSeen.remove(userId);
        }
        return first;
    }

    /** @return true if this was the gamer's last session, so presence has actually changed */
    public boolean disconnected(String userId) {
        // compute, not merge: this has to remove the entry at zero rather than leave a
        // count of 0 behind, or the map grows by one entry per gamer who ever connected.
        Integer remaining = sessions.compute(userId, (id, count) -> count == null || count <= 1 ? null : count - 1);
        if (remaining == null) {
            lastSeen.put(userId, clock.instant());
            return true;
        }
        return false;
    }

    public boolean isOnline(String userId) {
        return sessions.containsKey(userId);
    }

    /** Empty when they are online, or when this process has never seen them. */
    public Optional<Instant> lastSeenAt(String userId) {
        return isOnline(userId) ? Optional.empty() : Optional.ofNullable(lastSeen.get(userId));
    }

    /** Used to keep presence fan-out to people who can actually receive it. */
    public Set<String> onlineAmong(Set<String> userIds) {
        return userIds.stream().filter(this::isOnline).collect(java.util.stream.Collectors.toSet());
    }
}
