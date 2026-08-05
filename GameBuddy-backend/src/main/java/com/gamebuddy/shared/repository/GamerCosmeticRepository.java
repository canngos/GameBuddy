package com.gamebuddy.shared.repository;

import com.gamebuddy.shared.entity.GamerCosmetic;
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
}
