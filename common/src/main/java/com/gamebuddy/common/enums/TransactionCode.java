package com.gamebuddy.common.enums;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * The application-level error contract shared by every GameBuddy service.
 *
 * <p>Numeric ids are part of the public API consumed by the mobile client and are
 * asserted directly by the service test-suites, so they must not be renumbered.
 * The {@link HttpStatus} mapping, by contrast, is new: the previous implementation
 * answered every failure with HTTP 500, which made it impossible for a client to
 * distinguish "you sent a bad request" from "the server broke".
 */
@Getter
public enum TransactionCode {

    /** Application-level success. Historically surfaced as the string "100". */
    DEFAULT_100(100, "Success", HttpStatus.OK),

    // --- Registration / authentication -------------------------------------
    EMAIL_EXISTS(101, "Email already registered", HttpStatus.CONFLICT),
    EMAIL_SEND_FAILED(102, "Verification email could not be sent", HttpStatus.SERVICE_UNAVAILABLE),
    USER_NOT_FOUND(103, "User not found", HttpStatus.NOT_FOUND),
    VERIFICATION_CODE_NOT_FOUND(105, "Verification code is invalid", HttpStatus.BAD_REQUEST),
    USER_NOT_VERIFIED(106, "Account not verified", HttpStatus.FORBIDDEN),
    USERNAME_EXISTS(107, "Username already taken", HttpStatus.CONFLICT),
    WRONG_PASSWORD(108, "Invalid credentials", HttpStatus.UNAUTHORIZED),
    USER_NOT_COMPLETED(109, "Registration not finished", HttpStatus.FORBIDDEN),
    TOKEN_INVALID(110, "Token is invalid or expired", HttpStatus.UNAUTHORIZED),
    TOKEN_NOT_FOUND(111, "Token not found", HttpStatus.UNAUTHORIZED),
    PASSWORD_SAME(112, "New password must differ from the current one", HttpStatus.BAD_REQUEST),
    USER_BLOCKED(113, "Account is blocked", HttpStatus.FORBIDDEN),

    // --- Notifications ------------------------------------------------------
    NOTIF_SEND_FAILED(114, "Notification could not be sent", HttpStatus.SERVICE_UNAVAILABLE),

    // --- Friends ------------------------------------------------------------
    FRIEND_ALREADY_EXISTS(115, "Already friends", HttpStatus.CONFLICT),
    FRIEND_NO_REQUEST(116, "No pending friend request", HttpStatus.NOT_FOUND),
    FRIEND_NOT_FOUND(117, "Not in your friend list", HttpStatus.NOT_FOUND),
    USER_ALREADY_BLOCKED(118, "User already blocked", HttpStatus.CONFLICT),
    USER_NOT_BLOCKED(119, "User is not blocked", HttpStatus.CONFLICT),
    ALREADY_SENT_REQUEST(120, "Friend request already sent", HttpStatus.CONFLICT),
    USER_BLOCKED_YOU(121, "User has blocked you", HttpStatus.FORBIDDEN),
    ALREADY_FRIENDS(122, "Already friends", HttpStatus.CONFLICT),

    // --- Downstream services ------------------------------------------------
    RECOMMENDER_SERVICE_ERROR(123, "Recommendation service unavailable", HttpStatus.SERVICE_UNAVAILABLE),

    // --- Badges / marketplace ----------------------------------------------
    // Achievements became badges: same three numbers, same three meanings, renamed with
    // the feature. The numbers are the wire contract and do not move; the enum names and
    // the messages are ours.
    BADGE_NOT_FOUND(124, "Badge not found", HttpStatus.NOT_FOUND),
    ALREADY_COLLECTED(125, "You already claimed this badge", HttpStatus.CONFLICT),
    BADGE_NOT_EARNED(126, "You have not earned this badge yet", HttpStatus.CONFLICT),
    AVATAR_NOT_FOUND(127, "Avatar not found", HttpStatus.NOT_FOUND),
    // 128 was AVATAR_ALREADY_OWNED. Retired with paid avatars; see COSMETIC_ALREADY_OWNED.
    // The number stays spent rather than being reused: these codes go over the wire, and an
    // installed app that still knows 128 as an avatar message would render the wrong text
    // for whatever took its place.
    COIN_NOT_ENOUGH(129, "Not enough coins", HttpStatus.CONFLICT),

