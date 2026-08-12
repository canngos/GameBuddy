package com.gamebuddy.billing.domain;

import com.gamebuddy.common.enums.SubscriptionTier;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one-time day-3 nudge towards Gold.
 *
 * <p>Three days, and only after a match. The delay is not arbitrary: somebody who has not
 * yet had a match has no evidence the product works, so asking them for money is asking
 * them to buy a promise. Somebody who has matched has seen the thing they would be buying
 * more of. The analysis put the ask here for that reason, and the two conditions are what
 * make it an offer rather than an interruption.
 *
 * <p><b>Every condition is evaluated server-side.</b> The client is told yes or no and
 * nothing else — not the signup date, not the match count. A client that computed this
 * itself would be a client that could be wrong about it, and the failure would be silent
 * in both directions: prompting people who should not be prompted, and never prompting
 * people who should.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpgradePromptService {

    /**
     * How old the account has to be.
     *
     * <p>Three days from the analysis. Measured from {@code created_date} rather than from
     * first use, because an account that sat unopened for a week has not had three days of
     * the product — but it has had three days of not being asked, which is the part that
     * matters for not being pushy.
     */
    static final Duration MIN_AGE = Duration.ofDays(3);

    private final GamerRepository gamers;
    private final Clock clock;

    /**
     * Whether this gamer should be shown the prompt now.
     *
     * <p>Ordered cheapest-first, and the database is only touched if everything free has
     * already passed. This runs inside {@code GET /billing/subscription}, which the app
     * polls while a purchase is going through, so the common case has to cost nothing.
     */
    public boolean isDue(Gamer gamer) {
        if (gamer == null || gamer.getUpgradePromptShownAt() != null) {
            return false;
        }

        Instant now = clock.instant();

        // Somebody who already pays does not need selling. Checked through effective()
        // rather than off the stored tier, so a lapsed subscriber counts as free again —
        // they are a better prospect than a stranger, not a worse one.
        if (SubscriptionTier.effective(gamer.getSubscriptionTier(), gamer.getSubscriptionExpiresAt(), now)
                != SubscriptionTier.BASIC) {
            return false;
        }

        Instant createdDate = gamer.getCreatedDate();
        if (createdDate == null) {
            // Null means the row pre-dates the column being populated, and there is no way
            // to tell a three-day-old account from a three-minute-old one. Not prompting is
            // the safe direction: the cost is a missed sale, where the other direction is
            // pitching a subscription to somebody who signed up a moment ago.
            return false;
        }
        if (createdDate.isAfter(now.minus(MIN_AGE))) {
            return false;
        }

        return gamers.hasMutualMatch(gamer.getUserId());
    }

    /**
     * Records that it has been shown, so it is never shown again.
     *
     * <p>Written when the client says it displayed the prompt rather than when the server
     * decides it is due. The two are not the same moment — the answer can be fetched and
     * then thrown away by a navigation, a backgrounded app or a crash — and marking it on
     * the read would burn the single showing on a prompt nobody saw.
     *
     * <p>The cost of getting this wrong in the other direction is one duplicate prompt for
     * somebody whose app died mid-render, which is the cheaper mistake.
     *
     * <p>Re-reads the row rather than writing through the caller's copy: the principal was
     * loaded by the JWT filter in a persistence context that closed long ago, and writing a
     * detached entity would push every stale field on it back to the database along with
     * this one.
     */
    @Transactional
    public void markShown(String userId) {
        gamers.findById(userId).ifPresent(gamer -> {
            if (gamer.getUpgradePromptShownAt() != null) {
                // Already recorded. Not an error and not worth a write — two devices
                // rendering the same prompt is a normal race, not a problem.
                return;
            }
            gamer.setUpgradePromptShownAt(clock.instant());
            gamers.save(gamer);
        });
    }
}
