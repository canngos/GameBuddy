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
import com.gamebuddy.shared.badge.BadgeMetricSource;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.entity.GamerBadge;
import com.gamebuddy.shared.event.NotificationKind;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.repository.GamerBadgeRepository;
import com.gamebuddy.shared.repository.GamerRepository;
import com.gamebuddy.shared.storage.ObjectStorage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
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
     * Every module's contribution to what this gamer has done.
     *
     * <p>Injected as a list, so the profile module never learns that messages are counted
     * by {@code match} and posts by {@code community}. Adding a mission over something new
     * costs a source in the module that owns the data and a line in {@link Badge} — and no
     * new edge in the module graph.
     */
    private final List<BadgeMetricSource> metricSources;

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
     * Claims the coins.
     *
     * <p>Read-check-write inside one transaction so it runs under {@link Gamer}'s
     * {@code @Version}: two taps arriving together would otherwise both find the badge
     * uncollected and credit the reward twice. The {@code collectedAt} write is what makes
     * it once-only, and the version check is what makes that hold under a race.
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

        row.setCollectedAt(Instant.now());
        gamer.setCoin(gamer.getCoin() + badge.getReward());
        badgeRepository.save(row);
        gamerRepository.save(gamer);

        log.info("Gamer {} collected badge {} for {} coins", gamer.getUserId(), code, badge.getReward());
        return board(gamer);
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
     * Grants every mission this gamer has now finished.
     *
     * <p>Idempotent, and cheap for someone who has finished everything — the fast path
     * below is the common one for an old account.
     */
    @Override
    @Transactional
    public void evaluate(Gamer gamer) {
        Set<String> already = badgeRepository.findAllByUserId(gamer.getUserId()).stream()
                .map(GamerBadge::getBadgeCode)
                .collect(Collectors.toSet());
        if (already.size() >= Badge.values().length) {
            return;
        }

        Map<BadgeMetric, Integer> metrics = measure(gamer);
        List<GamerBadge> awarded = new ArrayList<>();
        for (Badge badge : Badge.values()) {
            if (already.contains(badge.getCode())) {
                continue;
            }
            if (metrics.getOrDefault(badge.getMetric(), 0) >= badge.getTarget()) {
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
                    gamer.getFcmToken(),
                    Constants.BADGE_TITLE,
                    String.format(Constants.BADGE_BODY, badge.getTitle()),
                    NotificationKind.BADGE));
        }
    }

    // =======================================================================
    // Internals
    // =======================================================================

    /** Merges what every module knows about this gamer into one map. */
    private Map<BadgeMetric, Integer> measure(Gamer gamer) {
        Map<BadgeMetric, Integer> merged = new EnumMap<>(BadgeMetric.class);
        for (BadgeMetricSource source : metricSources) {
            // Two sources claiming the same metric is a mistake in the sources, not
            // something to resolve silently — the larger wins so the gamer is never told
            // they have done less than they have, and it is logged.
            source.measure(gamer)
                    .forEach((metric, value) -> merged.merge(metric, value, (a, b) -> {
                        log.warn("Two sources measured {}: {} and {}", metric, a, b);
                        return Math.max(a, b);
                    }));
        }
        return merged;
    }

    /** The board, as the asking gamer sees it. */
    private BadgesResponse board(Gamer gamer) {
        Map<String, GamerBadge> rows = badgeRepository.findAllByUserId(gamer.getUserId()).stream()
                .collect(Collectors.toMap(GamerBadge::getBadgeCode, row -> row));
        Map<BadgeMetric, Integer> metrics = measure(gamer);

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
            // Capped: an earned badge whose counter has moved on would otherwise report
            // 47/10, and a progress bar reading "47 of 10" looks broken rather than proud.
            dto.setProgress(Math.min(metrics.getOrDefault(badge.getMetric(), 0), badge.getTarget()));
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
