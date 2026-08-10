package com.gamebuddy.profile.domain.service;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.util.Ids;
import com.gamebuddy.profile.interfaces.dto.CosmeticDto;
import com.gamebuddy.profile.interfaces.dto.CosmeticsResponseBody;
import com.gamebuddy.profile.interfaces.response.CosmeticsResponse;
import com.gamebuddy.shared.entity.Cosmetic;
import com.gamebuddy.shared.entity.CosmeticKind;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.entity.GamerCosmetic;
import com.gamebuddy.shared.repository.CosmeticRepository;
import com.gamebuddy.shared.repository.GamerCosmeticRepository;
import com.gamebuddy.shared.repository.GamerRepository;
import com.gamebuddy.shared.storage.CosmeticUrls;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The cosmetics store.
 *
 * <p>Ownership has one rule, and it lives in {@link #owns}: a gamer owns a cosmetic if it
 * is free, or if there is a purchase row for it. Free items deliberately have no purchase
 * row — see {@link GamerCosmetic} — so anything that asks "did they buy it" instead of
 * "do they own it" will wrongly lock the free frames, which are exactly the ones a new
 * gamer with no coins is supposed to be able to wear.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultCosmeticService implements CosmeticService {

    private final CosmeticRepository cosmeticRepository;
    private final GamerCosmeticRepository ownershipRepository;
    private final GamerRepository gamerRepository;
    private final CosmeticUrls cosmeticUrls;

    @Override
    @Transactional(readOnly = true)
    public CosmeticsResponse getCosmetics(Gamer principal) {
        return store(reload(principal));
    }

    /**
     * Buys a cosmetic.
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

        if (cosmetic.isFree() || ownershipRepository.existsByUserIdAndCosmeticId(gamer.getUserId(), cosmetic.getId())) {
            throw new BusinessException(TransactionCode.COSMETIC_ALREADY_OWNED);
        }
        // A membership item has a price of zero and is not a free item: it cannot be
        // bought at any price, because owning it is what says somebody is a member. The
        // store never offers these, so reaching here means a crafted request.
        if (cosmetic.isMembershipOnly()) {
            throw new BusinessException(TransactionCode.SUBSCRIPTION_REQUIRED);
        }
        if (gamer.getCoin() < cosmetic.getPrice()) {
            throw new BusinessException(TransactionCode.COIN_NOT_ENOUGH);
        }

        gamer.setCoin(gamer.getCoin() - cosmetic.getPrice());
        ownershipRepository.save(new GamerCosmetic(gamer.getUserId(), cosmetic.getId(), cosmetic.getPrice()));

        // The purchase badge is not awarded here. Counting bought cosmetics is what
        // BadgeService's metric source already does, so the threshold lives once, next to
        // the mission that uses it, instead of being restated inside the checkout.

        gamerRepository.save(gamer);
        log.info("Gamer {} bought cosmetic {} for {} coins", gamer.getUserId(), cosmetic.getId(), cosmetic.getPrice());
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
     * The one ownership rule.
     *
     * <p>Free means owned, without a row. See the class comment.
     */
    private boolean owns(Gamer gamer, Cosmetic cosmetic) {
        return cosmetic.isFree()
                || ownershipRepository.existsByUserIdAndCosmeticId(gamer.getUserId(), cosmetic.getId());
    }

    private void wear(Gamer gamer, CosmeticKind kind, Cosmetic cosmetic) {
        if (kind == CosmeticKind.FRAME) {
            gamer.setEquippedFrame(cosmetic);
        } else {
            gamer.setEquippedBanner(cosmetic);
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

        CosmeticsResponseBody body = new CosmeticsResponseBody();
        body.setFrames(toDtos(all, CosmeticKind.FRAME, owned, frameId));
        body.setBanners(toDtos(all, CosmeticKind.BANNER, owned, bannerId));
        body.setCoins(gamer.getCoin());

        CosmeticsResponse response = new CosmeticsResponse();
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    private List<CosmeticDto> toDtos(List<Cosmetic> all, CosmeticKind kind, Set<UUID> owned, UUID equippedId) {
        return all.stream()
                .filter(c -> c.getKind() == kind)
                .map(c -> {
                    CosmeticDto dto = new CosmeticDto();
                    dto.setId(c.getId().toString());
                    dto.setKind(c.getKind().name());
                    dto.setName(c.getName());
                    dto.setImage(cosmeticUrls.urlFor(c));
                    dto.setAnimated(c.isAnimated());
                    dto.setPrice(c.getPrice());
                    dto.setOwned(c.isFree() || owned.contains(c.getId()));
                    dto.setEquipped(c.getId().equals(equippedId));
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
