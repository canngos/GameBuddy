package com.gamebuddy.lobby.infrastructure.repository;

import com.gamebuddy.lobby.infrastructure.entity.LobbyMessage;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LobbyMessageRepository extends JpaRepository<LobbyMessage, UUID> {

    List<LobbyMessage> findAllByLobbyIdOrderByCreatedAtAsc(UUID lobbyId);

    /** Unread count against a member's watermark. */
    long countByLobbyIdAndCreatedAtAfter(UUID lobbyId, Instant since);

    /**
     * Archive sweep: one statement, not a load-and-delete of every row. The chat of an
     * archived lobby is gone by design, so there is nothing to cascade or report.
     */
    @Modifying
    @Query("DELETE FROM LobbyMessage m WHERE m.lobbyId IN :lobbyIds")
    void deleteAllByLobbyIdIn(@Param("lobbyIds") Collection<UUID> lobbyIds);
}
