package com.gamebuddy.auth.infrastructure.repository;

import com.gamebuddy.auth.infrastructure.entity.SocialLoginTicket;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SocialLoginTicketRepository extends JpaRepository<SocialLoginTicket, UUID> {

    /** The only lookup: a caller presents a token, we hash it and match. */
    Optional<SocialLoginTicket> findByTokenHash(String tokenHash);

    /**
     * Spends a ticket, atomically.
     *
     * <p>Conditional update rather than read-then-write, for the same reason as
     * {@code AccountLinkTicketRepository#spend}: two requests carrying the same ticket both
     * read {@code used = false} under READ_COMMITTED, both pass, and both would mint a
     * session. Here the database decides and exactly one caller sees a row count of 1.
     *
     * @return 1 if this caller spent it, 0 if it was already spent or does not exist
     */
    @Modifying
    @Query("update SocialLoginTicket t set t.used = true where t.tokenHash = :tokenHash and t.used = false")
    int spend(String tokenHash);

    /**
     * Drops tickets nobody can use any more.
     *
     * <p>Every row here is unusable ten minutes after it is written, and two are minted per
     * sign-in — so without a sweep this table grows twice as fast as the link-ticket one.
     */
    @Modifying
    @Query("delete from SocialLoginTicket t where t.expiresAt < :before")
    int deleteExpiredBefore(Instant before);
}
