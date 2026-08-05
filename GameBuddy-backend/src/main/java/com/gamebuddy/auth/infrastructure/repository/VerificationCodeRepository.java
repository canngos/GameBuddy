package com.gamebuddy.auth.infrastructure.repository;

import com.gamebuddy.auth.infrastructure.entity.VerificationCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Note the id type: this was declared {@code JpaRepository<VerificationCode, String>}
 * against a {@code UUID} primary key, which would have failed on any {@code findById}.
 */
@Repository
public interface VerificationCodeRepository extends JpaRepository<VerificationCode, UUID> {

    Optional<VerificationCode> findByEmailAndCodeAndIsValidTrue(String email, Integer code);

    /** The live code for an address, if any. Used to enforce the attempt cap. */
    Optional<VerificationCode> findFirstByEmailAndIsValidTrueOrderByCreatedAtDesc(String email);

    /**
     * Invalidates every outstanding code for an address in a single statement.
     *
     * <p>Replaces the in-memory {@code forEach(vc -> vc.setIsValid(false))} that was
     * never persisted, leaving every historical code permanently usable.
     */
    @Modifying
    @Query("update VerificationCode v set v.isValid = false where v.email = :email and v.isValid = true")
    int invalidateAllForEmail(String email);
}
