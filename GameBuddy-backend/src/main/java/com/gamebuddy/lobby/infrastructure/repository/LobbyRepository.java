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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Module-private, like every repository; other modules go through {@code LobbyService}.
 *
 * <p>The browse variants are four queries rather than one with nullable parameters. A bare
 * parameter compared against NULL leaves Postgres unable to infer its type — the same trap
 * {@code ChatMessageRepository.countUnread} documents — and four method names cost nothing.
 *
 * <p><b>Every browse query carries a {@code startsBefore} bound, always set.</b> It is
 * what the "starting now" filter narrows, and passing a far-future sentinel when that
 * filter is off keeps one code path and one set of methods: making the bound optional
 * would either double these four into eight, or reintroduce exactly the untyped-NULL
 * problem the paragraph above avoids.
 *
 * <p><b>They are written out as JPQL because of the sort.</b> Boosted lobbies come first and
 * the rest stay soonest-first, which is a two-key order with a CASE in it — not something
 * a derived method name can express. The signatures are the ones the derived methods had,
 * so only these four bodies know about the boost.
 */
public interface LobbyRepository extends JpaRepository<Lobby, UUID> {

    /**
     * The ordering every browse query shares: what somebody paid to promote, then whatever
     * starts soonest. Boosted lobbies are not re-sorted among themselves by when they were
     * boosted — they are all still ordered by start time, so the block at the top reads the
     * same way as the list under it.
     */
    String BROWSE_ORDER = " ORDER BY CASE WHEN l.boostedAt IS NULL THEN 1 ELSE 0 END ASC, l.startsAt ASC";

    @Query("SELECT l FROM Lobby l WHERE l.status = :status AND l.startsAt < :startsBefore" + BROWSE_ORDER)
    Page<Lobby> browse(
            @Param("status") LobbyStatus status, @Param("startsBefore") Instant startsBefore, Pageable pageable);

    @Query("SELECT l FROM Lobby l WHERE l.status = :status AND l.startsAt < :startsBefore"
            + " AND l.gameId = :gameId" + BROWSE_ORDER)
    Page<Lobby> browseByGame(
            @Param("status") LobbyStatus status,
            @Param("startsBefore") Instant startsBefore,
            @Param("gameId") String gameId,
            Pageable pageable);

    @Query("SELECT l FROM Lobby l WHERE l.status = :status AND l.startsAt < :startsBefore"
            + " AND l.tone = :tone" + BROWSE_ORDER)
    Page<Lobby> browseByTone(
            @Param("status") LobbyStatus status,
            @Param("startsBefore") Instant startsBefore,
            @Param("tone") LobbyTone tone,
            Pageable pageable);

    @Query("SELECT l FROM Lobby l WHERE l.status = :status AND l.startsAt < :startsBefore"
            + " AND l.gameId = :gameId AND l.tone = :tone" + BROWSE_ORDER)
    Page<Lobby> browseByGameAndTone(
            @Param("status") LobbyStatus status,
            @Param("startsBefore") Instant startsBefore,
            @Param("gameId") String gameId,
            @Param("tone") LobbyTone tone,
            Pageable pageable);

    /** The one-live-lobby check. The partial unique index is the backstop for its race. */
    boolean existsByOwnerIdAndStatusIn(String ownerId, Collection<LobbyStatus> statuses);

    /** Sweeper: still-OPEN lobbies whose planned start is long gone — nobody ever came. */
    List<Lobby> findAllByStatusAndStartsAtBefore(LobbyStatus status, Instant cutoff);

    /** Sweeper: terminal lobbies old enough to file away. {@code endedAt} is set on end and cancel both. */
    List<Lobby> findAllByStatusInAndEndedAtBefore(Collection<LobbyStatus> statuses, Instant cutoff);
}
