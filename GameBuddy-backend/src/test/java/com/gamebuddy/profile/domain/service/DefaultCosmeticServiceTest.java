package com.gamebuddy.profile.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.profile.interfaces.dto.CosmeticDto;
import com.gamebuddy.profile.interfaces.response.CosmeticsResponse;
import com.gamebuddy.shared.coin.CoinLedger;
import com.gamebuddy.shared.coin.CoinLedgerRepository;
import com.gamebuddy.shared.entity.Cosmetic;
import com.gamebuddy.shared.entity.CosmeticKind;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.entity.GamerCosmetic;
import com.gamebuddy.shared.repository.CosmeticRepository;
import com.gamebuddy.shared.repository.GamerCosmeticRepository;
import com.gamebuddy.shared.repository.GamerRepository;
import com.gamebuddy.shared.storage.CosmeticUrls;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultCosmeticServiceTest {

    private DefaultCosmeticService cosmeticService;

    @Mock
    private CosmeticRepository cosmeticRepository;

    @Mock
    private GamerCosmeticRepository ownershipRepository;

    @Mock
    private GamerRepository gamerRepository;

    @Mock
    private CosmeticUrls cosmeticUrls;

    @Mock
    private CoinLedgerRepository coinLedgerRepository;

    private Gamer gamer;

    @BeforeEach
    void setUp() {
        // A real ledger over a mocked repository rather than a mock ledger: the ledger is
        // what moves the balance now, and a stubbed one would leave every coin assertion
        // below passing without a coin having gone anywhere.
        cosmeticService = new DefaultCosmeticService(
                cosmeticRepository,
                ownershipRepository,
                gamerRepository,
                cosmeticUrls,
                new CoinLedger(coinLedgerRepository, Clock.systemUTC()));

        gamer = new Gamer();
        gamer.setUserId(UUID.randomUUID().toString());
        gamer.setCoin(100);

        when(gamerRepository.findById(gamer.getUserId())).thenReturn(Optional.of(gamer));
        when(ownershipRepository.findOwnedIds(anyString())).thenReturn(Set.of());
        when(cosmeticRepository.findAllByOrderByKindAscSortOrderAsc()).thenReturn(List.of());
    }

    private static Cosmetic cosmetic(CosmeticKind kind, int price) {
        Cosmetic c = new Cosmetic();
        c.setId(UUID.randomUUID());
        c.setKind(kind);
        c.setName("Test " + kind);
        c.setAssetKey("frames/test.webp");
        c.setPrice(price);
        return c;
    }

    @Nested
    class Browsing {

        @Test
        @DisplayName("split by kind, so the client does not have to partition it")
        void testGetCosmetics_whenCalled_SplitsFramesFromBanners() {
            when(cosmeticRepository.findAllByOrderByKindAscSortOrderAsc())
                    .thenReturn(List.of(cosmetic(CosmeticKind.FRAME, 0), cosmetic(CosmeticKind.BANNER, 0)));

            CosmeticsResponse response = cosmeticService.getCosmetics(gamer);

            assertEquals(1, response.getBody().getData().getFrames().size());
            assertEquals(1, response.getBody().getData().getBanners().size());
        }

        @Test
        @DisplayName("a free cosmetic is not owned until it has been claimed")
        void testGetCosmetics_whenItemIsFreeAndUnclaimed_MarksNotOwned() {
            when(cosmeticRepository.findAllByOrderByKindAscSortOrderAsc())
                    .thenReturn(List.of(cosmetic(CosmeticKind.FRAME, 0)));

            CosmeticsResponse response = cosmeticService.getCosmetics(gamer);

            // Free used to mean owned outright, which is what made the free frame something
            // a gamer already had rather than something they got from the store. Ownership
            // is the row now, for every item at every price.
            assertFalse(response.getBody().getData().getFrames().get(0).isOwned());
        }

        @Test
        @DisplayName("a claimed free cosmetic is owned like anything else")
        void testGetCosmetics_whenItemIsFreeAndClaimed_MarksOwned() {
            Cosmetic free = cosmetic(CosmeticKind.FRAME, 0);
            when(cosmeticRepository.findAllByOrderByKindAscSortOrderAsc()).thenReturn(List.of(free));
            when(ownershipRepository.findOwnedIds(gamer.getUserId())).thenReturn(Set.of(free.getId()));

            CosmeticsResponse response = cosmeticService.getCosmetics(gamer);

            assertTrue(response.getBody().getData().getFrames().get(0).isOwned());
        }

        @Test
        void testGetCosmetics_whenItemIsPaidAndNotBought_MarksNotOwned() {
            when(cosmeticRepository.findAllByOrderByKindAscSortOrderAsc())
                    .thenReturn(List.of(cosmetic(CosmeticKind.FRAME, 300)));

            CosmeticsResponse response = cosmeticService.getCosmetics(gamer);

            assertFalse(response.getBody().getData().getFrames().get(0).isOwned());
        }

        @Test
        void testGetCosmetics_whenItemIsBought_MarksOwned() {
            Cosmetic paid = cosmetic(CosmeticKind.FRAME, 300);
            when(cosmeticRepository.findAllByOrderByKindAscSortOrderAsc()).thenReturn(List.of(paid));
            when(ownershipRepository.findOwnedIds(gamer.getUserId())).thenReturn(Set.of(paid.getId()));

            CosmeticsResponse response = cosmeticService.getCosmetics(gamer);

            assertTrue(response.getBody().getData().getFrames().get(0).isOwned());
        }

        @Test
        void testGetCosmetics_whenWorn_MarksEquipped() {
            Cosmetic worn = cosmetic(CosmeticKind.FRAME, 0);
            Cosmetic other = cosmetic(CosmeticKind.FRAME, 0);
            gamer.setEquippedFrame(worn);
            when(cosmeticRepository.findAllByOrderByKindAscSortOrderAsc()).thenReturn(List.of(worn, other));

            List<CosmeticDto> frames =
                    cosmeticService.getCosmetics(gamer).getBody().getData().getFrames();

            assertTrue(frames.get(0).isEquipped());
            assertFalse(frames.get(1).isEquipped());
        }

        @Test
        @DisplayName("the balance rides along, so buying does not need a second request")
        void testGetCosmetics_whenCalled_IncludesCoins() {
            assertEquals(
                    100, cosmeticService.getCosmetics(gamer).getBody().getData().getCoins());
        }

        @Test
        @DisplayName("object keys come back resolved to URLs")
        void testGetCosmetics_whenCalled_ResolvesAssetKeys() {
            Cosmetic frame = cosmetic(CosmeticKind.FRAME, 0);
            when(cosmeticRepository.findAllByOrderByKindAscSortOrderAsc()).thenReturn(List.of(frame));
            when(cosmeticUrls.urlFor(frame)).thenReturn("https://cdn/frames/test.webp");

            CosmeticsResponse response = cosmeticService.getCosmetics(gamer);

            assertEquals(
                    "https://cdn/frames/test.webp",
                    response.getBody().getData().getFrames().get(0).getImage());
        }
    }

    @Nested
    class Buying {

        @Test
        void testBuy_whenCosmeticNotFound_ReturnErrorCode164() {
            String id = UUID.randomUUID().toString();
            when(cosmeticRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class, () -> cosmeticService.buy(gamer, id));
            assertEquals(164, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("a free cosmetic is claimed: a row is written and no coins move")
        void testBuy_whenFree_ClaimsWithoutCharging() {
            Cosmetic free = cosmetic(CosmeticKind.FRAME, 0);
            when(cosmeticRepository.findById(free.getId())).thenReturn(Optional.of(free));

            cosmeticService.buy(gamer, free.getId().toString());

            assertEquals(100, gamer.getCoin());
            // No ledger entry either. A zero-coin row is noise in a history whose job is to
            // account for coins that actually moved.
            verify(coinLedgerRepository, never()).save(any());

            ArgumentCaptor<GamerCosmetic> saved = ArgumentCaptor.forClass(GamerCosmetic.class);
            verify(ownershipRepository).save(saved.capture());
            assertEquals(0, saved.getValue().getPaid());
        }

        @Test
        @DisplayName("claiming twice is refused like any other double purchase")
        void testBuy_whenFreeAlreadyClaimed_ReturnErrorCode165() {
            Cosmetic free = cosmetic(CosmeticKind.FRAME, 0);
            when(cosmeticRepository.findById(free.getId())).thenReturn(Optional.of(free));
            when(ownershipRepository.existsByUserIdAndCosmeticId(gamer.getUserId(), free.getId()))
                    .thenReturn(true);
            String id = free.getId().toString();

            BusinessException ex = assertThrows(BusinessException.class, () -> cosmeticService.buy(gamer, id));
            assertEquals(165, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("a membership item is priced at zero and is still not claimable")
        void testBuy_whenMembershipOnly_ReturnsSubscriptionRequired() {
            Cosmetic members = cosmetic(CosmeticKind.BANNER, 0);
            members.setMembershipOnly(true);
            when(cosmeticRepository.findById(members.getId())).thenReturn(Optional.of(members));
            String id = members.getId().toString();

            // The reason this test exists: dropping the free-means-owned shortcut turned a
            // zero price into a claimable price, and membership items share that price.
            BusinessException ex = assertThrows(BusinessException.class, () -> cosmeticService.buy(gamer, id));
            assertEquals(159, ex.getTransactionCode().getId());
            verify(ownershipRepository, never()).save(any());
        }

        @Test
        void testBuy_whenAlreadyOwned_ReturnErrorCode165() {
            Cosmetic paid = cosmetic(CosmeticKind.FRAME, 50);
            when(cosmeticRepository.findById(paid.getId())).thenReturn(Optional.of(paid));
            when(ownershipRepository.existsByUserIdAndCosmeticId(gamer.getUserId(), paid.getId()))
                    .thenReturn(true);
            String id = paid.getId().toString();

            BusinessException ex = assertThrows(BusinessException.class, () -> cosmeticService.buy(gamer, id));
            assertEquals(165, ex.getTransactionCode().getId());
        }

        @Test
        void testBuy_whenCoinNotEnough_ReturnErrorCode129() {
            Cosmetic expensive = cosmetic(CosmeticKind.FRAME, 5000);
            when(cosmeticRepository.findById(expensive.getId())).thenReturn(Optional.of(expensive));
            String id = expensive.getId().toString();

            BusinessException ex = assertThrows(BusinessException.class, () -> cosmeticService.buy(gamer, id));
            assertEquals(129, ex.getTransactionCode().getId());
        }

        @Test
        void testBuy_whenValid_DebitsTheCoinsAndRecordsThePurchase() {
            Cosmetic paid = cosmetic(CosmeticKind.FRAME, 60);
            when(cosmeticRepository.findById(paid.getId())).thenReturn(Optional.of(paid));

            CosmeticsResponse response = cosmeticService.buy(gamer, paid.getId().toString());

            assertEquals("100", response.getStatus().getCode());
            assertEquals(40, gamer.getCoin());
            assertEquals(40, response.getBody().getData().getCoins());

            ArgumentCaptor<GamerCosmetic> saved = ArgumentCaptor.forClass(GamerCosmetic.class);
            verify(ownershipRepository).save(saved.capture());
            assertEquals(paid.getId(), saved.getValue().getCosmeticId());
            assertEquals(gamer.getUserId(), saved.getValue().getUserId());
        }

        @Test
        @DisplayName("the receipt records what was charged, not what the item costs later")
        void testBuy_whenValid_StoresThePricePaid() {
            Cosmetic paid = cosmetic(CosmeticKind.FRAME, 60);
            when(cosmeticRepository.findById(paid.getId())).thenReturn(Optional.of(paid));

            cosmeticService.buy(gamer, paid.getId().toString());

            ArgumentCaptor<GamerCosmetic> saved = ArgumentCaptor.forClass(GamerCosmetic.class);
            verify(ownershipRepository).save(saved.capture());
            assertEquals(60, saved.getValue().getPaid());
        }

        // The purchase-count badge moved to BadgeService, and so did its tests: counting
        // bought cosmetics is a metric now, not something the checkout does on the way past.
    }

    @Nested
    class Equipping {

        @Test
        @DisplayName("a paid cosmetic the gamer does not own is refused, whatever the client thinks")
        void testEquip_whenNotOwned_ReturnErrorCode166() {
            Cosmetic paid = cosmetic(CosmeticKind.FRAME, 300);
            when(cosmeticRepository.findById(paid.getId())).thenReturn(Optional.of(paid));
            String id = paid.getId().toString();

            BusinessException ex = assertThrows(BusinessException.class, () -> cosmeticService.equip(gamer, id));
            assertEquals(166, ex.getTransactionCode().getId());
            assertNull(gamer.getEquippedFrame());
        }

        @Test
        void testEquip_whenOwned_WearsIt() {
            Cosmetic paid = cosmetic(CosmeticKind.FRAME, 300);
            when(cosmeticRepository.findById(paid.getId())).thenReturn(Optional.of(paid));
            when(ownershipRepository.existsByUserIdAndCosmeticId(gamer.getUserId(), paid.getId()))
                    .thenReturn(true);

            cosmeticService.equip(gamer, paid.getId().toString());

            assertEquals(paid, gamer.getEquippedFrame());
        }

        @Test
        @DisplayName("an unclaimed free cosmetic cannot be worn either")
        void testEquip_whenFreeAndUnclaimed_ReturnErrorCode166() {
            Cosmetic free = cosmetic(CosmeticKind.BANNER, 0);
            when(cosmeticRepository.findById(free.getId())).thenReturn(Optional.of(free));
            String id = free.getId().toString();

            BusinessException ex = assertThrows(BusinessException.class, () -> cosmeticService.equip(gamer, id));
            assertEquals(166, ex.getTransactionCode().getId());
            assertNull(gamer.getEquippedBanner());
        }

        @Test
        @DisplayName("a claimed free cosmetic is worn like any other owned item")
        void testEquip_whenFreeAndClaimed_WearsIt() {
            Cosmetic free = own(cosmetic(CosmeticKind.BANNER, 0));

            cosmeticService.equip(gamer, free.getId().toString());

            assertEquals(free, gamer.getEquippedBanner());
        }

        @Test
        @DisplayName("each kind has its own slot, so a banner does not displace a frame")
        void testEquip_whenBannerEquipped_LeavesFrameAlone() {
            Cosmetic frame = cosmetic(CosmeticKind.FRAME, 0);
            Cosmetic banner = own(cosmetic(CosmeticKind.BANNER, 0));
            gamer.setEquippedFrame(frame);

            cosmeticService.equip(gamer, banner.getId().toString());

            assertEquals(frame, gamer.getEquippedFrame());
            assertEquals(banner, gamer.getEquippedBanner());
        }

        @Test
        void testEquip_whenAnotherFrameWorn_ReplacesIt() {
            Cosmetic old = cosmetic(CosmeticKind.FRAME, 0);
            Cosmetic replacement = own(cosmetic(CosmeticKind.FRAME, 0));
            gamer.setEquippedFrame(old);

            cosmeticService.equip(gamer, replacement.getId().toString());

            assertEquals(replacement, gamer.getEquippedFrame());
        }

        /** Findable in the catalogue and owned by this gamer — the state equipping needs. */
        private Cosmetic own(Cosmetic cosmetic) {
            when(cosmeticRepository.findById(cosmetic.getId())).thenReturn(Optional.of(cosmetic));
            when(ownershipRepository.existsByUserIdAndCosmeticId(gamer.getUserId(), cosmetic.getId()))
                    .thenReturn(true);
            return cosmetic;
        }

        @Test
        void testUnequip_whenCalled_EmptiesOnlyThatSlot() {
            gamer.setEquippedFrame(cosmetic(CosmeticKind.FRAME, 0));
            Cosmetic banner = cosmetic(CosmeticKind.BANNER, 0);
            gamer.setEquippedBanner(banner);

            cosmeticService.unequip(gamer, CosmeticKind.FRAME);

            assertNull(gamer.getEquippedFrame());
            assertEquals(banner, gamer.getEquippedBanner());
        }

        @Test
        @DisplayName("unequipping an empty slot is not an error")
        void testUnequip_whenNothingWorn_Succeeds() {
            assertEquals(
                    "100",
                    cosmeticService
                            .unequip(gamer, CosmeticKind.BANNER)
                            .getStatus()
                            .getCode());
        }
    }
}
