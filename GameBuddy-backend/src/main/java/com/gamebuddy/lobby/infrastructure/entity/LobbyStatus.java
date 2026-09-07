package com.gamebuddy.lobby.infrastructure.entity;

/**
 * Where a lobby is in its life. The owner drives every transition except the sweeper's.
 *
 * <p>There is deliberately no FILLING: it would just be OPEN with members, and a stored
 * copy of a derivable fact is one more thing that can be wrong.
 */
public enum LobbyStatus {

    /** Taking join requests, visible in the browse feed. */
    OPEN,

    /**
     * Team found; requests refused and pending ones quietly rejected.
     *
     * <p>Locking starts <b>no</b> timer and schedules nothing. Its whole meaning is "stop
     * asking": a team found at four may not play until seven, and the lobby simply sits
     * here with its chat open until the owner ends it. Unlocking is allowed — somebody
     * bailed and one more player is needed. Cancelling is not allowed from here; the owner
     * unlocks first, which keeps a formed team one deliberate step away from an accidental
     * cancel.
     */
    LOCKED,

    /** Played and closed by the owner (or presumed finished by the sweeper). Chat stays readable. */
    ENDED,

    /** Called off — by the owner while OPEN, or by the sweeper when nobody ever came. */
    CANCELLED,

    /** Swept out of the app a month after ending; the chat is deleted with it. */
    ARCHIVED;

    /** Terminal states: nothing transitions out of these except the sweep to ARCHIVED. */
    public boolean finished() {
        return this == ENDED || this == CANCELLED || this == ARCHIVED;
    }
}
