package com.gamebuddy.auth.domain.service;

import com.gamebuddy.auth.domain.event.ProfileChangedEvent;
import com.gamebuddy.auth.infrastructure.entity.*;
import com.gamebuddy.auth.infrastructure.repository.*;
import com.gamebuddy.auth.interfaces.dto.*;
import com.gamebuddy.auth.interfaces.request.*;
import com.gamebuddy.auth.interfaces.response.*;
import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.security.JwtService;
import com.gamebuddy.common.util.Constants;
import com.gamebuddy.shared.entity.*;
import com.gamebuddy.shared.event.AccountDeletedEvent;
import com.gamebuddy.shared.repository.*;
import com.gamebuddy.shared.storage.ObjectStorage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
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

    private static final int MIN_GAMES = 3;
    private static final int MIN_KEYWORDS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final GamerRepository gamerRepository;
    private final VerificationCodeRepository verificationCodeRepository;
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

    @Value("${spring.mail.username:noreply@gamebuddy.app}")
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
        }
        gamer.setPwd(passwordEncoder.encode(registerRequest.getPassword()));
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

        Gamer gamer = gamerRepository
                .findByEmail(email)
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));

        VerificationCode verification = verificationCodeRepository
                .findByEmailAndCodeAndIsValidTrue(email, code)
                .orElseGet(() -> {
                    // Charge the wrong guess against the live code so a brute-force run
                    // burns its budget instead of guessing indefinitely.
                    verificationCodeRepository
                            .findFirstByEmailAndIsValidTrueOrderByCreatedAtDesc(email)
                            .ifPresent(live -> {
                                live.setAttempts(live.getAttempts() + 1);
                                if (live.getAttempts() >= MAX_CODE_ATTEMPTS) {
                                    live.setIsValid(false);
                                }
                                verificationCodeRepository.save(live);
                            });
                    throw new BusinessException(TransactionCode.VERIFICATION_CODE_NOT_FOUND);
                });

        if (verification.isExpired()) {
            verification.setIsValid(false);
            verificationCodeRepository.save(verification);
            throw new BusinessException(TransactionCode.VERIFICATION_CODE_EXPIRED);
        }
        if (verification.getAttempts() >= MAX_CODE_ATTEMPTS) {
            verification.setIsValid(false);
            verificationCodeRepository.save(verification);
            throw new BusinessException(TransactionCode.TOO_MANY_ATTEMPTS);
        }

        // Burn this code and every other outstanding one for the address.
        verification.setIsValid(false);
        verificationCodeRepository.save(verification);
        verificationCodeRepository.invalidateAllForEmail(email);
        rateLimiters.verify().reset(email);

        gamer.setIsVerified(true);
        // Proving control of the mailbox invalidates any previously issued token.
        gamer.revokeIssuedTokens();
        gamerRepository.save(gamer);

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
        gamerRepository.findByEmail(email).orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));

        issueAndSendCode(email, Boolean.TRUE.equals(sendCodeRequest.getIsRegister()));
        return DefaultMessageResponse.of("Verification code sent successfully");
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

        Optional<Gamer> clash = gamerRepository.findByGamerUsername(username);
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

        gamer.setAge(detailsRequest.getAge());
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
        gamer.setAge(changeAgeRequest.getAge());
        gamerRepository.save(gamer);
        return DefaultMessageResponse.of("Age changed successfully");
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

    private String issueSession(Gamer gamer) {
        String token = jwtService.generateToken(gamer);
        sessionRepository.deleteAllByEmail(gamer.getEmail());

        Session session = new Session();
        session.setTokenHash(hashToken(token));
        session.setEmail(gamer.getEmail());
        session.setExpiresAt(jwtService.extractExpiration(token));
        sessionRepository.save(session);
        return token;
    }

    /** Creates a fresh code, invalidates any predecessors, and mails it out. */
    private void issueAndSendCode(String email, boolean forRegistration) {
        verificationCodeRepository.invalidateAllForEmail(email);

        int code = 100000 + RANDOM.nextInt(900000);
        Instant now = Instant.now();

        VerificationCode verification = new VerificationCode();
        verification.setCode(code);
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
     * <p><strong>Nothing listens to this yet, and the recommender is therefore stale until
     * the next retrain.</strong> The Javadoc here used to point at a
     * {@code RecommenderRefreshListener} that does not exist in this codebase — it was
     * planned and never written — which is also why this line does not compile in an IDE
     * that resolves {@code @link} targets.
     *
     * <p>Why it matters: {@code /predict} ranks from features baked into the pickled
     * artefact at training time, and takes only a user id. {@code DefaultMatchService}
     * falls back to {@code /predict/cold-start} — the path that reads live games and
     * keywords — only when {@code /predict} returns an <em>empty</em> list. A gamer the
     * model already knows returns a non-empty ranking, so editing their profile changes
     * nothing about who they are shown until the artefact is rebuilt.
     *
     * <p>The event is left in place because it is the right signal and the publishers are
     * correct; what is missing is a consumer. The cheap fix is to mark the gamer so the
     * feed treats them as cold-start until the next training run, which reuses machinery
     * that already exists rather than retraining per edit.
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
