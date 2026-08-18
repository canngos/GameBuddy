package com.gamebuddy.match.domain.service.chat;

import com.gamebuddy.match.interfaces.dto.PresenceUpdate;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import com.gamebuddy.shared.messaging.UserMessaging;
import java.security.Principal;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Turns socket lifecycle into presence, and tells the people who care.
 *
 * <p>The socket is opened for as long as the app is open — see the client's
 * {@code ChatSocketProvider} — so "has a session" and "is using GameBuddy" are the same
 * thing. That is the definition a chat app needs; deriving it from the conversation screen
 * alone would have marked somebody offline the moment they went back to the deck.
 *
 * <p><strong>Every mutual match is told, whether or not they look online.</strong> This used
 * to skip anyone the registry did not have a session for, on the reasoning that presence is
 * only useful to somebody looking at a screen right now. The reasoning holds; the check does
 * not, because it is asking about the recipient at the exact moment both connections are in
 * motion. Two gamers whose sockets drop together — a backend restart, one flaky network —
 * race to reconnect, and whichever reconnects first announces to somebody the registry has
 * not caught up with yet. That announcement was dropped and never retried, so each side kept
 * the last thing it heard, which was "offline", for as long as the app stayed open. It looked
 * exactly like the bug it was reported as: both people offline, both people's messages
 * arriving, and a restart fixing it.
 *
 * <p>The cost of the other choice is a frame the broker discards for a gamer who really is
 * away — a map lookup. The cost of this check was a label that stayed wrong until the app was
 * closed. Anyone genuinely offline still learns the current state when they next ask.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private final PresenceRegistry registry;
    private final GamerRepository gamerRepository;
    private final UserMessaging messaging;

    /**
     * {@code @Transactional} belongs on the listeners, not on {@link #announce}.
     *
     * <p>It was on {@code announce}, which does nothing: these methods call it on
     * {@code this}, so the call never passes through the transactional proxy and there is
     * no session when the matches are read. Every announcement died in
     * {@code LazyInitializationException} — caught, logged, and otherwise silent, so
     * presence simply never updated while looking entirely healthy.
     *
     * <p>The listeners themselves are invoked by Spring's event multicaster, which is an
     * external call, so the annotation takes effect here.
     */
    @EventListener
    @Transactional(readOnly = true)
    public void onConnected(SessionConnectedEvent event) {
        gamerOf(event.getUser()).ifPresent(userId -> {
            if (registry.connected(userId)) {
                announce(userId);
            }
        });
    }

    @EventListener
    @Transactional(readOnly = true)
    public void onDisconnected(SessionDisconnectEvent event) {
        gamerOf(event.getUser()).ifPresent(userId -> {
            if (registry.disconnected(userId)) {
                announce(userId);
            }
        });
    }

    /**
     * The current state of one gamer, for whoever just opened a conversation with them.
     *
     * <p>Answered without a database read: presence lives entirely in memory.
     */
    public PresenceUpdate presenceOf(String userId) {
        return new PresenceUpdate(
                userId, registry.isOnline(userId), registry.lastSeenAt(userId).orElse(null));
    }

    /**
     * Pushes a change to the gamer's mutual matches.
     *
     * <p>Runs inside the listener's transaction — see above. Failures are swallowed because
     * this is a websocket lifecycle callback, not part of anybody's request: a broken
     * announcement must not tear down the connection handling that triggered it. The cost
     * is a stale label until the next change.
     */
    public void announce(String userId) {
        try {
            Gamer gamer = gamerRepository.findById(userId).orElse(null);
            if (gamer == null) {
                return;
            }

            PresenceUpdate update = presenceOf(userId);
            Set<String> matchIds =
                    gamer.getApprovedMatches().stream().map(Gamer::getUserId).collect(Collectors.toSet());

            for (Gamer match : gamer.getApprovedMatches()) {
                // Mutual matches only, the same rule chat itself applies. A one-sided like
                // is not a relationship, and it must not leak when somebody is at their phone.
                if (!match.getApprovedMatches().contains(gamer)) {
                    continue;
                }
                messaging.sendToUser(match.getEmail(), "/queue/presence", update);
            }

            log.debug("Presence for {} is now online={} ({} matches)", userId, update.online(), matchIds.size());
        } catch (RuntimeException e) {
            log.warn("Could not announce presence for {}", userId, e);
        }
    }

    /**
     * The authenticated gamer's id from a session event, if there is one.
     *
     * <p>The principal is whatever {@code StompAuthChannelInterceptor} bound at CONNECT, so
     * in practice always present — but an unauthenticated session is not worth an
     * exception on a lifecycle event, and returning empty simply means no presence.
     */
    private Optional<String> gamerOf(@Nullable Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof Gamer gamer) {
            return Optional.of(gamer.getUserId());
        }
        return Optional.empty();
    }
}
