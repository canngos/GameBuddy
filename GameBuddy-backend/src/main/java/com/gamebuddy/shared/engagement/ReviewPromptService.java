package com.gamebuddy.shared.engagement;

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
 * Whether to ask this gamer for a Play Store review, and the record that it was asked.
 *
 * <p>Modelled on {@link com.gamebuddy.billing.domain.UpgradePromptService}, with one
 * deliberate difference that is worth stating first.
 *
 * <h2>Why the check and the record are a single call</h2>
 *
 * <p>The upgrade prompt is decided by the server and then marked by the client, once it has
 * actually rendered. That split is right there, because the client genuinely knows whether
 * it drew the sheet.
 *
 * <p>Here it cannot know. Google Play decides for itself whether to show a review card —
 * every app shares a rolling per-user quota — and {@code requestReview()} resolves exactly
 * the same way whether the card appeared or was silently dropped. There is no callback and
 * no error. So a client reporting "shown" would be reporting a guess, and the two available
 * mistakes are not symmetrical:
 *
 * <ul>
 *   <li>Record the ask, and a throttled card costs one missed request. The gamer sees
 *       nothing and is asked again in ninety days.
 *   <li>Record only confirmed showings, and there are none to record — so the app would ask
 *       Play again on every single dismissed match, forever. That is review spam, it is
 *       against Play's own guidance, and Play answers it by throttling this app harder.
 * </ul>
 *
 * <p>So this claims and marks in one transaction, and the name says so: a claim, not a
 * question.
 *
 * <h2>The conditions</h2>
 *
 * <p>Three days old, two mutual matches, and at least one message sent. Someone who has not
 * matched has no opinion to give about a matching app; someone who matched but never spoke
 * has not seen it work. Two matches rather than one because one can be luck, and a review is
 * a question about the product rather than about a good afternoon.
 *
 * <p><b>Every condition is evaluated server-side.</b> A client that decided this itself
 * would be a client that could be wrong about it, silently, in both directions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewPromptService {

    /** Long enough that the app has been used rather than merely installed. */
    static final Duration MIN_AGE = Duration.ofDays(3);

    /** One match can be luck. */
    static final int MIN_MATCHES = 2;

    /**
     * How long before the same gamer may be asked again.
     *
     * <p>Play enforces its own quota and will not say what it is. Ninety days is this
     * application's own floor on top of that, chosen so that somebody who declines is not
     * asked twice in a season and somebody who has since had a much better run still gets a
     * second chance eventually.
     */
    static final Duration COOLDOWN = Duration.ofDays(90);

    private final GamerRepository gamers;
    private final Clock clock;

    /**
     * Answers whether to ask now, and records the ask in the same breath.
     *
     * <p>Ordered cheapest-first: two of the three conditions are already on the principal,
     * and the database is only touched once everything free has passed. This is called from
     * the deck the moment a match celebration is dismissed, which is not a moment to spend
     * two queries on somebody who signed up yesterday.
     *
     * <p>The final write is conditional, so two devices dismissing the same match at the
     * same time produce one ask rather than two. Whoever's update touches a row wins; the
     * other is told no and shows nothing.
     */
    @Transactional
    public boolean claim(Gamer principal) {
        if (principal == null) {
            return false;
        }

        Instant now = clock.instant();
        Instant createdDate = principal.getCreatedDate();
        if (createdDate == null || createdDate.isAfter(now.minus(MIN_AGE))) {
            // Null means the row pre-dates the column being populated, and there is no way
            // to tell a three-day-old account from a three-minute-old one. Not asking is the
            // safe direction — the cost is a missed review, and the other direction is
            // interrupting somebody in their first minute.
            return false;
        }

        String userId = principal.getUserId();
        if (!gamers.hasMutualMatches(userId, MIN_MATCHES) || !gamers.hasSentMessage(userId)) {
            return false;
        }

        boolean claimed = gamers.claimReviewPrompt(userId, now, now.minus(COOLDOWN)) == 1;
        if (claimed) {
            log.debug("Review prompt claimed for {}", userId);
        }
        return claimed;
    }
}
