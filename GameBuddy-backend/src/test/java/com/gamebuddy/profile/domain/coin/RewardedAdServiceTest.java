package com.gamebuddy.profile.domain.coin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.profile.domain.coin.RewardedAdService.Outcome;
import com.gamebuddy.profile.infrastructure.repository.RewardedAdGrantRepository;
import com.gamebuddy.shared.coin.CoinLedger;
import com.gamebuddy.shared.coin.CoinLedgerRepository;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Paying out a verified advert.
 *
 * <p>Verification is somebody else's job here — see {@link RewardedAdVerifierTest}. What
 * this covers is everything that can still go wrong once a callback is known to be genuine:
 * being paid twice for it, being paid past the cap, and the cap resetting.
 */
@DisplayName("RewardedAdService")
class RewardedAdServiceTest {

    private final CoinFaucet faucet = new CoinFaucet(new CoinEconomyProperties());

    private static final Instant NOON = Instant.parse("2026-08-12T12:00:00Z");
    private static final String USER = "gamer-1";
    private static final String TXN = "txn-1";

    private GamerRepository gamers;
    private RewardedAdGrantRepository grants;
    private Gamer gamer;
    private RewardedAdService service;

    @BeforeEach
    void setUp() {
        gamers = mock(GamerRepository.class);
        grants = mock(RewardedAdGrantRepository.class);

        gamer = new Gamer();
        gamer.setUserId(USER);
        gamer.setCoin(0);
        when(gamers.findById(USER)).thenReturn(Optional.of(gamer));
        // 1 = this call claimed the transaction. The repository reports a row count rather
        // than throwing, so this is the honest stand-in; a mock told to throw would assert
        // an assumption about JPA instead of what the query does.
        when(grants.claim(anyString(), anyString(), anyInt(), any())).thenReturn(1);

        service = build(NOON);
    }

    private RewardedAdService build(Instant now) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        // A real ledger over a mocked repository: it is what moves the balance, and a
        // stubbed one would make every coin assertion below pass without a coin moving.
        return new RewardedAdService(
                gamers, faucet, grants, new CoinLedger(mock(CoinLedgerRepository.class), clock), clock);
    }

    @Nested
    @DisplayName("granting")
    class Granting {

        @Test
        @DisplayName("a verified advert pays the configured amount")
        void paysOut() {
            assertEquals(Outcome.GRANTED, service.grant(TXN, USER));

            assertEquals(faucet.rewardedAdCoins(), gamer.getCoin());
            assertEquals(1, gamer.getRewardedAdsToday());
            assertEquals(CoinFaucet.adDay(NOON), gamer.getRewardedAdDay());
        }

        @Test
        @DisplayName("the transaction is recorded before the coins are paid")
        void recordsBeforePaying() {
            service.grant(TXN, USER);

            // The unique constraint is the idempotency mechanism, so it has to be claimed
            // first. Paying first and recording after leaves a window where a retry pays
            // twice — and AdMob retries anything that is not a 200.
            var order = inOrder(grants, gamers);
            order.verify(grants).claim(eq(TXN), eq(USER), anyInt(), any());
            order.verify(gamers).save(gamer);
        }

        @Test
        @DisplayName("what AdMob says the reward is worth is ignored")
        void usesOurAmountNotTheirs() {
            service.grant(TXN, USER);

            // The console's reward_amount never reaches this class. The economy is
            // balanced in CoinFaucet, and a fat-fingered 2000 in a web form must not be
            // able to inflate the currency.
            assertEquals(faucet.rewardedAdCoins(), gamer.getCoin());
        }
    }

    @Nested
    @DisplayName("replays")
    class Replays {

        @Test
        @DisplayName("a repeated callback pays nothing and is not an error")
        void duplicateIsIgnored() {
            // 0 rows inserted: somebody else already holds this transaction id.
            when(grants.claim(anyString(), anyString(), anyInt(), any())).thenReturn(0);

            assertEquals(Outcome.DUPLICATE, service.grant(TXN, USER));
            assertEquals(0, gamer.getCoin());
            verify(gamers, never()).save(any());
        }
    }

    @Nested
    @DisplayName("the daily cap")
    class DailyCap {

        @Test
        @DisplayName("the last advert of the day still pays")
        void capIsInclusive() {
            gamer.setRewardedAdDay(CoinFaucet.adDay(NOON));
            gamer.setRewardedAdsToday(faucet.rewardedAdDailyCap() - 1);

            assertEquals(Outcome.GRANTED, service.grant(TXN, USER));
            assertEquals(faucet.rewardedAdCoins(), gamer.getCoin());
        }

        @Test
        @DisplayName("one past the cap pays nothing")
        void capIsEnforced() {
            gamer.setRewardedAdDay(CoinFaucet.adDay(NOON));
            gamer.setRewardedAdsToday(faucet.rewardedAdDailyCap());

            assertEquals(Outcome.CAPPED, service.grant(TXN, USER));
            assertEquals(0, gamer.getCoin());
        }

        @Test
        @DisplayName("a capped transaction is still spent, so it cannot be replayed tomorrow")
        void cappedTransactionIsStillConsumed() {
            gamer.setRewardedAdDay(CoinFaucet.adDay(NOON));
            gamer.setRewardedAdsToday(faucet.rewardedAdDailyCap());

            service.grant(TXN, USER);

            // Rolling the row back on a cap would let today's refused callback be held and
            // replayed against tomorrow's fresh allowance.
            verify(grants).claim(eq(TXN), eq(USER), anyInt(), any());
        }

        @Test
        @DisplayName("yesterday's count does not carry into today")
        void countResetsWithTheDay() {
            gamer.setRewardedAdDay(CoinFaucet.adDay(NOON.minus(Duration.ofDays(1))));
            gamer.setRewardedAdsToday(faucet.rewardedAdDailyCap());

            assertEquals(Outcome.GRANTED, service.grant(TXN, USER));
            assertEquals(1, gamer.getRewardedAdsToday());
        }

        @Test
        @DisplayName("the allowance is read from the stored day, not from the stale count")
        void remainingIgnoresAStaleDay() {
            gamer.setRewardedAdDay(CoinFaucet.adDay(NOON.minus(Duration.ofDays(3))));
            gamer.setRewardedAdsToday(faucet.rewardedAdDailyCap());

            assertEquals(faucet.rewardedAdDailyCap(), service.remainingToday(gamer));
        }
    }

    @Nested
    @DisplayName("accounts that are not there")
    class MissingAccounts {

        @Test
        @DisplayName("an unknown account is dropped, not retried")
        void unknownUser() {
            when(gamers.findById(USER)).thenReturn(Optional.empty());

            assertEquals(Outcome.UNKNOWN_USER, service.grant(TXN, USER));
            verify(grants, never()).claim(anyString(), anyString(), anyInt(), any());
        }

        @Test
        @DisplayName("a deleted account is paid nothing")
        void deletedUser() {
            gamer.setDeletedAt(NOON.minus(Duration.ofDays(1)));

            assertEquals(Outcome.UNKNOWN_USER, service.grant(TXN, USER));
            assertEquals(0, gamer.getCoin());
        }
    }
}
