package com.gamebuddy.moderation.config;

import java.math.BigDecimal;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * The numbers behind the automatic half of moderation.
 *
 * <p>Configuration rather than constants because every one of them is a guess about a
 * population that does not exist yet. What is not configurable is the shape: nothing
 * here can make the policy ban anyone. Its strongest move is to hide a profile from
 * decks and mark the case urgent, and both of those are undone by whichever decision a
 * person then makes.
 *
 * <p>Tunable at {@code gamebuddy.moderation.*}; see {@code application.yml}.
 */
@Configuration
@ConfigurationProperties(prefix = "gamebuddy.moderation")
@Getter
@Setter
public class ModerationProperties {

    /**
     * The weighted score at which a reported gamer is hidden from decks pending review.
     *
     * <p>A reporter with no history weighs 1.0, so three long-standing users saying the
     * same thing reach this; three accounts made this week reach half of it. Either way it
     * is a hide, not a verdict.
     */
    private BigDecimal hideScore = new BigDecimal("3.0");

    /**
     * How many <em>different</em> people it takes, whatever their combined weight. One
     * heavily trusted reporter is still one person.
     */
    private int hideReporters = 3;

    /** Only reports this recent count towards the hide. Old complaints are history, not a flood. */
    private Duration hideWindow = Duration.ofDays(7);

    /** Younger accounts than this weigh {@link #newAccountWeight} — a brigade is made of new accounts. */
    private Duration newAccountAge = Duration.ofDays(7);

    private BigDecimal newAccountWeight = new BigDecimal("0.5");

    /**
     * Dismissed reports after which a reporter is shown, once, that their reports are not
     * landing. Their reports are still accepted and still reviewed.
     */
    private int lowTrustNotice = 5;

    /** How many messages either side of a reported one are captured as context. */
    private int contextMessages = 10;
}
