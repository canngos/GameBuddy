package com.gamebuddy.auth.infrastructure.repository;

import com.gamebuddy.auth.infrastructure.entity.Session;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionRepository extends JpaRepository<Session, UUID> {

    Optional<Session> findByTokenHash(String tokenHash);

    /**
     * Bulk delete in one statement. The previous {@code findAllByEmail} +
     * {@code deleteAll} pair issued a SELECT plus one DELETE per row, and the
     * ban path used {@code findByEmail(...).orElse(new Session())} which handed a
     * transient entity to {@code delete()}.
     */
    @Modifying
    @Query("delete from Session s where s.email = :email")
    int deleteAllByEmail(String email);

    /** Housekeeping for tokens that have simply aged out. */
    @Modifying
    @Query("delete from Session s where s.expiresAt < :cutoff")
    int deleteAllExpired(Instant cutoff);
}
