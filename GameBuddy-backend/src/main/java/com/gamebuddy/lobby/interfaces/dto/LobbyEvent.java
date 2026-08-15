package com.gamebuddy.lobby.interfaces.dto;

import com.gamebuddy.lobby.infrastructure.entity.LobbyStatus;

/**
 * One frame on {@code /user/queue/lobby} — a single destination with a discriminator,
 * rather than a destination per event type.
 *
 * <p>Per-user queues rather than a {@code /topic/lobby.{id}}: the simple broker has no
 * subscription authorisation, so a topic would let any client subscribe to any lobby id it
 * invented. Fan-out to at most five people is nothing, and per-user delivery already works
 * across instances through the Redis channel.
 *
 * @param type {@code MESSAGE} — a chat line, in {@code message}; {@code MEMBER} — the
 *     roster changed, refetch it; {@code STATE} — the lifecycle moved, new value in
 *     {@code status}
 * @param lobbyId which lobby, always set
 * @param message the chat line for {@code MESSAGE}, null otherwise
 * @param status the new status for {@code STATE}, null otherwise
 */
public record LobbyEvent(String type, String lobbyId, LobbyMessageDto message, LobbyStatus status) {

    public static LobbyEvent messageEvent(String lobbyId, LobbyMessageDto message) {
        return new LobbyEvent("MESSAGE", lobbyId, message, null);
    }

    public static LobbyEvent memberEvent(String lobbyId) {
        return new LobbyEvent("MEMBER", lobbyId, null, null);
    }

    public static LobbyEvent stateEvent(String lobbyId, LobbyStatus status) {
        return new LobbyEvent("STATE", lobbyId, null, status);
    }
}
