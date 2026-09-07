package com.gamebuddy.auth.infrastructure.repository;

import com.gamebuddy.auth.infrastructure.entity.PasswordResetTicket;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PasswordResetTicketRepository extends JpaRepository<PasswordResetTicket, UUID> {

    /** The only lookup: the client presents a token, we hash it and match. */
    Optional<PasswordResetTicket> findByTokenHash(String tokenHash);

    /**
     * Spends every outstanding ticket for an address.
     *
     * <p>Called when one is redeemed and again when a new one is issued, so a reset begun
     * twice cannot be completed twice — and a ticket left over from an abandoned attempt
     * stops being a way in.
     */
    @Modifying
    @Query("update PasswordResetTicket t set t.used = true where t.email = :email and t.used = false")
    int burnAllForEmail(String email);
}
