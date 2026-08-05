package com.gamebuddy.billing.infrastructure.repository;

import com.gamebuddy.billing.infrastructure.entity.Purchase;
import com.gamebuddy.billing.infrastructure.entity.PurchasePlatform;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    /** The idempotency check. Backed by the unique constraint on the same two columns. */
    boolean existsByPlatformAndStoreTransactionId(PurchasePlatform platform, String storeTransactionId);

    Optional<Purchase> findByPlatformAndStoreTransactionId(PurchasePlatform platform, String storeTransactionId);

    /** A gamer's purchase history, newest first — for the account screen and support. */
    List<Purchase> findByUserIdOrderByPurchasedAtDesc(String userId);
}
