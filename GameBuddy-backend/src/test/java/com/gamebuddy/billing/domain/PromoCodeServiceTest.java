package com.gamebuddy.billing.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gamebuddy.billing.infrastructure.entity.PromoCode;
import com.gamebuddy.billing.infrastructure.entity.PromoCodeKind;
import com.gamebuddy.billing.infrastructure.entity.PromoCodeStatus;
import com.gamebuddy.billing.infrastructure.repository.PromoCodeAssignmentRepository;
import com.gamebuddy.billing.infrastructure.repository.PromoCodeRepository;
import com.gamebuddy.billing.infrastructure.repository.PromoRedemptionRepository;
import com.gamebuddy.billing.interfaces.dto.PromoCodeDto;
import com.gamebuddy.billing.interfaces.dto.RedeemPromoCodeResponseBody;
import com.gamebuddy.billing.interfaces.request.PromoCodeRequest;
import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.enums.SubscriptionTier;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.ratelimit.Budget;
import com.gamebuddy.common.ratelimit.RateLimiter;
import com.gamebuddy.shared.coin.CoinLedger;
import com.gamebuddy.shared.coin.CoinLedgerRepository;
import com.gamebuddy.shared.coin.CoinReason;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.mail.Mailer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailSendException;

/**
 * Issuing and redeeming promotion codes.
 *
 * <p>The redemption tests are the point of the file. Every one of them is about a race or a
 * repeat — the same tap arriving twice, the last remaining use going to two people — because
 * that is where a giveaway turns into an unlimited one, and because the two repositories
 * report a row count rather than throwing, which is exactly the shape a mock can lie about.
 * The stubs below return counts for that reason; telling a mock to throw would assert an
 * assumption about JPA rather than what the queries do.
 */
