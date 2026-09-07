package com.gamebuddy.shared.coin;

import com.gamebuddy.shared.entity.Gamer;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Moves coins and writes down that it happened.
 *
 * <p>One place, called from every faucet and sink, so the ledger cannot fall behind the
 * balance by somebody adding a feature and forgetting the second line. It deliberately
 * does both the mutation and the record: a helper that only wrote the record would be
 * exactly as easy to forget as the record itself.
 *
 * <p>No transaction of its own — it joins the caller's, which is what makes the balance
 * and the entry commit or roll back together. A ledger that survived a rolled-back
 * purchase would be worse than no ledger, because it would be believed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoinLedger {

    private final CoinLedgerRepository entries;
    private final Clock clock;

    /**
     * Adds coins and records why.
     *
     * @param amount how many, positive
     */
    public void earn(Gamer gamer, int amount, CoinReason reason) {
        if (amount <= 0) {
            return;
        }
        gamer.setCoin(gamer.getCoin() + amount);
        entries.save(new CoinLedgerEntry(gamer.getUserId(), amount, reason, clock.instant()));
    }

    /**
     * Takes coins and records why.
     *
     * <p>Does not check affordability — the caller has to, because only the caller knows
     * what to say when the answer is no. It does floor the balance at zero, which matters
     * for a refund clawing back coins that have already been spent.
     *
     * @param amount how many, positive
     */
    public void spend(Gamer gamer, int amount, CoinReason reason) {
        if (amount <= 0) {
            return;
        }
        int taken = Math.min(amount, gamer.getCoin());
        gamer.setCoin(gamer.getCoin() - taken);
        entries.save(new CoinLedgerEntry(gamer.getUserId(), -taken, reason, clock.instant()));

        if (taken < amount) {
            // Only reachable from a refund against a spent balance. Worth a line: it is the
            // one case where the ledger and the money genuinely disagree.
            log.info("Clawed back only {} of {} coins from {}", taken, amount, gamer.getUserId());
        }
    }
}