    // --- Communities --------------------------------------------------------
    // 131-139 belonged to the retired Community feature. The entries stay so the numbers
    // stay spent — a reused id would mean two generations of clients disagreeing about
    // what an error meant. Only the ones still thrown remain referenced.
    COMMUNITY_NOT_FOUND(131, "Community not found", HttpStatus.NOT_FOUND),
    NOT_MEMBER(132, "You are not a member of this community", HttpStatus.FORBIDDEN),
    POST_NOT_FOUND(133, "Post not found", HttpStatus.NOT_FOUND),
    NOT_OWNER(134, "You do not own this resource", HttpStatus.FORBIDDEN),
    COMMENT_NOT_FOUND(135, "Comment not found", HttpStatus.NOT_FOUND),
    ALREADY_MEMBER(136, "Already a member", HttpStatus.CONFLICT),
    // 137 was AVATAR_NOT_BOUGHT. Retired with paid avatars; every catalogue avatar is now
    // selectable by anyone. Number left spent for the reason given at 128.
    USER_OWNER(138, "Owner cannot leave their own community", HttpStatus.CONFLICT),
    ALREADY_LIKED(139, "Already liked", HttpStatus.CONFLICT),

    // --- Administration -----------------------------------------------------
    NOT_ADMIN(140, "Administrator role required", HttpStatus.FORBIDDEN),
    MESSAGE_NOT_REPORTED(141, "Message is not reported", HttpStatus.NOT_FOUND),

    // --- Chat ---------------------------------------------------------------
    MESSAGE_NOT_FOUND(142, "Message not found", HttpStatus.NOT_FOUND),
    RECEIVER_IS_DIFFERENT(143, "You are not the recipient of this message", HttpStatus.FORBIDDEN),

    // --- Added during the 2026 hardening pass -------------------------------
    /** Verification code exists but is past its validity window. */
    VERIFICATION_CODE_EXPIRED(144, "Verification code has expired", HttpStatus.BAD_REQUEST),
    /** Too many verification attempts; the code has been burned. */
    TOO_MANY_ATTEMPTS(145, "Too many attempts, request a new code", HttpStatus.TOO_MANY_REQUESTS),
    /** Caller exceeded the send-code / login rate limit. */
    RATE_LIMITED(146, "Too many requests, please wait", HttpStatus.TOO_MANY_REQUESTS),
    /** Password fails the minimum strength policy. */
    WEAK_PASSWORD(147, "Password does not meet the minimum requirements", HttpStatus.BAD_REQUEST),
    /** Malformed path variable or body field, e.g. an unparseable UUID. */
    INVALID_REQUEST(148, "Request contains an invalid value", HttpStatus.BAD_REQUEST),
    /** Authenticated, but not permitted to act on this resource. */
    FORBIDDEN(149, "You are not allowed to perform this action", HttpStatus.FORBIDDEN),
    /** Current password supplied during a change-password call did not match. */
    CURRENT_PASSWORD_WRONG(150, "Current password is incorrect", HttpStatus.UNAUTHORIZED),
    /** Catalogue lookup miss. Previously reported as DB_ERROR, i.e. an HTTP 500. */
    GAME_NOT_FOUND(151, "Game not found", HttpStatus.NOT_FOUND),
    /** A concurrent update won; the caller should retry. */
    CONCURRENT_UPDATE(152, "The record changed while you were working on it", HttpStatus.CONFLICT),
    /** Chat requires a mutual match. Nothing enforced this before. */
    NOT_MATCHED(153, "You can only chat with a matched gamer", HttpStatus.FORBIDDEN),
    /** Minors are never paired with adults. See {@link AgeBand}. */
    AGE_BAND_MISMATCH(154, "This gamer is not in your age group", HttpStatus.FORBIDDEN),
    /** A friend request may only be sent to someone you have already matched with. */
    FRIEND_REQUIRES_MATCH(155, "You can only add a matched gamer as a friend", HttpStatus.FORBIDDEN),
    /** The account has been deleted by its owner. */
    ACCOUNT_DELETED(156, "This account no longer exists", HttpStatus.NOT_FOUND),
    /** Reported content that has already been reported by the same gamer. */
    ALREADY_REPORTED(157, "You have already reported this", HttpStatus.CONFLICT),

