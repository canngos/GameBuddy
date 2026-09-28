package com.gamebuddy.billing.infrastructure.repository;

import com.gamebuddy.billing.infrastructure.entity.Purchase;
import com.gamebuddy.billing.infrastructure.entity.PurchasePlatform;
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
public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    /** The idempotency check. Backed by the unique constraint on the same two columns. */
    boolean existsByPlatformAndStoreTransactionId(PurchasePlatform platform, String storeTransactionId);

    Optional<Purchase> findByPlatformAndStoreTransactionId(PurchasePlatform platform, String storeTransactionId);

    /** A gamer's purchase history, newest first — for the account screen and support. */
    List<Purchase> findByUserIdOrderByPurchasedAtDesc(String userId);

    /** Claims a transfer ID atomically; a webhook retry receives zero and changes nothing. */
    @Modifying
    @Query(value = """
                    INSERT INTO revenuecat_transfer_event (event_id, processed_at)
                    VALUES (:eventId, :now)
                    ON CONFLICT (event_id) DO NOTHING
                    """, nativeQuery = true)
    int claimTransferEvent(@Param("eventId") String eventId, @Param("now") Instant now);
}
