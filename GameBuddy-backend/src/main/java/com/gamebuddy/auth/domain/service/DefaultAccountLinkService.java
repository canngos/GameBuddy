package com.gamebuddy.auth.domain.service;

import com.gamebuddy.auth.config.AccountLinkProperties;
import com.gamebuddy.auth.infrastructure.client.DiscordClient;
import com.gamebuddy.auth.infrastructure.entity.AccountLinkTicket;
import com.gamebuddy.auth.infrastructure.repository.AccountLinkTicketRepository;
import com.gamebuddy.auth.interfaces.dto.LinkProvidersResponseBody;
import com.gamebuddy.auth.interfaces.dto.LinkStartResponseBody;
import com.gamebuddy.auth.interfaces.request.LinkVisibilityRequest;
import com.gamebuddy.auth.interfaces.response.LinkProvidersResponse;
import com.gamebuddy.auth.interfaces.response.LinkStartResponse;
import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.LinkVisibility;
import com.gamebuddy.common.enums.LinkedProvider;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.security.TokenHashing;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.entity.GamerLinkedAccount;
import com.gamebuddy.shared.moderation.TextAssessment;
import com.gamebuddy.shared.moderation.TextModerationService;
import com.gamebuddy.shared.moderation.TextSurface;
import com.gamebuddy.shared.repository.GamerLinkedAccountRepository;
import com.gamebuddy.shared.repository.GamerRepository;
import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
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
 * Proving that a gamer owns the Discord account they say they do.
 *
 * <h2>Why a round-trip at all</h2>
 *
 * <p>A text box would have been a tenth of this file. It would also have let anybody type
 * anybody's handle, and a profile that displays an unverified handle is not neutral about
 * it — it is making a claim on the account holder's behalf, to strangers, in the one place
 * they cannot correct it. Discord gives the verification away for free, so the only
 * thing the cheap version would have saved is this code.
 *
 * <h2>Why the browser, and what carries the identity through it</h2>
 *
 * <p>Discord will not authenticate inside the app. It is asking for a password, and a
 * password typed into a WebView the app controls is a password the app could have read — the address bar is the only thing that
 * tells somebody they are really on discord.com. So the user leaves, and comes back through
 * a callback that has no session and no token.
 *
 * <p>What travels in that URL is an {@link AccountLinkTicket}: single use, ten minutes,
 * bound server-side to one gamer and one provider, stored only as a hash. Not the JWT —
 * putting a seven-day credential in a redirect leaves it in browser history, in the
 * provider's logs, and in whatever referrer follows it around.
 *
 * <p>For Discord the ticket <em>is</em> the OAuth {@code state}. That is not two jobs in one
 * field: what {@code state} exists to prove is that the callback belongs to a flow this
 * server started, and an unguessable, server-bound, single-use token proves exactly that.
 *
 * <h2>The residual risk, stated plainly</h2>
 *
 * <p>Because the ticket is the only thing tying a callback to an account, somebody who
 * persuades a victim to open <em>their</em> authorize URL within ten minutes gets the
 * victim's handle displayed on the attacker's profile. This is inherent to system-browser
 * linking without a shared web session, and it is bounded rather than removed: tickets are
 * short and single-use, the unique index means the victim linking their own account
 * afterwards surfaces the collision, and the settings screen names the handle it just
 * linked. It gains an attacker a handle that is, in most cases, already on the victim's
 * profile. Accepted — and written down here so that it is accepted knowingly.
 *
 * <h2>What is not stored</h2>
 *
 * <p>No OAuth tokens. The {@code identify} scope buys one fact — who this is — and it is
 * read once at link time. Keeping the token would create a credential worth stealing and an
 * implied duty to refresh it, in exchange for saving the user a single tap through a consent
 * screen they have already agreed to.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAccountLinkService implements AccountLinkService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TICKET_BYTES = 32;

    /** Long enough to answer a consent screen, short enough that an abandoned one closes. */
    private static final Duration TICKET_TTL = Duration.ofMinutes(10);

    private final AccountLinkTicketRepository ticketRepository;
    private final GamerLinkedAccountRepository linkedAccountRepository;
    private final GamerRepository gamerRepository;
    private final DiscordClient discordClient;
    private final AccountLinkProperties properties;
    private final TextModerationService textModeration;
    private final AuthRateLimiters rateLimiters;

    /**
     * Warns when linking is switched on but still pointed at the emulator.
     *
     * <p>{@code public-base-url} defaults to {@code 10.0.2.2}, which is the host machine as
     * seen from inside an Android emulator and is reachable from nowhere else. A deployment
     * that sets the Discord credentials and forgets this one passes every configuration
     * check and then fails every link: Discord refuses the redirect_uri it was never told
     * about. Nothing else would say a word about it.
     *
     * <p>A warning rather than a refusal, because the default is right for the emulator and
     * that is where this gets developed.
     */
    @PostConstruct
    void warnIfBaseUrlIsUnreachable() {
        if (properties.discordConfigured() && properties.getPublicBaseUrl().contains("10.0.2.2")) {
            log.warn(
                    "Account linking is configured but public-base-url is still {} — the emulator"
                            + " address. Set BACKEND_PUBLIC_URL to a host the phone's browser can reach,"
                            + " and register the same origin with Discord.",
                    properties.getPublicBaseUrl());
        }
    }

    // =======================================================================
    // Starting a link
    // =======================================================================

    @Override
    @Transactional(readOnly = true)
    public LinkProvidersResponse availableProviders() {
        List<String> configured = Arrays.stream(LinkedProvider.values())
                .filter(this::isConfigured)
                .map(Enum::name)
                .toList();

        LinkProvidersResponse response = new LinkProvidersResponse();
        response.setBody(new BaseBody<>(new LinkProvidersResponseBody(configured)));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    @Override
    @Transactional
    public LinkStartResponse startLink(Gamer principal, String provider) {
        LinkedProvider target = requireProvider(provider);
        requireConfigured(target);

        // Keyed by account rather than by address: minting is authenticated, so the only
        // thing left to stop is one account filling the ticket table.
        if (!rateLimiters.link().tryAcquire(principal.getUserId())) {
            throw new BusinessException(TransactionCode.RATE_LIMITED);
        }

        // Anything left over from an attempt they walked away from stops being a way in.
        ticketRepository.burnAllFor(principal.getUserId(), target);
        String token = mintTicket(principal.getUserId(), target);

        // A switch over the enum rather than a ternary: adding a provider back has to be a
        // compile error here, not a silent redirect to the wrong login page.
        String url =
                switch (target) {
                    case DISCORD -> discordAuthorizeUrl(token);
                };
        log.info("Link started for {} on {}", principal.getUserId(), target);

        LinkStartResponse response = new LinkStartResponse();
        response.setBody(new BaseBody<>(new LinkStartResponseBody(url)));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    private String mintTicket(String userId, LinkedProvider provider) {
        byte[] raw = new byte[TICKET_BYTES];
        RANDOM.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);

        Instant now = Instant.now();
        AccountLinkTicket ticket = new AccountLinkTicket();
        ticket.setUserId(userId);
        ticket.setProvider(provider);
        ticket.setTokenHash(TokenHashing.sha256Hex(token));
        ticket.setUsed(false);
        ticket.setCreatedAt(now);
        ticket.setExpiresAt(now.plus(TICKET_TTL));
        ticketRepository.save(ticket);
        return token;
    }

    private String discordAuthorizeUrl(String ticket) {
        return UriComponentsBuilder.fromUriString(properties.getDiscord().getAuthorizeUrl())
                .queryParam("client_id", properties.getDiscord().getClientId())
                .queryParam("response_type", "code")
                // The narrowest scope Discord offers: an id and a display name. Not email,
                // not guilds, and nothing that can act as the user.
                .queryParam("scope", "identify")
                .queryParam("redirect_uri", callbackUrl(LinkedProvider.DISCORD))
                .queryParam("state", ticket)
                .encode()
                .build()
                .toUriString();
    }

    // =======================================================================
    // Finishing a link
    // =======================================================================

    /**
     * {@inheritDoc}
     *
     * <p>Every failure ends the same way: a redirect carrying a status the app can explain.
     * Nothing here throws to the caller, because the caller is a browser — an exception would
     * put a JSON error page in front of somebody who expected to be back in the app, with no
     * way onwards but the back button.
     */
    @Override
    @Transactional
    public String completeDiscordLink(String code, String state) {
        try {
            if (isBlank(code)) {
                // The Cancel button. Discord sends no code when consent is refused, and that
                // is somebody changing their mind rather than anything going wrong — so it
                // gets its own status and the app stays quiet, instead of showing a red "that
                // did not work, please try again" for a decision they made deliberately.
                //
                // Answered before the ticket is redeemed, so a cancelled attempt costs nothing
                // and the Link button works again immediately on a second thought.
                log.debug("Discord consent was declined");
                return returnUrl(LinkedProvider.DISCORD, "cancelled");
            }
            AccountLinkTicket ticket = redeem(state, LinkedProvider.DISCORD);

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", properties.getDiscord().getClientId());
            form.add("client_secret", properties.getDiscord().getClientSecret());
            form.add("grant_type", "authorization_code");
            form.add("code", code);
            // Must be byte-identical to the one sent to the authorize endpoint. Discord
            // compares them and refuses a mismatch, which is what the parameter is for.
            form.add("redirect_uri", callbackUrl(LinkedProvider.DISCORD));

            String accessToken = discordClient.exchangeCode(form).accessToken();
            DiscordClient.DiscordUser user = discordClient.currentUser("Bearer " + accessToken);

            // The display name if they have set one, the handle otherwise: the first is what
            // people see in a server, the second is what they are searched by.
            String display = isBlank(user.globalName()) ? user.username() : user.globalName();
            saveLink(ticket.getUserId(), LinkedProvider.DISCORD, user.id(), display);

            return returnUrl(LinkedProvider.DISCORD, "ok");
        } catch (BusinessException e) {
            log.info("Discord link failed: {}", e.getMessage());
            return returnUrl(LinkedProvider.DISCORD, statusFor(e));
        } catch (RuntimeException e) {
            // Discord unreachable, a malformed response, a timeout. The user is told to try
            // again; the detail stays here rather than going into the URL.
            log.warn("Discord link failed unexpectedly", e);
            return returnUrl(LinkedProvider.DISCORD, "failed");
        }
    }

    /**
     * Spends a ticket.
     *
     * <p>Burned whether or not what follows succeeds. A ticket that survived a failed attempt
     * would be a second go at the same callback with the same state, which is the thing
     * single-use exists to prevent.
     */
    private AccountLinkTicket redeem(String token, LinkedProvider provider) {
        if (isBlank(token)) {
            throw new BusinessException(TransactionCode.ACCOUNT_LINK_FAILED, "no ticket");
        }
        String hash = TokenHashing.sha256Hex(token);
        AccountLinkTicket ticket = ticketRepository
                .findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(TransactionCode.ACCOUNT_LINK_FAILED, "unknown ticket"));

        // A ticket minted for another provider is refused rather than accepted: two providers'
        // callbacks verify entirely different things, and one that worked at either would let
        // the weaker check stand in for the stronger. Checked before it is spent, so one
        // provider's callback cannot burn a ticket pending at another.
        if (ticket.getProvider() != provider) {
            throw new BusinessException(TransactionCode.ACCOUNT_LINK_FAILED, "ticket is for another provider");
        }
        if (ticket.isExpired()) {
            throw new BusinessException(TransactionCode.ACCOUNT_LINK_FAILED, "ticket has expired");
        }

        // The database decides who spends it. Reading `used` and then setting it leaves a gap
        // that two concurrent callbacks both pass through — a double-submitted browser request
        // is enough to reach it, no attacker required — and single use is the property this
        // whole flow rests on. Exactly one caller sees a row count of 1.
        if (ticketRepository.spend(hash) != 1) {
            throw new BusinessException(TransactionCode.ACCOUNT_LINK_FAILED, "ticket already spent");
        }
        return ticket;
    }

    // =======================================================================
    // Writing the link
    // =======================================================================

    private void saveLink(String userId, LinkedProvider provider, String externalId, String rawHandle) {
        Optional<GamerLinkedAccount> claimedElsewhere =
                linkedAccountRepository.findByProviderAndExternalId(provider, externalId);
        if (claimedElsewhere.isPresent()
                && !claimedElsewhere.get().getGamer().getUserId().equals(userId)) {
            throw new BusinessException(TransactionCode.ACCOUNT_ALREADY_LINKED);
        }

        Gamer gamer = gamerRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));

        GamerLinkedAccount link = linkedAccountRepository
                .findByGamer_UserIdAndProvider(userId, provider)
                .orElseGet(() -> {
                    GamerLinkedAccount fresh = new GamerLinkedAccount();
                    fresh.setGamer(gamer);
                    fresh.setProvider(provider);
                    fresh.setLinkedAt(Instant.now());
                    // The private end by default. A contact detail should not reach strangers
                    // because somebody never found the toggle.
                    fresh.setVisibility(LinkVisibility.MATCHES);
                    return fresh;
                });

        link.setExternalId(externalId);
        link.setHandle(screenHandle(rawHandle, provider));
        link.setHandleRefreshedAt(Instant.now());
        linkedAccountRepository.save(link);

        log.info("{} linked for {}", provider, userId);
    }

    /**
     * Runs a provider-supplied handle past the same filter as anything else on a profile.
     *
     * <p>Verified does not mean harmless: a Discord display name is free text somebody chose,
     * and putting it on a profile publishes it to people who never agreed to read it.
     *
     * <p>A handle that does not come back untouched is dropped rather than stored masked. The
     * masked form would be a name the person is not called, printed under a badge asserting
     * that we checked — a worse outcome than the badge on its own. The link survives either
     * way: what was verified is the account, and that is still true.
     */
    private String screenHandle(String rawHandle, LinkedProvider provider) {
        if (isBlank(rawHandle)) {
            return null;
        }
        TextAssessment assessment = textModeration.screen(rawHandle, TextSurface.PUBLIC);
        if (assessment.blocked() || assessment.modified()) {
            log.info("{} handle withheld from display after screening", provider);
            return null;
        }
        return assessment.cleaned();
    }

    // =======================================================================
    // Managing an existing link
    // =======================================================================

    @Override
    @Transactional
    public DefaultMessageResponse unlink(Gamer principal, String provider) {
        LinkedProvider target = requireProvider(provider);
        requireLink(principal.getUserId(), target);
        linkedAccountRepository.deleteByGamer_UserIdAndProvider(principal.getUserId(), target);
        // Nothing to revoke at the provider, because no token was kept. Deleting the row is
        // the whole of "unlink" — the tidiest consequence of not storing one.
        log.info("{} unlinked for {}", target, principal.getUserId());
        return DefaultMessageResponse.of("Account unlinked.");
    }

    @Override
    @Transactional
    public DefaultMessageResponse setVisibility(Gamer principal, String provider, LinkVisibilityRequest request) {
        LinkedProvider target = requireProvider(provider);
        LinkVisibility visibility = LinkVisibility.from(request.getVisibility());
        if (visibility == null) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "unknown visibility");
        }
        GamerLinkedAccount link = requireLink(principal.getUserId(), target);
        link.setVisibility(visibility);
        linkedAccountRepository.save(link);
        return DefaultMessageResponse.of("Visibility updated.");
    }

    /**
     * Drops tickets nobody can use.
     *
     * <p>Every row here is unusable ten minutes after it is written, so without a sweep the
     * table grows by one row per link attempt for the life of the deployment. Hourly is more
     * often than it needs to be and costs one indexed delete.
     */
    @Transactional
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    public void purgeExpiredTickets() {
        int removed = ticketRepository.deleteExpiredBefore(Instant.now().minus(TICKET_TTL));
        if (removed > 0) {
            log.debug("Purged {} expired link tickets", removed);
        }
    }

    // =======================================================================
    // Helpers
    // =======================================================================

    private LinkedProvider requireProvider(String provider) {
        LinkedProvider target = LinkedProvider.from(provider);
        if (target == null) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "unknown provider " + provider);
        }
        return target;
    }

    /**
     * Refuses a provider this deployment has no credentials for.
     *
     * <p>A clone with no Discord application registered still starts and still runs every
     * other screen — but it must not hand somebody a consent URL that answers "invalid
     * client", because that failure looks like a broken app rather than an unconfigured one.
     */
    private void requireConfigured(LinkedProvider provider) {
        if (!isConfigured(provider)) {
            log.warn("{} linking was requested but no credentials are configured", provider);
            throw new BusinessException(TransactionCode.ACCOUNT_LINK_FAILED, provider + " linking is not configured");
        }
    }

    /**
     * Whether this deployment can link that provider at all.
     *
     * <p>A switch over the enum rather than a ternary, so a provider added back is a compile
     * error here instead of silently inheriting Discord's answer. Shared with
     * {@link #availableProviders()}, so what the app is offered and what the server will
     * accept cannot drift apart.
     */
    private boolean isConfigured(LinkedProvider provider) {
        return switch (provider) {
            case DISCORD -> properties.discordConfigured();
        };
    }

    private GamerLinkedAccount requireLink(String userId, LinkedProvider provider) {
        return linkedAccountRepository
                .findByGamer_UserIdAndProvider(userId, provider)
                .orElseThrow(() -> new BusinessException(TransactionCode.ACCOUNT_NOT_LINKED));
    }

    private String callbackUrl(LinkedProvider provider) {
        return properties.getPublicBaseUrl() + "/auth/link/" + lower(provider) + "/callback";
    }

    private String returnUrl(LinkedProvider provider, String status) {
        return UriComponentsBuilder.fromUriString(properties.getAppReturnUrl())
                .queryParam("provider", lower(provider))
                .queryParam("status", status)
                .build()
                .toUriString();
    }

    /**
     * What the app is told, which is less than what is logged.
     *
     * <p>Only the collision is named, because it is the only one the user can act on: unlink
     * it from the other account, or sign in to the right one. Every other failure ends in "try
     * again", and telling whoever is holding a forged callback which check refused them is
     * free help for them.
     */
    private String statusFor(BusinessException e) {
        return e.getTransactionCode() == TransactionCode.ACCOUNT_ALREADY_LINKED ? "already_linked" : "failed";
    }

    private static String lower(LinkedProvider provider) {
        return provider.name().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
