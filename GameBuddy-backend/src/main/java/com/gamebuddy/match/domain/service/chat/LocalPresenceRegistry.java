package com.gamebuddy.match.domain.service.chat;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;

/**
 * Presence held in this process's memory. The default, and correct for one instance.
 *
 * <p><strong>Per-instance, which is why it is not the only implementation.</strong> An
 * instance only knows about sockets it holds, so behind a load balancer this reports
 * everybody on another instance as offline — and, worse, silently: an offline answer is
 * indistinguishable from a correct one. {@link RedisPresenceRegistry} replaces it as soon
 * as a Redis host is configured.
 *
 * <p>Counted, not flagged. A gamer with a phone and a tablet has two sessions, and a
 * boolean would mark them offline when either one closed. The count also survives the
 * reconnect that follows a network change, which would otherwise flicker offline.
 */
@RequiredArgsConstructor
public class LocalPresenceRegistry implements PresenceRegistry {

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
    @Override
    public boolean connected(String userId) {
        boolean first = sessions.merge(userId, 1, Integer::sum) == 1;
        if (first) {
            lastSeen.remove(userId);
        }
        return first;
    }

    /** @return true if this was the gamer's last session, so presence has actually changed */
    @Override
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

    @Override
    public boolean isOnline(String userId) {
        return sessions.containsKey(userId);
    }

    /** Empty when they are online, or when this process has never seen them. */
    @Override
    public Optional<Instant> lastSeenAt(String userId) {
        return isOnline(userId) ? Optional.empty() : Optional.ofNullable(lastSeen.get(userId));
    }

    /** Used to keep presence fan-out to people who can actually receive it. */
    @Override
    public Set<String> onlineAmong(Set<String> userIds) {
        return userIds.stream().filter(this::isOnline).collect(java.util.stream.Collectors.toSet());
    }
}
