package com.gamebuddy.auth.infrastructure.repository;

import com.gamebuddy.auth.infrastructure.entity.AccountLinkTicket;
import com.gamebuddy.common.enums.LinkedProvider;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountLinkTicketRepository extends JpaRepository<AccountLinkTicket, UUID> {

    /** The only lookup: the callback presents a token, we hash it and match. */
    Optional<AccountLinkTicket> findByTokenHash(String tokenHash);

    /**
     * Spends every outstanding ticket for one gamer and provider.
     *
     * <p>Called when a new one is minted and again when one is redeemed, so a link started
     * twice cannot be finished twice and a ticket left over from an abandoned attempt stops
     * being a way in.
     */
    @Modifying
    @Query("update AccountLinkTicket t set t.used = true"
            + " where t.userId = :userId and t.provider = :provider and t.used = false")
    int burnAllFor(String userId, LinkedProvider provider);

    /**
     * Drops tickets nobody can use any more.
     *
     * <p>Rows here are write-once and never read again after ten minutes, so without this
     * the table grows by one row per link attempt forever. {@code password_reset_ticket}
     * has the same shape and the same absence; this one is swept because the settings
     * screen makes starting a link and wandering off far cheaper than starting a password
     * reset and wandering off.
     */
    /**
     * Spends a ticket, atomically.
     *
     * <p>A conditional update rather than read-then-write, and the {@code and t.used = false}
     * is the whole point: "check {@code isUsable()}, then {@code setUsed(true)}" is two
     * statements with a gap between them, and under READ_COMMITTED two callbacks carrying the
     * same ticket both read {@code false}, both pass, and both proceed. A browser that
     * double-submits is enough to reach it — no attacker required. Here the database decides,
     * and exactly one caller sees a row count of 1.
     *
     * @return 1 if this caller spent it, 0 if it was already spent or does not exist
     */
    @Modifying
    @Query("update AccountLinkTicket t set t.used = true where t.tokenHash = :tokenHash and t.used = false")
    int spend(String tokenHash);

    @Modifying
    @Query("delete from AccountLinkTicket t where t.expiresAt < :before")
    int deleteExpiredBefore(Instant before);

    /** Tickets belonging to a deleted account. */
    @Modifying
    @Query("delete from AccountLinkTicket t where t.userId = :userId")
    int deleteAllByUserId(String userId);
}
