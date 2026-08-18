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

    /**
     * The live code for an address, if any.
     *
     * <p>The only way in now that the code is hashed: there is no finder taking a code,
     * because bcrypt output cannot be matched with a WHERE clause. Callers fetch this row
     * and compare against it, which is also what charges the attempt.
     */
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
