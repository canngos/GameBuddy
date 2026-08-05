package com.gamebuddy.match.domain.service;

import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.match.interfaces.request.GamerRequest;
import com.gamebuddy.match.interfaces.response.AcceptResponse;
import com.gamebuddy.match.interfaces.response.LikedYouResponse;
import com.gamebuddy.match.interfaces.response.RecommendationResponse;
import com.gamebuddy.match.interfaces.response.SwipeAllowanceResponse;
import com.gamebuddy.shared.entity.Gamer;

public interface MatchService {

    RecommendationResponse getRecommendations(Gamer principal);

    RecommendationResponse getSelectedGameRecommendations(Gamer principal, String gameId);

    /**
     * The gamers who have accepted this gamer back.
     *
     * <p>A match is mutual by definition, and there was no way to ask for the list at
     * all: the client could see who it had accepted, but not who had accepted it.
     */
    RecommendationResponse getMatches(Gamer principal);

    /** Reports whether the accept produced a mutual match; see {@link AcceptResponse}. */
    AcceptResponse acceptGamer(Gamer principal, GamerRequest gamerRequest);

    DefaultMessageResponse declineGamer(Gamer principal, GamerRequest gamerRequest);

    /**
     * Who has swiped yes on this gamer without being answered yet.
     *
     * <p>A paid feature, and the one most likely to convert: it resolves a curiosity the
     * free product has already created. The count is always available — that is the hook —
     * but the identities require Gold.
     */
    LikedYouResponse getWhoLikedYou(Gamer principal);

    /** Accepts left today, and when the allowance returns. Declining is never rationed. */
    SwipeAllowanceResponse getSwipeAllowance(Gamer principal);
}
