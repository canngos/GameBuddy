package com.gamebuddy.profile.infrastructure.repository;

import com.gamebuddy.profile.infrastructure.entity.RewardedAdGrant;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RewardedAdGrantRepository extends JpaRepository<RewardedAdGrant, String> {

    /**
     * Claims a transaction id, and reports whether this call is the one that got it.
     *
     * <p><b>Not {@code save()}, and that distinction is the whole replay defence.</b> The
     * primary key here is AdMob's value rather than one we generate, so it is already set
     * on a new entity — and Spring Data decides between persist and merge by asking whether
     * the id is null. With an assigned id it always chooses merge, which issues a SELECT and
     * then an UPDATE. No constraint is ever violated, no exception is ever thrown, and a
     * replayed callback quietly overwrites its own row and is paid again.
     *
     * <p>That is not theoretical: it was measured. Replaying one signed callback against a
     * running server paid the coins a second time, while the table still held a single row.
     * The unit test missed it because a mocked repository was told to throw, which asserted
     * an assumption about JPA rather than JPA's behaviour.
     *
     * <p>{@code ON CONFLICT DO NOTHING} moves the decision into the database, where it is
     * atomic: two concurrent retries of the same reward cannot both see one row. The return
     * value is the number of rows inserted — 1 for the caller that won it, 0 for a repeat —
     * so the caller needs no exception handling and no read-then-write race of its own.
     *
     * @return 1 if this call claimed the transaction, 0 if it was already claimed
     */
    @Modifying
    @Query(value = """
                    INSERT INTO rewarded_ad_grant (transaction_id, user_id, coins, created_at)
                    VALUES (:transactionId, :userId, :coins, :createdAt)
                    ON CONFLICT (transaction_id) DO NOTHING
                    """, nativeQuery = true)
    int claim(
            @Param("transactionId") String transactionId,
            @Param("userId") String userId,
            @Param("coins") int coins,
            @Param("createdAt") Instant createdAt);
}
