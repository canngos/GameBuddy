package com.gamebuddy.shared.funnel;

/**
 * Which side of the free like-cap experiment an account is on.
 *
 * <p>Derived from the user id rather than drawn at random, which buys two things. The
 * assignment is reproducible — the same account always lands in the same bucket, so a
 * re-run of the backfill cannot reshuffle history — and it needs no coordination, so two
 * instances registering accounts at the same time still split evenly.
 *
 * <p><b>The cap itself is still uniform.</b> This records the bucket ahead of the decision
 * (see the analysis's fourth open question) because assignment has to precede the
 * behaviour it explains. Turning the experiment on later is then a change to
 * {@code SubscriptionTier.dailyAccepts}, not a migration and an apology about the data.
 */
public final class LikeCapCohort {

    public static final String CONTROL = "CONTROL";
    public static final String VARIANT = "VARIANT";

    private LikeCapCohort() {}

    /**
     * An even, stable split.
     *
     * <p>Deliberately <em>not</em> the same function as the SQL backfill in
     * upgrade-2026-22, which uses md5 because that is what Postgres has to hand. They do
     * not need to agree: every account is assigned exactly once, by whichever ran first,
     * and never reassigned. What matters is that each rule is stable and even on its own —
     * an account that changed bucket would corrupt both cohorts at once.
     */
    public static String forUser(String userId) {
        return Math.floorMod(userId.hashCode(), 2) == 0 ? CONTROL : VARIANT;
    }
}
