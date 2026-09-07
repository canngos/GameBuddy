package com.gamebuddy.profile.domain.service;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.util.Constants;
import com.gamebuddy.profile.domain.badge.Badge;
import com.gamebuddy.profile.interfaces.dto.BadgeDto;
import com.gamebuddy.profile.interfaces.dto.BadgesResponseBody;
import com.gamebuddy.profile.interfaces.dto.ShowcasedBadgeDto;
import com.gamebuddy.profile.interfaces.response.BadgesResponse;
import com.gamebuddy.shared.badge.BadgeMetric;
import com.gamebuddy.shared.badge.GamerMetrics;
import com.gamebuddy.shared.coin.CoinLedger;
import com.gamebuddy.shared.coin.CoinReason;
import com.gamebuddy.shared.entity.Cosmetic;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.entity.GamerBadge;
import com.gamebuddy.shared.event.NotificationKind;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.repository.CosmeticRepository;
import com.gamebuddy.shared.repository.GamerBadgeRepository;
import com.gamebuddy.shared.repository.GamerCosmeticRepository;
import com.gamebuddy.shared.repository.GamerRepository;
import com.gamebuddy.shared.storage.ObjectStorage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Badges: awarding them, claiming them, and choosing which three to show.
 *
 * <p><b>Awarding happens on read.</b> {@link #evaluate} measures the gamer and grants
 * whatever is now finished, and it is called when the badges screen or a profile is
 * opened. The alternative is to award at each write — the match service on a mutual
 * match, the community service on a post, the store on a purchase — and that is what the
 * three old achievements did. It meant every future mission needed a hook wired into
 * somebody else's transaction, three copies of "add it if missing, then notify" that had
 * already started to drift, and a badge that was silently unreachable forever if the
 * counter jumped past the threshold while nobody was looking.
 *
 * <p>The cost of evaluating on read is a handful of counts on a screen a person opened by
 * hand, and it is self-healing: change a target, or add a mission to a year-old app, and
 * everyone's badges are simply right the next time they look. Nothing needs backfilling.
 *
 * <p>The visible consequence is that a badge is granted the next time the gamer opens
 * their profile or the badges page rather than the instant the third match lands. That is
 * the trade, and it is the right way round — a notification arriving mid-swipe is worth
 * less than a system with one rule in one place.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultBadgeService implements BadgeService {

    private final GamerBadgeRepository badgeRepository;
    private final GamerRepository gamerRepository;
    private final ObjectStorage storage;
    private final ApplicationEventPublisher events;

    /**
     * Everything every module knows about this gamer.
     *
     * <p>The profile module never learns that messages are counted by {@code match} and
     * lobbies by {@code lobby}: {@code GamerMetrics} injects the sources and merges them.
     * Adding a badge over something new costs a source in the module that owns the data and
     * a line in {@link Badge} — and no new edge in the module graph.
     */
    private final GamerMetrics metrics;

    private final CoinLedger coins;

    /** For the four PRISMATIC badges that hand over a frame instead of coins. */
    private final CosmeticRepository cosmetics;

    private final GamerCosmeticRepository ownership;

    // =======================================================================
    // Reading
    // =======================================================================

    @Override
    @Transactional
    public BadgesResponse getBadges(Gamer principal) {
        Gamer gamer = reload(principal);
        evaluate(gamer);
        return board(gamer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShowcasedBadgeDto> showcasedFor(Gamer gamer) {
        return badgeRepository.findAllByUserIdAndShowcaseSlotIsNotNullOrderByShowcaseSlotAsc(gamer.getUserId()).stream()
                // A retired mission leaves its rows behind; they simply stop rendering.
                .map(row -> Badge.byCode(row.getBadgeCode()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(badge -> {
                    ShowcasedBadgeDto dto = new ShowcasedBadgeDto();
                    dto.setCode(badge.getCode());
                    dto.setTitle(badge.getTitle());
                    dto.setDescription(badge.getDescription());
                    dto.setIcon(iconUrl(badge));
                    dto.setAnimated(badge.isAnimated());
                    return dto;
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long earnedCount(Gamer gamer) {
        return badgeRepository.countByUserId(gamer.getUserId());
    }

    // =======================================================================
    // Writing
    // =======================================================================

    /**
     * Claims what a badge is worth: coins, or a frame that is not for sale.
     *
     * <p><strong>A conditional update, not read-check-write.</strong> It used to rely on
     * {@link Gamer}'s {@code @Version} to stop two simultaneous taps both finding the badge
     * uncollected — which worked, but by turning one of the two into a conflict the gamer
     * then had to understand, and only because a coin credit happened to touch the gamer
     * row. {@code UPDATE ... WHERE collected_at IS NULL} makes the claim itself the atom:
     * zero rows back means somebody already had it, and the second tap becomes a no-op
     * rather than an error. That matters more now than it did at 25 coins a badge — the
     * hard tier pays 125, or a cosmetic that cannot be bought back if it is granted twice.
     */
    @Override
    @Transactional
    public BadgesResponse collect(Gamer principal, String code) {
        Gamer gamer = reload(principal);
        Badge badge = Badge.byCode(code).orElseThrow(() -> new BusinessException(TransactionCode.BADGE_NOT_FOUND));

        GamerBadge row = badgeRepository
                .findById(key(gamer, badge))
                .orElseThrow(() -> new BusinessException(TransactionCode.BADGE_NOT_EARNED));
        if (row.isCollected()) {
            throw new BusinessException(TransactionCode.ALREADY_COLLECTED);
        }
        if (badgeRepository.collect(gamer.getUserId(), badge.getCode(), Instant.now()) == 0) {
            throw new BusinessException(TransactionCode.ALREADY_COLLECTED);
        }
        row.setCollectedAt(Instant.now());

        if (badge.grantsCosmetic()) {
            grantCosmetic(gamer, badge);
        } else {
            coins.earn(gamer, badge.getReward(), CoinReason.BADGE_REWARD);
            log.info("Gamer {} collected badge {} for {} coins", gamer.getUserId(), code, badge.getReward());
        }
        gamerRepository.save(gamer);

        return board(gamer);
    }

    /**
     * Hands over the frame a hard badge unlocks.
     *
     * <p>The cosmetic names the badge, not the other way round, so this is a lookup rather
     * than a mapping kept in two places. A missing row is logged and swallowed: the badge is
     * already marked collected by the update above, and throwing here would roll that back
     * and leave the gamer tapping a button that can never succeed. A trophy that has not
     * been uploaded yet is an operational mistake, not something to punish the player for.
     */
    private void grantCosmetic(Gamer gamer, Badge badge) {
        cosmetics
                .findByUnlockedByBadge(badge.getCode())
                .ifPresentOrElse(
                        cosmetic -> {
                            // ON CONFLICT DO NOTHING, so re-granting is harmless even though
                            // the conditional collect above should make it impossible.
                            ownership.grant(gamer.getUserId(), cosmetic.getId(), Instant.now());
                            log.info(
                                    "Gamer {} collected badge {} and was granted {}",
                                    gamer.getUserId(),
                                    badge.getCode(),
                                    cosmetic.getAssetKey());
                        },
                        () -> log.error(
                                "Badge {} grants a cosmetic but no row claims it — check migration 39 and the art upload",
                                badge.getCode()));
    }

    /**
     * Replaces the showcase.
     *
     * <p>The whole selection at once. Add and remove endpoints would need a rule for what
     * "add a fourth" means, and every answer to that is a surprise to somebody.
     */
    @Override
    @Transactional
    public BadgesResponse showcase(Gamer principal, List<String> codes) {
        Gamer gamer = reload(principal);

        // Duplicates silently collapse rather than being rejected: a client that sends the
        // same badge twice meant to show it once, and there is nothing to warn about.
        Set<String> wanted = new LinkedHashSet<>(codes == null ? List.of() : codes);
        if (wanted.size() > GamerBadge.SHOWCASE_SLOTS) {
            throw new BusinessException(TransactionCode.SHOWCASE_FULL);
        }

        Map<String, GamerBadge> earned = badgeRepository.findAllByUserId(gamer.getUserId()).stream()
                .collect(Collectors.toMap(GamerBadge::getBadgeCode, row -> row));

        // Everything asked for must be earned and must still be a real mission. Checked
        // before anything is written, so a bad code cannot leave the showcase half-cleared.
        for (String code : wanted) {
            if (Badge.byCode(code).isEmpty()) {
                throw new BusinessException(TransactionCode.BADGE_NOT_FOUND);
            }
            if (!earned.containsKey(code)) {
                throw new BusinessException(TransactionCode.BADGE_NOT_EARNED);
            }
        }

        // Clear every slot and flush before assigning the new ones. There is a unique index
        // on (user_id, showcase_slot); without the flush, Hibernate is free to write the
        // new slot 0 before it has written the old row's NULL, and the insert order decides
        // whether the request succeeds.
        List<GamerBadge> cleared = earned.values().stream()
                .filter(row -> row.getShowcaseSlot() != null)
                .peek(row -> row.setShowcaseSlot(null))
                .toList();
        badgeRepository.saveAllAndFlush(cleared);

        int slot = 0;
        for (String code : wanted) {
            GamerBadge row = earned.get(code);
            row.setShowcaseSlot(slot++);
            badgeRepository.save(row);
        }

        return board(gamer);
    }

    /**
     * Grants every badge this gamer has now finished.
     *
     * <p>Idempotent, and cheap for someone who has finished everything — the fast path
     * below is the common one for an old account.
     *
     * <p><strong>The fast path compares sets, not sizes.</strong> It used to be
     * {@code already.size() >= Badge.values().length}, which is only the same question when
     * every row corresponds to a live badge. It does not: three codes were retired with the
     * Community feature and their rows survive on purpose, so an account from that era
     * holds thirteen rows against a ten-badge catalogue and short-circuited out of
     * evaluation entirely — earning nothing, ever again, silently. The accounts most likely
     * to qualify for the hard tier were exactly the ones that could never reach it.
     */
    @Override
    @Transactional
    public void evaluate(Gamer gamer) {
        Set<String> already = badgeRepository.findAllByUserId(gamer.getUserId()).stream()
                .map(GamerBadge::getBadgeCode)
                .collect(Collectors.toSet());
        if (Arrays.stream(Badge.values()).allMatch(badge -> already.contains(badge.getCode()))) {
            return;
        }

        Map<BadgeMetric, Integer> measured = metrics.measure(gamer);
        List<GamerBadge> awarded = new ArrayList<>();
        for (Badge badge : Badge.values()) {
            if (already.contains(badge.getCode())) {
                continue;
            }
            if (measured.getOrDefault(badge.getMetric(), 0) >= badge.getTarget()) {
                awarded.add(new GamerBadge(gamer.getUserId(), badge.getCode()));
            }
        }
        if (awarded.isEmpty()) {
            return;
        }

        badgeRepository.saveAll(awarded);
        for (GamerBadge row : awarded) {
            Badge badge = Badge.byCode(row.getBadgeCode()).orElseThrow();
            log.info("Gamer {} earned badge {}", gamer.getUserId(), badge.getCode());
            // Published rather than sent: the outbox delivers it after commit, so a
            // transaction that rolls back cannot congratulate anyone on nothing.
            events.publishEvent(new NotificationRequestedEvent(
                    gamer.getUserId(),
                    gamer.getFcmToken(),
                    Constants.BADGE_TITLE,
                    String.format(Constants.BADGE_BODY, badge.getTitle()),
                    NotificationKind.BADGE));
        }
    }

    // =======================================================================
    // Internals
    // =======================================================================

    /** The board, as the asking gamer sees it. */
    private BadgesResponse board(Gamer gamer) {
        Map<String, GamerBadge> rows = badgeRepository.findAllByUserId(gamer.getUserId()).stream()
                .collect(Collectors.toMap(GamerBadge::getBadgeCode, row -> row));
        Map<BadgeMetric, Integer> measured = metrics.measure(gamer);

        // One query for the handful of trophy frames rather than one per hard badge. The
        // sheet has to name what a cosmetic badge is actually offering, and "a reward" is
        // not a thing anybody can want.
        Map<String, String> trophies = cosmetics.findAllByUnlockedByBadgeIsNotNull().stream()
                .collect(Collectors.toMap(Cosmetic::getUnlockedByBadge, Cosmetic::getName));

        List<BadgeDto> badges = new ArrayList<>();
        for (Badge badge : Badge.values()) {
            GamerBadge row = rows.get(badge.getCode());
            BadgeDto dto = new BadgeDto();
            dto.setCode(badge.getCode());
            dto.setTitle(badge.getTitle());
            dto.setDescription(badge.getDescription());
            dto.setIcon(iconUrl(badge));
            dto.setTarget(badge.getTarget());
            dto.setReward(badge.getReward());
            dto.setTier(badge.getTier().name());
            dto.setAnimated(badge.isAnimated());
            dto.setCosmeticName(badge.grantsCosmetic() ? trophies.get(badge.getCode()) : null);
            // Capped: an earned badge whose counter has moved on would otherwise report
            // 47/10, and a progress bar reading "47 of 10" looks broken rather than proud.
            dto.setProgress(Math.min(measured.getOrDefault(badge.getMetric(), 0), badge.getTarget()));
            dto.setEarned(row != null);
            dto.setCollected(row != null && row.isCollected());
            dto.setShowcased(row != null && row.getShowcaseSlot() != null);
            badges.add(dto);
        }

        BadgesResponseBody body = new BadgesResponseBody();
        body.setBadges(badges);
        body.setCoins(gamer.getCoin());
        body.setEarned(rows.size());
        body.setTotal(Badge.values().length);
        body.setShowcaseSlots(GamerBadge.SHOWCASE_SLOTS);

        BadgesResponse response = new BadgesResponse();
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    private String iconUrl(Badge badge) {
        return storage.publicUrl(badge.iconKey());
    }

    private GamerBadge.Key key(Gamer gamer, Badge badge) {
        GamerBadge.Key key = new GamerBadge.Key();
        key.setUserId(gamer.getUserId());
        key.setBadgeCode(badge.getCode());
        return key;
    }

    /** The principal comes off a token and is detached; every write needs the managed row. */
    private Gamer reload(Gamer principal) {
        return gamerRepository
                .findById(principal.getUserId())
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));
    }
}
