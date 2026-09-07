package com.gamebuddy.billing.infrastructure.repository;

import com.gamebuddy.billing.infrastructure.entity.PromoRedemption;
import com.gamebuddy.billing.infrastructure.entity.PromoRedemption.PromoRedemptionId;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PromoRedemptionRepository extends JpaRepository<PromoRedemption, PromoRedemptionId> {

    /**
     * Claims this code for this account, and reports whether this call is the one that got
     * it.
     *
     * <p><b>Not {@code save()}, and the distinction is the entire once-per-account
     * guarantee.</b> The key is assigned rather than generated, so Spring Data sees a
     * non-null id, concludes the row exists, and merges: a SELECT followed by an UPDATE. No
     * constraint is violated, nothing throws, and a double tap is paid twice while the
     * table still holds a single row. That is not hypothetical — it was measured on the
     * rewarded-ad grant, and the unit test missed it because a mocked repository had been
     * told to throw, which asserted an assumption about JPA rather than JPA's behaviour.
     *
     * <p>{@code ON CONFLICT DO NOTHING} moves the decision into the database, where it is
     * atomic. The caller needs no exception handling and no read-then-write of its own.
     *
     * @return 1 if this call claimed the code, 0 if this account had already redeemed it
     */
    @Modifying
    @Query(value = """
                    INSERT INTO promo_redemption
                        (code_id, user_id, kind, coin_amount, gold_days, gold_expires_at, created_at)
                    VALUES
                        (:codeId, :userId, :kind, :coinAmount, :goldDays, :goldExpiresAt, :createdAt)
                    ON CONFLICT (code_id, user_id) DO NOTHING
                    """, nativeQuery = true)
    int claim(
            @Param("codeId") UUID codeId,
            @Param("userId") String userId,
            @Param("kind") String kind,
            @Param("coinAmount") Integer coinAmount,
            @Param("goldDays") Integer goldDays,
            @Param("goldExpiresAt") Instant goldExpiresAt,
            @Param("createdAt") Instant createdAt);

    List<PromoRedemption> findByIdUserIdOrderByCreatedAtDesc(String userId);

    /** Which of a code's recipients have already used it — for the console's edit screen. */
    @Query("select r.id.userId from PromoRedemption r where r.id.codeId = :codeId")
    Set<String> redeemerIds(@Param("codeId") UUID codeId);
}
