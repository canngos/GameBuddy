package com.gamebuddy.billing.infrastructure.repository;

import com.gamebuddy.billing.infrastructure.entity.PromoCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {

    /** Codes are uppercased before they are stored, so the argument must be too. */
    Optional<PromoCode> findByCode(String code);

    boolean existsByCode(String code);

    List<PromoCode> findAllByOrderByCreatedAtDesc();

    /**
     * Codes addressed to this gamer that could still be redeemed.
     *
     * <p>Exhaustion is deliberately not filtered here. It is a race by nature — the last
     * use can be taken between this query and the tap — so the redemption path has to
     * handle it either way, and doing it twice would only mean two places to keep in step.
     */
    @Query("""
            select c from PromoCode c
            where c.disabledAt is null
              and c.expiresAt > :now
              and exists (
                    select 1 from PromoCodeAssignment a
                    where a.id.codeId = c.id and a.id.userId = :userId)
            order by c.expiresAt asc
            """)
    List<PromoCode> findAssignedTo(@Param("userId") String userId, @Param("now") Instant now);

    /**
     * Takes one of the code's remaining redemptions, and says whether there was one.
     *
     * <p>The whole limit check lives in this one statement on purpose. Reading the count,
     * comparing it in Java and writing it back leaves a window in which two redemptions of
     * the last remaining use both see room; here the database compares and increments
     * under the row lock it already takes, so exactly one of them gets the 1.
     *
     * <p>Disabled and expired are re-checked as part of the same condition rather than
     * trusted from the earlier read: an administrator can switch a code off in the moment
     * between validation and grant, and this is the last point where that can still be
     * observed.
     *
     * @return 1 if this call took a redemption, 0 if there was nothing left to take
     */
    @Modifying
    @Query(value = """
                    UPDATE promo_code
                       SET redemption_count = redemption_count + 1
                     WHERE id = :id
                       AND disabled_at IS NULL
                       AND expires_at > :now
                       AND (max_redemptions IS NULL OR redemption_count < max_redemptions)
                    """, nativeQuery = true)
    int reserveRedemption(@Param("id") UUID id, @Param("now") Instant now);
}