@DisplayName("PromoCodeService")
class PromoCodeServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");
    private static final String USER = "gamer-1";
    private static final String ADMIN_ID = "admin-1";
    private static final UUID CODE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private PromoCodeRepository codes;
    private PromoCodeAssignmentRepository assignments;
    private PromoRedemptionRepository redemptions;
    private GamerRepository gamers;
    private Mailer mailer;
    private ApplicationEventPublisher events;
    private RateLimiter limiter;
    private Gamer gamer;
    private Gamer admin;
    private PromoCodeService service;

    @BeforeEach
    void setUp() {
        codes = mock(PromoCodeRepository.class);
        assignments = mock(PromoCodeAssignmentRepository.class);
        redemptions = mock(PromoRedemptionRepository.class);
        gamers = mock(GamerRepository.class);
        mailer = mock(Mailer.class);
        events = mock(ApplicationEventPublisher.class);
        limiter = Budget.of(50, Duration.ofHours(1)).limiter();

        gamer = new Gamer();
        gamer.setUserId(USER);
        gamer.setEmail("player@example.com");
        gamer.setGamerUsername("player");
        gamer.setCoin(100);
        gamer.setSubscriptionTier(SubscriptionTier.BASIC);

        admin = new Gamer();
        admin.setUserId(ADMIN_ID);
        admin.setRole(Role.ADMIN);

        when(gamers.findById(USER)).thenReturn(Optional.of(gamer));
        when(codes.saveAndFlush(any(PromoCode.class))).thenAnswer(call -> call.getArgument(0));
        when(codes.save(any(PromoCode.class))).thenAnswer(call -> call.getArgument(0));
        when(assignments.add(any(), anyString(), any())).thenReturn(1);
        when(assignments.findByIdCodeId(any())).thenReturn(List.of());
        when(redemptions.redeemerIds(any())).thenReturn(Set.of());
        // 1 = this call won the row. See the class comment.
        when(redemptions.claim(any(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(1);
        when(codes.reserveRedemption(any(), any())).thenReturn(1);

        service = build();
    }

    private PromoCodeService build() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        // A real ledger over a mocked repository, and a real PurchaseService for the Gold
        // rule: stubbing either would make the coin and expiry assertions below pass
        // without a coin or a day moving.
        CoinLedger ledger = new CoinLedger(mock(CoinLedgerRepository.class), clock);
        PurchaseService purchases = new PurchaseService(
                mock(com.gamebuddy.billing.infrastructure.repository.PurchaseRepository.class), gamers, clock, ledger);
        return new PromoCodeService(
                codes, assignments, redemptions, gamers, ledger, purchases, mailer, clock, limiter, events);
    }

    private PromoCode coinCode(int amount) {
        PromoCode code = new PromoCode();
        code.setId(CODE_ID);
        code.setCode("GIFT2026");
        code.setKind(PromoCodeKind.COIN);
        code.setCoinAmount(amount);
        code.setExpiresAt(NOW.plus(Duration.ofDays(30)));
        code.setCreatedAt(NOW);
        code.setUpdatedAt(NOW);
        code.setCreatedBy(ADMIN_ID);
        when(codes.findByCode("GIFT2026")).thenReturn(Optional.of(code));
        when(codes.findById(CODE_ID)).thenReturn(Optional.of(code));
        return code;
    }

    private PromoCode goldCode(int days) {
        PromoCode code = coinCode(0);
        code.setKind(PromoCodeKind.GOLD);
        code.setCoinAmount(null);
        code.setGoldDays(days);
        return code;
    }

    private PromoCodeRequest request(PromoCodeKind kind) {
        PromoCodeRequest request = new PromoCodeRequest();
        request.setKind(kind);
        request.setValidDays(30);
        if (kind == PromoCodeKind.COIN) {
            request.setCoinAmount(500);
        } else {
            request.setGoldDays(30);
        }
        return request;
    }

    @Nested
    @DisplayName("creating")
    class Creating {

        @Test
        @DisplayName("a generated code avoids the characters people misread")
        void generatesAnUnambiguousCode() {
            PromoCode created = service.create(admin, request(PromoCodeKind.COIN));

            assertEquals(8, created.getCode().length());
            assertTrue(
                    created.getCode().chars().noneMatch(c -> "O0I1".indexOf(c) >= 0),
                    "generated " + created.getCode() + ", which contains a character that is read wrong");
            assertEquals(NOW.plus(Duration.ofDays(30)), created.getExpiresAt());
            assertEquals(ADMIN_ID, created.getCreatedBy());
        }

        @Test
        @DisplayName("a code typed with dashes is stored the way it will be compared")
        void normalisesACustomCode() {
            PromoCodeRequest request = request(PromoCodeKind.COIN);
            request.setCode("welcome-2026");

            assertEquals("WELCOME2026", service.create(admin, request).getCode());
        }

        @Test
        @DisplayName("a code somebody already used is refused rather than overwritten")
        void refusesADuplicate() {
            when(codes.existsByCode("WELCOME2026")).thenReturn(true);
            PromoCodeRequest request = request(PromoCodeKind.COIN);
            request.setCode("WELCOME2026");

            assertEquals(
                    TransactionCode.PROMO_CODE_EXISTS,
                    assertThrows(BusinessException.class, () -> service.create(admin, request))
                            .getTransactionCode());
        }

        @Test
        @DisplayName("the payload has to match the kind")
        void refusesAKindWithoutItsAmount() {
            PromoCodeRequest request = request(PromoCodeKind.COIN);
            request.setCoinAmount(null);

            assertEquals(
                    TransactionCode.INVALID_REQUEST,
                    assertThrows(BusinessException.class, () -> service.create(admin, request))
                            .getTransactionCode());
        }

        @Test
        @DisplayName("there is nobody to email a public code to")
        void refusesEmailWithoutRecipients() {
            PromoCodeRequest request = request(PromoCodeKind.COIN);
            request.setSendEmail(true);

            assertEquals(
                    TransactionCode.INVALID_REQUEST,
                    assertThrows(BusinessException.class, () -> service.create(admin, request))
                            .getTransactionCode());
        }

        @Test
        @DisplayName("a recipient who cannot receive anything is refused, not skipped")
        void refusesABannedRecipient() {
            gamer.setIsBlocked(true);
            when(gamers.findAllById(any())).thenReturn(List.of(gamer));
            PromoCodeRequest request = request(PromoCodeKind.COIN);
            request.setAssigneeIds(List.of(USER));

            assertEquals(
                    TransactionCode.PROMO_USER_NOT_FOUND,
                    assertThrows(BusinessException.class, () -> service.create(admin, request))
                            .getTransactionCode());
        }

        @Test
        @DisplayName("an id that matches nobody is refused")
        void refusesAnUnknownRecipient() {
            when(gamers.findAllById(any())).thenReturn(List.of());
            PromoCodeRequest request = request(PromoCodeKind.COIN);
            request.setAssigneeIds(List.of("ghost"));

            assertEquals(
                    TransactionCode.PROMO_USER_NOT_FOUND,
                    assertThrows(BusinessException.class, () -> service.create(admin, request))
                            .getTransactionCode());
        }

        @Test
        @DisplayName("a recipient with a device is told, once")
        void notifiesANewRecipient() {
            gamer.setFcmToken("device-token");
            when(gamers.findAllById(any())).thenReturn(List.of(gamer));
            PromoCodeRequest request = request(PromoCodeKind.COIN);
            request.setAssigneeIds(List.of(USER));

            service.create(admin, request);

            verify(events).publishEvent(any(Object.class));
            verify(assignments).markNotified(any(), eq(USER), any());
        }

        @Test
        @DisplayName("re-assigning somebody who is already on the list does not notify them again")
        void doesNotRepeatTheNotification() {
            gamer.setFcmToken("device-token");
            when(gamers.findAllById(any())).thenReturn(List.of(gamer));
            when(assignments.add(any(), anyString(), any())).thenReturn(0);
            PromoCodeRequest request = request(PromoCodeKind.COIN);
            request.setAssigneeIds(List.of(USER));

            service.create(admin, request);

            verify(events, never()).publishEvent(any(Object.class));
        }
    }

    @Nested
    @DisplayName("editing")
    class Editing {

        @Test
        @DisplayName("the limit cannot be dropped below what has already been redeemed")
        void refusesALimitBelowTheCount() {
            PromoCode code = coinCode(500);
            code.setRedemptionCount(3);
            PromoCodeRequest request = request(PromoCodeKind.COIN);
            request.setMaxRedemptions(2);

            assertEquals(
                    TransactionCode.INVALID_REQUEST,
                    assertThrows(BusinessException.class, () -> service.update(CODE_ID, request))
                            .getTransactionCode());
        }

        @Test
        @DisplayName("the code string is not editable, whatever the request says")
        void keepsTheCodeString() {
            coinCode(500);
            PromoCodeRequest request = request(PromoCodeKind.COIN);
            request.setCode("SOMETHINGELSE");

            assertEquals("GIFT2026", service.update(CODE_ID, request).getCode());
        }

        @Test
        @DisplayName("validity is counted from now, so editing does not extend by accident")
        void recountsValidityFromNow() {
            PromoCode code = coinCode(500);
            code.setExpiresAt(NOW.plus(Duration.ofDays(2)));
            PromoCodeRequest request = request(PromoCodeKind.COIN);
            request.setValidDays(10);

            assertEquals(
                    NOW.plus(Duration.ofDays(10)),
                    service.update(CODE_ID, request).getExpiresAt());
        }

        @Test
        @DisplayName("editing can switch a code back on")
        void reEnables() {
            PromoCode code = coinCode(500);
            code.setDisabledAt(NOW.minus(Duration.ofDays(1)));
            PromoCodeRequest request = request(PromoCodeKind.COIN);
            request.setDisabled(false);

            assertNull(service.update(CODE_ID, request).getDisabledAt());
        }

        @Test
        @DisplayName("somebody who already redeemed stays on the list when they are dropped from it")
        void keepsRedeemedRecipients() {
            coinCode(500);
            com.gamebuddy.billing.infrastructure.entity.PromoCodeAssignment existing =
                    new com.gamebuddy.billing.infrastructure.entity.PromoCodeAssignment();
            existing.setId(new com.gamebuddy.billing.infrastructure.entity.PromoCodeAssignment.PromoAssignmentId(
                    CODE_ID, USER));
            when(assignments.findByIdCodeId(CODE_ID)).thenReturn(List.of(existing));
            when(redemptions.redeemerIds(CODE_ID)).thenReturn(Set.of(USER));

            PromoCodeRequest request = request(PromoCodeKind.COIN);
            request.setAssigneeIds(List.of());
            service.update(CODE_ID, request);

            verify(assignments, never()).deleteByIdCodeIdAndIdUserId(CODE_ID, USER);
        }

        @Test
        @DisplayName("somebody who has not redeemed is taken off when they are dropped")
        void removesUnusedRecipients() {
            coinCode(500);
            com.gamebuddy.billing.infrastructure.entity.PromoCodeAssignment existing =
                    new com.gamebuddy.billing.infrastructure.entity.PromoCodeAssignment();
            existing.setId(new com.gamebuddy.billing.infrastructure.entity.PromoCodeAssignment.PromoAssignmentId(
                    CODE_ID, USER));
            when(assignments.findByIdCodeId(CODE_ID)).thenReturn(List.of(existing));

            PromoCodeRequest request = request(PromoCodeKind.COIN);
            request.setAssigneeIds(List.of());
            service.update(CODE_ID, request);

            verify(assignments).deleteByIdCodeIdAndIdUserId(CODE_ID, USER);
        }
    }

    @Nested
    @DisplayName("deleting")
    class Deleting {

        @Test
        @DisplayName("a code that is not there cannot be deleted")
        void refusesAnUnknownCode() {
            when(codes.findById(CODE_ID)).thenReturn(Optional.empty());

            assertEquals(
                    TransactionCode.PROMO_CODE_INVALID,
                    assertThrows(BusinessException.class, () -> service.delete(CODE_ID))
                            .getTransactionCode());
        }

        @Test
        @DisplayName("deleting removes the code; the database cascades the rest")
        void deletesTheRow() {
            PromoCode code = coinCode(500);

            service.delete(CODE_ID);

            verify(codes).delete(code);
        }
    }

    @Nested
    @DisplayName("redeeming")
    class Redeeming {

        @Test
        @DisplayName("a coin code credits the balance through the ledger")
        void creditsCoins() {
            coinCode(500);

            RedeemPromoCodeResponseBody result = service.redeem(USER, "gift 2026");

            assertEquals(600, gamer.getCoin());
            assertEquals(600, result.getCoinBalance());
            assertEquals("COIN", result.getKind());
        }

        @Test
        @DisplayName("a Gold code on a free account runs from today")
        void grantsGoldFromNow() {
            goldCode(30);

            RedeemPromoCodeResponseBody result = service.redeem(USER, "GIFT2026");

            assertEquals(SubscriptionTier.GOLD, gamer.getSubscriptionTier());
            assertEquals(NOW.plus(Duration.ofDays(30)), gamer.getSubscriptionExpiresAt());
            assertEquals(NOW.plus(Duration.ofDays(30)), result.getGoldExpiresAt());
        }

        @Test
        @DisplayName("a Gold code on a member adds to what they have")
        void extendsExistingGold() {
            goldCode(30);
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.plus(Duration.ofDays(10)));

            service.redeem(USER, "GIFT2026");

            assertEquals(NOW.plus(Duration.ofDays(40)), gamer.getSubscriptionExpiresAt());
        }

        @Test
        @DisplayName("a Gold code on a lapsed member runs from today, not from the lapse")
        void doesNotBackdateALapsedMembership() {
            goldCode(30);
            gamer.setSubscriptionTier(SubscriptionTier.GOLD);
            gamer.setSubscriptionExpiresAt(NOW.minus(Duration.ofDays(5)));

            service.redeem(USER, "GIFT2026");

            assertEquals(NOW.plus(Duration.ofDays(30)), gamer.getSubscriptionExpiresAt());
        }

        @Test
        @DisplayName("the claim is taken before anything is paid")
        void claimsBeforePaying() {
            coinCode(500);
            InOrder order = inOrder(redemptions, codes, gamers);

            service.redeem(USER, "GIFT2026");

            order.verify(redemptions).claim(any(), anyString(), anyString(), any(), any(), any(), any());
            order.verify(codes).reserveRedemption(eq(CODE_ID), any());
            order.verify(gamers).save(gamer);
        }

        @Test
        @DisplayName("a second redemption by the same account pays nothing")
        void refusesASecondRedemption() {
            coinCode(500);
            when(redemptions.claim(any(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(0);

            assertEquals(
                    TransactionCode.PROMO_CODE_ALREADY_REDEEMED,
                    assertThrows(BusinessException.class, () -> service.redeem(USER, "GIFT2026"))
                            .getTransactionCode());
            assertEquals(100, gamer.getCoin());
            verify(codes, never()).reserveRedemption(any(), any());
        }

        @Test
        @DisplayName("losing the last remaining use pays nothing")
        void refusesWhenTheLastUseIsTakenFirst() {
            coinCode(500);
            when(codes.reserveRedemption(any(), any())).thenReturn(0);

            assertEquals(
                    TransactionCode.PROMO_CODE_EXHAUSTED,
                    assertThrows(BusinessException.class, () -> service.redeem(USER, "GIFT2026"))
                            .getTransactionCode());
            assertEquals(100, gamer.getCoin());
            verify(gamers, never()).save(any(Gamer.class));
        }

        @Test
        @DisplayName("a code nobody issued")
        void refusesAnUnknownCode() {
            when(codes.findByCode(anyString())).thenReturn(Optional.empty());

            assertEquals(
                    TransactionCode.PROMO_CODE_INVALID,
                    assertThrows(BusinessException.class, () -> service.redeem(USER, "NOPE"))
                            .getTransactionCode());
        }

        @Test
        @DisplayName("a disabled code reads as invalid, not as switched off")
        void refusesADisabledCode() {
            coinCode(500).setDisabledAt(NOW);

            assertEquals(
                    TransactionCode.PROMO_CODE_INVALID,
                    assertThrows(BusinessException.class, () -> service.redeem(USER, "GIFT2026"))
                            .getTransactionCode());
        }

        @Test
        @DisplayName("an expired code says so")
        void refusesAnExpiredCode() {
            coinCode(500).setExpiresAt(NOW.minus(Duration.ofSeconds(1)));

            assertEquals(
                    TransactionCode.PROMO_CODE_EXPIRED,
                    assertThrows(BusinessException.class, () -> service.redeem(USER, "GIFT2026"))
                            .getTransactionCode());
        }

        @Test
        @DisplayName("a fully used code says so")
        void refusesAnExhaustedCode() {
            PromoCode code = coinCode(500);
            code.setMaxRedemptions(2);
            code.setRedemptionCount(2);

            assertEquals(
                    TransactionCode.PROMO_CODE_EXHAUSTED,
                    assertThrows(BusinessException.class, () -> service.redeem(USER, "GIFT2026"))
                            .getTransactionCode());
        }

        @Test
        @DisplayName("somebody else's gift is refused honestly")
        void refusesACodeAddressedToSomebodyElse() {
            coinCode(500);
            when(assignments.countByIdCodeId(CODE_ID)).thenReturn(1L);
            when(assignments.existsByIdCodeIdAndIdUserId(CODE_ID, USER)).thenReturn(false);

            assertEquals(
                    TransactionCode.PROMO_CODE_NOT_YOURS,
                    assertThrows(BusinessException.class, () -> service.redeem(USER, "GIFT2026"))
                            .getTransactionCode());
        }

        @Test
        @DisplayName("the person it was addressed to can redeem it")
        void allowsTheAddressee() {
            coinCode(500);
            when(assignments.countByIdCodeId(CODE_ID)).thenReturn(1L);
            when(assignments.existsByIdCodeIdAndIdUserId(CODE_ID, USER)).thenReturn(true);

            assertEquals(600, service.redeem(USER, "GIFT2026").getCoinBalance());
        }

        @Test
        @DisplayName("the rate limit runs before the lookup, so guessing learns nothing")
        void limitsBeforeLooking() {
            coinCode(500);
            RateLimiter exhausted = Budget.of(1, Duration.ofHours(1)).limiter();
            exhausted.tryAcquire(USER);
            service = new PromoCodeService(
                    codes,
                    assignments,
                    redemptions,
                    gamers,
                    new CoinLedger(mock(CoinLedgerRepository.class), Clock.fixed(NOW, ZoneOffset.UTC)),
                    mock(PurchaseService.class),
                    mailer,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    exhausted,
                    events);

            assertEquals(
                    TransactionCode.RATE_LIMITED,
                    assertThrows(BusinessException.class, () -> service.redeem(USER, "GIFT2026"))
                            .getTransactionCode());
            verify(codes, never()).findByCode(anyString());
        }

        @Test
        @DisplayName("the coins are recorded as a promotion, not as a purchase")
        void writesTheRightLedgerReason() {
            coinCode(500);
            CoinLedger ledger = mock(CoinLedger.class);
            service = new PromoCodeService(
                    codes,
                    assignments,
                    redemptions,
                    gamers,
                    ledger,
                    mock(PurchaseService.class),
                    mailer,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    limiter,
                    events);

            service.redeem(USER, "GIFT2026");

            verify(ledger).earn(gamer, 500, CoinReason.PROMO_CODE);
        }
    }

    @Nested
    @DisplayName("emailing")
    class Emailing {

        @Test
        @DisplayName("a relay that refuses one message does not fail the rest, or the code")
        void countsFailuresWithoutThrowing() {
            PromoCode code = coinCode(500);
            Gamer second = new Gamer();
            second.setUserId("gamer-2");
            second.setEmail("second@example.com");

            when(assignments.findByIdCodeIdAndEmailedAtIsNull(CODE_ID))
                    .thenReturn(List.of(assignmentFor(USER), assignmentFor("gamer-2")));
            when(gamers.findAllById(any())).thenReturn(List.of(gamer, second));
            org.mockito.Mockito.doThrow(new MailSendException("relay refused"))
                    .when(mailer)
                    .send(eq("second@example.com"), any());

            PromoCodeDto.EmailOutcome outcome = service.sendPending(CODE_ID);

            assertEquals(1, outcome.sent());
            assertEquals(1, outcome.failed());
            // Left unmarked, so a later send picks this recipient up rather than skipping
            // them forever.
            verify(assignments, never()).markEmailed(eq(CODE_ID), eq("gamer-2"), any());
            assertNotNull(code);
        }

        @Test
        @DisplayName("the message names the reward and the code")
        void sendsTheCode() {
            coinCode(500);
            when(assignments.findByIdCodeIdAndEmailedAtIsNull(CODE_ID)).thenReturn(List.of(assignmentFor(USER)));
            when(gamers.findAllById(any())).thenReturn(List.of(gamer));

            service.sendPending(CODE_ID);

            ArgumentCaptor<com.gamebuddy.shared.mail.EmailContent> sent =
                    ArgumentCaptor.forClass(com.gamebuddy.shared.mail.EmailContent.class);
            verify(mailer).send(eq("player@example.com"), sent.capture());
            assertEquals("GIFT2026", sent.getValue().code());
            assertTrue(sent.getValue().subject().contains("500 coins"));
        }

        private com.gamebuddy.billing.infrastructure.entity.PromoCodeAssignment assignmentFor(String userId) {
            com.gamebuddy.billing.infrastructure.entity.PromoCodeAssignment assignment =
                    new com.gamebuddy.billing.infrastructure.entity.PromoCodeAssignment();
            assignment.setId(new com.gamebuddy.billing.infrastructure.entity.PromoCodeAssignment.PromoAssignmentId(
                    CODE_ID, userId));
            return assignment;
        }
    }

    @Nested
    @DisplayName("status")
    class Status {

        @Test
        @DisplayName("a switched-off code reads as disabled even after it expires")
        void disabledOutranksExpired() {
            PromoCode code = coinCode(500);
            code.setDisabledAt(NOW);
            code.setExpiresAt(NOW.minus(Duration.ofDays(1)));

            assertEquals(PromoCodeStatus.DISABLED, code.status(NOW));
        }

        @Test
        @DisplayName("an unlimited code is never exhausted")
        void unlimitedNeverExhausts() {
            PromoCode code = coinCode(500);
            code.setRedemptionCount(10_000);

            assertFalse(code.isExhausted());
            assertEquals(PromoCodeStatus.ACTIVE, code.status(NOW));
        }
    }
}
