package com.gamebuddy.shared.coin;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CoinLedgerRepository extends JpaRepository<CoinLedgerEntry, UUID> {

    /**
     * Coins this gamer has spent, lifetime, as a positive number.
     *
     * <p>Off the ledger rather than off the balance, because the balance is what is left
     * and the mission is about what was let go of. Somebody who has earned 5,000 and spent
     * 5,000 has a balance of nothing and has spent a great deal.
     *
     * <p>Only negative deltas, negated. {@code coalesce} because a gamer who has never
     * spent anything has no rows at all, and a null there would reach {@code measure()} as
     * a {@link NullPointerException} rather than as the zero it means.
     */
    @Query("select coalesce(-sum(e.delta), 0) from CoinLedgerEntry e where e.userId = :userId and e.delta < 0")
    long sumSpent(@Param("userId") String userId);
}
