package com.gamebuddy.lobby.infrastructure.repository;

import com.gamebuddy.lobby.infrastructure.entity.LobbyMember;
import com.gamebuddy.lobby.infrastructure.entity.LobbyMemberStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LobbyMemberRepository extends JpaRepository<LobbyMember, LobbyMember.Key> {

    List<LobbyMember> findAllByLobbyId(UUID lobbyId);

    Optional<LobbyMember> findByLobbyIdAndUserId(UUID lobbyId, String userId);

    /** "My lobbies": owned, joined and pending, one indexed read. */
    List<LobbyMember> findAllByUserIdAndStatusIn(String userId, Collection<LobbyMemberStatus> statuses);

    /** Seats taken. OWNER counts — maxPlayers includes the owner. */
    long countByLobbyIdAndStatusIn(UUID lobbyId, Collection<LobbyMemberStatus> statuses);

    /** Teams this gamer is part of, for the LOBBIES_JOINED badge metric. */
    long countByUserIdAndStatusIn(String userId, Collection<LobbyMemberStatus> statuses);

    List<LobbyMember> findAllByLobbyIdAndStatus(UUID lobbyId, LobbyMemberStatus status);

    /** Sweeper: everyone to notify across a batch of lobbies being cancelled. */
    List<LobbyMember> findAllByLobbyIdInAndStatusIn(
            Collection<UUID> lobbyIds, Collection<LobbyMemberStatus> statuses);
}
