package com.gamebuddy.admin.domain.service;

/**
 * The groups an administrator can pick recipients from.
 *
 * <p>Each one exists because it answers "who would this code be worth sending to", not
 * because the column was there: dormant players are the reason the feature has a cohort
 * picker at all, upheld reporters are the people worth thanking, and free accounts are who
 * a Gold code is wasted on if it goes to anybody else.
 *
 * <p>A closed enum, so an unknown value in a query string is a 400 rather than a silent
 * "everybody" — the failure mode of a typo'd filter must not be sending two hundred people
 * something meant for twelve.
 */
public enum DirectoryFilter {

    /** Everyone, subject to the search term. */
    ALL,

    /** Not seen for a fortnight — the re-engagement job's own definition of drifted away. */
    OFFLINE_14D,

    /** Reported somebody, and a moderator agreed. */
    REPORT_CONTRIBUTORS,

    /** Currently a member. */
    GOLD,

    /** Not currently a member. */
    FREE,

    /** Signed up within the last week. */
    NEW_7D
}
