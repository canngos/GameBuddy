package com.gamebuddy.profile.domain.service;

import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.profile.interfaces.request.FriendRequest;
import com.gamebuddy.profile.interfaces.response.*;
import com.gamebuddy.shared.entity.Gamer;

/**
 * Every authenticated operation takes the authenticated {@link Gamer} rather than a raw
 * bearer token string.
 *
 * <p>Previously each method re-parsed the token the security filter had already
 * validated, which meant the service trusted a value it had not checked and duplicated
 * work on every call. The principal now arrives from the security context.
 */
public interface ProfileService {

    /** Full profile when {@code userId} is the caller, public subset otherwise. */
    UserInfoResponse getUserInfo(Gamer principal, String userId);

    KeywordsResponse getKeywords();

    GamesResponse getGames();

    GameResponse getGame(String gameId);

    GamesResponse getPopularGames();

    AvatarsResponse getAvatars();

    // Achievements are badges, and live on BadgeService. The avatar marketplace is gone;
    // see CosmeticService for what is on sale now.

    FriendsResponse getFriends(Gamer principal);

    FriendsResponse getWaitingFriends(Gamer principal);

    /** Requests this gamer has sent that are still unanswered. */
    FriendsResponse getSentFriendRequests(Gamer principal);

    FriendsResponse getBlockedFriends(Gamer principal);

    DefaultMessageResponse acceptFriend(Gamer principal, FriendRequest addFriendRequest);

    DefaultMessageResponse rejectFriend(Gamer principal, FriendRequest rejectFriendRequest);

    DefaultMessageResponse removeFriend(Gamer principal, FriendRequest removeFriendRequest);

    DefaultMessageResponse blockUser(Gamer principal, FriendRequest blockFriendRequest);

    DefaultMessageResponse unblockUser(Gamer principal, FriendRequest unblockFriendRequest);

    DefaultMessageResponse sendFriendRequest(Gamer principal, FriendRequest sendFriendRequest);
}
