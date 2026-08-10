package com.gamebuddy.shared.repository;

import com.gamebuddy.shared.entity.GamerCosmetic;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/** Who bought what. */
@Repository
public interface GamerCosmeticRepository extends JpaRepository<GamerCosmetic, GamerCosmetic.Key> {

    List<GamerCosmetic> findAllByUserId(String userId);

    /**
     * Just the ids, for deciding what to mark as owned in the store.
     *
     * <p>The store needs a set membership test, not the receipts. Loading whole rows to
     * throw away two of their four columns is the sort of thing that is free at ten items
     * and not at ten thousand.
     */
    @Query("select gc.cosmeticId from GamerCosmetic gc where gc.userId = :userId")
    Set<UUID> findOwnedIds(String userId);

    boolean existsByUserIdAndCosmeticId(String userId, UUID cosmeticId);

    /**
     * Erases a deleted account's purchases.
     *
     * <p>Bulk delete rather than loading and removing: this runs inside account deletion,
     * where the point is to leave nothing behind, and there is nothing to inspect on the
     * way out.
     */
    @Modifying
    @Query("delete from GamerCosmetic gc where gc.userId = :userId")
    void deleteAllByUserId(String userId);

    // --- membership cosmetics -----------------------------------------------
    //
    // Native, and set-based on purpose. The alternative is loading every gamer and every
    // membership cosmetic into the application to compute a difference that the database
    // can express directly — and at a thousand accounts that is a thousand round trips to
    // decide that nothing changed.
    //
    // The tier test is spelled out here rather than calling SubscriptionTier.effective,
    // which is the rule everywhere else. It is the same rule: a tier counts only while its
    // expiry is in the future, and a null expiry is not a membership. Duplicating it in
    // SQL is a real cost — if that policy changes, this must change with it — and the
    // alternative is fetching every row to ask Java the same question.

    /** Gives every current Gold member every membership cosmetic they do not already have. */
    @Modifying
    @Query(value = """
                    INSERT INTO gamer_cosmetic (user_id, cosmetic_id, paid, acquired_at)
                    SELECT g.user_id, c.id, 0, :now
                    FROM gamer g
                    CROSS JOIN cosmetic c
                    WHERE c.membership_only = true
                      AND g.subscription_tier = 'GOLD'
                      AND g.subscription_expires_at IS NOT NULL
                      AND g.subscription_expires_at > :now
                      AND g.deleted_at IS NULL
                      AND NOT EXISTS (
                          SELECT 1 FROM gamer_cosmetic gc
                          WHERE gc.user_id = g.user_id AND gc.cosmetic_id = c.id)
                    """, nativeQuery = true)
    int grantMembershipCosmetics(Instant now);

    /** Takes them back from everybody whose membership is not currently active. */
    @Modifying
    @Query(value = """
                    DELETE FROM gamer_cosmetic gc
                    USING cosmetic c, gamer g
                    WHERE c.id = gc.cosmetic_id
                      AND g.user_id = gc.user_id
                      AND c.membership_only = true
                      AND (g.subscription_tier IS DISTINCT FROM 'GOLD'
                           OR g.subscription_expires_at IS NULL
                           OR g.subscription_expires_at <= :now)
                    """, nativeQuery = true)
    int revokeLapsedMembershipCosmetics(Instant now);

    /**
     * Clears anything somebody is wearing but no longer owns.
     *
     * <p>Separate from the revoke, and after it, because the equipped columns live on
     * {@code gamer} rather than on the ownership row — revoking alone would leave a lapsed
     * member still visibly wearing the frame on everybody else's screen, which is the
     * exact thing this whole mechanism exists to prevent.
     */
    @Modifying
    @Query(value = """
                    UPDATE gamer g
                    SET equipped_frame_id = CASE
                            WHEN g.equipped_frame_id IS NOT NULL AND NOT EXISTS (
                                SELECT 1 FROM gamer_cosmetic gc
                                WHERE gc.user_id = g.user_id AND gc.cosmetic_id = g.equipped_frame_id)
                            THEN NULL ELSE g.equipped_frame_id END,
                        equipped_banner_id = CASE
                            WHEN g.equipped_banner_id IS NOT NULL AND NOT EXISTS (
                                SELECT 1 FROM gamer_cosmetic gc
                                WHERE gc.user_id = g.user_id AND gc.cosmetic_id = g.equipped_banner_id)
                            THEN NULL ELSE g.equipped_banner_id END
                    WHERE (g.equipped_frame_id IS NOT NULL AND NOT EXISTS (
                               SELECT 1 FROM gamer_cosmetic gc
                               WHERE gc.user_id = g.user_id AND gc.cosmetic_id = g.equipped_frame_id))
                       OR (g.equipped_banner_id IS NOT NULL AND NOT EXISTS (
                               SELECT 1 FROM gamer_cosmetic gc
                               WHERE gc.user_id = g.user_id AND gc.cosmetic_id = g.equipped_banner_id))
                    """, nativeQuery = true)
    int unequipUnownedCosmetics();
}
