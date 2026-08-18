package com.gamebuddy.auth.domain.service;

import com.gamebuddy.auth.infrastructure.entity.*;
import com.gamebuddy.auth.infrastructure.repository.*;
import com.gamebuddy.auth.interfaces.dto.*;
import com.gamebuddy.auth.interfaces.request.*;
import com.gamebuddy.auth.interfaces.response.*;
import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.Platform;
import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.security.JwtService;
import com.gamebuddy.common.util.Constants;
import com.gamebuddy.shared.entity.*;
import com.gamebuddy.shared.event.AccountDeletedEvent;
import com.gamebuddy.shared.event.ProfileChangedEvent;
import com.gamebuddy.shared.funnel.LikeCapCohort;
import com.gamebuddy.shared.moderation.TextModerationService;
import com.gamebuddy.shared.repository.*;
import com.gamebuddy.shared.storage.ObjectStorage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAuthService implements AuthService {

    /** How long a verification code stays usable. Previously: forever. */
    private static final Duration CODE_TTL = Duration.ofMinutes(15);

    /** Wrong guesses tolerated per code before it is burned. */
    private static final int MAX_CODE_ATTEMPTS = 5;

    /**
     * How long a reset ticket lives, and why it is shorter than the code's fifteen minutes.
     *
     * <p>The code has to survive a trip to a mail client on another device. The ticket only
     * has to survive typing a password on a screen the user is already looking at, so there
     * is no reason to leave it lying around for longer.
     */
    private static final Duration TICKET_TTL = Duration.ofMinutes(10);

    /** 256 bits. Enough that guessing a ticket is not a strategy worth rate-limiting for. */
    private static final int TICKET_BYTES = 32;

    private static final int MIN_GAMES = 3;
    private static final int MIN_KEYWORDS = 5;

    /**
     * One is enough, and more than one is the common case.
     *
     * <p>Unlike games and keywords, which need a handful before the recommender has
     * anything to work with, a single platform is a complete and true answer — most people
     * do play on exactly one. Demanding more would push them into ticking a box that is not
     * true of them, which is worse than a short list.
     */
    private static final int MIN_PLATFORMS = 1;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final GamerRepository gamerRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final PasswordResetTicketRepository passwordResetTicketRepository;
    private final GamesRepository gamesRepository;
    private final SessionRepository sessionRepository;
    private final KeywordsRepository keywordsRepository;
    private final AvatarsRepository avatarsRepository;
    private final GamerCosmeticRepository gamerCosmeticRepository;
    private final GamerBadgeRepository gamerBadgeRepository;
    private final ObjectStorage objectStorage;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender emailSender;
    private final ApplicationEventPublisher events;
    private final AuthRateLimiters rateLimiters;
    private final TextModerationService textModeration;
    private final Clock clock;

    @Value("${gamebuddy.mail.from:noreply@mail.findgamebuddy.com}")
    private String sender;

    // =======================================================================
    // Unauthenticated
    // =======================================================================

    @Override
    @Transactional
    public LoginResponse login(LoginRequest loginRequest) {
        String usernameOrEmail = loginRequest.getUsernameOrEmail();
        String password = loginRequest.getPassword();
        String throttleKey = usernameOrEmail.toLowerCase(Locale.ROOT);

        if (!rateLimiters.login().tryAcquire(throttleKey)) {
            throw new BusinessException(TransactionCode.RATE_LIMITED);
        }

        Optional<Gamer> gamerOptional = usernameOrEmail.contains("@")
                ? gamerRepository.findByEmail(usernameOrEmail)
                : gamerRepository.findByGamerUsername(usernameOrEmail);

        // Deliberately the same error whether the account is missing or the password is
        // wrong. The previous code threw USER_NOT_FOUND first, which turned the login
        // endpoint into an account-enumeration oracle.
        Gamer gamer = gamerOptional.orElseThrow(() -> new BusinessException(TransactionCode.WRONG_PASSWORD));

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(gamer.getEmail(), password));
        } catch (BadCredentialsException _) {
            // Logged at WARN so repeated failures against one account are visible to
            // whoever is watching the logs; the exception itself carries nothing the
            // caller should see.
            log.warn("Failed login attempt for {}", throttleKey);
            throw new BusinessException(TransactionCode.WRONG_PASSWORD);
        }

        // Only after proving possession of the password do we disclose account state.
        if (gamer.getDeletedAt() != null) {
            // The row survives so other people's content keeps its references, but the
            // account itself is gone as far as its owner and everyone else is concerned.
            throw new BusinessException(TransactionCode.ACCOUNT_DELETED);
        }
        if (Boolean.TRUE.equals(gamer.getIsBlocked())) {
            throw new BusinessException(TransactionCode.USER_BLOCKED);
        }
        if (Boolean.FALSE.equals(gamer.getIsVerified())) {
            throw new BusinessException(TransactionCode.USER_NOT_VERIFIED);
        }
        if (Boolean.FALSE.equals(gamer.getIsRegistered())) {
            throw new BusinessException(TransactionCode.USER_NOT_COMPLETED);
        }

        rateLimiters.login().reset(throttleKey);
        String token = issueSession(gamer);

        // The counterpart to the failed-attempt warning above. Without a record of the
        // successes, a run of failures followed by silence is indistinguishable from a run
        // of failures followed by someone getting in.
        log.info("Login for {}", gamer.getUserId());

        LoginResponse response = new LoginResponse();
        LoginResponseBody body = new LoginResponseBody();
        body.setAccessToken(token);
        body.setUserId(gamer.getUserId());
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    /**
     * Registration is now a single transaction that also covers the outbound e-mail.
     *
     * <p>Previously the gamer row was committed before the mail was attempted, so an
     * SMTP failure left an unverifiable account behind that could never be registered
     * again — {@code EMAIL_EXISTS} on every retry, with no way out.
     */
    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest registerRequest) {
        String email = registerRequest.getEmail().trim().toLowerCase(Locale.ROOT);
        PasswordPolicy.validate(registerRequest.getPassword());
        TermsPolicy.requireAcceptance(registerRequest.getAcceptedTerms());

        Gamer gamer = gamerRepository.findByEmail(email).orElse(null);
        if (gamer != null) {
            // An account that never completed verification may be re-claimed; a live
            // one may not.
            if (Boolean.TRUE.equals(gamer.getIsVerified())) {
                throw new BusinessException(TransactionCode.EMAIL_EXISTS);
            }
            log.info("Re-issuing registration for unverified account {}", email);
        } else {
            gamer = new Gamer();
            gamer.setUserId(UUID.randomUUID().toString());
            gamer.setEmail(email);
            gamer.setRole(Role.USER);
            // Assigned here, once, and never again. The free like-cap experiment is not
            // running yet — every tier still gets the same cap — but a cohort handed out
            // later is not a cohort: the accounts that already existed would be sorted by a
            // rule that was not in force while they were forming their habits, and their
            // retention would be attributed to an experiment they never took part in.
            gamer.setLikeCapCohort(LikeCapCohort.forUser(gamer.getUserId()));
        }
        gamer.setPwd(passwordEncoder.encode(registerRequest.getPassword()));
        // Stamped on every registration attempt, including a re-claimed unverified one:
        // whoever ends up owning this account is the person who ticked the box just now.
        gamer.setTermsAcceptedAt(clock.instant());
        gamer.setTermsVersion(TermsPolicy.CURRENT_VERSION);
        // Deliberately no device token here. Registration is not device registration: on
        // Android 13+ the client has not asked for notification permission yet and cannot
        // possess a real token, so what used to arrive was the literal placeholder
        // "pending" — and it was stored verbatim, for every account.
        //
        // That made the column non-unique by construction. Every account that had not yet
        // completed push registration carried an identical token, which broke the two
        // places that resolve a gamer *by* token: the preference check in
        // NotificationDispatcher (a NonUniqueResultException that escaped as a 500 from
        // whatever request triggered the notification) and the history write in
        // DefaultNotificationService. It also queued outbox rows addressed to "pending",
        // which can only ever fail at Firebase.
        //
        // The device registers itself after sign-in through updateFcmToken, which detaches
        // the token from any previous owner first and so keeps the column unique. A null
        // here is the honest state: no device registered yet.
        gamerRepository.save(gamer);

        issueAndSendCode(email, true);

        RegisterResponse response = new RegisterResponse();
        RegisterResponseBody body = new RegisterResponseBody();
        body.setUserId(gamer.getUserId());
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    @Override
    @Transactional
    public VerifyResponse verifyCode(VerifyRequest verifyRequest) {
        String email = verifyRequest.getEmail().trim().toLowerCase(Locale.ROOT);
        Integer code = verifyRequest.getVerificationCode();

        if (!rateLimiters.verify().tryAcquire(email)) {
            throw new BusinessException(TransactionCode.RATE_LIMITED);
        }

        // An unknown address returns exactly what a wrong code does (below), so verify cannot
        // be used to tell a registered email from an unregistered one.
        Gamer gamer = gamerRepository
                .findByEmail(email)
                .orElseThrow(() -> new BusinessException(TransactionCode.VERIFICATION_CODE_NOT_FOUND));

        redeemCode(email, code, CodePurpose.REGISTRATION);
        rateLimiters.verify().reset(email);

        gamer.setIsVerified(true);
        // Proving control of the mailbox invalidates any previously issued token.
        gamer.revokeIssuedTokens();
        gamerRepository.save(gamer);

        log.info("Account {} verified; previously issued tokens revoked", gamer.getUserId());

        String token = issueSession(gamer);

        VerifyResponse response = new VerifyResponse();
        VerifyResponseBody body = new VerifyResponseBody();
        body.setAccessToken(token);
        body.setUserId(gamer.getUserId());
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    @Override
    @Transactional
    public DefaultMessageResponse sendVerificationEmail(SendCodeRequest sendCodeRequest) {
        String email = sendCodeRequest.getEmail().trim().toLowerCase(Locale.ROOT);

        if (!rateLimiters.sendCode().tryAcquire(email)) {
            throw new BusinessException(TransactionCode.RATE_LIMITED);
        }
        // Uniform response whether or not the address has an account, so this endpoint
        // cannot be used to enumerate registered emails or to mail-bomb a known one: a real
        // account is sent a code, an unknown address gets the same reply and nothing is sent.
        gamerRepository
                .findByEmail(email)
                .ifPresent(g -> issueAndSendCode(email, Boolean.TRUE.equals(sendCodeRequest.getIsRegister())));
        return DefaultMessageResponse.of("If an account exists for that address, a verification code has been sent");
    }

    /**
     * Step one of a reset: spend the emailed code, hand back a ticket for step two.
     *
     * <p>Deliberately not a session. {@code verifyCode} answers a correct code with an access
     * token, which is right when the code proves "this address is mine" at signup and wrong
     * here: the holder has not authenticated, they have only shown they can read the mailbox,
     * and the one thing they should be able to do next is set a password. A ticket says
     * exactly that and nothing more.
     *
     * <p>Every failure looks like a wrong code, including an address with no account, so this
     * cannot be used to find out who has one.
     */
    @Override
    @Transactional
    public ResetVerifyResponse verifyResetCode(ResetVerifyRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (!rateLimiters.resetPassword().tryAcquire(email)) {
            throw new BusinessException(TransactionCode.RATE_LIMITED);
        }

        gamerRepository
                .findByEmail(email)
                .orElseThrow(() -> new BusinessException(TransactionCode.VERIFICATION_CODE_NOT_FOUND));

        redeemCode(email, request.getVerificationCode(), CodePurpose.PASSWORD_RESET);

        // Any ticket from an abandoned earlier attempt stops being a way in.
        passwordResetTicketRepository.burnAllForEmail(email);

        byte[] raw = new byte[TICKET_BYTES];
        RANDOM.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);

        Instant now = Instant.now();
        PasswordResetTicket ticket = new PasswordResetTicket();
        ticket.setEmail(email);
        ticket.setTokenHash(hashToken(token));
        ticket.setUsed(false);
        ticket.setCreatedAt(now);
        ticket.setExpiresAt(now.plus(TICKET_TTL));
        passwordResetTicketRepository.save(ticket);

        rateLimiters.resetPassword().reset(email);
        log.info("Password reset code accepted for {}; ticket issued", email);

        ResetVerifyResponse response = new ResetVerifyResponse();
        ResetVerifyResponseBody body = new ResetVerifyResponseBody();
        body.setResetToken(token);
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    /**
     * Step two: spend the ticket and set the new password.
     *
     * <p>Does what {@code changePwd} does once it is satisfied the request is genuine — encode,
     * revoke, delete the sessions — because the two must not diverge on what "the password
     * changed" means. What differs is only how the caller proved themselves: there, the
     * current password; here, a ticket earned with a mailed code.
     */
    @Override
    @Transactional
    public DefaultMessageResponse resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        if (!rateLimiters.resetPassword().tryAcquire(email)) {
            throw new BusinessException(TransactionCode.RATE_LIMITED);
        }

        // A ticket that is missing, spent, expired or issued for a different address is one
        // answer: start again. Distinguishing them would describe the state of somebody
        // else's reset to whoever is guessing.
        PasswordResetTicket ticket = passwordResetTicketRepository
                .findByTokenHash(hashToken(request.getResetToken()))
                .filter(t -> t.getEmail().equals(email))
                .orElseThrow(() -> new BusinessException(TransactionCode.VERIFICATION_CODE_NOT_FOUND));

        if (ticket.isExpired()) {
            throw new BusinessException(TransactionCode.VERIFICATION_CODE_EXPIRED);
        }
        if (Boolean.TRUE.equals(ticket.getUsed())) {
            throw new BusinessException(TransactionCode.VERIFICATION_CODE_NOT_FOUND);
        }

        Gamer gamer = gamerRepository
                .findByEmail(email)
                .orElseThrow(() -> new BusinessException(TransactionCode.VERIFICATION_CODE_NOT_FOUND));

        String newPassword = request.getPassword();
        if (passwordEncoder.matches(newPassword, gamer.getPwd())) {
            throw new BusinessException(TransactionCode.PASSWORD_SAME);
        }
        PasswordPolicy.validate(newPassword);

        gamer.setPwd(passwordEncoder.encode(newPassword));
        gamer.revokeIssuedTokens();
        gamerRepository.save(gamer);
        sessionRepository.deleteAllByEmail(email);

        ticket.setUsed(true);
        passwordResetTicketRepository.save(ticket);
        passwordResetTicketRepository.burnAllForEmail(email);
        verificationCodeRepository.invalidateAllForEmail(email);

        log.info("Password reset for {}; all sessions invalidated", gamer.getUserId());

        sendPasswordChangedNotice(email);

        return DefaultMessageResponse.of("Password changed successfully. Please sign in.");
    }

    /**
     * Tells the account its password changed — the one message a takeover victim receives.
     *
     * <p>Failure is swallowed, unlike every other mail in this class. Elsewhere a dead relay
     * should roll the transaction back, because an account with no deliverable code is worse
     * than no account. Here the password has already changed and the sessions are already
     * gone; throwing would undo a reset the user completed successfully because we could not
     * send them a courtesy note.
     */
    private void sendPasswordChangedNotice(String email) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(email);
        message.setSubject(Constants.EMAIL_SUBJECT_PASSWORD_CHANGED);
        message.setText(String.format(Constants.EMAIL_TEXT_PASSWORD_CHANGED, email));
        try {
            emailSender.send(message);
        } catch (MailException e) {
            log.warn("Password-changed notice to {} could not be sent", email, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TokenResponse validateToken(String token) {
        Session session = sessionRepository
                .findByTokenHash(hashToken(token))
                .orElseThrow(() -> new BusinessException(TransactionCode.TOKEN_NOT_FOUND));

        Gamer gamer = gamerRepository
                .findByEmail(session.getEmail())
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));

        if (!jwtService.isTokenValid(token, gamer)) {
            throw new BusinessException(TransactionCode.TOKEN_INVALID);
        }

        TokenResponse response = new TokenResponse();
        TokenResponseBody body = new TokenResponseBody();
        body.setUsername(gamer.getGamerUsername());
        body.setIsValid(true);
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    // =======================================================================
    // Authenticated
    // =======================================================================

    @Override
    @Transactional
    public DefaultMessageResponse setUsername(Gamer principal, UsernameRequest usernameRequest) {
        Gamer gamer = reload(principal);
        String username = usernameRequest.getUsername().trim();
        UsernamePolicy.validate(username);
        // A username is on every message, every post and every profile card this account
        // ever appears on, so it is refused rather than masked — there is no useful
        // rendering of a slur with asterisks in it.
        if (!textModeration.isCleanIdentifier(username)) {
            throw new BusinessException(TransactionCode.CONTENT_BLOCKED);
        }

        Optional<Gamer> clash = gamerRepository.findByGamerUsernameIgnoreCase(username);
        if (clash.isPresent() && !Objects.equals(clash.get().getUserId(), gamer.getUserId())) {
            throw new BusinessException(TransactionCode.USERNAME_EXISTS);
        }
        gamer.setGamerUsername(username);
        gamerRepository.save(gamer);
        return DefaultMessageResponse.of("Username set successfully");
    }

    @Override
    @Transactional
    public DefaultMessageResponse details(Gamer principal, DetailsRequest detailsRequest) {
        Gamer gamer = reload(principal);

        List<String> games = detailsRequest.getFavoriteGames();
        List<String> keywords = detailsRequest.getKeywords();
        // Enforced here as well as in bean validation: the documented rule is at least
        // 3 games and 5 keywords, but the annotations said 1 and 3 respectively.
        if (games == null || games.size() < MIN_GAMES) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "select at least " + MIN_GAMES + " games");
        }
        if (keywords == null || keywords.size() < MIN_KEYWORDS) {
            throw new BusinessException(
                    TransactionCode.INVALID_REQUEST, "select at least " + MIN_KEYWORDS + " keywords");
        }
        // Checked here with the others rather than left to mapAndSetPlatforms below, so
        // that every "you have not picked enough" refusal happens before any lookup. Left
        // where it was mapped, somebody who sent no platforms got whatever error the avatar
        // or game lookup produced first — a message about the wrong field entirely.
        if (detailsRequest.getPlatforms() == null
                || detailsRequest.getPlatforms().size() < MIN_PLATFORMS) {
            throw new BusinessException(
                    TransactionCode.INVALID_REQUEST, "select at least " + MIN_PLATFORMS + " platform");
        }

        // The client sends a date, never an age: the number that decides eligibility is
        // computed here or it is not trustworthy.
        gamer.setBirthDate(detailsRequest.getBirthDate());
        gamer.setAge(AgePolicy.validate(detailsRequest.getBirthDate(), clock));
        gamer.setCountry(detailsRequest.getCountry());
        gamer.setGender(detailsRequest.getGender());
        // Only when one was sent. Onboarding no longer asks for an avatar — see
        // DetailsRequest#avatar — but a catalogue id is still honoured while the
        // catalogue exists.
        if (detailsRequest.getAvatar() != null && !detailsRequest.getAvatar().isBlank()) {
            gamer.setAvatar(requireSelectableAvatar(detailsRequest.getAvatar()).getId());
        }

        gamer.getKeywords().clear();
        gamer.getLikedgames().clear();
        mapAndSetKeywords(gamer, keywords);
        mapAndSetUserGames(gamer, games);
        mapAndSetPlatforms(gamer, detailsRequest.getPlatforms());
        gamer.setIsRegistered(true);
        gamerRepository.save(gamer);

        refreshRecommenderClusters(gamer);
        return DefaultMessageResponse.of("User details saved successfully");
    }

    @Override
    @Transactional
    public DefaultMessageResponse changePwd(Gamer principal, ChangePwdRequest changePwdRequest) {
        Gamer gamer = reload(principal);

        // The old endpoint changed the password on the strength of the bearer token
        // alone, so a leaked token meant permanent account takeover.
        if (!passwordEncoder.matches(changePwdRequest.getCurrentPassword(), gamer.getPwd())) {
            throw new BusinessException(TransactionCode.CURRENT_PASSWORD_WRONG);
        }
        String newPassword = changePwdRequest.getPassword();
        if (passwordEncoder.matches(newPassword, gamer.getPwd())) {
            throw new BusinessException(TransactionCode.PASSWORD_SAME);
        }
        PasswordPolicy.validate(newPassword);

        gamer.setPwd(passwordEncoder.encode(newPassword));
        gamer.revokeIssuedTokens();
        gamerRepository.save(gamer);
        sessionRepository.deleteAllByEmail(gamer.getEmail());

        // A password change is the action a user takes when they believe they have been
        // compromised, and the action an attacker takes once they are in. Either way it is
        // the first thing anyone looks for afterwards.
        log.info("Password changed for {}; all sessions invalidated", gamer.getUserId());

        return DefaultMessageResponse.of("Password changed successfully. Please sign in again.");
    }

    @Override
    @Transactional
    public DefaultMessageResponse changeAvatar(Gamer principal, ChangeAvatarRequest avatarRequest) {
        Gamer gamer = reload(principal);
        Avatars avatar = requireSelectableAvatar(avatarRequest.getAvatarId());

        gamer.setAvatar(avatar.getId());
        gamerRepository.save(gamer);
        return DefaultMessageResponse.of("Avatar changed successfully");
    }

    @Override
    @Transactional
    public DefaultMessageResponse changeAge(Gamer principal, ChangeAgeRequest changeAgeRequest) {
        Gamer gamer = reload(principal);
        int age = AgePolicy.validate(changeAgeRequest.getBirthDate(), clock);

        // Logged because this is the field an account holder would edit if they wanted to
        // be somewhere they are not allowed. It cannot achieve that any more — the floor
        // is 18 and it is checked above — but an unexplained change of date of birth is
        // still the first thing worth seeing when investigating a report.
        log.info(
                "Gamer {} changed date of birth from {} to {} (age {})",
                gamer.getUserId(),
                gamer.getBirthDate(),
                changeAgeRequest.getBirthDate(),
                age);

        gamer.setBirthDate(changeAgeRequest.getBirthDate());
        gamer.setAge(age);
        gamerRepository.save(gamer);
        return DefaultMessageResponse.of("Date of birth changed successfully");
    }

    /**
     * Deletes the caller's account.
     *
     * <p>The row is anonymised rather than removed. Posts, comments, chat messages and
     * match edges all reference it; deleting it would either cascade a gamer's entire
     * history away — including the other half of conversations that are not theirs to
     * erase — or leave dangling references. Anonymising keeps other people's data intact
     * while removing everything that identifies this one.
     *
     * <p>{@code deletedAt} makes {@code isEnabled()} false, and the shared JWT filter
     * checks it, so the account stops authenticating against all five services at once.
     */
    @Override
    @Transactional
    public DefaultMessageResponse deleteAccount(Gamer principal, DeleteAccountRequest request) {
        Gamer gamer = reload(principal);

        // Ordered before the password check: the caller is already authenticated as this
        // account, so there is nothing to leak, and after a deletion the stored hash is a
        // random one nobody can satisfy — checking it first would report the wrong reason.
        if (gamer.getDeletedAt() != null) {
            throw new BusinessException(TransactionCode.ACCOUNT_DELETED);
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), gamer.getPwd())) {
            throw new BusinessException(TransactionCode.CURRENT_PASSWORD_WRONG);
        }

        String userId = gamer.getUserId();
        // Captured before anonymisation. Sessions and verification codes are keyed by the
        // real address, and the principal may be the same instance being rewritten below.
        String originalEmail = gamer.getEmail();

        // The uploaded avatar, likewise. It is the one piece of this account that lives
        // outside the database, so nulling the column below would otherwise leave a
        // photograph of a real person sitting in a public bucket, readable by anyone who
        // kept the URL. "We deleted the row that pointed at it" is not erasure.
        String avatarKey = gamer.getAvatarKey();
        AvatarStatus avatarStatus = gamer.getAvatarStatus();

        // The unique columns get a value derived from the id rather than null, so a
        // second deletion cannot collide with the first on the unique index.
        gamer.setEmail("deleted-" + userId + "@deleted.invalid");
        gamer.setGamerUsername("deleted_" + userId.substring(0, Math.min(8, userId.length())));

        // A password nobody holds. Leaving the old hash would keep it verifiable.
        gamer.setPwd(passwordEncoder.encode(UUID.randomUUID().toString()));

        gamer.setAge(null);
        gamer.setCountry(null);
        gamer.setGender(null);
        gamer.setAvatar(null);
        gamer.setAvatarKey(null);
        gamer.setAvatarStatus(null);
        gamer.setFcmToken(null);
        gamer.setIsRegistered(false);

        // Taste data feeds the recommender, so it has to go or the account keeps being
        // suggested to people.
        gamer.getKeywords().clear();
        gamer.getLikedgames().clear();

        // Purchases go too. They are personal data — a record of what this person spent
        // money on and when — and there is no account left for them to belong to. The
        // equipped slots have to be cleared before the rows are deleted, or the gamer row
        // keeps pointing at cosmetics its owner no longer owns.
        gamer.setEquippedFrame(null);
        gamer.setEquippedBanner(null);
        gamerCosmeticRepository.deleteAllByUserId(gamer.getUserId());

        // Badges too. What somebody achieved is a record of what they did here, and the
        // showcase is the part of it other people could see.
        gamerBadgeRepository.deleteAllByUserId(gamer.getUserId());

        gamer.setDeletedAt(Instant.now());
        gamer.revokeIssuedTokens();
        gamerRepository.save(gamer);

        sessionRepository.deleteAllByEmail(originalEmail);
        verificationCodeRepository.invalidateAllForEmail(originalEmail);

        // Inside the transaction, matching AccountDeletedEvent's listeners and for the
        // same reason. The trade is explicit: a commit that fails after this point loses
        // the avatar of an account that still exists, which is visible and fixable by
        // re-uploading. The other order — delete after commit — risks the opposite, an
        // orphaned photograph nothing points at any more and nobody will ever notice.
        // For a deletion done on privacy grounds that is the worse failure.
        if (avatarKey != null) {
            objectStorage.delete(
                    avatarStatus == AvatarStatus.APPROVED ? ObjectStorage.Bucket.MEDIA : ObjectStorage.Bucket.UPLOADS,
                    avatarKey);
        }

        // Other modules hold personal data this module does not know about — the impression
        // log, for one. Listeners run inside this transaction, so if a purge fails the
        // deletion fails with it rather than reporting success over surviving data.
        events.publishEvent(new AccountDeletedEvent(userId));

        log.info("Account {} deleted at the owner's request", userId);
        return DefaultMessageResponse.of("Your account has been deleted");
    }

    @Override
    @Transactional
    public DefaultMessageResponse updateFcmToken(Gamer principal, FcmTokenRequest request) {
        Gamer gamer = reload(principal);
        String token = request.getFcmToken().trim();

        if (token.equals(gamer.getFcmToken())) {
            // The client calls this on every start, so the common case is no change.
            return DefaultMessageResponse.of("Device token is up to date");
        }

        // A device token identifies a device, not an account. When a gamer signs in on a
        // handset that another account used before, the token has to move with the
        // device or the previous owner keeps receiving this gamer's notifications.
        gamerRepository.clearFcmTokenFrom(token, gamer.getUserId());

        gamer.setFcmToken(token);
        gamerRepository.save(gamer);
        return DefaultMessageResponse.of("Device token updated");
    }

    @Override
    @Transactional
    public DefaultMessageResponse changeGames(Gamer principal, ChangeDetailRequest changeGamesRequest) {
        Gamer gamer = reload(principal);
        List<String> games = changeGamesRequest.getGamesOrKeywordsList();
        if (games.size() < MIN_GAMES) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "select at least " + MIN_GAMES + " games");
        }
        gamer.getLikedgames().clear();
        mapAndSetUserGames(gamer, games);
        gamerRepository.save(gamer);

        refreshRecommenderClusters(gamer);
        return DefaultMessageResponse.of("Games changed successfully");
    }

    @Override
    @Transactional
    public DefaultMessageResponse changeKeywords(Gamer principal, ChangeDetailRequest changeKeywordsRequest) {
        Gamer gamer = reload(principal);
        List<String> keywords = changeKeywordsRequest.getGamesOrKeywordsList();
        if (keywords.size() < MIN_KEYWORDS) {
            throw new BusinessException(
                    TransactionCode.INVALID_REQUEST, "select at least " + MIN_KEYWORDS + " keywords");
        }
        gamer.getKeywords().clear();
        mapAndSetKeywords(gamer, keywords);
        gamerRepository.save(gamer);

        refreshRecommenderClusters(gamer);
        return DefaultMessageResponse.of("Keywords changed successfully");
    }

    /**
     * Changes what a gamer plays on.
     *
     * <p>No {@code refreshRecommenderClusters} call, unlike games and keywords. The model
     * ranks on taste, and platform is not taste — it is a hard constraint the feed applies
     * afterwards as a filter. Marking the profile stale here would force a live re-rank for
     * a change that cannot move a single score.
     */
    @Override
    @Transactional
    public DefaultMessageResponse changePlatforms(Gamer principal, ChangeDetailRequest changePlatformsRequest) {
        Gamer gamer = reload(principal);
        mapAndSetPlatforms(gamer, changePlatformsRequest.getGamesOrKeywordsList());
        gamerRepository.save(gamer);
        return DefaultMessageResponse.of("Platforms changed successfully");
    }

    // =======================================================================
    // Helpers
    // =======================================================================

    /**
     * Re-reads the principal inside the current transaction.
     *
     * <p>The instance held by the security context was loaded by the JWT filter in a
     * persistence context that is long closed, so it is detached and its lazy
     * collections are unusable.
     */
    private Gamer reload(Gamer principal) {
        Gamer gamer = gamerRepository
                .findById(principal.getUserId())
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));
        if (Boolean.FALSE.equals(gamer.getIsVerified())) {
            throw new BusinessException(TransactionCode.USER_NOT_VERIFIED);
        }
        return gamer;
    }

    /**
     * Issues a fresh token for the session the caller already holds.
     *
     * <p>Deliberately does almost nothing else. It does not re-check the password (the
     * bearer token is the credential), and it does not re-run the login gates — the JWT
     * filter has already loaded the account and refused a blocked or deleted one, and
     * {@code tokensValidFrom} kills every outstanding token the moment a password
     * changes, so a session cannot be refreshed past a revocation.
     */
    @Override
    @Transactional
    public LoginResponse refreshSession(Gamer principal, String bearerToken) {
        Gamer gamer = gamerRepository
                .findById(principal.getUserId())
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));

        Instant sessionStart = jwtService.extractSessionStart(bearerToken);
        if (!jwtService.withinMaxSessionAge(sessionStart, Instant.now())) {
            // The ceiling. Reported as an invalid token because that is what it is from
            // here on, and because the client already knows to ask for the password when
            // it sees this rather than showing an error nobody can act on.
            log.info("Session for {} reached its maximum age; a new login is required", gamer.getUserId());
            throw new BusinessException(TransactionCode.TOKEN_INVALID);
        }

        String token = issueSession(gamer, sessionStart);

        LoginResponse response = new LoginResponse();
        LoginResponseBody body = new LoginResponseBody();
        body.setAccessToken(token);
        body.setUserId(gamer.getUserId());
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    /** Starts a new session: the token's clock, and the ceiling's, both begin now. */
    private String issueSession(Gamer gamer) {
        return issueSession(gamer, Instant.now());
    }

    /**
     * Records a token against the account, replacing whatever was there.
     *
     * <p>{@code sessionStart} is what separates a login from a refresh: a login passes
     * now, a refresh passes the moment the session originally began, so extending a
     * session never resets its age.
     */
    private String issueSession(Gamer gamer, Instant sessionStart) {
        String token = jwtService.generateToken(gamer, sessionStart);
        // One row per account, as before — a refresh replaces its predecessor rather
        // than piling up a row a week for every gamer who keeps playing.
        sessionRepository.deleteAllByEmail(gamer.getEmail());

        Session session = new Session();
        session.setTokenHash(hashToken(token));
        session.setEmail(gamer.getEmail());
        session.setExpiresAt(jwtService.extractExpiration(token));
        sessionRepository.save(session);
        return token;
    }

    /**
     * Spends a six-digit code, or refuses and charges the attempt.
     *
     * <p>Shared by registration and password reset so the two cannot drift apart on the
     * rules that matter: expiry, the attempt cap, single use, and burning every sibling
     * code on success.
     *
     * <p><b>The lookup is by address, not by code.</b> The code is bcrypt-hashed, so there is
     * nothing to put in a WHERE clause — we take the live row for the address and compare
     * against it. That is a happier shape than the one it replaces, which searched by code
     * and then, on the miss, had to go looking for the row again just to charge the attempt.
     *
     * <p><b>A code issued for the other flow is treated exactly like a wrong guess</b> — same
     * error, same attempt charged. Saying "that code is real but for something else" would
     * tell an attacker which flow an address is in the middle of.
     */
    private VerificationCode redeemCode(String email, Integer code, CodePurpose purpose) {
        VerificationCode live = verificationCodeRepository
                .findFirstByEmailAndIsValidTrueOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new BusinessException(TransactionCode.VERIFICATION_CODE_NOT_FOUND));

        if (live.isExpired()) {
            live.setIsValid(false);
            verificationCodeRepository.save(live);
            throw new BusinessException(TransactionCode.VERIFICATION_CODE_EXPIRED);
        }
        if (live.getAttempts() >= MAX_CODE_ATTEMPTS) {
            live.setIsValid(false);
            verificationCodeRepository.save(live);
            throw new BusinessException(TransactionCode.TOO_MANY_ATTEMPTS);
        }

        boolean matches = live.getPurpose() == purpose
                && code != null
                && passwordEncoder.matches(String.valueOf(code), live.getCodeHash());
        if (!matches) {
            // Charge the wrong guess so a brute-force run burns its budget rather than
            // walking the million values a six-digit code has.
            live.setAttempts(live.getAttempts() + 1);
            if (live.getAttempts() >= MAX_CODE_ATTEMPTS) {
                live.setIsValid(false);
            }
            verificationCodeRepository.save(live);
            throw new BusinessException(TransactionCode.VERIFICATION_CODE_NOT_FOUND);
        }

        // Burn this code and every other outstanding one for the address.
        live.setIsValid(false);
        verificationCodeRepository.save(live);
        verificationCodeRepository.invalidateAllForEmail(email);
        return live;
    }

    /** Creates a fresh code, invalidates any predecessors, and mails it out. */
    private void issueAndSendCode(String email, boolean forRegistration) {
        verificationCodeRepository.invalidateAllForEmail(email);

        int code = 100000 + RANDOM.nextInt(900000);
        Instant now = Instant.now();

        VerificationCode verification = new VerificationCode();
        // Hashed, never stored in the clear. See the comment on the entity for why bcrypt
        // rather than the SHA-256 used for session tokens.
        verification.setCodeHash(passwordEncoder.encode(String.valueOf(code)));
        verification.setPurpose(forRegistration ? CodePurpose.REGISTRATION : CodePurpose.PASSWORD_RESET);
        verification.setEmail(email);
        verification.setIsValid(true);
        verification.setAttempts(0);
        verification.setCreatedAt(now);
        verification.setExpiresAt(now.plus(CODE_TTL));
        verificationCodeRepository.save(verification);

        long minutes = CODE_TTL.toMinutes();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(email);
        if (forRegistration) {
            message.setSubject(String.format(Constants.EMAIL_SUBJECT, code));
            message.setText(String.format(Constants.EMAIL_TEXT, code, minutes));
        } else {
            message.setSubject(Constants.EMAIL_SUBJECT_FORGOT_PASSWORD);
            message.setText(String.format(Constants.EMAIL_TEXT_FORGOT_PASSWORD, code, email, minutes));
        }

        try {
            emailSender.send(message);
        } catch (MailException e) {
            // Propagating rolls the surrounding transaction back, so no orphaned
            // account or dangling code survives a mail outage.
            log.warn("Verification email to {} failed", email, e);
            throw new BusinessException(TransactionCode.EMAIL_SEND_FAILED);
        }
    }

    /**
     * Resolves a stock avatar by id.
     *
     * <p>There is no entitlement check left to make: nothing in the catalogue is for sale,
     * so every row in it is selectable by anyone. This used to enforce a paywall — and had
     * to, because {@code details()} once skipped the check that {@code changeAvatar()}
     * made, which meant every special avatar was free exactly once during onboarding, which
     * is to say free. Both paths still share this method; it now only answers "does that
     * avatar exist".
     */
    private Avatars requireSelectableAvatar(String avatarId) {
        return avatarsRepository
                .findById(parseUuid(avatarId))
                .orElseThrow(() -> new BusinessException(TransactionCode.AVATAR_NOT_FOUND));
    }

    /** {@code UUID.fromString} on unvalidated input used to surface as an HTTP 500. */
    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "malformed identifier", e);
        }
    }

    private void mapAndSetUserGames(Gamer gamer, List<String> favGames) {
        for (String gameId : favGames) {
            Games game = gamesRepository
                    .findById(gameId)
                    .orElseThrow(() -> new BusinessException(TransactionCode.DB_ERROR, "unknown game " + gameId));
            gamer.getLikedgames().add(game);
        }
    }

    /**
     * Replaces the platform set from a list of enum names.
     *
     * <p>An unrecognised name is refused rather than skipped. Skipping would let a client
     * that sends {@code "Playstation5"} appear to succeed while saving nothing, and the
     * account holder would find an empty platform list with no error to explain it.
     */
    private void mapAndSetPlatforms(Gamer gamer, List<String> platformNames) {
        if (platformNames == null || platformNames.size() < MIN_PLATFORMS) {
            throw new BusinessException(
                    TransactionCode.INVALID_REQUEST, "select at least " + MIN_PLATFORMS + " platform");
        }
        Set<Platform> platforms = new LinkedHashSet<>();
        for (String name : platformNames) {
            Platform platform = Platform.from(name);
            if (platform == null) {
                throw new BusinessException(TransactionCode.INVALID_REQUEST, "unknown platform " + name);
            }
            platforms.add(platform);
        }
        // Cleared and refilled rather than reassigned: Hibernate manages this collection,
        // and handing it a different Set instance detaches the one it is tracking.
        gamer.getPlatforms().clear();
        gamer.getPlatforms().addAll(platforms);
    }

    private void mapAndSetKeywords(Gamer gamer, List<String> keywordIds) {
        for (String keywordId : keywordIds) {
            Keywords keyword = keywordsRepository
                    .findById(parseUuid(keywordId))
                    .orElseThrow(() -> new BusinessException(TransactionCode.DB_ERROR, "unknown keyword " + keywordId));
            gamer.getKeywords().add(keyword);
        }
    }

    /**
     * Announces that this gamer's games or keywords changed.
     *
     * <p>Consumed by {@code RecommenderStalenessListener} in the match module, which marks
     * the gamer so the feed ranks them from their live profile until the model is retrained
     * on it. Nothing here re-clusters anything: the recommender is trained offline, and the
     * name this method still carries is the last trace of a design where every profile edit
     * refitted the whole model.
     *
     * <p>The Javadoc used to point at a {@code RecommenderRefreshListener} that was never
     * written, and for a while there was no consumer at all — so {@code /predict}, which
     * ranks a known gamer from features pickled at training time, went on answering with
     * the profile they had abandoned.
     */
    private void refreshRecommenderClusters(Gamer gamer) {
        events.publishEvent(new ProfileChangedEvent(gamer.getUserId()));
    }

    private static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
