package com.gamebuddy.profile.domain.service;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.BaseModel;
import com.gamebuddy.common.base.BaseResponse;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.Platform;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.util.Constants;
import com.gamebuddy.community.domain.service.CommunityMembership;
import com.gamebuddy.profile.application.mapper.ProfileCatalogueMapper;
import com.gamebuddy.profile.application.mapper.ProfileMapper;
import com.gamebuddy.profile.interfaces.dto.*;
import com.gamebuddy.profile.interfaces.request.FriendRequest;
import com.gamebuddy.profile.interfaces.response.*;
import com.gamebuddy.shared.entity.*;
import com.gamebuddy.shared.event.NotificationKind;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.repository.*;
import com.gamebuddy.shared.storage.AvatarUrls;
import com.gamebuddy.shared.storage.CosmeticUrls;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultProfileService implements ProfileService {

    private final KeywordsRepository keywordsRepository;
    private final GamesRepository gamesRepository;
    private final GamerRepository gamerRepository;
    private final AvatarsRepository avatarsRepository;
    private final AvatarUrls avatarUrls;
    private final CosmeticUrls cosmeticUrls;
    private final ProfileCatalogueMapper profileCatalogueMapper;
    private final ProfileMapper profileMapper;
    private final CommunityMembership communityMembership;
    private final BadgeService badges;
    private final ApplicationEventPublisher events;

    // =======================================================================
    // Profile
    // =======================================================================

    /**
     * A gamer's profile.
     *
     * <p>The previous version took only a user id and returned everything the row held —
     * e-mail address, coin balance and full friend list — for whatever id the caller
     * asked about. Any authenticated user could walk the id space and harvest every
     * account's e-mail. The private fields are now filled in only for the caller's own
     * profile, and a gamer who has blocked the caller is invisible to them.
     */
    @Override
    @Transactional
    public UserInfoResponse getUserInfo(Gamer principal, String userId) {
        Gamer self = reload(principal);
        boolean own = self.getUserId().equals(userId);

        // Not read-only, because this awards badges. Missions are evaluated when a gamer
        // looks at something rather than from inside every write that could complete one —
        // see DefaultBadgeService — and the profile is the screen with the badge count on
        // it, so it would be the one place guaranteed to show a stale number.
        //
        // Evaluated for the caller even when they are looking at somebody else: it is their
        // own badges either way, and there is no version of this that awards a stranger's.
        badges.evaluate(self);

        Gamer gamer = own
                ? self
                : gamerRepository
                        .findById(userId)
                        .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));

        if (!own && gamer.getBlockedFriends().contains(self)) {
            // Indistinguishable from a non-existent account on purpose: confirming the
            // account exists would tell the caller they have been blocked.
            throw new BusinessException(TransactionCode.USER_NOT_FOUND);
        }
        if (!own && !gamer.isDiscoverable()) {
            // The moderator, fetched by id. Nothing links to this account, but ids appear
            // in deep links and in anything anybody has previously seen, so the only
            // reliable answer is the one given for an account that is not there.
            throw new BusinessException(TransactionCode.USER_NOT_FOUND);
        }

        UserInfoResponseBody body = new UserInfoResponseBody();
        body.setUserId(gamer.getUserId());
        body.setUsername(gamer.getGamerUsername());
        body.setAge(gamer.getAge() == null ? null : String.valueOf(gamer.getAge()));
        // Your own only. See UserInfoResponseBody#birthDate.
        body.setBirthDate(
                own && gamer.getBirthDate() != null ? gamer.getBirthDate().toString() : null);
        body.setCountry(gamer.getCountry());
        body.setGender(gamer.getGender());
        body.setAvatar(
                gamer.getUserId().equals(principal.getUserId())
                        ? avatarUrls.visibleToOwner(gamer)
                        : avatarUrls.visibleTo(gamer));
        body.setFrame(cosmeticUrls.frameUrl(gamer));
        body.setBanner(cosmeticUrls.bannerUrl(gamer));
        body.setGames(profileCatalogueMapper.toGameDtos(gamer.getLikedgames()));
        body.setKeywords(profileCatalogueMapper.toKeywordDtos(gamer.getKeywords()));
        body.setPlatforms(gamer.getPlatforms().stream().map(Platform::label).toList());
        // The three on show, and the total. Not the whole board: that is a screen of its
        // own, and sending thirteen missions with progress to render a number and three
        // pictures would put the badge catalogue on every profile view.
        body.setBadges(badges.showcasedFor(gamer));
        body.setBadgeCount((int) badges.earnedCount(gamer));
        body.setJoinedCommunities(toCommunityDtos(gamer));

        if (own) {
            body.setEmail(gamer.getEmail());
            body.setCoin(gamer.getCoin());
            body.setFriends(toFriendDtos(gamer.getFriends()));
            body.setRole(gamer.getRole().name());
        }

        return respond(new UserInfoResponse(), body);
    }

    // =======================================================================
    // Catalogue
    // =======================================================================

    @Override
    @Transactional(readOnly = true)
    public KeywordsResponse getKeywords() {
        KeywordsResponseBody body = new KeywordsResponseBody();
        body.setKeywords(profileCatalogueMapper.toKeywordDtos(keywordsRepository.findAll()));
        return respond(new KeywordsResponse(), body);
    }

    @Override
    @Transactional(readOnly = true)
    public GamesResponse getGames() {
        return gamesResponse(gamesRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public GameResponse getGame(String gameId) {
        Games game = gamesRepository
                .findById(gameId)
                // Was DB_ERROR, i.e. HTTP 500 for a client asking about a game that does
                // not exist. A missing record is a 404.
                .orElseThrow(() -> new BusinessException(TransactionCode.GAME_NOT_FOUND));

        GameResponseBody body = new GameResponseBody();
        body.setGameData(profileCatalogueMapper.toDto(game));
        return respond(new GameResponse(), body);
    }

    @Override
    @Transactional(readOnly = true)
    public GamesResponse getPopularGames() {
        return gamesResponse(gamesRepository.findAllByIsPopularTrueOrderByAvgVoteDesc());
    }

    /**
     * The stock pictures, all of them.
     *
     * <p>No longer takes the gamer into account. It used to return the free avatars plus
     * whichever paid ones this gamer had bought; with nothing for sale the catalogue is the
     * same short list for everyone, and the choice a gamer actually makes here is between
     * these and uploading their own.
     */
    @Override
    @Transactional(readOnly = true)
    public AvatarsResponse getAvatars() {
        // The rows hold object keys; the client needs URLs. Resolved here rather than
        // stored resolved, for the same reason the gamer's own avatar is: which host
        // serves an image is a deployment concern that changes.
        List<AvatarsDto> dtos = profileCatalogueMapper.toAvatarDtos(avatarsRepository.findAll());
        dtos.forEach(dto -> dto.setImage(avatarUrls.publicUrlFor(dto.getImage())));

        AvatarsResponseBody body = new AvatarsResponseBody();
        body.setAvatars(dtos);
        return respond(new AvatarsResponse(), body);
    }

    // Achievements used to live here — the catalogue read and the coin payout. They are
    // badges now, and both moved to BadgeService along with the rules that award them.
    //
    // Buying used to live here too, and bought avatars. Both moved to CosmeticService:
    // what is for sale now is frames and banners, and coins are spent there.

    // =======================================================================
    // Friends
    // =======================================================================

    /**
     * The friends list.
     *
     * <p>Read-only again. It used to award the "Friendly Person" achievement as a side
     * effect of being viewed, so a gamer with friends who never opened this screen never
     * earned it, while opening it repeatedly did nothing. The achievement is granted when
     * the friendship is actually formed, in {@link #acceptFriend}.
     */
    @Override
    @Transactional(readOnly = true)
    public FriendsResponse getFriends(Gamer principal) {
        return friendsResponse(reload(principal).getFriends());
    }

    @Override
    @Transactional(readOnly = true)
    public FriendsResponse getWaitingFriends(Gamer principal) {
        return friendsResponse(reload(principal).getWaitingFriends());
    }

    /**
     * Requests this gamer has sent and nobody has answered yet.
     *
     * <p>Nothing could read this before, which meant no screen could tell "you have not
     * asked" apart from "you asked and they have not replied". A button in that state
     * either invites a request that comes back {@code ALREADY_SENT_REQUEST}, or has to
     * guess.
     */
    @Override
    @Transactional(readOnly = true)
    public FriendsResponse getSentFriendRequests(Gamer principal) {
        return friendsResponse(gamerRepository.findRequestedByMe(principal.getUserId()));
    }

    @Override
    @Transactional(readOnly = true)
    public FriendsResponse getBlockedFriends(Gamer principal) {
        return friendsResponse(reload(principal).getBlockedFriends());
    }

    @Override
    @Transactional
    public DefaultMessageResponse acceptFriend(Gamer principal, FriendRequest request) {
        Gamer gamer = reload(principal);
        Gamer user = requireGamer(request.getUserId());

        if (gamer.getFriends().contains(user)) {
            throw new BusinessException(TransactionCode.FRIEND_ALREADY_EXISTS);
        }
        if (!gamer.getWaitingFriends().contains(user)) {
            throw new BusinessException(TransactionCode.FRIEND_NO_REQUEST);
        }

        gamer.getWaitingFriends().remove(user);
        gamer.getFriends().add(user);
        user.getFriends().add(gamer);

        // The friend badges are not awarded here. They used to be — one call per side,
        // with the threshold written out again next to the friendship it counted — and
        // that is the shape BadgeService exists to remove. Both gamers get them the next
        // time either opens a profile or the badges screen.

        gamerRepository.save(gamer);
        gamerRepository.save(user);

        events.publishEvent(new NotificationRequestedEvent(
                user.getUserId(),
                user.getFcmToken(),
                Constants.FRIEND_REQUEST_ACCEPTED_TITLE,
                String.format(Constants.FRIEND_REQUEST_ACCEPTED_BODY, gamer.getGamerUsername()),
                NotificationKind.FRIEND_ACCEPTED,
                gamer.getUserId()));

        return DefaultMessageResponse.of("Friend added successfully");
    }

    @Override
    @Transactional
    public DefaultMessageResponse rejectFriend(Gamer principal, FriendRequest request) {
        Gamer gamer = reload(principal);
        Gamer user = requireGamer(request.getUserId());

        if (!gamer.getWaitingFriends().contains(user)) {
            throw new BusinessException(TransactionCode.FRIEND_NO_REQUEST);
        }

        gamer.getWaitingFriends().remove(user);
        gamerRepository.save(gamer);
        return DefaultMessageResponse.of("Friend rejected successfully");
    }

    @Override
    @Transactional
    public DefaultMessageResponse removeFriend(Gamer principal, FriendRequest request) {
        Gamer gamer = reload(principal);
        Gamer friend = requireGamer(request.getUserId());

        if (!gamer.getFriends().contains(friend)) {
            throw new BusinessException(TransactionCode.FRIEND_NOT_FOUND);
        }

        gamer.getFriends().remove(friend);
        friend.getFriends().remove(gamer);
        gamerRepository.save(gamer);
        gamerRepository.save(friend);

        return DefaultMessageResponse.of("Friend removed successfully");
    }

    /**
     * Blocks another gamer, severing the relationship from both sides.
     *
     * <p>The old version mutated {@code user}'s friend and waiting-friend sets but only
     * saved {@code gamer}. With no transaction there was no dirty-checking flush either,
     * so the other side of the relationship was never written: the blocker stopped seeing
     * the blocked user, while the blocked user still saw them as a friend.
     */
    @Override
    @Transactional
    public DefaultMessageResponse blockUser(Gamer principal, FriendRequest request) {
        Gamer gamer = reload(principal);
        Gamer user = requireGamer(request.getUserId());

        if (gamer.getUserId().equals(user.getUserId())) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "you cannot block yourself");
        }
        if (gamer.getBlockedFriends().contains(user)) {
            throw new BusinessException(TransactionCode.USER_ALREADY_BLOCKED);
        }

        gamer.getFriends().remove(user);
        user.getFriends().remove(gamer);
        gamer.getWaitingFriends().remove(user);
        user.getWaitingFriends().remove(gamer);
        gamer.getBlockedFriends().add(user);

        gamerRepository.save(gamer);
        gamerRepository.save(user);

        return DefaultMessageResponse.of("User blocked successfully");
    }

    @Override
    @Transactional
    public DefaultMessageResponse unblockUser(Gamer principal, FriendRequest request) {
        Gamer gamer = reload(principal);
        Gamer user = requireGamer(request.getUserId());

        if (!gamer.getBlockedFriends().contains(user)) {
            throw new BusinessException(TransactionCode.USER_NOT_BLOCKED);
        }

        gamer.getBlockedFriends().remove(user);
        gamerRepository.save(gamer);
        return DefaultMessageResponse.of("User unblocked successfully");
    }

    /**
     * Asks a matched gamer to become a friend.
     *
     * <p>Friendship is the second tier of the relationship model: matching is how gamers
     * find each other and unlocks a one-to-one chat, and a friend request is a deliberate
     * step taken afterwards, between people who have already matched and talked.
     *
     * <p>Previously it could be sent to any user id in the system. That made the friend
     * graph and the match graph two unrelated social networks over the same users, with
     * chat gated on one and the friends list rendering the other — so a gamer could have
     * friends they were unable to message, and could message people who were not friends.
     */
    @Override
    @Transactional
    public DefaultMessageResponse sendFriendRequest(Gamer principal, FriendRequest request) {
        Gamer gamer = reload(principal);
        Gamer user = requireGamer(request.getUserId());

        if (gamer.getUserId().equals(user.getUserId())) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "you cannot befriend yourself");
        }
        if (gamer.hasBlockRelationshipWith(user)) {
            // One code for both directions: which side blocked whom is not information
            // the caller should be able to infer.
            throw new BusinessException(TransactionCode.USER_BLOCKED);
        }
        if (!gamer.isMatchedWith(user)) {
            throw new BusinessException(TransactionCode.FRIEND_REQUIRES_MATCH);
        }
        if (user.getFriends().contains(gamer)) {
            throw new BusinessException(TransactionCode.ALREADY_FRIENDS);
        }
        if (user.getWaitingFriends().contains(gamer)) {
            throw new BusinessException(TransactionCode.ALREADY_SENT_REQUEST);
        }

        user.getWaitingFriends().add(gamer);
        gamerRepository.save(user);

        events.publishEvent(new NotificationRequestedEvent(
                user.getUserId(),
                user.getFcmToken(),
                Constants.FRIEND_REQUEST_TITLE,
                String.format(Constants.FRIEND_REQUEST_BODY, gamer.getGamerUsername()),
                NotificationKind.FRIEND_REQUEST,
                gamer.getUserId()));

        return DefaultMessageResponse.of("Friend request sent successfully");
    }

    // =======================================================================
    // Helpers
    // =======================================================================

    /**
     * Re-reads the principal inside the current transaction.
     *
     * <p>The security filter loaded it in a persistence context that has since closed,
     * so the instance is detached and its lazy collections cannot be walked.
     */
    private Gamer reload(Gamer principal) {
        return gamerRepository
                .findById(principal.getUserId())
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));
    }

    private Gamer requireGamer(String userId) {
        return gamerRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));
    }

    /** Resolves every friend's avatar in one query instead of one per friend. */
    private List<GamerDto> toFriendDtos(Collection<Gamer> friends) {
        Map<String, String> avatars = avatarUrls.visibleTo(friends);
        return friends.stream()
                .map(friend -> {
                    GamerDto dto = profileMapper.toDto(friend);
                    dto.setAvatar(avatars.get(friend.getUserId()));
                    dto.setFrame(cosmeticUrls.frameUrl(friend));
                    return dto;
                })
                .toList();
    }

    /**
     * The communities on a profile, asked of the module that owns them.
     *
     * <p>This used to map {@code community} with a second entity of its own and walk
     * {@code Gamer.joinedCommunities} — one module reading another's tables through a
     * duplicate mapping of the same join table, which is how the two ends came to disagree
     * about which of them owned it.
     */
    private List<CommunityDto> toCommunityDtos(Gamer gamer) {
        return communityMembership.findJoinedBy(gamer).stream()
                .map(joined -> {
                    CommunityDto dto = new CommunityDto();
                    dto.setCommunityId(joined.id().toString());
                    dto.setName(joined.name());
                    dto.setCommunityAvatar(joined.avatar());
                    dto.setIsOwner(joined.owned());
                    return dto;
                })
                .toList();
    }

    private GamesResponse gamesResponse(List<Games> games) {
        GamesResponseBody body = new GamesResponseBody();
        body.setGames(profileCatalogueMapper.toGameDtos(games));
        return respond(new GamesResponse(), body);
    }

    private FriendsResponse friendsResponse(Collection<Gamer> friends) {
        FriendsResponseBody body = new FriendsResponseBody();
        body.setFriends(toFriendDtos(friends));
        return respond(new FriendsResponse(), body);
    }

    /** Fills in the standard envelope so every endpoint answers with the same shape. */
    private <B extends BaseModel, R extends BaseResponse<B>> R respond(R response, B body) {
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }
}
