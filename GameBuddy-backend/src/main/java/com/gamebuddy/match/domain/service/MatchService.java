package com.gamebuddy.match.domain.service;

import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.match.interfaces.request.GamerRequest;
import com.gamebuddy.match.interfaces.response.AcceptResponse;
import com.gamebuddy.match.interfaces.response.ConsumableResponse;
import com.gamebuddy.match.interfaces.response.LikedYouResponse;
import com.gamebuddy.match.interfaces.response.RecommendationResponse;
import com.gamebuddy.match.interfaces.response.RewindResponse;
import com.gamebuddy.match.interfaces.response.SwipeAllowanceResponse;
import com.gamebuddy.shared.entity.Gamer;

public interface MatchService {

    RecommendationResponse getRecommendations(Gamer principal);

    /**
     * The same feed, narrowed. Refuses with {@code SUBSCRIPTION_REQUIRED} when the filters
     * ask for anything and the tier does not carry {@code canUseAdvancedFilters}.
     */
    RecommendationResponse getRecommendations(Gamer principal, FeedFilters filters);

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

    /**
     * Takes back the most recent swipe.
     *
     * <p>Free on Gold, otherwise coins. Refuses when there is nothing to undo, and when
     * the like being undone was already answered — that one is not only yours to reverse.
     */
    RewindResponse rewind(Gamer principal);

    // The deck boost — thirty minutes at the front of decks in your country — was retired.
    // Promotion now belongs to lobbies, where what is being promoted is a plan somebody can
    // join rather than a face in a stack. See DefaultLobbyService#boost.

    /**
     * Buys a consumable with coins.
     *
     * <p>Not {@code UNLOCK_ADMIRER}, which is bought against a person — see
     * {@link #unlockAdmirer}.
     */
    ConsumableResponse buyConsumable(Gamer principal, Consumable item);

    /**
     * Pays coins to see one particular admirer.
     *
     * <p>Returns the refreshed list, so the face appears without a second request.
     */
    LikedYouResponse unlockAdmirer(Gamer principal, String admirerId);

    /**
     * Reveals the newest admirer still hidden, chosen server-side.
     *
     * <p>The client cannot name one: locked admirers are sent with no id, and sending ids
     * so it could choose would give the paid feature away.
     */
    LikedYouResponse unlockNextAdmirer(Gamer principal);
}