    // --- Subscriptions and purchases ---------------------------------------
    /**
     * The free tier's daily allowance of accepts is spent. 429 so the client can show a
     * timer. Declining is never rationed, so this can only come from an accept.
     */
    ACCEPT_LIMIT_REACHED(158, "Daily like limit reached", HttpStatus.TOO_MANY_REQUESTS),
    /**
     * The whole daily swipe budget is spent, so neither accepting nor declining is
     * possible until it resets. Distinct from {@link #ACCEPT_LIMIT_REACHED}, which leaves
     * the gamer able to keep browsing.
     */
    SWIPE_LIMIT_REACHED(163, "Daily swipe limit reached", HttpStatus.TOO_MANY_REQUESTS),
    /** 402, so the client can route straight to the upgrade screen. */
    SUBSCRIPTION_REQUIRED(159, "This feature requires GameBuddy Gold", HttpStatus.PAYMENT_REQUIRED),
    PRODUCT_NOT_FOUND(160, "Unknown product", HttpStatus.NOT_FOUND),
    /** The store receipt did not verify. Never grant an entitlement on this path. */
    PURCHASE_VERIFICATION_FAILED(161, "Purchase could not be verified", HttpStatus.BAD_REQUEST),
    /** Idempotent replay: the transaction was already credited. Not an error to the user. */
    PURCHASE_ALREADY_PROCESSED(162, "Purchase already processed", HttpStatus.CONFLICT),

    // --- Cosmetics ----------------------------------------------------------
    COSMETIC_NOT_FOUND(164, "Frame or banner not found", HttpStatus.NOT_FOUND),
    COSMETIC_ALREADY_OWNED(165, "You already own this", HttpStatus.CONFLICT),
    /** Equipping something that was never bought. The store should not have offered it. */
    COSMETIC_NOT_OWNED(166, "You do not own this yet", HttpStatus.FORBIDDEN),

    // --- Badges -------------------------------------------------------------
    /** More badges chosen for the showcase than a profile has slots for. */
    SHOWCASE_FULL(167, "You can show at most three badges", HttpStatus.CONFLICT),

