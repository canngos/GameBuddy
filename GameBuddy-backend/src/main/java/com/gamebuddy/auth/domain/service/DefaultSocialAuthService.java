package com.gamebuddy.auth.domain.service;

import com.gamebuddy.auth.config.AccountLinkProperties;
import com.gamebuddy.auth.config.SocialAuthProperties;
import com.gamebuddy.auth.infrastructure.client.DiscordClient;
import com.gamebuddy.auth.infrastructure.client.GoogleIdTokenVerifier;
import com.gamebuddy.auth.infrastructure.entity.GamerAuthIdentity;
import com.gamebuddy.auth.infrastructure.entity.SocialLoginTicket;
import com.gamebuddy.auth.infrastructure.repository.GamerAuthIdentityRepository;
import com.gamebuddy.auth.infrastructure.repository.SocialLoginTicketRepository;
import com.gamebuddy.auth.interfaces.dto.SocialIdentitiesResponseBody;
import com.gamebuddy.auth.interfaces.dto.SocialSessionResponseBody;
import com.gamebuddy.common.enums.AuthProvider;
import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.security.TokenHashing;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.funnel.LikeCapCohort;
import com.gamebuddy.shared.repository.GamerRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Signing in with Google or Discord.
 *
 * <h2>The two shapes, and why they differ</h2>
 *
 * <p><b>Google is one request.</b> The app opens the platform's own account sheet, receives an
 * ID token signed by Google, and posts it here. Nothing leaves the app, nothing is stored
 * mid-flight, and the token proves both who the person is and that Google said so.
 *
 * <p><b>Discord is a browser round trip</b>, because Discord has no such sheet. The user
 * leaves for the system browser and comes back to a callback with no session on it — the same
 * problem, and the same solution, as profile linking next door in
 * {@link DefaultAccountLinkService}. What differs is that a link ticket knows which account
 * it belongs to and a login ticket cannot: deciding that is the whole job here.
 *
 * <h2>Who an identity becomes</h2>
 *
 * <p>See {@link #resolve}. In one line: a known identity signs in, an unknown one attaches to
 * the account holding the same <em>verified</em> address, and otherwise a new account is
 * created — but only if the terms were agreed to first.
 *
 * <h2>What is not stored</h2>
 *
 * <p>No OAuth tokens, exactly as for linking. Identity is read once and the token is dropped.
 * Keeping it would create a credential worth stealing in exchange for nothing this feature
 * needs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultSocialAuthService implements SocialAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TICKET_BYTES = 32;

    /** Long enough for a consent screen, short enough that an abandoned attempt closes. */
    private static final Duration TICKET_TTL = Duration.ofMinutes(10);

    private final GamerRepository gamerRepository;
    private final GamerAuthIdentityRepository identityRepository;
    private final SocialLoginTicketRepository ticketRepository;
    private final DiscordClient discordClient;
    private final Optional<GoogleIdTokenVerifier> googleVerifier;
    private final SocialAuthProperties properties;
    private final AccountLinkProperties linkProperties;
    private final SessionIssuer sessionIssuer;
    private final AuthRateLimiters rateLimiters;
    private final Clock clock;

    // =======================================================================
    // What is on offer
    // =======================================================================

    @Override
    @Transactional(readOnly = true)
    public List<String> availableProviders() {
        List<String> configured = new ArrayList<>();
        if (googleVerifier.isPresent() && properties.googleConfigured()) {
            configured.add(AuthProvider.GOOGLE.name());
        }
        if (linkProperties.discordConfigured()) {
            configured.add(AuthProvider.DISCORD.name());
        }
        return configured;
    }

    // =======================================================================
    // Google
    // =======================================================================

    @Override
    @Transactional
    public SocialSessionResponseBody signInWithGoogle(String idToken, Boolean acceptedTerms) {
        // Keyed by the token rather than by an account, because at this point there is no
        // account to key by. A hash of the credential is stable for as long as one token is
        // being retried and tells us nothing we would want to keep.
        throttle(TokenHashing.sha256Hex(idToken == null ? "" : idToken));

        GoogleIdTokenVerifier verifier = googleVerifier.orElseThrow(() -> {
            // Refused here rather than at Google, for the same reason an unconfigured
            // Discord is: a button whose only outcome is an error reads as a broken app.
            log.warn("Google sign-in was attempted but no client id is configured");
            return new BusinessException(TransactionCode.SOCIAL_TOKEN_INVALID, "Google sign-in is not configured");
        });

        GoogleIdTokenVerifier.GoogleIdentity identity = verifier.verify(idToken);
        return resolve(
                AuthProvider.GOOGLE, identity.subject(), identity.email(), identity.emailVerified(), acceptedTerms);
    }

    // =======================================================================
    // Discord
    // =======================================================================

    @Override
    @Transactional
    public String startDiscordLogin() {
        requireDiscordConfigured();
        String token = mintTicket(AuthProvider.DISCORD, null, null, null, null);
        return UriComponentsBuilder.fromUriString(linkProperties.getDiscord().getAuthorizeUrl())
                .queryParam("client_id", linkProperties.getDiscord().getClientId())
                .queryParam("response_type", "code")
                // One scope more than profile linking asks for. The address is what lets
                // somebody who registered with a password land on their existing account
                // instead of a second one.
                .queryParam("scope", "identify email")
                .queryParam("redirect_uri", callbackUrl())
                .queryParam("state", token)
                .encode()
                .build()
                .toUriString();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Every failure ends the same way: a redirect carrying a status the app can explain.
     * Nothing here throws to the caller, because the caller is a browser — an exception would
     * put a JSON error page in front of somebody who expected to be back in the app.
     */
    @Override
    @Transactional
    public String completeDiscordLogin(String code, String state) {
        try {
            if (isBlank(code)) {
                // The Cancel button. Answered before the ticket is spent, so a change of mind
                // costs nothing and the button works again immediately.
                log.debug("Discord sign-in was declined");
                return returnUrl("cancelled", null);
            }

            SocialLoginTicket outbound = spendOutbound(state);

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", linkProperties.getDiscord().getClientId());
            form.add("client_secret", linkProperties.getDiscord().getClientSecret());
            form.add("grant_type", "authorization_code");
            form.add("code", code);
            // Byte-identical to the one sent to the authorize endpoint; Discord compares them.
            form.add("redirect_uri", callbackUrl());

            String accessToken = discordClient.exchangeCode(form).accessToken();
            DiscordClient.DiscordUser user = discordClient.currentUser("Bearer " + accessToken);

            if (isBlank(user.email()) || !Boolean.TRUE.equals(user.verified())) {
                // Refused before anything is written. An unverified address is exactly the
                // takeover vector that `resolve` guards against, and finding out here means
                // the app can say so rather than showing a generic failure.
                log.info("Discord sign-in refused: the account's email is not verified");
                return returnUrl("email_unverified", null);
            }

            String display = isBlank(user.globalName()) ? user.username() : user.globalName();
            // A second ticket, minted now. The one that travelled through Discord and the
            // browser is spent; this one has only ever existed here and in the redirect the
            // app itself receives.
            String inbound = mintTicket(AuthProvider.DISCORD, user.id(), user.email(), Boolean.TRUE, display);
            log.info("Discord sign-in verified for subject {}", outbound.getId());
            return returnUrl("ok", inbound);
        } catch (BusinessException e) {
            log.info("Discord sign-in failed: {}", e.getMessage());
            return returnUrl("failed", null);
        } catch (RuntimeException e) {
            // Discord unreachable, a malformed response, a timeout. The detail stays here
            // rather than going into a URL.
            log.warn("Discord sign-in failed unexpectedly", e);
            return returnUrl("failed", null);
        }
    }

    @Override
    @Transactional
    public SocialSessionResponseBody exchange(String ticketToken, Boolean acceptedTerms) {
        throttle(TokenHashing.sha256Hex(ticketToken == null ? "" : ticketToken));
        if (isBlank(ticketToken)) {
            throw new BusinessException(TransactionCode.SOCIAL_TOKEN_INVALID, "no ticket");
        }

        String hash = TokenHashing.sha256Hex(ticketToken);
        SocialLoginTicket ticket = ticketRepository
                .findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(TransactionCode.SOCIAL_TOKEN_INVALID, "unknown ticket"));
        if (!ticket.isRedeemable(clock.instant())) {
            throw new BusinessException(TransactionCode.SOCIAL_TOKEN_INVALID, "ticket is spent or expired");
        }

        // Resolved *before* the ticket is spent, and deliberately. A brand-new account needs
        // the terms accepted, and the app cannot know that until the server has said so —
        // so the first attempt legitimately fails with TERMS_NOT_ACCEPTED and the second one
        // carries the tick. Spending first would make that second attempt impossible and the
        // consent sheet would be a dead end.
        SocialSessionResponseBody session = resolve(
                ticket.getProvider(),
                ticket.getSubject(),
                ticket.getEmail(),
                Boolean.TRUE.equals(ticket.getEmailVerified()),
                acceptedTerms);

        // Spent only once everything has succeeded. The database decides who spends it, so
        // two requests carrying the same ticket cannot both mint a session.
        if (ticketRepository.spend(hash) != 1) {
            throw new BusinessException(TransactionCode.SOCIAL_TOKEN_INVALID, "ticket already spent");
        }
        return session;
    }

    // =======================================================================
    // Who this identity becomes
    // =======================================================================

    /**
     * The one decision this whole feature exists to make.
     *
     * <p>In order, and the order is the security argument:
     *
     * <ol>
     *   <li><b>A known identity signs in.</b> Nothing else is consulted — not the address,
     *       which people change, and not the terms, which this account agreed to already.
     *       An account that never finished onboarding is <em>not</em> refused the way
     *       password login refuses it: social sign-in is how somebody gets back to a
     *       half-finished account, and the client lands them in onboarding from the stage on
     *       their profile.
     *   <li><b>An unverified address is refused</b>, before anything is written. Attaching by
     *       address is only safe when somebody else has already proved the mailbox; without
     *       that, anybody who can set an address at a provider could claim any account here.
     *   <li><b>A verified address attaches to the account holding it.</b> This is what makes
     *       "I registered with a password, then tapped Continue with Google" land on one
     *       account rather than two. An <em>unverified</em> GameBuddy account is attached to
     *       as well, and its password is cleared — the same rule registration already applies
     *       when re-claiming one: whoever set that password never proved the mailbox, and
     *       leaving it would hand a squatter a working login.
     *   <li><b>Otherwise a new account</b>, and only with the terms accepted.
     * </ol>
     */
    private SocialSessionResponseBody resolve(
            AuthProvider provider, String subject, String email, boolean emailVerified, Boolean acceptedTerms) {

        if (isBlank(subject)) {
            throw new BusinessException(TransactionCode.SOCIAL_TOKEN_INVALID, "no subject");
        }

        Instant now = clock.instant();
        String address = email == null ? null : email.trim().toLowerCase(Locale.ROOT);

        Optional<GamerAuthIdentity> existing = identityRepository.findByProviderAndSubject(provider, subject);
        if (existing.isPresent()) {
            Gamer gamer = gamerRepository
                    .findById(existing.get().getUserId())
                    .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));
            refuseIfUnusable(gamer);

            GamerAuthIdentity identity = existing.get();
            identity.setLastUsedAt(now);
            identityRepository.save(identity);
            return session(gamer, false);
        }

        if (!emailVerified || isBlank(address)) {
            throw new BusinessException(TransactionCode.SOCIAL_EMAIL_UNVERIFIED);
        }

        Gamer byEmail = gamerRepository.findByEmail(address).orElse(null);
        if (byEmail != null) {
            refuseIfUnusable(byEmail);
            if (!Boolean.TRUE.equals(byEmail.getIsVerified())) {
                // Re-claiming an unverified account, exactly as `register` does. The mailbox
                // has now been proved by the provider, which is more than the password on it
                // ever was.
                log.info("Attaching {} to an unverified account and clearing its password", provider);
                byEmail.setPwd(null);
                byEmail.setIsVerified(true);
                byEmail.setTermsAcceptedAt(now);
                byEmail.setTermsVersion(TermsPolicy.CURRENT_VERSION);
                // Anything issued to whoever was holding that password stops working.
                byEmail.revokeIssuedTokens();
                gamerRepository.save(byEmail);
            }
            attach(byEmail.getUserId(), provider, subject, address, now);
            log.info("{} identity attached to existing account", provider);
            return session(byEmail, false);
        }

        TermsPolicy.requireAcceptance(acceptedTerms);

        Gamer gamer = new Gamer();
        gamer.setUserId(java.util.UUID.randomUUID().toString());
        gamer.setEmail(address);
        gamer.setRole(Role.USER);
        gamer.setLikeCapCohort(LikeCapCohort.forUser(gamer.getUserId()));
        // No password at all, rather than a random hash: the app has to be able to tell
        // "Set a password" from "Change password", and a hash nobody knows makes that
        // unanswerable. The column is nullable for exactly this.
        gamer.setPwd(null);
        // The provider proved the mailbox, which is the whole job the six-digit code does
        // for a password registration. Sending a code here would be asking somebody to
        // prove an address Google has just vouched for.
        gamer.setIsVerified(true);
        gamer.setIsRegistered(false);
        gamer.setTermsAcceptedAt(now);
        gamer.setTermsVersion(TermsPolicy.CURRENT_VERSION);
        gamerRepository.save(gamer);

        attach(gamer.getUserId(), provider, subject, address, now);
        log.info("New account created from a {} sign-in", provider);
        return session(gamer, true);
    }

    /** The two states an account can be in that no sign-in may recover from. */
    private void refuseIfUnusable(Gamer gamer) {
        if (gamer.getDeletedAt() != null) {
            throw new BusinessException(TransactionCode.ACCOUNT_DELETED);
        }
        if (!Boolean.TRUE.equals(gamer.getIsBlocked())) {
            return;
        }
        // A suspension has an end time and its own code; a ban has neither. A suspension
        // already served is lifted here, same as on the password path -- see
        // DefaultAuthService.refuseIfBlocked.
        Instant until = gamer.getSuspendedUntil();
        if (until == null) {
            throw new BusinessException(TransactionCode.USER_BLOCKED);
        }
        if (until.isAfter(clock.instant())) {
            throw new BusinessException(TransactionCode.ACCOUNT_SUSPENDED, SUSPENDED_UNTIL.format(until));
        }
        gamer.setIsBlocked(false);
        gamer.setSuspendedUntil(null);
        gamerRepository.save(gamer);
    }

    private static final java.time.format.DateTimeFormatter SUSPENDED_UNTIL =
            java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm 'UTC'").withZone(java.time.ZoneOffset.UTC);

    private void attach(String userId, AuthProvider provider, String subject, String email, Instant now) {
        GamerAuthIdentity identity = new GamerAuthIdentity();
        identity.setUserId(userId);
        identity.setProvider(provider);
        identity.setSubject(subject);
        identity.setEmailAtLink(email);
        identity.setCreatedAt(now);
        identity.setLastUsedAt(now);
        identityRepository.save(identity);
    }

    private SocialSessionResponseBody session(Gamer gamer, boolean newAccount) {
        return new SocialSessionResponseBody(sessionIssuer.issue(gamer), gamer.getUserId(), newAccount);
    }

    // =======================================================================
    // Managing the ways in
    // =======================================================================

    @Override
    @Transactional(readOnly = true)
    public SocialIdentitiesResponseBody identities(Gamer principal) {
        List<SocialIdentitiesResponseBody.Identity> identities =
                identityRepository.findByUserId(principal.getUserId()).stream()
                        .map(i -> new SocialIdentitiesResponseBody.Identity(
                                i.getProvider().name(), i.getEmailAtLink(), i.getCreatedAt()))
                        .toList();
        return new SocialIdentitiesResponseBody(identities, principal.getPassword() != null);
    }

    @Override
    @Transactional
    public DefaultMessageResponse unlink(Gamer principal, String provider) {
        AuthProvider target = AuthProvider.from(provider);
        if (target == null) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "unknown provider " + provider);
        }

        GamerAuthIdentity identity = identityRepository
                .findByUserIdAndProvider(principal.getUserId(), target)
                .orElseThrow(() -> new BusinessException(TransactionCode.ACCOUNT_NOT_LINKED));

        // The check that stops this being a lockout somebody had no reason to expect.
        // Re-read rather than trusted from the principal: the JWT filter loaded that object
        // in a persistence context that closed long ago.
        boolean hasPassword = gamerRepository
                .findById(principal.getUserId())
                .map(g -> g.getPassword() != null)
                .orElse(false);
        if (!hasPassword && identityRepository.countByUserId(principal.getUserId()) <= 1) {
            throw new BusinessException(TransactionCode.AUTH_IDENTITY_LAST);
        }

        identityRepository.delete(identity);
        log.info("{} sign-in removed for {}", target, principal.getUserId());
        return DefaultMessageResponse.of("Sign-in method removed.");
    }

    // =======================================================================
    // Tickets
    // =======================================================================

    private String mintTicket(
            AuthProvider provider, String subject, String email, Boolean emailVerified, String displayName) {
        byte[] raw = new byte[TICKET_BYTES];
        RANDOM.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);

        Instant now = clock.instant();
        SocialLoginTicket ticket = new SocialLoginTicket();
        ticket.setProvider(provider);
        ticket.setTokenHash(TokenHashing.sha256Hex(token));
        ticket.setSubject(subject);
        ticket.setEmail(email);
        ticket.setEmailVerified(emailVerified);
        ticket.setDisplayName(displayName);
        ticket.setUsed(false);
        ticket.setCreatedAt(now);
        ticket.setExpiresAt(now.plus(TICKET_TTL));
        ticketRepository.save(ticket);
        return token;
    }

    /**
     * Spends the ticket Discord echoed back as {@code state}.
     *
     * <p>Burned whether or not what follows succeeds: a ticket that survived a failed
     * callback would be a second go at the same callback, which is the thing single-use
     * exists to prevent.
     */
    private SocialLoginTicket spendOutbound(String token) {
        if (isBlank(token)) {
            throw new BusinessException(TransactionCode.SOCIAL_TOKEN_INVALID, "no state");
        }
        String hash = TokenHashing.sha256Hex(token);
        SocialLoginTicket ticket = ticketRepository
                .findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(TransactionCode.SOCIAL_TOKEN_INVALID, "unknown state"));
        if (ticket.getProvider() != AuthProvider.DISCORD) {
            throw new BusinessException(TransactionCode.SOCIAL_TOKEN_INVALID, "state is for another provider");
        }
        if (ticket.isExpired(clock.instant())) {
            throw new BusinessException(TransactionCode.SOCIAL_TOKEN_INVALID, "state has expired");
        }
        if (ticketRepository.spend(hash) != 1) {
            throw new BusinessException(TransactionCode.SOCIAL_TOKEN_INVALID, "state already spent");
        }
        return ticket;
    }

    /**
     * Drops tickets nobody can use.
     *
     * <p>Two are minted per sign-in, so this table grows twice as fast as the link-ticket
     * one. Hourly is more often than it needs to be and costs one indexed delete.
     */
    @Transactional
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    public void purgeExpiredTickets() {
        int removed = ticketRepository.deleteExpiredBefore(clock.instant().minus(TICKET_TTL));
        if (removed > 0) {
            log.debug("Purged {} expired social login tickets", removed);
        }
    }

    // =======================================================================
    // Helpers
    // =======================================================================

    private void throttle(String key) {
        if (!rateLimiters.social().tryAcquire(key)) {
            throw new BusinessException(TransactionCode.RATE_LIMITED);
        }
    }

    private void requireDiscordConfigured() {
        if (!linkProperties.discordConfigured()) {
            log.warn("Discord sign-in was requested but no credentials are configured");
            throw new BusinessException(TransactionCode.SOCIAL_TOKEN_INVALID, "Discord sign-in is not configured");
        }
    }

    private String callbackUrl() {
        return linkProperties.getPublicBaseUrl() + "/auth/social/discord/callback";
    }

    private String returnUrl(String status, String ticket) {
        UriComponentsBuilder url = UriComponentsBuilder.fromUriString(properties.getAppReturnUrl())
                .queryParam("provider", AuthProvider.DISCORD.name().toLowerCase(Locale.ROOT))
                .queryParam("status", status);
        if (ticket != null) {
            url.queryParam("ticket", ticket);
        }
        return url.build().toUriString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
