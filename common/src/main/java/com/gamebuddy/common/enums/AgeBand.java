package com.gamebuddy.common.enums;

/**
 * Which age group a gamer may be matched within.
 *
 * <p>GameBuddy pairs strangers and then puts them in a private one-to-one chat, so who
 * may be paired with whom is a safety decision, not a preference. Minors are never
 * matched with adults.
 *
 * <p>Deliberately two bands rather than a numeric window: a window has to be tuned, and
 * "within five years" still lets a 17-year-old match a 22-year-old. The line that matters
 * legally and morally is majority, so that is the only line drawn.
 *
 * <p>The band is derived from the stored age on every use rather than persisted. That
 * means a gamer who edits their age simply stops being able to act on matches outside
 * their new band — no cleanup job, no stale copy of the band to drift, and no way to
 * reach the other band by editing a field after the fact.
 */
public enum AgeBand {
    MINOR,
    ADULT;

    /** Anyone who has not reached majority. */
    public static final int MAJORITY_AGE = 18;

    /**
     * @param age the gamer's stated age; null is treated as MINOR, because an unknown age
     *     must not grant access to the adult pool
     */
    public static AgeBand of(Integer age) {
        return age == null || age < MAJORITY_AGE ? MINOR : ADULT;
    }

    /** Whether two gamers may be matched, chat, or otherwise be paired. */
    public static boolean compatible(Integer left, Integer right) {
        return of(left) == of(right);
    }
}
