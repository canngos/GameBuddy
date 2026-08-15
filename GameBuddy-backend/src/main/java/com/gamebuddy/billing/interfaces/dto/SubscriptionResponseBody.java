package com.gamebuddy.billing.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What the gamer currently holds.
 *
 * <p>The entitlements are sent rather than left for the client to infer from the tier
 * name. A client that hard-codes "GOLD means unlimited swipes" has to ship an update every
 * time a limit is tuned, and until it does the two disagree about what the user bought.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SubscriptionResponseBody implements BaseModel {

    private String tier;

    /** Null on the free tier. */
    private Instant expiresAt;

    /** How many gamers may be accepted per day. Declining is unlimited on every tier. */
    private int dailyAccepts;

    private boolean canSeeWhoLikedYou;
    private boolean canUseAdvancedFilters;

    /** Whether opening a game lobby is included. Gold's perk; joining is free for everyone. */
    private boolean canCreateLobby;

    /**
     * Whether to show the Season Pass teaser.
     *
     * <p>Not an entitlement — nothing is unlocked by it. It rides on this response because
     * the client already calls it on every screen that cares about tiers, which makes the
     * card switchable from the server with no app release. An env var read by the client
     * would need a rebuild and a store review to turn off a card that turned out to
     * over-promise.
     *
     * <p>It is a promise about future work, and the season pipeline is an art commitment
     * before it is an engineering one — six cosmetics every six weeks, forever. Being able
     * to withdraw the promise in one deploy is the point.
     */
    private boolean seasonPassTeaser;

    /**
     * Whether the one-time day-3 Gold prompt is due right now.
     *
     * <p>Rides on this response for the same reason the teaser does: the app already asks
     * for it wherever tiers matter, so the flag costs no extra round trip on a screen that
     * is otherwise doing nothing. A dedicated endpoint would be a second request on every
     * home mount to be told "no" almost every time.
     *
     * <p>Answering true does not mark it shown — see {@code UpgradePromptService.markShown}
     * for why the client has to say when it actually rendered it.
     */
    private boolean upgradePromptDue;
}
