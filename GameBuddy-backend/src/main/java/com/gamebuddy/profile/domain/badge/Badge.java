package com.gamebuddy.profile.domain.badge;

import static com.gamebuddy.profile.domain.badge.BadgeTier.*;
import static com.gamebuddy.shared.badge.BadgeMetric.*;

import com.gamebuddy.shared.badge.BadgeMetric;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;

/**
 * The badges. This is the catalogue.
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
 * social first, then the long-haul ones, then the hard tier. A gamer opening the screen on
 * day one should see something they can finish today at the top, and something they will
 * not finish this year at the bottom.
 *
 * <p>The {@code code} is also the icon's filename ({@code badges/badge-<code>.png} in the
 * media bucket, drawn by {@code badges/generate.py}) and what goes in the database. One
 * string, three uses, so a badge cannot half-exist.
 *
 * <h2>Rewards come from the tier</h2>
 *
 * <p>There is no per-badge coin figure any more. {@link BadgeTier} carries it, which is
 * what stopped the catalogue drifting to 775 coins across ten badges. A PRISMATIC badge
 * either pays its tier's 125 or grants an exclusive cosmetic instead — see
 * {@link #grantsCosmetic()} — and never does both.
 */
@Getter
public enum Badge {

    // --- getting started ---------------------------------------------------

    FIRST_CONTACT("first-contact", "First Contact", "Match with your first player.", BRONZE, MATCHES, 1),
    FULLY_KITTED(
            "fully-kitted",
            "Fully Kitted",
            "Add a picture, three games and three keywords to your profile.",
            BRONZE,
            PROFILE_COMPLETENESS,
            3),
    ICEBREAKER("icebreaker", "Icebreaker", "Send your first message.", BRONZE, MESSAGES_SENT, 1),
    FRIENDLY_PERSON("friendly-person", "Friendly Person", "Add your first friend.", BRONZE, FRIENDS, 1),

    // guild-member, say-something and local-legend retired with the Community feature.
    // Their earned rows survive on purpose — byCode() answers empty and the screen skips
    // them — but the codes are spent forever: reusing one would resurrect everybody's old
    // badge under a new name. FIRST_LOBBY below is the lobby mission they made room for.
    FIRST_LOBBY("first-lobby", "Signed Up", "Join your first lobby.", BRONZE, LOBBIES_JOINED_EVER, 1),
    HOST_MODE("host-mode", "Host Mode", "Open a lobby of your own.", BRONZE, LOBBIES_CREATED, 1),
    STANDING_OVATION(
            "standing-ovation", "Standing Ovation", "Send your first super like.", BRONZE, SUPER_LIKES_SENT, 1),

    // --- getting somewhere -------------------------------------------------

    SQUAD_FORMING("squad-forming", "Squad Forming", "Match with 3 players.", SILVER, MATCHES, 3),
    DRIP_CHECK("drip-check", "Drip Check", "Wear a frame and a banner at the same time.", SILVER, COSMETICS_WORN, 2),
    INNER_CIRCLE("inner-circle", "Inner Circle", "Reach 5 friends.", SILVER, FRIENDS, 5),
    RICH_IN_THE_HOOD("rich-in-the-hood", "Rich in the Hood", "Buy 3 frames or banners.", SILVER, COSMETICS_OWNED, 3),
    LOBBY_REGULAR("lobby-regular", "Regular", "Join 5 lobbies.", SILVER, LOBBIES_JOINED_EVER, 5),
    WAR_ROOM("war-room", "War Room", "Send 25 messages in lobby chat.", SILVER, LOBBY_MESSAGES_SENT, 25),
    BIG_SPENDER("big-spender", "Big Spender", "Spend 1,000 coins.", SILVER, COINS_SPENT, 1000),
    SPONSORED("sponsored", "Sponsored", "Watch 10 short videos.", SILVER, ADS_WATCHED, 10),

    // --- the long haul -----------------------------------------------------

    FULL_PARTY("full-party", "Full Party", "Match with 10 players.", GOLD, MATCHES, 10),
    NEVER_OFFLINE("never-offline", "Never Offline", "Send 100 messages.", GOLD, MESSAGES_SENT, 100),
    WEEK_ONE("week-one", "Seven Days", "Keep a seven-day streak.", GOLD, DAILY_STREAK, 7),

    // --- the hard tier -----------------------------------------------------
    // Months of work each, and the only badges the app draws an aura over. Three pay 125;
    // four grant a cosmetic nobody can buy, and those carry a reward of zero because the
    // frame *is* the reward. Two of the seven also have moving artwork — see isAnimated().

