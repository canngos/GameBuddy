package com.gamebuddy.match.domain.service.chat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Presence across every instance, held in Redis.
 *
 * <p>One hash per gamer, {@code gb:presence:{userId}}, whose fields are instance ids and
 * whose values are how many sockets that instance holds for them. Online is "the hash is
 * not empty", which no single instance could answer on its own.
 *
 * <p><b>Why a hash of counts rather than a set of session ids.</b> Both answer "is anybody
 * connected", but only the hash can be repaired: an instance can overwrite its own field
 * with the truth it holds locally, whereas a set of session ids from a process that has
 * died cannot be distinguished from live ones by anybody.
 *
 * <p><b>Instance death is the failure that matters</b>, and it is handled by expiry rather
 * than by cleanup. A process that is killed never runs a disconnect, so its field would
 * otherwise sit there and report a dozen people online forever — presence that is wrong in
 * the direction of "message them, they're right there" is worse than no presence at all.
 * So every key carries a short TTL and each instance re-asserts its own fields on a timer.
 * A dead instance stops re-asserting, the key expires, and the survivors rebuild it from
 * what they actually hold. Nothing has to notice the death or agree that it happened.
 *
 * <p>The local map is authoritative for this instance's own sessions. Redis is where the
 * instances meet, not where any of them keeps its own truth — which is what makes the
 * re-assertion possible and makes a Redis outage survivable.
 */
@Slf4j
public class RedisPresenceRegistry implements PresenceRegistry {

    private static final String PRESENCE_KEY = "gb:presence:";
    private static final String LAST_SEEN_KEY = "gb:lastseen:";

    /**
     * How long a presence hash survives without being re-asserted.
     *
     * <p>Comfortably longer than {@link #REASSERT_INTERVAL} so an ordinary GC pause or a
     * slow Redis round trip cannot expire a key that is still being maintained. The cost of
     * it being this long is that a killed instance leaves its people looking online for up
     * to this window — which is roughly how long a client takes to notice a dead socket
     * anyway, so the two failures resolve on the same timescale.
     */
    private static final Duration PRESENCE_TTL = Duration.ofSeconds(90);

    private static final Duration REASSERT_INTERVAL = Duration.ofSeconds(30);

    /**
     * How long "last seen" is kept.
     *
     * <p>Long enough to be useful when reopening a conversation the next morning, short
     * enough that Redis does not accumulate a row per account that ever connected. Beyond
     * it the answer is absent rather than stale, which the client already renders as
     * nothing — an invented timestamp would be worse than saying nothing.
     */
    private static final Duration LAST_SEEN_TTL = Duration.ofDays(7);

    /** This instance's own sessions. The source of truth for what we re-assert. */
    private final Map<String, Integer> local = new ConcurrentHashMap<>();

    private final StringRedisTemplate redis;
    private final String instanceId;
    private final Clock clock;

    public RedisPresenceRegistry(StringRedisTemplate redis, String instanceId, Clock clock) {
        this.redis = redis;
        this.instanceId = instanceId;
        this.clock = clock;
    }

    @Override
    public boolean connected(String userId) {
        boolean firstHere = local.merge(userId, 1, Integer::sum) == 1;
        try {
            String key = PRESENCE_KEY + userId;
            redis.opsForHash().increment(key, instanceId, 1);
            redis.expire(key, PRESENCE_TTL);
            redis.delete(LAST_SEEN_KEY + userId);
        } catch (RuntimeException e) {
            log.warn("Could not record presence for {} in Redis", userId, e);
        }
        // Whether this is the *cluster's* first session is not asked, deliberately. An
        // announcement that only fires for the globally-first socket would be skipped for
        // somebody opening a second device, and their matches would never learn they are
        // back. Announcing on this instance's first is at worst a duplicate.
        return firstHere;
    }

    @Override
    public boolean disconnected(String userId) {
        Integer remaining = local.compute(userId, (id, count) -> count == null || count <= 1 ? null : count - 1);

        try {
            String key = PRESENCE_KEY + userId;
            if (remaining == null) {
                redis.opsForHash().delete(key, instanceId);
            } else {
                redis.opsForHash().put(key, instanceId, String.valueOf(remaining));
                redis.expire(key, PRESENCE_TTL);
            }
            if (remaining == null && !isOnline(userId)) {
                // Only once nobody anywhere holds a socket. Writing it on our own last
                // session would stamp "last seen now" for somebody still connected on
                // another instance, and their conversation would show them as just left.
                redis.opsForValue().set(LAST_SEEN_KEY + userId, String.valueOf(clock.millis()), LAST_SEEN_TTL);
            }
        } catch (RuntimeException e) {
            log.warn("Could not clear presence for {} in Redis", userId, e);
        }
        return remaining == null;
    }

    @Override
    public boolean isOnline(String userId) {
        try {
            Long fields = redis.opsForHash().size(PRESENCE_KEY + userId);
            return fields != null && fields > 0;
        } catch (RuntimeException e) {
            // Falls back to what this instance knows. Wrong for anybody connected
            // elsewhere, and the best available answer when the shared view is unreachable
            // — the alternative is reporting everybody offline, which is wrong for
            // everybody instead.
            log.warn("Could not read presence for {} from Redis", userId, e);
            return local.containsKey(userId);
        }
    }

    @Override
    public Optional<Instant> lastSeenAt(String userId) {
        if (isOnline(userId)) {
            return Optional.empty();
        }
        try {
            String millis = redis.opsForValue().get(LAST_SEEN_KEY + userId);
            return millis == null ? Optional.empty() : Optional.of(Instant.ofEpochMilli(Long.parseLong(millis)));
        } catch (RuntimeException e) {
            // Covers a NumberFormatException too: a value that is not a number is a key
            // somebody else wrote, and guessing at it is worse than reporting nothing.
            log.warn("Could not read last-seen for {} from Redis", userId, e);
            return Optional.empty();
        }
    }

    @Override
    public Set<String> onlineAmong(Set<String> userIds) {
        return userIds.stream().filter(this::isOnline).collect(Collectors.toSet());
    }

    /**
     * Re-states what this instance holds, and pushes the expiry back.
     *
     * <p>This is the repair mechanism described on the class: the keys only stay alive
     * because somebody keeps saying so, and only a live instance can say it. It writes the
     * local count rather than incrementing, so a field that has drifted — a lost
     * disconnect, a Redis blip during a connect — is corrected rather than compounded.
     */
    @Scheduled(fixedRateString = "PT30S")
    void reassert() {
        if (local.isEmpty()) {
            return;
        }
        try {
            for (Map.Entry<String, Integer> held : new HashMap<>(local).entrySet()) {
                String key = PRESENCE_KEY + held.getKey();
                redis.opsForHash().put(key, instanceId, String.valueOf(held.getValue()));
                redis.expire(key, PRESENCE_TTL);
            }
        } catch (RuntimeException e) {
            // Nothing to do but wait for the next tick. Redis being briefly down means our
            // keys may expire and this instance's people look offline until it returns,
            // which is the same as the outage itself.
            log.warn("Could not re-assert presence for {} gamers", local.size(), e);
        }
    }

    /** Only for the re-assertion test; {@link #REASSERT_INTERVAL} documents the schedule. */
    static Duration reassertInterval() {
        return REASSERT_INTERVAL;
    }
}
