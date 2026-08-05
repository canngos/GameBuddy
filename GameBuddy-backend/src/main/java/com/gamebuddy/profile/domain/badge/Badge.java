package com.gamebuddy.profile.domain.badge;

import static com.gamebuddy.shared.badge.BadgeMetric.*;

import com.gamebuddy.shared.badge.BadgeMetric;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;

/**
 * The missions. This is the catalogue.
 *
 * <p>In code, not in a table. A badge is a rule — "match with three players" — and a rule
 * cannot live in a row; the old {@code achievements} table stored a name and a coin value
 * and left the actual condition scattered across three services, which is why one of them
 * was awarded from the match module, one from the profile module and one from the store,
 * each with its own copy of "add it if missing, then notify". Here the rule and the thing
 * it rewards are the same declaration, and {@code DefaultBadgeService} is the only code
 * that awards anything.
 *
 * <p>Order is the order they are shown in, and it is the order of this file: easy and
 * social first, then the long-haul ones. A gamer opening the screen on day one should see
 * something they can finish today at the top.
 *
 * <p>The {@code code} is also the icon's filename ({@code badges/badge-<code>.png} in the
 * media bucket, drawn by {@code badges/generate.py}) and what goes in the database. One
 * string, three uses, so a badge cannot half-exist.
 */
@Getter
public enum Badge {

    // --- getting started ---------------------------------------------------

    FIRST_CONTACT("first-contact", "First Contact", "Match with your first player.", MATCHES, 1, 25),
    FULLY_KITTED(
            "fully-kitted",
            "Fully Kitted",
            "Add a picture, three games and three keywords to your profile.",
            PROFILE_COMPLETENESS,
            3,
            50),
    ICEBREAKER("icebreaker", "Icebreaker", "Send your first message.", MESSAGES_SENT, 1, 25),
    FRIENDLY_PERSON("friendly-person", "Friendly Person", "Add your first friend.", FRIENDS, 1, 25),
    GUILD_MEMBER("guild-member", "Guild Member", "Join a community.", COMMUNITIES_JOINED, 1, 25),
    SAY_SOMETHING("say-something", "Say Something", "Write your first post.", POSTS_WRITTEN, 1, 25),

    // --- getting somewhere -------------------------------------------------

    SQUAD_FORMING("squad-forming", "Squad Forming", "Match with 3 players.", MATCHES, 3, 50),
    DRIP_CHECK("drip-check", "Drip Check", "Wear a frame and a banner at the same time.", COSMETICS_WORN, 2, 50),
    INNER_CIRCLE("inner-circle", "Inner Circle", "Reach 5 friends.", FRIENDS, 5, 100),
    RICH_IN_THE_HOOD("rich-in-the-hood", "Rich in the Hood", "Buy 3 frames or banners.", COSMETICS_OWNED, 3, 100),

    // --- the long haul -----------------------------------------------------

    FULL_PARTY("full-party", "Full Party", "Match with 10 players.", MATCHES, 10, 150),
    LOCAL_LEGEND("local-legend", "Local Legend", "Write 10 posts.", POSTS_WRITTEN, 10, 150),
    NEVER_OFFLINE("never-offline", "Never Offline", "Send 100 messages.", MESSAGES_SENT, 100, 200);

    /** Stored in the database, and the icon's filename. Kebab-case, stable forever. */
    private final String code;

    private final String title;

    /** What to do to earn it, as an instruction. Shown under the title. */
    private final String description;

    private final BadgeMetric metric;

    /** The value of {@link #metric} at which this is earned. */
    private final int target;

    /** Coins credited when the gamer claims it. */
    private final int reward;

    Badge(String code, String title, String description, BadgeMetric metric, int target, int reward) {
        this.code = code;
        this.title = title;
        this.description = description;
        this.metric = metric;
        this.target = target;
        this.reward = reward;
    }

    /** The object key of this badge's icon in the media bucket. */
    public String iconKey() {
        return "badges/badge-" + code + ".png";
    }

    /**
     * Looks a badge up by the code stored on a row.
     *
     * <p>Empty rather than throwing, because a row can outlive its badge: retiring a
     * mission should make it quietly disappear from everyone's list, not break the screen
     * for whoever had earned it.
     */
    public static Optional<Badge> byCode(String code) {
        return Arrays.stream(values()).filter(b -> b.code.equals(code)).findFirst();
    }
}
