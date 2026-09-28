package com.gamebuddy.profile.domain.service;

import com.gamebuddy.shared.repository.GamerCosmeticRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the membership cosmetics in the hands of members, and only members.
 *
 * <p><strong>Why a job as well as an event.</strong> A subscription starting is an event —
 * somebody bought something, and their cosmetics are granted in that transaction. A
 * subscription <em>ending</em> can be just a timestamp quietly passing while nobody is
 * looking. {@code SubscriptionTier.effective} handles that
 * correctly for entitlements because it recomputes the tier on every read, but a cosmetic
 * is not read that way — it is a row in {@code gamer_cosmetic} and a foreign key on
 * {@code gamer}, and other people's screens render it. There is nothing to hang a lazy
 * check on, so something has to go and look.
 *
 * <p>The grant pass also repairs missing rows for members whose entitlement was created
 * before immediate grants existed, or whose cosmetics were otherwise missed.
 *
 * <p>Hourly. A lapsed member wearing one for up to an hour is not a problem worth a
 * tighter loop.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MembershipCosmeticsJob {

    private final GamerCosmeticRepository ownershipRepository;
    private final Clock clock;

    @Transactional
    @Scheduled(cron = "${gamebuddy.membership.cosmetics-cron:0 5 * * * *}")
    public void reconcile() {
        try {
            int granted = ownershipRepository.grantMembershipCosmetics(clock.instant());
            int revoked = ownershipRepository.revokeLapsedMembershipCosmetics(clock.instant());
            int unequipped = ownershipRepository.unequipUnownedCosmetics();

            if (granted > 0 || revoked > 0 || unequipped > 0) {
                log.info("Membership cosmetics: granted {}, revoked {}, unequipped {}", granted, revoked, unequipped);
            }
        } catch (RuntimeException e) {
            // Never let a scheduled failure take the scheduler thread with it.
            log.error("Reconciling membership cosmetics failed", e);
        }
    }
}