    CENTURION("centurion", "Centurion", "Send 1,000 messages.", PRISMATIC, MESSAGES_SENT, 1000, 125),
    IRON_WILL("iron-will", "Iron Will", "Claim the daily reward 100 times.", PRISMATIC, DAILY_CLAIMS, 100, 125),
    MAGNETIC("magnetic", "Magnetic", "Match with 50 players.", PRISMATIC, MATCHES, 50, 0),
    UNBROKEN("unbroken", "Unbroken", "Keep a thirty-day streak.", PRISMATIC, DAILY_STREAK, 30, 0),
    COLLECTOR("collector", "Collector", "Own 15 frames, banners or themes.", PRISMATIC, COSMETICS_OWNED, 15, 0),
    /**
     * Finish the mission campaign — all eight sets, all twenty-four missions.
     *
     * <p>Over {@code MISSION_SETS_DONE} rather than granted by hand from the claim that ends
     * the campaign. A metric means this is awarded by the same loop as everything else,
     * with the same notification and the same once-only guarantee, instead of a second
     * awarding path that would have to be kept in step with the first. {@code MissionService}
     * only nudges the evaluator so the badge arrives with the last claim rather than on the
     * next screen.
     */
    TRAILBLAZER("trailblazer", "Trailblazer", "Finish every mission set.", PRISMATIC, MISSION_SETS_DONE, 8, 0),
    /**
     * Every other badge in the game.
     *
     * <p>The target is one less than the size of this enum, and a test asserts that so the
     * two cannot drift when a badge is added. It counts rows rather than live catalogue
     * entries, so somebody holding a retired code is credited for it — see
     * {@code BadgeMetric.BADGES_EARNED}.
     */
    // "Full Set", not "Completionist": a badge title sits under a tile one third of the
    // screen wide, and a thirteen-letter word with nowhere to break renders as
    // "Completionis / t" at font scale 1.3. Two short words wrap where a reader expects.
    // The code stays `completionist` — codes are stable forever, titles are not.
    COMPLETIONIST("completionist", "Full Set", "Earn every other badge.", PRISMATIC, BADGES_EARNED, 24, 125);

    /** Stored in the database, and the icon's filename. Kebab-case, stable forever. */
    private final String code;

    private final String title;

    /** What to do to earn it, as an instruction. Shown under the title. */
    private final String description;

    /** How hard it is. Decides the reward, the rim colour, and whether it animates. */
    private final BadgeTier tier;

    private final BadgeMetric metric;

    /** The value of {@link #metric} at which this is earned. */
    private final int target;

    /**
     * Coins credited when the gamer claims it.
     *
     * <p>Normally {@link BadgeTier#getReward()}. The only badges that override it are the
     * PRISMATIC ones that pay a cosmetic instead, which carry zero.
     */
    private final int reward;

    Badge(String code, String title, String description, BadgeTier tier, BadgeMetric metric, int target) {
        this(code, title, description, tier, metric, target, tier.getReward());
    }

    Badge(String code, String title, String description, BadgeTier tier, BadgeMetric metric, int target, int reward) {
        this.code = code;
        this.title = title;
        this.description = description;
        this.tier = tier;
        this.metric = metric;
        this.target = target;
        this.reward = reward;
    }

    /**
     * True when claiming this unlocks a cosmetic rather than paying coins.
     *
     * <p>Derived rather than declared, and deliberately one-directional: the {@code cosmetic}
     * row names the badge that unlocks it, so there is exactly one place that says which
     * frame goes with which badge. A slug here as well would be a second place, and two
     * places for one fact is how a badge ends up granting the wrong thing.
     */
    public boolean grantsCosmetic() {
        return tier == PRISMATIC && reward == 0;
    }

    /**
     * True for the two badges whose artwork is animated WebP rather than a still PNG.
     *
     * <p>Not every PRISMATIC badge. All seven get the aura the app draws over them, which
     * costs nothing to download; only these two also get moving art, because animated WebP
     * is the most expensive thing this app decodes and the badge grid is three columns of
     * it. Two is enough to make the bottom of the wall look different from the top.
     *
     * <p>They are the two that can only be earned last: one needs every mission set, the
     * other needs every other badge.
     */
    public boolean isAnimated() {
        return this == TRAILBLAZER || this == COMPLETIONIST;
    }

    /**
     * The object key of this badge's icon in the media bucket.
     *
     * <p>Animated WebP for the two above — the one format that animates on both Android and
     * iOS — and a still PNG for everything else. Both are drawn by
     * {@code badges/generate.py}, and {@code expo-image} plays either, so this is the only
     * place in the backend the difference exists.
     */
    public String iconKey() {
        return "badges/badge-" + code + (isAnimated() ? ".webp" : ".png");
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
