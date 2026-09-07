package com.gamebuddy.auth.infrastructure.repository;

import com.gamebuddy.auth.infrastructure.entity.GamerAuthIdentity;
import com.gamebuddy.common.enums.AuthProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface GamerAuthIdentityRepository extends JpaRepository<GamerAuthIdentity, GamerAuthIdentity.Key> {

    /**
     * The sign-in lookup: who, if anyone, is this provider account.
     *
     * <p>Backed by the unique index on {@code (provider, subject)}, which is what stops one
     * Google account from signing in as two gamers.
     */
    Optional<GamerAuthIdentity> findByProviderAndSubject(AuthProvider provider, String subject);

    /** Everything this gamer can sign in with. Read by the settings screen and by unlink. */
    List<GamerAuthIdentity> findByUserId(String userId);

    /**
     * How many ways in this account has.
     *
     * <p>Read before an unlink: removing the last one on an account with no password is a
     * lockout the user had no reason to expect, and is refused.
     */
    long countByUserId(String userId);

    Optional<GamerAuthIdentity> findByUserIdAndProvider(String userId, AuthProvider provider);

    /** Identities belonging to a deleted account. */
    @Modifying
    @Query("delete from GamerAuthIdentity i where i.userId = :userId")
    int deleteAllByUserId(String userId);
}
