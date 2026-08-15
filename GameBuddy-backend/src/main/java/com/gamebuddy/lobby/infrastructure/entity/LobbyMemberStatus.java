package com.gamebuddy.lobby.infrastructure.entity;

/**
 * One person's standing in one lobby. A request and a membership are the same row in
 * different states — accepting a request does not create anything, it advances this.
 */
public enum LobbyMemberStatus {

    /** The creator. Exists as a row so "everyone in the chat" is one uniform query. */
    OWNER,

    /** Asked, not yet answered. What the owner's inbox lists. */
    PENDING,

    /** In the team, in the chat. */
    ACCEPTED,

    /**
     * The owner said no, and the primary key makes that the last word for this lobby —
     * a second request has nowhere to insert itself. Deliberate: an owner screening
     * strangers answers each one once, and is not petitioned until they give in.
     */
    REJECTED,

    /** Walked out. May ask again; the row flips back to PENDING. */
    LEFT,

    /** Removed by the owner. May ask again like LEFT — a kick undoes an accept, it is not a ban. */
    KICKED;

    /** The people a lobby push or chat message goes to. */
    public boolean inTeam() {
        return this == OWNER || this == ACCEPTED;
    }
}
