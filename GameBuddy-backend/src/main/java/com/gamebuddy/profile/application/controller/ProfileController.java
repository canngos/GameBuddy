package com.gamebuddy.profile.application.controller;

import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.profile.domain.service.AvatarUploadService;
import com.gamebuddy.profile.domain.service.ProfileService;
import com.gamebuddy.profile.interfaces.request.FriendRequest;
import com.gamebuddy.profile.interfaces.response.*;
import com.gamebuddy.shared.entity.Gamer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * The authenticated principal now arrives via {@link AuthenticationPrincipal}.
 *
 * <p>Every method used to declare a {@code @RequestHeader("Authorization")} parameter and
 * pass {@code token.substring(7)} down to the service. That threw
 * {@code StringIndexOutOfBoundsException} — an HTTP 500 — for any Authorization header
 * shorter than seven characters, and it re-parsed a token the security filter had
 * already validated.
 */
@RestController
@RequestMapping("/application")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final AvatarUploadService avatarUploadService;

    // ---- profile ----------------------------------------------------------

    /** The caller's own profile, including the private fields. */
    @GetMapping("/get/user/info")
    public ResponseEntity<UserInfoResponse> getOwnInfo(@AuthenticationPrincipal Gamer principal) {
        return ResponseEntity.ok(profileService.getUserInfo(principal, principal.getUserId()));
    }

    /** Another gamer's public profile. */
    @GetMapping("/get/user/info/{userId}")
    public ResponseEntity<UserInfoResponse> getUserInfo(
            @AuthenticationPrincipal Gamer principal, @PathVariable String userId) {
        return ResponseEntity.ok(profileService.getUserInfo(principal, userId));
    }

    // ---- catalogue --------------------------------------------------------

    @GetMapping("/get/keywords")
    public ResponseEntity<KeywordsResponse> getKeywords() {
        return ResponseEntity.ok(profileService.getKeywords());
    }

    @GetMapping("/get/games")
    public ResponseEntity<GamesResponse> getGames() {
        return ResponseEntity.ok(profileService.getGames());
    }

    @GetMapping("/get/game/{gameId}")
    public ResponseEntity<GameResponse> getGame(@PathVariable String gameId) {
        return ResponseEntity.ok(profileService.getGame(gameId));
    }

    @GetMapping("/get/popular/games")
    public ResponseEntity<GamesResponse> getPopularGames() {
        return ResponseEntity.ok(profileService.getPopularGames());
    }

    /** The stock pictures, for a gamer who does not want to upload their own. */
    @GetMapping("/get/avatars")
    public ResponseEntity<AvatarsResponse> getAvatars() {
        return ResponseEntity.ok(profileService.getAvatars());
    }

    // GET /get/achievements and POST /collect/achievement/{id} became badges; see
    // BadgeController, which also owns the showcase.
    //
    // POST /buy/item/{itemId} bought a paid avatar. Retired with them; buying now lives on
    // CosmeticController, which is the only place coins are spent.

    /**
     * Uploads an avatar.
     *
     * <p>Multipart rather than a base64 field: an image is binary, and base64 costs a
     * third more bytes on a mobile connection to encode something the transport already
     * carries natively.
     *
     * <p>The response says which of three things happened — see
     * {@code AvatarUploadResponseBody}. A client that treats this as a plain success and
     * shows the picture immediately will be showing an image that nobody else can see, and
     * will not tell the user why.
     */
    @PostMapping(value = "/avatar/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AvatarUploadResponse> uploadAvatar(
            @AuthenticationPrincipal Gamer principal, @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(avatarUploadService.upload(principal, file));
    }

    // ---- friends ----------------------------------------------------------

    @GetMapping("/get/friends")
    public ResponseEntity<FriendsResponse> getFriends(@AuthenticationPrincipal Gamer principal) {
        return ResponseEntity.ok(profileService.getFriends(principal));
    }

    @GetMapping("/get/requests/friends")
    public ResponseEntity<FriendsResponse> getWaitingFriends(@AuthenticationPrincipal Gamer principal) {
        return ResponseEntity.ok(profileService.getWaitingFriends(principal));
    }

    /** Requests this gamer sent and nobody has answered yet. */
    @GetMapping("/get/sent/friends")
    public ResponseEntity<FriendsResponse> getSentFriendRequests(@AuthenticationPrincipal Gamer principal) {
        return ResponseEntity.ok(profileService.getSentFriendRequests(principal));
    }

    @GetMapping("/get/blocked/friends")
    public ResponseEntity<FriendsResponse> getBlockedFriends(@AuthenticationPrincipal Gamer principal) {
        return ResponseEntity.ok(profileService.getBlockedFriends(principal));
    }

    @PostMapping("/accept/friend")
    public ResponseEntity<DefaultMessageResponse> acceptFriend(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody FriendRequest request) {
        return ResponseEntity.ok(profileService.acceptFriend(principal, request));
    }

    @PostMapping("/reject/friend")
    public ResponseEntity<DefaultMessageResponse> rejectFriend(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody FriendRequest request) {
        return ResponseEntity.ok(profileService.rejectFriend(principal, request));
    }

    @PostMapping("/remove/friend")
    public ResponseEntity<DefaultMessageResponse> removeFriend(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody FriendRequest request) {
        return ResponseEntity.ok(profileService.removeFriend(principal, request));
    }

    @PostMapping("/block/friend")
    public ResponseEntity<DefaultMessageResponse> blockFriend(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody FriendRequest request) {
        return ResponseEntity.ok(profileService.blockUser(principal, request));
    }

    @PostMapping("/unblock/friend")
    public ResponseEntity<DefaultMessageResponse> unblockFriend(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody FriendRequest request) {
        return ResponseEntity.ok(profileService.unblockUser(principal, request));
    }

    @PostMapping("/send/friend")
    public ResponseEntity<DefaultMessageResponse> sendFriendRequest(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody FriendRequest request) {
        return ResponseEntity.ok(profileService.sendFriendRequest(principal, request));
    }

    /**
     * Takes back a request this gamer sent. Distinct from {@code /reject/friend}, which
     * answers a request you <em>received</em> — the sender has never had a way to undo one.
     */
    @PostMapping("/withdraw/friend")
    public ResponseEntity<DefaultMessageResponse> withdrawFriendRequest(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody FriendRequest request) {
        return ResponseEntity.ok(profileService.withdrawFriendRequest(principal, request));
    }
}
