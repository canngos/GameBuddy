package com.gamebuddy.match.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Matching parameters that depend on how many gamers exist, settable per deployment.
 *
 * <p>Tunable at {@code gamebuddy.match.*}; see {@code application.yml} for the environment
 * variables. The defaults below are what this build was tested with, so an environment that
 * sets nothing behaves exactly as verified.
 */
@Configuration
@ConfigurationProperties(prefix = "gamebuddy.match")
@Getter
@Setter
public class MatchProperties {

    /**
     * How long a pass keeps someone out of the feed.
     *
     * <p><strong>Why declines expire at all.</strong> The recommendation ranking is a
     * deterministic function of profiles, so without a window a pass is permanent: everyone
     * ever declined is excluded forever and the candidate pool only shrinks. A gamer who
     * swipes enthusiastically for a week ends up with an empty feed and no way back. Expiry
     * is not ignoring the decision; it is recognising that the decision was about a profile
     * that has probably changed its games, its keywords or its photo since.
     *
     * <p><strong>Why this is configuration and not a constant.</strong> It was
     * {@code static final}, set to thirty days because that is roughly where the large dating
     * apps are understood to sit. That number is not a fact about people, it is a fact about
     * <em>density</em>: in a pool where nobody can swipe deep enough to reach the end, a long
     * window costs nothing and only ever catches the rare heavy swiper. Borrowing it for a
     * pool that every gamer can exhaust in one sitting borrows the number without the
     * condition that made it free.
     *
     * <p><strong>Why seven days.</strong> It still satisfies what the window is actually for
     * — a pass is not undone while the gamer who made it still remembers it — while meaning
     * somebody who reaches the end of the deck on Saturday has one again the following
     * weekend. Raise it as the user base grows; the right value is the largest one that does
     * not routinely empty a deck. Going much lower is not the safe direction it appears to
     * be: at a day or two the same faces return so promptly that passing feels ignored.
     *
     * <p>Read by both the exclusion queries and the retention sweep that deletes rows past
     * the window. Deliberately one value and not two: the sweep must trail the read cutoff,
     * and two copies of a window eventually disagree about where it is.
     */
    private Duration declineRecycle = Duration.ofDays(7);
}
