package com.gamebuddy.profile.domain.service;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.util.Ids;
import com.gamebuddy.profile.interfaces.dto.BundleDto;
import com.gamebuddy.profile.interfaces.dto.CosmeticDto;
import com.gamebuddy.profile.interfaces.dto.CosmeticsResponseBody;
import com.gamebuddy.profile.interfaces.response.CosmeticsResponse;
import com.gamebuddy.shared.coin.CoinLedger;
import com.gamebuddy.shared.coin.CoinReason;
import com.gamebuddy.shared.entity.Cosmetic;
import com.gamebuddy.shared.entity.CosmeticBundle;
import com.gamebuddy.shared.entity.CosmeticKind;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.entity.GamerCosmetic;
import com.gamebuddy.shared.repository.CosmeticBundleRepository;
import com.gamebuddy.shared.repository.CosmeticRepository;
import com.gamebuddy.shared.repository.GamerCosmeticRepository;
import com.gamebuddy.shared.repository.GamerRepository;
import com.gamebuddy.shared.storage.CosmeticUrls;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The cosmetics store.
 *
 * <p>Ownership has one rule and it lives in {@link #owns}: there is a row in
 * {@code gamer_cosmetic}, or the gamer does not own it. No exceptions — not for free items,
 * not for membership ones.
 *
 * <p><strong>Free used to mean owned, with no row at all</strong>, which made a free item
 * something a gamer already had rather than something they got. The Steel frame simply
 * appeared in every inventory, and {@link #buy} refused it with COSMETIC_ALREADY_OWNED, so
 * there was no way to claim it even in principle. Now a free item is claimed like any other
 * purchase and costs zero coins — the row is written, the ledger is not touched.
 *
 * <p>The change also closed a hole: membership items are priced at zero, so under the old
 * rule {@code owns} returned true for them for <em>everybody</em>, and only the store's
 * filtering kept non-members out. Equipping one directly by id would have worked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultCosmeticService implements CosmeticService {

    private final CosmeticRepository cosmeticRepository;
    private final CosmeticBundleRepository bundleRepository;
    private final GamerCosmeticRepository ownershipRepository;
    private final GamerRepository gamerRepository;
    private final CosmeticUrls cosmeticUrls;
    private final CoinLedger coins;

    @Override
    @Transactional(readOnly = true)
    public CosmeticsResponse getCosmetics(Gamer principal) {
        return store(reload(principal));
    }

    /**
     * Buys a cosmetic, or claims a free one — the same act, at a price of zero.
     *
     * <p>Read-check-write on the coin balance inside one transaction, so it runs under the
     * {@code @Version} optimistic lock on {@link Gamer}. Two taps of the buy button
     * arriving together would otherwise both read the old balance, both find it sufficient,
     * and charge once for two items — or charge twice and grant one, depending on which
     * write landed second.
     */
    @Override
    @Transactional
    public CosmeticsResponse buy(Gamer principal, String cosmeticId) {
        Gamer gamer = reload(principal);
        Cosmetic cosmetic = require(cosmeticId);

        if (ownershipRepository.existsByUserIdAndCosmeticId(gamer.getUserId(), cosmetic.getId())) {
            throw new BusinessException(TransactionCode.COSMETIC_ALREADY_OWNED);
        }
        // A membership item is also priced at zero and is emphatically not free: owning it
        // is what says somebody is a member, so it is granted by the membership job and
        // never sold. The store does not offer these, so reaching here means a crafted
        // request — and this check is why a zero price is not a way in.
        if (cosmetic.isMembershipOnly()) {
            throw new BusinessException(TransactionCode.SUBSCRIPTION_REQUIRED);
        }
        // Same reasoning one step further: a trophy is priced at zero too, and the store
        // does not list it, so reaching here means somebody sent the id by hand. Hiding it
        // from the shelf is presentation; this is the rule.
        if (cosmetic.getUnlockedByBadge() != null) {
            throw new BusinessException(TransactionCode.BADGE_NOT_EARNED);
        }
        if (gamer.getCoin() < cosmetic.getPrice()) {
            throw new BusinessException(TransactionCode.COIN_NOT_ENOUGH);
        }

        // Nothing is spent on a claim, and nothing is written to the ledger for it either:
        // a zero-coin entry is noise in a history whose job is to explain where coins went.
        if (cosmetic.getPrice() > 0) {
            coins.spend(gamer, cosmetic.getPrice(), CoinReason.COSMETIC);
        }
        ownershipRepository.save(new GamerCosmetic(gamer.getUserId(), cosmetic.getId(), cosmetic.getPrice()));

        // The purchase badge is not awarded here. Counting bought cosmetics is what
        // BadgeService's metric source already does, so the threshold lives once, next to
        // the mission that uses it, instead of being restated inside the checkout.

        gamerRepository.save(gamer);
        log.info("Gamer {} bought cosmetic {} for {} coins", gamer.getUserId(), cosmetic.getId(), cosmetic.getPrice());
        return store(gamer);
    }

    /**
     * Buys a set of cosmetics at the set's price.
     *
     * <p>The same read-check-write under the same optimistic lock as {@link #buy}, and for
     * the same reason — but one debit and several grants, which is the whole point: the
     * discount only exists as the gap between {@code bundle.price} and what the parts cost
     * separately.
     *
     * <p><strong>All or nothing.</strong> Owning any part refuses the whole thing rather
     * than quietly charging full price for a smaller set or trying to work out a fair
     * fraction. Both alternatives were considered and both are worse: a partial grant means
     * the price on the shelf is not the price charged, and proration turns one clear offer
     * into an amount that depends on what you already have. A gamer who owns half of a set
     * buys the other half on its own, and the message says so.
     */
    @Override
    @Transactional
    public CosmeticsResponse buyBundle(Gamer principal, String bundleId) {
        Gamer gamer = reload(principal);
        CosmeticBundle bundle = bundleRepository
                .findWithItemsById(Ids.uuid(bundleId))
                .orElseThrow(() -> new BusinessException(TransactionCode.BUNDLE_NOT_FOUND));

        Set<UUID> owned = ownershipRepository.findOwnedIds(gamer.getUserId());
        List<Cosmetic> items = bundle.getItems();

        if (items.stream().anyMatch(c -> owned.contains(c.getId()))) {
            throw new BusinessException(TransactionCode.BUNDLE_PARTLY_OWNED);
        }
        // Defensive: nothing seeds a membership item into a bundle, and if anything ever
        // did it would be a way to buy the thing that is supposed to mark a subscriber.
        if (items.stream().anyMatch(Cosmetic::isMembershipOnly)) {
            throw new BusinessException(TransactionCode.SUBSCRIPTION_REQUIRED);
        }
        if (gamer.getCoin() < bundle.getPrice()) {
            throw new BusinessException(TransactionCode.COIN_NOT_ENOUGH);
        }

        coins.spend(gamer, bundle.getPrice(), CoinReason.BUNDLE);
        for (Map.Entry<Cosmetic, Integer> grant : split(bundle).entrySet()) {
            ownershipRepository.save(
                    new GamerCosmetic(gamer.getUserId(), grant.getKey().getId(), grant.getValue()));
        }

        gamerRepository.save(gamer);
        log.info(
                "Gamer {} bought bundle {} ({} items) for {} coins",
                gamer.getUserId(),
                bundle.getId(),
                items.size(),
                bundle.getPrice());
        return store(gamer);
    }

    @Override
    @Transactional
    public CosmeticsResponse equip(Gamer principal, String cosmeticId) {
        Gamer gamer = reload(principal);
        Cosmetic cosmetic = require(cosmeticId);

        // Checked on the server even though the store greys out what it does not own: the
        // client's opinion about what a gamer owns arrives over the same connection an
        // attacker controls, and this is the door to the paid items.
        if (!owns(gamer, cosmetic)) {
            throw new BusinessException(TransactionCode.COSMETIC_NOT_OWNED);
        }

        wear(gamer, cosmetic.getKind(), cosmetic);
        gamerRepository.save(gamer);
        return store(gamer);
    }

    @Override
    @Transactional
    public CosmeticsResponse unequip(Gamer principal, CosmeticKind kind) {
        Gamer gamer = reload(principal);
        wear(gamer, kind, null);
        gamerRepository.save(gamer);
        return store(gamer);
    }

    // =======================================================================
    // Internals
    // =======================================================================

    /**
     * The one ownership rule: a row, or nothing. See the class comment.
     */
    private boolean owns(Gamer gamer, Cosmetic cosmetic) {
        return ownershipRepository.existsByUserIdAndCosmeticId(gamer.getUserId(), cosmetic.getId());
    }

    /**
     * Puts something in its slot, or empties that slot when given null.
     *
     * <p>A switch rather than an if/else, and exhaustive on purpose: as an if/else this
     * wrote anything that was not a frame into the banner slot, so the day a third kind
     * arrived it would have silently replaced people's banners. Written this way the
     * compiler refuses a new {@link CosmeticKind} until it has somewhere to go.
     */
    private void wear(Gamer gamer, CosmeticKind kind, Cosmetic cosmetic) {
        switch (kind) {
            case FRAME -> gamer.setEquippedFrame(cosmetic);
            case BANNER -> gamer.setEquippedBanner(cosmetic);
            case THEME -> gamer.setEquippedTheme(cosmetic);
        }
    }

    /**
     * Builds the store view for one gamer.
     *
     * <p>Two queries regardless of catalogue size: the whole shelf, and the set of ids this
     * gamer has bought. The alternative — asking "do you own this" per row — is a query per
     * item on a screen that shows all of them.
     */
    private CosmeticsResponse store(Gamer gamer) {
        List<Cosmetic> all = cosmeticRepository.findAllByOrderByKindAscSortOrderAsc();
        Set<UUID> owned = ownershipRepository.findOwnedIds(gamer.getUserId());

        UUID frameId = gamer.getEquippedFrame() == null
                ? null
                : gamer.getEquippedFrame().getId();
        UUID bannerId = gamer.getEquippedBanner() == null
                ? null
                : gamer.getEquippedBanner().getId();
        UUID themeId = gamer.getEquippedTheme() == null
                ? null
                : gamer.getEquippedTheme().getId();

        CosmeticsResponseBody body = new CosmeticsResponseBody();
        body.setFrames(toDtos(all, CosmeticKind.FRAME, owned, frameId));
        body.setBanners(toDtos(all, CosmeticKind.BANNER, owned, bannerId));
        body.setThemes(toDtos(all, CosmeticKind.THEME, owned, themeId));
        body.setBundles(bundles(owned));
        body.setCoins(gamer.getCoin());

        CosmeticsResponse response = new CosmeticsResponse();
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    private List<CosmeticDto> toDtos(List<Cosmetic> all, CosmeticKind kind, Set<UUID> owned, UUID equippedId) {
        return all.stream()
                .filter(c -> c.getKind() == kind)
                // A badge's trophy is never on the shelf. It is priced at zero like a
                // membership item and is just as emphatically not free — leaving it here
                // would put "Free" under a frame that four months of play is the only way
                // to get, which is the worst possible thing to say about it.
                //
                // Filtered here rather than in the query so the Inventory, which maps the
                // same rows through `toDtos(items, owned)` below, still shows one to the
                // gamer who earned it.
                .filter(c -> c.getUnlockedByBadge() == null)
                .map(c -> toDto(c, owned, equippedId))
                .toList();
    }

    /**
     * The parts of a bundle, mapped as ordinary items.
     *
     * <p>No equipped id: the bundle shelf sells sets and never marks one as worn — the
     * items appear again on their own shelves, which is where that is shown.
     */
    private List<CosmeticDto> toDtos(List<Cosmetic> items, Set<UUID> owned) {
        return items.stream().map(c -> toDto(c, owned, null)).toList();
    }

    private CosmeticDto toDto(Cosmetic c, Set<UUID> owned, UUID equippedId) {
        CosmeticDto dto = new CosmeticDto();
        dto.setId(c.getId().toString());
        dto.setKind(c.getKind().name());
        dto.setName(c.getName());
        dto.setImage(cosmeticUrls.urlFor(c));
        // Null for everything that is not a theme; a theme has this and no image.
        dto.setTheme(cosmeticUrls.slug(c));
        dto.setAnimated(c.isAnimated());
        dto.setPrice(c.getPrice());
        dto.setOwned(owned.contains(c.getId()));
        dto.setMembershipOnly(c.isMembershipOnly());
        dto.setEquipped(c.getId().equals(equippedId));
        return dto;
    }

    /**
     * How much of a bundle's price each of its items is recorded as having cost.
     *
     * <p>Proportional to list price, floored, with the rounding remainder given to the
     * dearest item. It matters because {@code gamer_cosmetic.paid} is a receipt, not a
     * label: {@code upgrade-2026-18} refunded people by reading it, so the parts have to
     * sum to what was actually charged — and if a set is bought at a discount, "what this
     * item cost me" is the discounted share, not the shelf price.
     */
    private Map<Cosmetic, Integer> split(CosmeticBundle bundle) {
        List<Cosmetic> items = bundle.getItems();
        int listTotal = items.stream().mapToInt(Cosmetic::getPrice).sum();

        Map<Cosmetic, Integer> shares = new LinkedHashMap<>();
        if (listTotal <= 0) {
            // A set of free items priced at zero: nothing to apportion.
            items.forEach(c -> shares.put(c, 0));
            return shares;
        }

        int assigned = 0;
        for (Cosmetic item : items) {
            int share = (int) ((long) bundle.getPrice() * item.getPrice() / listTotal);
            shares.put(item, share);
            assigned += share;
        }

        // The remainder lands on the priciest item, which is the last one: `items` is
        // ordered by price ascending, so this is a single lookup rather than a search.
        Cosmetic dearest = items.get(items.size() - 1);
        shares.merge(dearest, bundle.getPrice() - assigned, Integer::sum);
        return shares;
    }

    private List<BundleDto> bundles(Set<UUID> owned) {
        return bundleRepository.findAllByOrderBySortOrderAsc().stream()
                .map(b -> {
                    BundleDto dto = new BundleDto();
                    dto.setId(b.getId().toString());
                    dto.setName(b.getName());
                    dto.setPrice(b.getPrice());
                    dto.setPartsPrice(
                            b.getItems().stream().mapToInt(Cosmetic::getPrice).sum());
                    dto.setItems(toDtos(b.getItems(), owned));
                    dto.setOwned(b.getItems().stream().allMatch(c -> owned.contains(c.getId())));
                    return dto;
                })
                .toList();
    }

    private Cosmetic require(String cosmeticId) {
        return cosmeticRepository
                .findById(Ids.uuid(cosmeticId))
                .orElseThrow(() -> new BusinessException(TransactionCode.COSMETIC_NOT_FOUND));
    }

    /**
     * The principal comes off a token and is detached; every write needs the managed row.
     *
     * <p>Also the reason the balance check is safe: this read is what the version check at
     * commit is against.
     */
    private Gamer reload(Gamer principal) {
        return gamerRepository
                .findById(principal.getUserId())
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));
    }
}
