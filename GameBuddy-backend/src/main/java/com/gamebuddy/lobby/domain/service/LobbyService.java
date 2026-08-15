package com.gamebuddy.lobby.domain.service;

import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.lobby.infrastructure.entity.LobbyTone;
import com.gamebuddy.lobby.interfaces.request.CreateLobbyRequest;
import com.gamebuddy.lobby.interfaces.request.UpdateLobbyRequest;
import com.gamebuddy.lobby.interfaces.response.LobbiesResponse;
import com.gamebuddy.lobby.interfaces.response.LobbyResponse;
import com.gamebuddy.shared.entity.Gamer;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/**
 * Lobbies: an open invitation to play one game, together, at a stated time.
 *
 * <p>Creating one is Gold's perk; browsing and asking to join are free, because a lobby
 * list only Gold members could fill would be an empty lobby list. Nobody walks in — the
 * owner accepts every member by hand, which is also why the entry requirements are free
 * text rather than enforced rules.
 */
public interface LobbyService {

    /**
     * OPEN lobbies, soonest first, optionally narrowed.
     *
     * <p>The three narrowings combine rather than replace each other: game and tone are
     * what a lobby is, {@code startingSoon} is when it plays, and "competitive Valorant,
     * about to go" is a reasonable thing to ask for.
     *
     * @param startingSoon only lobbies whose planned start is inside the next quarter of
     *     an hour — including ones a little past it, which are the most "now" of all
     */
    LobbiesResponse browse(
            Gamer principal, String gameId, LobbyTone tone, boolean startingSoon, Pageable pageable);

    /** Everything of mine: owned, joined, and requests I am waiting on. */
    LobbiesResponse mine(Gamer principal);

    LobbyResponse get(Gamer principal, UUID lobbyId);

    /** Gold only. One live lobby per owner. */
    LobbyResponse create(Gamer principal, CreateLobbyRequest request);

    DefaultMessageResponse update(Gamer principal, UUID lobbyId, UpdateLobbyRequest request);

    DefaultMessageResponse join(Gamer principal, UUID lobbyId);

    DefaultMessageResponse accept(Gamer principal, UUID lobbyId, String userId);

    DefaultMessageResponse reject(Gamer principal, UUID lobbyId, String userId);

    DefaultMessageResponse leave(Gamer principal, UUID lobbyId);

    DefaultMessageResponse kick(Gamer principal, UUID lobbyId, String userId);

    DefaultMessageResponse lock(Gamer principal, UUID lobbyId);

    DefaultMessageResponse unlock(Gamer principal, UUID lobbyId);

    DefaultMessageResponse end(Gamer principal, UUID lobbyId);

    DefaultMessageResponse cancel(Gamer principal, UUID lobbyId);
}
