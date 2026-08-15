package com.gamebuddy.lobby.infrastructure.repository;

import com.gamebuddy.lobby.infrastructure.entity.Lobby;
import com.gamebuddy.lobby.infrastructure.entity.LobbyStatus;
import com.gamebuddy.lobby.infrastructure.entity.LobbyTone;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Module-private, like every repository; other modules go through {@code LobbyService}.
 *
 * <p>The browse variants are four derived queries rather than one query with nullable
 * parameters. A bare parameter compared against NULL leaves Postgres unable to infer its
 * type — the same trap {@code ChatMessageRepository.countUnread} documents — and four
 * method names cost nothing.
 *
 * <p><b>Every browse query carries a {@code startsBefore} bound, always set.</b> It is
 * what the "starting now" filter narrows, and passing a far-future sentinel when that
 * filter is off keeps one code path and one set of methods: making the bound optional
 * would either double these four into eight, or reintroduce exactly the untyped-NULL
 * problem the paragraph above avoids.
 */
public interface LobbyRepository extends JpaRepository<Lobby, UUID> {

    Page<Lobby> findAllByStatusAndStartsAtBeforeOrderByStartsAtAsc(
            LobbyStatus status, Instant startsBefore, Pageable pageable);

    Page<Lobby> findAllByStatusAndStartsAtBeforeAndGameIdOrderByStartsAtAsc(
            LobbyStatus status, Instant startsBefore, String gameId, Pageable pageable);

    Page<Lobby> findAllByStatusAndStartsAtBeforeAndToneOrderByStartsAtAsc(
            LobbyStatus status, Instant startsBefore, LobbyTone tone, Pageable pageable);

    Page<Lobby> findAllByStatusAndStartsAtBeforeAndGameIdAndToneOrderByStartsAtAsc(
            LobbyStatus status, Instant startsBefore, String gameId, LobbyTone tone, Pageable pageable);

    /** The one-live-lobby check. The partial unique index is the backstop for its race. */
    boolean existsByOwnerIdAndStatusIn(String ownerId, Collection<LobbyStatus> statuses);

    /** Sweeper: still-OPEN lobbies whose planned start is long gone — nobody ever came. */
    List<Lobby> findAllByStatusAndStartsAtBefore(LobbyStatus status, Instant cutoff);

    /** Sweeper: terminal lobbies old enough to file away. {@code endedAt} is set on end and cancel both. */
    List<Lobby> findAllByStatusInAndEndedAtBefore(Collection<LobbyStatus> statuses, Instant cutoff);
}