    // --- Eligibility and content --------------------------------------------
    /**
     * The stated date of birth puts the account holder under 18. Its own code rather than
     * a generic invalid value, because the client has to say something specific and final
     * here — this is the one rejection that is not worth retrying.
     */
    UNDERAGE(168, "You must be at least 18 years old to use GameBuddy", HttpStatus.FORBIDDEN),
    /** Registration attempted without agreeing to the terms. */
    TERMS_NOT_ACCEPTED(169, "You must accept the terms to create an account", HttpStatus.BAD_REQUEST),
    /**
     * Text refused outright by the content filter — a slur, or sexual abuse aimed at
     * somebody. Ordinary profanity is masked instead and never reaches this.
     */
    CONTENT_BLOCKED(170, "That message breaks the community rules", HttpStatus.BAD_REQUEST),
    NOTHING_TO_REWIND(171, "There is no swipe to take back", HttpStatus.CONFLICT),
    /**
     * The like being rewound was answered. Undoing it would delete a conversation both
     * sides can already see, and take a match away from somebody who did nothing wrong.
     */
    REWIND_MATCHED(172, "You matched with them — that one cannot be taken back", HttpStatus.CONFLICT),
    /**
     * Was BOOST_ALREADY_ACTIVE, for the retired deck boost — same meaning, and now about the
     * lobby: a boost is bought once and lasts until the lobby starts, so a second one buys
     * nothing. The id is reused rather than retired because the message is the same sentence
     * about the same act, and the old code had no client left to confuse.
     */
    LOBBY_ALREADY_BOOSTED(173, "That lobby is already boosted", HttpStatus.CONFLICT),
    /** The daily coins, a quest or the stipend was asked for before it was due. */
    REWARD_NOT_READY(174, "There is nothing to claim yet", HttpStatus.CONFLICT),
    /** A quest whose target has not been reached. */
    QUEST_UNFINISHED(175, "That one is not finished yet", HttpStatus.CONFLICT),
    /**
     * Two writes to the same row raced and this one lost the optimistic lock.
     *
     * <p>Almost always a double-tap on a button that spends or earns coins. The winner's
     * write stands; this one changed nothing, which is exactly the property the lock exists
     * to guarantee. A conflict rather than a server error, because nothing is broken.
     */
    CONCURRENT_MODIFICATION(176, "That went through already — check and try again", HttpStatus.CONFLICT),
    /**
     * Every admirer has already been revealed.
     *
     * <p>Its own code because the alternative was USER_NOT_FOUND, which is true internally
     * — there is no next admirer to look up — and reads to a gamer as though something is
     * broken with their account.
     */
    NO_ADMIRERS_LEFT(177, "You have already revealed everybody", HttpStatus.CONFLICT),
    LOBBY_NOT_FOUND(178, "Lobby not found", HttpStatus.NOT_FOUND),
    LOBBY_FULL(179, "This lobby is already full", HttpStatus.CONFLICT),
    /**
     * The lobby stopped taking this action: it is locked, ended, cancelled or archived.
     *
     * <p>One code for every "too late" rather than one per state, because the caller's
     * remedy is the same in all of them — refresh and look at the lobby as it is now.
     */
    LOBBY_NOT_OPEN(180, "This lobby is no longer open", HttpStatus.CONFLICT),
    LOBBY_ALREADY_MEMBER(181, "You already asked to join this lobby", HttpStatus.CONFLICT),
    LOBBY_NOT_MEMBER(182, "You are not in this lobby", HttpStatus.FORBIDDEN),
    /** One live lobby per owner. Backed by a partial unique index the entity cannot express. */
    LOBBY_LIMIT_REACHED(183, "Finish or cancel your current lobby first", HttpStatus.CONFLICT),
    LOBBY_REQUEST_NOT_FOUND(184, "No pending request from this gamer", HttpStatus.NOT_FOUND),
    /**
     * The owner already said no, and that answer is final for this lobby.
     *
     * <p>Final by design: an owner who screens strangers by hand must be able to answer
     * each of them exactly once, not be petitioned until they give in.
     */
    LOBBY_REJECTED(185, "The owner already answered your request", HttpStatus.CONFLICT),

    /**
     * The sender is the one who blocked, and is writing to the person they blocked.
     *
     * <p>The counterpart to {@link #USER_BLOCKED_YOU}, which says the other side blocked
     * you. Chat tells the two apart — {@link #USER_BLOCKED} covered both directions with
     * one sentence written for a banned account, so whichever end you were, the message
     * described somebody else's situation. Nothing is disclosed by separating them here:
     * both people already know a block exists the moment a message will not send, and each
     * is only ever told about their own half of it.
     */
    USER_BLOCKED_BY_YOU(186, "You have blocked this gamer", HttpStatus.FORBIDDEN),

    /** Unexpected persistence failure. Kept at -99 for backwards compatibility. */
    DB_ERROR(-99, "Data access error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int id;
    private final String message;
    private final HttpStatus httpStatus;

    TransactionCode(int id, String message, HttpStatus httpStatus) {
        this.id = id;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
