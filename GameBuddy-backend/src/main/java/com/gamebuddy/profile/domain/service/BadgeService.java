package com.gamebuddy.profile.domain.service;

import com.gamebuddy.profile.interfaces.dto.ShowcasedBadgeDto;
import com.gamebuddy.profile.interfaces.response.BadgesResponse;
import com.gamebuddy.shared.entity.Gamer;
import java.util.List;

/** Missions, the badges they award, and the three a gamer puts on show. */
public interface BadgeService {

    /**
     * Every badge, with this gamer's progress towards it.
     *
     * <p>Evaluates first, so opening the screen is what turns a finished mission into an
     * earned badge. That is deliberate — see {@code DefaultBadgeService} for why the
     * alternative, awarding from every write path, is worse.
     */
    BadgesResponse getBadges(Gamer principal);

    /**
     * Claims the coins for an earned badge.
     *
     * <p>Returns the whole board, like the store does: the balance, the badge's state and
     * possibly a newly earned badge have all changed together.
     */
    BadgesResponse collect(Gamer principal, String code);

    /**
     * Replaces the showcase with these badges, in this order, up to three.
     *
     * <p>The whole selection at once rather than add/remove: the client holds three slots
     * and knows what it wants them to be, and two endpoints would need an ordering rule to
     * decide what "add a fourth" means.
     */
    BadgesResponse showcase(Gamer principal, List<String> codes);

    /**
     * The badges a gamer has chosen to display, for a profile.
     *
     * <p>Public: this is what other people see. Never more than three.
     */
    List<ShowcasedBadgeDto> showcasedFor(Gamer gamer);

    /** How many badges this gamer has earned, for the profile's badge count. */
    long earnedCount(Gamer gamer);

    /**
     * Awards every mission this gamer has now finished.
     *
     * <p>Called from the badges screen and from a profile load rather than from each write
     * that could complete something — see {@code DefaultBadgeService} for why. Idempotent,
     * so calling it more often is only a cost.
     *
     * @param gamer must be managed; this writes
     */
    void evaluate(Gamer gamer);
}
