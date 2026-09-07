package com.gamebuddy.profile.domain.mission;

import static com.gamebuddy.profile.domain.mission.MissionBand.*;
import static com.gamebuddy.shared.badge.BadgeMetric.*;

import com.gamebuddy.shared.badge.BadgeMetric;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

/**
 * The missions. This is the campaign.
 *
 * <p>Twenty-four of them, dealt three at a time. Finish all three and the next three arrive
 * at once — there is no waiting for a Monday, because the thing that made the old three
 * weekly quests feel dead was that finishing them on Tuesday bought you five days of a
 * screen with nothing on it.
 *
 * <h2>Why this is a campaign and not a treadmill</h2>
 *
 * <p>Rewards rise with the set index: set one is the easiest so it pays the least. That is
 * the right shape for a player and the wrong shape for an economy — a ladder that climbs
 * forever is a money glitch with extra steps. So the ladder is finite. There are exactly
 * eight sets, the pool is dealt out once with every mission appearing exactly once, and
 * then it is over. After that the HARD band reshuffles and keeps dealing at the EASY rate,
 * so the earn screen never goes empty and the escalation never comes back.
 *
 * <p>{@link #SETS} falls out of the catalogue rather than being declared beside it: each
 * band holds a whole number of sets, and {@code bandForSet} counts through them. Adding a
 * twenty-fifth mission is therefore a compile-time-fine, test-time-loud change — the
 * invariant test asserts every band divides by {@link #PER_SET}.
 *
 * <h2>Missions may only use cumulative metrics</h2>
 *
 * <p>Progress is the metric now minus what it was when the set was dealt. A metric that can
 * fall — friends, worn cosmetics, the current streak — would leave a mission stuck at zero
 * through no fault of the player, so those are badge material only. See the note at the top
 * of {@code BadgeMetric}.
 *
 * <p>Codes are stable forever and retired codes are spent forever, exactly as in
 * {@code Badge}: a {@code gamer_mission} row outlives the catalogue entry that made it.
 */
@Getter
public enum Mission {

    // --- easy: sets 1-3 ----------------------------------------------------

    TALK_10("talk-10", "Send 10 messages", EASY, MESSAGES_SENT, 10),
    MEET_2("meet-2", "Match with 2 gamers", EASY, MATCHES, 2),
    LOBBY_1("lobby-1", "Join a lobby", EASY, LOBBIES_JOINED_EVER, 1),
    LIKE_10("like-10", "Like 10 profiles", EASY, LIKES_SENT, 10),
    DAILY_2("daily-2", "Claim your daily reward twice", EASY, DAILY_CLAIMS, 2),
    ADVERT_1("advert-1", "Watch a short video", EASY, ADS_WATCHED, 1),
    LOBBY_CHAT_5("lobby-chat-5", "Send 5 messages in a lobby", EASY, LOBBY_MESSAGES_SENT, 5),
    SUPER_LIKE_1("super-like-1", "Send a super like", EASY, SUPER_LIKES_SENT, 1),
    SPEND_100("spend-100", "Spend 100 coins", EASY, COINS_SPENT, 100),

    // --- medium: sets 4-6 --------------------------------------------------

    TALK_50("talk-50", "Send 50 messages", MEDIUM, MESSAGES_SENT, 50),
    MEET_5("meet-5", "Match with 5 gamers", MEDIUM, MATCHES, 5),
    LOBBY_3("lobby-3", "Join 3 lobbies", MEDIUM, LOBBIES_JOINED_EVER, 3),
    HOST_1("host-1", "Open a lobby", MEDIUM, LOBBIES_CREATED, 1),
    LOBBY_CHAT_15("lobby-chat-15", "Send 15 messages in lobbies", MEDIUM, LOBBY_MESSAGES_SENT, 15),
    LIKE_40("like-40", "Like 40 profiles", MEDIUM, LIKES_SENT, 40),
    ADVERT_5("advert-5", "Watch 5 short videos", MEDIUM, ADS_WATCHED, 5),
    BUY_1("buy-1", "Buy something from the Market", MEDIUM, COSMETICS_OWNED, 1),
    DAILY_5("daily-5", "Claim your daily reward 5 times", MEDIUM, DAILY_CLAIMS, 5),

    // --- hard: sets 7-8, and the veteran loop ------------------------------

    TALK_200("talk-200", "Send 200 messages", HARD, MESSAGES_SENT, 200),
    MEET_15("meet-15", "Match with 15 gamers", HARD, MATCHES, 15),
    HOST_3("host-3", "Open 3 lobbies", HARD, LOBBIES_CREATED, 3),
    LOBBY_CHAT_50("lobby-chat-50", "Send 50 messages in lobbies", HARD, LOBBY_MESSAGES_SENT, 50),
    SUPER_LIKE_5("super-like-5", "Send 5 super likes", HARD, SUPER_LIKES_SENT, 5),
    SPEND_1000("spend-1000", "Spend 1,000 coins", HARD, COINS_SPENT, 1000);

    /** How many missions are on screen at once. Subway Surfers' number, and it is the right one. */
    public static final int PER_SET = 3;

    /**
     * Stored in {@code gamer_mission.mission_code}, and the i18n key the app looks the title
     * up by. Kebab-case, stable forever.
     */
    private final String code;

    /**
     * English, and a fallback rather than the thing anybody reads.
     *
     * <p>The app translates by {@link #code} and falls back to this — the pattern the old
     * quests already used. It matters because a mission added server-side would otherwise
     * be blank in six languages until the next store release; this way it is merely English.
     */
    private final String title;

    private final MissionBand band;

    private final BadgeMetric metric;

    /** How much further than the baseline the metric has to go. */
    private final int target;

    Mission(String code, String title, MissionBand band, BadgeMetric metric, int target) {
        this.code = code;
        this.title = title;
        this.band = band;
        this.metric = metric;
        this.target = target;
    }

    /** Every mission in a band, in declaration order. */
    public static List<Mission> inBand(MissionBand band) {
        return Arrays.stream(values()).filter(m -> m.band == band).toList();
    }

    /** How many sets a band is worth. Nine missions is three sets of three. */
    public static int setsIn(MissionBand band) {
        return inBand(band).size() / PER_SET;
    }

    /** The length of the campaign, in sets. Eight, and it is counted rather than declared. */
    public static final int SETS = setsIn(EASY) + setsIn(MEDIUM) + setsIn(HARD);

    /**
     * Which band set {@code setIndex} (1-based) is dealt from.
     *
     * <p>Past the end of the campaign this stays HARD forever — the veteran loop. The
     * reward is what changes there, not the difficulty; see {@link MissionBand#HARD}.
     */
    public static MissionBand bandForSet(int setIndex) {
        int remaining = setIndex;
        for (MissionBand band : MissionBand.values()) {
            remaining -= setsIn(band);
            if (remaining <= 0) {
                return band;
            }
        }
        return HARD;
    }

    /** True once the campaign is behind this gamer and the pool has started repeating. */
    public static boolean isVeteranSet(int setIndex) {
        return setIndex > SETS;
    }

    /**
     * Looks a mission up by the code stored on a row.
     *
     * <p>Empty rather than throwing, for the reason {@code Badge.byCode} is: a dealt row can
     * outlive the mission that made it, and retiring one should make it disappear quietly
     * rather than break the earn screen for whoever was halfway through it.
     */
    public static Optional<Mission> byCode(String code) {
        return Arrays.stream(values()).filter(m -> m.code.equals(code)).findFirst();
    }
}
