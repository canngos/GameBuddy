package com.gamebuddy.lobby.domain.service;

import com.gamebuddy.lobby.interfaces.response.LobbyMessageResponse;
import com.gamebuddy.lobby.interfaces.response.LobbyMessagesResponse;
import com.gamebuddy.shared.entity.Gamer;
import java.util.UUID;

/**
 * The lobby's chat: everyone the owner accepted, in one room, until the lobby archives.
 *
 * <p>Sends travel over HTTP and arrivals over the socket, the same posture as 1:1 chat —
 * a dropped socket slows chat down, it never breaks it.
 */
public interface LobbyChatService {

    /** The history, oldest first, decrypted. Reading is what moves the caller's watermark. */
    LobbyMessagesResponse messages(Gamer principal, UUID lobbyId);

    LobbyMessageResponse send(Gamer principal, UUID lobbyId, String body);
}
