package com.gamebuddy.billing.domain;

import com.gamebuddy.billing.infrastructure.entity.PromoCode;
import com.gamebuddy.billing.infrastructure.entity.PromoCodeAssignment;
import com.gamebuddy.billing.infrastructure.entity.PromoCodeKind;
import com.gamebuddy.billing.infrastructure.entity.PromoRedemption;
import com.gamebuddy.billing.infrastructure.repository.PromoCodeAssignmentRepository;
import com.gamebuddy.billing.infrastructure.repository.PromoCodeRepository;
import com.gamebuddy.billing.infrastructure.repository.PromoRedemptionRepository;
import com.gamebuddy.billing.interfaces.dto.MyPromoCodesResponseBody;
import com.gamebuddy.billing.interfaces.dto.PromoAssigneeDto;
import com.gamebuddy.billing.interfaces.dto.PromoCodeDto;
import com.gamebuddy.billing.interfaces.dto.RedeemPromoCodeResponseBody;
import com.gamebuddy.billing.interfaces.request.PromoCodeRequest;
import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.ratelimit.RateLimiter;
import com.gamebuddy.common.util.Constants;
import com.gamebuddy.shared.coin.CoinLedger;
import com.gamebuddy.shared.coin.CoinReason;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.event.NotificationKind;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.mail.EmailContent;
import com.gamebuddy.shared.mail.Mailer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Promotion codes: coins or Gold, given away rather than sold.
 *
 * <p>This is the one path into an entitlement that no money passed through, so it is worth
 * being explicit about why it does not reopen the hole {@code BillingController} closed
 * when it deleted the client-facing redeem endpoint. That endpoint let the app assert a
 * purchase; this one lets the app assert nothing at all. It presents a string, and every
 * decision — whether the string exists, whether it was addressed to this account, whether
 * there is a redemption left, and what it is worth — is made here against rows an
 * administrator wrote. The worst outcome of a forged request is a coupon somebody already
 * had.
 *
 * <h2>The order redemption happens in</h2>
 *
 * <p>Claim, then reserve, then pay — the shape {@code RewardedAdService} arrived at after a
 * replayed callback was measured paying twice. Both claims are single statements that the
 * database decides:
 *
 * <ol>
 *   <li>{@code PromoRedemptionRepository.claim} inserts (code, account) and reports whether
 *       this call is the one that got it. That is the once-per-account rule.
 *   <li>{@code PromoCodeRepository.reserveRedemption} increments the counter only while it
 *       is below the limit. That is the how-many-times rule, and it has to be a conditional
 *       UPDATE rather than a read and a write, or the last remaining use goes to both of
 *       two simultaneous redeemers.
 *   <li>Only then are coins credited or the membership extended. Failing the second step
 *       throws, which rolls the first one back — the person got nothing, so the record of
 *       them getting something must not survive.
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromoCodeService {

    /**
     * No {@code O}, {@code 0}, {@code I} or {@code 1}.
     *
     * <p>These codes get read off a screen, out of an email, and occasionally out loud, and
     * the four characters removed here are the ones that turn into each other on the way.
     * Thirty-one symbols over eight places is still about 1.5e12 codes, so nothing is
     * given up: the space is not what makes this safe to type, and the ambiguity is
     * absolutely what makes it annoying.
     */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int CODE_LENGTH = 8;

    /** Enough attempts that a collision is a non-event; more than one is already absurd. */
    private static final int GENERATION_ATTEMPTS = 5;

    private static final DateTimeFormatter EMAIL_DATE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final PromoCodeRepository codes;
    private final PromoCodeAssignmentRepository assignments;
    private final PromoRedemptionRepository redemptions;
    private final GamerRepository gamers;
    private final CoinLedger coins;
    private final PurchaseService purchases;
    private final Mailer mailer;
    private final Clock clock;
    private final RateLimiter promoRedeemRateLimiter;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    // --- administration ------------------------------------------------------

    /** Every code, newest first, with counts rather than recipient lists. */
    @Transactional(readOnly = true)
    public List<PromoCodeDto> list() {
        Instant now = clock.instant();
        return codes.findAllByOrderByCreatedAtDesc().stream()
                .map(code -> toDto(code, now, null))
                .toList();
    }

    /** One code, with who it was addressed to and who has used it. */
    @Transactional(readOnly = true)
    public PromoCodeDto get(UUID id) {
        PromoCode code = require(id);
        return toDto(code, clock.instant(), assigneesOf(code));
    }

    /**
     * Issues a code.
     *
     * <p>Does not send the email. Sending inside this transaction would hold it open across
     * a relay that is slow or dead, and a code that exists is worth more than a code that
     * was announced — see {@link #sendPending}, which the controller calls after the commit.
     */
    @Transactional
    public PromoCode create(Gamer admin, PromoCodeRequest request) {
        validate(request);
        List<Gamer> recipients = resolveRecipients(request.getAssigneeIds());

        Instant now = clock.instant();
        PromoCode code = new PromoCode();
        code.setId(UUID.randomUUID());
        code.setCode(chooseCode(request.getCode()));
        code.setKind(request.getKind());
        code.setCoinAmount(request.getKind() == PromoCodeKind.COIN ? request.getCoinAmount() : null);
        code.setGoldDays(request.getKind() == PromoCodeKind.GOLD ? request.getGoldDays() : null);
        code.setExpiresAt(now.plus(Duration.ofDays(request.getValidDays())));
        code.setMaxRedemptions(request.getMaxRedemptions());
        code.setCreatedBy(admin.getUserId());
        code.setNote(blankToNull(request.getNote()));
        code.setCreatedAt(now);
        code.setUpdatedAt(now);

        PromoCode saved;
        try {
            // Flushed here so a duplicate surfaces as a conflict rather than escaping at
            // commit time as a 500. The pre-check above catches the ordinary case; this
            // catches two administrators typing the same code at once.
            saved = codes.saveAndFlush(code);
        } catch (DataIntegrityViolationException duplicate) {
            throw new BusinessException(TransactionCode.PROMO_CODE_EXISTS);
        }

        recipients.forEach(recipient -> assign(saved, recipient, now));

        log.info(
                "Promotion code {} ({}) created by {} for {} recipients",
                saved.getCode(),
                saved.rewardText(),
                admin.getUserId(),
                recipients.size());
        return saved;
    }

    /**
     * Edits a code. Everything can change except the code string itself.
     *
     * <p>That one exception is not tidiness: the string may already be in somebody's inbox,
     * and renaming it would turn a gift that was sent into a code that does not exist, with
     * no way for the holder to find out why.
     *
     * <p>Recipients absent from the request are taken off the list — unless they have
     * already redeemed, in which case the assignment stays. Removing it would not take
     * anything back, it would only delete the record of who the code was for.
     */
    @Transactional
    public PromoCode update(UUID id, PromoCodeRequest request) {
        validate(request);
        PromoCode code = require(id);
        List<Gamer> recipients = resolveRecipients(request.getAssigneeIds());
        Instant now = clock.instant();

        if (request.getMaxRedemptions() != null && request.getMaxRedemptions() < code.getRedemptionCount()) {
            // Allowing this would leave the code permanently EXHAUSTED with a limit below
            // the number of people already holding what it gave them, which reads as a bug
            // in the counter rather than as a decision somebody made.
            throw new BusinessException(TransactionCode.INVALID_REQUEST);
        }

        code.setKind(request.getKind());
        code.setCoinAmount(request.getKind() == PromoCodeKind.COIN ? request.getCoinAmount() : null);
        code.setGoldDays(request.getKind() == PromoCodeKind.GOLD ? request.getGoldDays() : null);
        code.setExpiresAt(now.plus(Duration.ofDays(request.getValidDays())));
        code.setMaxRedemptions(request.getMaxRedemptions());
        code.setNote(blankToNull(request.getNote()));
        code.setDisabledAt(
                request.isDisabled() ? Optional.ofNullable(code.getDisabledAt()).orElse(now) : null);
        code.setUpdatedAt(now);
        codes.save(code);

        Set<String> wanted = recipients.stream().map(Gamer::getUserId).collect(Collectors.toSet());
        Set<String> alreadyRedeemed = redemptions.redeemerIds(id);
        assignments.findByIdCodeId(id).stream()
                .map(existing -> existing.getId().getUserId())
                .filter(userId -> !wanted.contains(userId))
                .filter(userId -> !alreadyRedeemed.contains(userId))
                .forEach(userId -> assignments.deleteByIdCodeIdAndIdUserId(id, userId));

        recipients.forEach(recipient -> assign(code, recipient, now));

        log.info("Promotion code {} edited; {} recipients", code.getCode(), recipients.size());
        return code;
    }

    /** Switches a code off, or back on. Reversible, and keeps every row. */
    @Transactional
    public PromoCode setDisabled(UUID id, boolean disabled) {
        PromoCode code = require(id);
        Instant now = clock.instant();
        code.setDisabledAt(disabled ? Optional.ofNullable(code.getDisabledAt()).orElse(now) : null);
        code.setUpdatedAt(now);
        codes.save(code);
        log.info("Promotion code {} {}", code.getCode(), disabled ? "disabled" : "re-enabled");
        return code;
    }

    /**
     * Deletes a code outright, with its recipient list and its redemption records.
     *
     * <p>Offered next to {@link #setDisabled} rather than instead of it, because they answer
     * different questions: disabling stops a campaign, deleting removes a mistake. Nothing
     * granted is taken back and nothing about the money is lost — coins live in
     * {@code coin_ledger} under {@link CoinReason#PROMO_CODE} and Gold lives on the gamer's
     * own expiry. What goes is the record of the offer, which is the thing an administrator
     * is asking to be rid of.
     */
    @Transactional
    public void delete(UUID id) {
        PromoCode code = require(id);
        int taken = code.getRedemptionCount();
        codes.delete(code);
        log.info("Promotion code {} deleted, discarding {} redemption records", code.getCode(), taken);
    }

    /**
     * Emails the code to every recipient who has not been sent it yet.
     *
     * <p>Called after the transaction that wrote the rows has committed, and never from
     * inside one: a relay that hangs would otherwise hold a write transaction open for its
     * whole timeout, and a relay that fails would roll back a code that had already been
     * created perfectly well.
     *
     * <p>Each failure is counted rather than thrown. The code exists and works whether or
     * not the message arrived, and the console says how many got through so an administrator
     * can pass the rest on another way.
     */
    public PromoCodeDto.EmailOutcome sendPending(UUID codeId) {
        PromoCode code = codes.findById(codeId).orElse(null);
        if (code == null) {
            return new PromoCodeDto.EmailOutcome(0, 0);
        }

        List<PromoCodeAssignment> pending = assignments.findByIdCodeIdAndEmailedAtIsNull(codeId);
        if (pending.isEmpty()) {
            return new PromoCodeDto.EmailOutcome(0, 0);
        }

        Map<String, Gamer> byId =
                gamers
                        .findAllById(pending.stream()
                                .map(assignment -> assignment.getId().getUserId())
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Gamer::getUserId, Function.identity()));

        EmailContent content = EmailContent.withCode(
                String.format(Constants.EMAIL_SUBJECT_PROMO_CODE, capitalise(code.rewardText())),
                Constants.EMAIL_PREHEADER_PROMO_CODE,
                Constants.EMAIL_HEADING_PROMO_CODE,
                String.format(Constants.EMAIL_INTRO_PROMO_CODE, code.rewardText()),
                code.getCode(),
                String.format(Constants.EMAIL_CODE_CAPTION_PROMO_CODE, EMAIL_DATE.format(code.getExpiresAt())),
                Constants.EMAIL_FOOTNOTE_PROMO_CODE);

        int sent = 0;
        int failed = 0;
        for (PromoCodeAssignment assignment : pending) {
            Gamer recipient = byId.get(assignment.getId().getUserId());
            if (recipient == null || recipient.getEmail() == null) {
                failed++;
                continue;
            }
            try {
                mailer.send(recipient.getEmail(), content);
                assignments.markEmailed(codeId, recipient.getUserId(), clock.instant());
                sent++;
            } catch (MailException undelivered) {
                // Left unmarked on purpose, so a later edit with "send by email" ticked
                // picks this recipient up again rather than skipping them forever.
                log.warn(
                        "Promotion code {} could not be mailed to {}",
                        code.getCode(),
                        recipient.getUserId(),
                        undelivered);
                failed++;
            }
        }
        return new PromoCodeDto.EmailOutcome(sent, failed);
    }

    // --- the gamer's own screen ---------------------------------------------

    /** What is waiting for this account, and what it has already used. */
    @Transactional(readOnly = true)
    public MyPromoCodesResponseBody mine(String userId) {
        Instant now = clock.instant();
        List<PromoRedemption> history = redemptions.findByIdUserIdOrderByCreatedAtDesc(userId);

        // One fetch for every code in the history rather than one per row. A gamer with a
        // dozen redemptions is not a performance problem, but a lookup inside a map() is
        // how a screen quietly becomes a dozen round trips, and this one is on the path
        // that opens whenever somebody checks what is waiting.
        Map<UUID, String> names =
                codes
                        .findAllById(history.stream()
                                .map(row -> row.getId().getCodeId())
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(PromoCode::getId, PromoCode::getCode));

        Set<String> used = new HashSet<>();
        List<MyPromoCodesResponseBody.RedeemedCode> redeemed = history.stream()
                .peek(row -> used.add(row.getId().getCodeId().toString()))
                .map(row -> new MyPromoCodesResponseBody.RedeemedCode(
                        names.getOrDefault(row.getId().getCodeId(), ""),
                        row.getKind().name(),
                        row.getCoinAmount(),
                        row.getGoldDays(),
                        row.getCreatedAt()))
                .toList();

        List<MyPromoCodesResponseBody.WaitingCode> waiting = codes.findAssignedTo(userId, now).stream()
                .filter(code -> !used.contains(code.getId().toString()))
                .filter(code -> !code.isExhausted())
                .map(code -> new MyPromoCodesResponseBody.WaitingCode(
                        code.getId().toString(),
                        code.getCode(),
                        code.getKind().name(),
                        code.getCoinAmount(),
                        code.getGoldDays(),
                        code.getExpiresAt()))
                .toList();

        return new MyPromoCodesResponseBody(waiting, redeemed);
    }

    /**
     * Redeems a code for an account. See the class comment for why the steps are in this
     * order.
     */
    @Transactional
    public RedeemPromoCodeResponseBody redeem(String userId, String rawCode) {
        // First, and before any lookup: this is the only endpoint in the application where
        // guessing is the attack, and a limiter that runs after the lookup is a limiter
        // that has already told the guesser whether the code exists.
        if (!promoRedeemRateLimiter.tryAcquire(userId)) {
            throw new BusinessException(TransactionCode.RATE_LIMITED);
        }

        String normalised = normalise(rawCode);
        Instant now = clock.instant();

        // Reloaded rather than taken from the principal: the gamer carries an @Version, and
        // the copy attached to the request has been sitting in a security context since the
        // token was validated. Writing through it is how a stale version turns into a
        // spurious 409.
        Gamer gamer = gamers.findById(userId).orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));

        PromoCode code = codes.findByCode(normalised)
                .filter(found -> found.getDisabledAt() == null)
                .orElseThrow(() -> new BusinessException(TransactionCode.PROMO_CODE_INVALID));

        boolean addressed = assignments.countByIdCodeId(code.getId()) > 0;
        if (addressed && !assignments.existsByIdCodeIdAndIdUserId(code.getId(), userId)) {
            throw new BusinessException(TransactionCode.PROMO_CODE_NOT_YOURS);
        }
        if (!code.getExpiresAt().isAfter(now)) {
            throw new BusinessException(TransactionCode.PROMO_CODE_EXPIRED);
        }
        if (code.isExhausted()) {
            throw new BusinessException(TransactionCode.PROMO_CODE_EXHAUSTED);
        }

        Instant goldExpiresAt = code.getKind() == PromoCodeKind.GOLD
                ? purchases.extendGold(gamer, Duration.ofDays(code.getGoldDays()))
                : gamer.getSubscriptionExpiresAt();

        if (redemptions.claim(
                        code.getId(),
                        userId,
                        code.getKind().name(),
                        code.getCoinAmount(),
                        code.getGoldDays(),
                        code.getKind() == PromoCodeKind.GOLD ? goldExpiresAt : null,
                        now)
                == 0) {
            throw new BusinessException(TransactionCode.PROMO_CODE_ALREADY_REDEEMED);
        }

        if (codes.reserveRedemption(code.getId(), now) == 0) {
            // Somebody took the last use between the check above and here, or an
            // administrator switched the code off in the same moment. Throwing rolls the
            // claim back, which is exactly right: nothing was granted.
            throw new BusinessException(TransactionCode.PROMO_CODE_EXHAUSTED);
        }

        if (code.getKind() == PromoCodeKind.COIN) {
            coins.earn(gamer, code.getCoinAmount(), CoinReason.PROMO_CODE);
        }
        gamers.save(gamer);

        log.info("Promotion code {} redeemed by {}", code.getCode(), userId);
        return new RedeemPromoCodeResponseBody(
                code.getKind().name(),
                code.getCoinAmount(),
                code.getGoldDays(),
                gamer.getCoin(),
                gamer.getSubscriptionExpiresAt());
    }

    // --- internals -----------------------------------------------------------

    private PromoCode require(UUID id) {
        return codes.findById(id).orElseThrow(() -> new BusinessException(TransactionCode.PROMO_CODE_INVALID));
    }

    /**
     * Cross-field rules the annotations cannot express: the payload has to match the kind,
     * and there is nobody to email a public code to.
     */
    private void validate(PromoCodeRequest request) {
        boolean payloadMatchesKind = request.getKind() == PromoCodeKind.COIN
                ? request.getCoinAmount() != null
                : request.getGoldDays() != null;
        if (!payloadMatchesKind) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST);
        }
        if (request.isSendEmail()
                && (request.getAssigneeIds() == null || request.getAssigneeIds().isEmpty())) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST);
        }
    }

    /**
     * Turns the requested ids into accounts that can actually receive a code.
     *
     * <p>Deleted, banned and staff accounts are refused rather than skipped. The console
     * picks recipients from a list that already excludes all three, so an id that is none
     * of those things means the picker and the database disagree — and silently dropping
     * somebody from a campaign is the kind of thing nobody notices until they ask why one
     * person never got their coins.
     */
    private List<Gamer> resolveRecipients(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>(ids);
        List<Gamer> found = gamers.findAllById(unique);
        if (found.size() != unique.size()) {
            throw new BusinessException(TransactionCode.PROMO_USER_NOT_FOUND);
        }
        found.forEach(gamer -> {
            boolean receivable = gamer.getDeletedAt() == null
                    && !Boolean.TRUE.equals(gamer.getIsBlocked())
                    && gamer.getRole() != Role.ADMIN;
            if (!receivable) {
                throw new BusinessException(TransactionCode.PROMO_USER_NOT_FOUND);
            }
        });
        return found;
    }

    /**
     * Addresses the code to one account and tells them, once.
     *
     * <p>The push only goes out when the assignment is new — {@code add} returning 0 means
     * this recipient was already on the list — so editing a code five times does not
     * notify its recipients five times about the same gift.
     */
    private void assign(PromoCode code, Gamer recipient, Instant now) {
        if (assignments.add(code.getId(), recipient.getUserId(), now) == 0) {
            return;
        }
        if (recipient.getFcmToken() == null || recipient.getFcmToken().isBlank()) {
            return;
        }
        events.publishEvent(new NotificationRequestedEvent(
                recipient.getUserId(),
                recipient.getFcmToken(),
                "You have a promotion code",
                code.rewardText() + " is waiting in Settings.",
                NotificationKind.PROMO,
                null));
        assignments.markNotified(code.getId(), recipient.getUserId(), now);
    }

    /** The requested code, uppercased and checked, or a fresh one. */
    private String chooseCode(String requested) {
        if (requested != null && !requested.isBlank()) {
            String normalised = normalise(requested);
            if (codes.existsByCode(normalised)) {
                throw new BusinessException(TransactionCode.PROMO_CODE_EXISTS);
            }
            return normalised;
        }
        for (int attempt = 0; attempt < GENERATION_ATTEMPTS; attempt++) {
            String candidate = generate();
            if (!codes.existsByCode(candidate)) {
                return candidate;
            }
        }
        // Five collisions in a row against a 1.5e12 space is not a busy table, it is a
        // broken random source. Failing loudly beats handing out a code that is not unique.
        throw new BusinessException(TransactionCode.PROMO_CODE_EXISTS);
    }

    private String generate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int position = 0; position < CODE_LENGTH; position++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * What a person typed, turned into what is stored: uppercase, no spaces, no dashes.
     *
     * <p>WELCOME-2026 and welcome 2026 are the same coupon to everybody except a string
     * comparison, and a code is usually being copied from somewhere that punctuated it
     * differently.
     */
    private String normalise(String raw) {
        return raw == null ? "" : raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String capitalise(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private List<PromoAssigneeDto> assigneesOf(PromoCode code) {
        List<PromoCodeAssignment> rows = assignments.findByIdCodeId(code.getId());
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<String> redeemed = redemptions.redeemerIds(code.getId());
        Map<String, Gamer> byId =
                gamers
                        .findAllById(rows.stream()
                                .map(row -> row.getId().getUserId())
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Gamer::getUserId, Function.identity()));

        List<PromoAssigneeDto> people = new ArrayList<>(rows.size());
        rows.forEach(row -> {
            String userId = row.getId().getUserId();
            Gamer gamer = byId.get(userId);
            people.add(new PromoAssigneeDto(
                    userId,
                    gamer == null ? null : gamer.getGamerUsername(),
                    gamer == null ? null : gamer.getEmail(),
                    row.getEmailedAt(),
                    redeemed.contains(userId)));
        });
        people.sort(
                Comparator.comparing(PromoAssigneeDto::getUsername, Comparator.nullsLast(Comparator.naturalOrder())));
        return people;
    }

    /** @param people null on the list screen, where recipients are counted rather than named */
    public PromoCodeDto toDto(PromoCode code, Instant now, List<PromoAssigneeDto> people) {
        List<PromoCodeAssignment> rows = people == null ? assignments.findByIdCodeId(code.getId()) : List.of();
        int assigneeCount = people == null ? rows.size() : people.size();
        int emailedCount = people == null
                ? (int) rows.stream().filter(row -> row.getEmailedAt() != null).count()
                : (int) people.stream()
                        .filter(row -> row.getEmailedAt() != null)
                        .count();

        return PromoCodeDto.builder()
                .id(code.getId().toString())
                .code(code.getCode())
                .kind(code.getKind().name())
                .coinAmount(code.getCoinAmount())
                .goldDays(code.getGoldDays())
                .expiresAt(code.getExpiresAt())
                .maxRedemptions(code.getMaxRedemptions())
                .redemptionCount(code.getRedemptionCount())
                .assigneeCount(assigneeCount)
                .emailedCount(emailedCount)
                .disabledAt(code.getDisabledAt())
                .status(code.status(now).name())
                .note(code.getNote())
                .createdAt(code.getCreatedAt())
                .assignees(people)
                .build();
    }

    /** The DTO for one code, with recipients, as the write endpoints answer. */
    @Transactional(readOnly = true)
    public PromoCodeDto detail(UUID id, PromoCodeDto.EmailOutcome emailed) {
        PromoCode code = require(id);
        PromoCodeDto dto = toDto(code, clock.instant(), assigneesOf(code));
        dto.setEmailed(emailed);
        return dto;
    }
}
