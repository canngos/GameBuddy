package com.gamebuddy.moderation.domain.service;

import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.moderation.infrastructure.entity.ContentReport;
import com.gamebuddy.moderation.infrastructure.entity.ModerationAction;
import com.gamebuddy.moderation.infrastructure.entity.ModerationAction.Action;
import com.gamebuddy.moderation.infrastructure.repository.ModerationActionRepository;
import com.gamebuddy.shared.entity.AvatarStatus;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.event.NotificationKind;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carries out a moderator's decision about a person, and records it.
 *
 * <p>One place, because a sanction is several things that must not drift apart: the account
 * effect (a suspension, a removed photo, a ban), the audit row that says who did it and why,
 * and the notice to the person naming the rule they broke. Split across call sites, the
 * notice gets forgotten on one path and the audit row on another, and then a ban exists that
 * nobody can explain — which is the state this whole redesign is undoing.
 *
 * <p>The statement of reasons is not decoration. The terms promise the person is told what
 * rule was broken, and a hosting service in the EU owes that by law. So every action but a
 * dismissal ends with a {@link NotificationKind#SANCTION} to the target; a suspension or ban
 * is also authoritative at sign-in, where the reason is returned whether or not the push
 * arrived.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SanctionExecutor {

    /** Where an appeal goes. The terms name the same address; kept here so the notice can too. */
    public static final String APPEAL_ADDRESS = "contact@findgamebuddy.com";

    private static final DateTimeFormatter UNTIL =
            DateTimeFormatter.ofPattern("d MMM HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final GamerRepository gamerRepository;
    private final ModerationActionRepository actions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * Applies an action to an account, records it, and tells the person.
     *
     * @param target the account, already loaded and about to be saved by the caller
     * @param caseId the case this closed, or null for a direct action (a ban from the
     *     accounts tab)
     * @param actorId the moderator
     * @param action the ladder step
     * @param reasonCode the rule, in the reporters' vocabulary; null on a dismissal
     * @param note the moderator's words; required for a ban
     * @param removePhoto whether to pull the picture alongside the action
     * @return the recorded action
     */
    @Transactional
    public ModerationAction apply(
            Gamer target,
            UUID caseId,
            String actorId,
            Action action,
            ContentReport.ReasonCode reasonCode,
            String note,
            boolean removePhoto) {

        if (action == Action.BAN && (note == null || note.isBlank())) {
            throw new BusinessException(TransactionCode.MODERATION_NOTE_REQUIRED);
        }

        Instant now = clock.instant();
        Instant expiresAt = null;

        boolean photoWentWithIt = removePhoto || action == Action.REMOVE_PHOTO;
        if (photoWentWithIt && target.getAvatarStatus() != null) {
            // Same as the review path's REJECT: the object stays in the private bucket so an
            // appeal has something to look at and a re-upload of the same picture is not a
            // fresh first offence. REJECTED is not shown to anyone, which is the removal.
            target.setAvatarStatus(AvatarStatus.REJECTED);
            target.setAvatarHeldAt(null);
        }

        switch (action) {
            case DISMISS, WARN, REMOVE_PHOTO -> {
                // No account block. A warning is a warning; the photo case is handled above.
            }
            case SUSPEND_24H, SUSPEND_7D -> {
                Duration d = action.suspension();
                expiresAt = now.plus(d);
                target.setIsBlocked(true);
                target.setSuspendedUntil(expiresAt);
                // Every live token dies now; the block is not only enforced at next sign-in.
                target.revokeIssuedTokens();
            }
            case BAN -> {
                target.setIsBlocked(true);
                target.setSuspendedUntil(null); // null with is_blocked = true means permanent
                target.revokeIssuedTokens();
            }
            case UNBAN -> {
                target.setIsBlocked(false);
                target.setSuspendedUntil(null);
            }
        }
        gamerRepository.save(target);

        ModerationAction row = new ModerationAction();
        row.setId(UUID.randomUUID());
        row.setCaseId(caseId);
        row.setTargetId(target.getUserId());
        row.setActorId(actorId);
        row.setAction(action);
        row.setReasonCode(reasonCode);
        row.setNote(note);
        row.setPhotoRemoved(photoWentWithIt);
        row.setExpiresAt(expiresAt);
        row.setCreatedAt(now);
        actions.save(row);

        if (action != Action.DISMISS && action != Action.UNBAN) {
            notifyTarget(target, action, reasonCode, expiresAt);
        }

        log.info(
                "Moderator {} applied {} to {}{}",
                actorId,
                action,
                target.getUserId(),
                caseId == null ? "" : " (case " + caseId + ")");
        return row;
    }

    /**
     * The statement of reasons. Names the rule and, for a suspension, when it ends, and
     * points at the appeal address. Never names a reporter — the target learns what they
     * did, not who objected.
     */
    private void notifyTarget(Gamer target, Action action, ContentReport.ReasonCode reason, Instant expiresAt) {
        String rule = ruleText(reason);
        String title;
        String body;
        switch (action) {
            case WARN -> {
                title = "A warning about your account";
                body = "A moderator reviewed a report about " + rule + ". Please read the community rules. "
                        + "If you think this is a mistake, write to " + APPEAL_ADDRESS + ".";
            }
            case REMOVE_PHOTO -> {
                title = "Your photo was removed";
                body = "Your profile picture was removed after a report about " + rule
                        + ". You can upload a different one. To appeal, write to " + APPEAL_ADDRESS + ".";
            }
            case SUSPEND_24H, SUSPEND_7D -> {
                title = "Your account is suspended";
                body = "Your account is suspended until " + UNTIL.format(expiresAt) + " after a report about " + rule
                        + ". To appeal, write to " + APPEAL_ADDRESS + ".";
            }
            case BAN -> {
                title = "Your account has been closed";
                body = "Your account was closed after a report about " + rule + ". To appeal, write to "
                        + APPEAL_ADDRESS + ".";
            }
            default -> {
                return;
            }
        }
        // No fcmToken lookup here: the event carries the recipient id and the dispatcher
        // resolves the token and the preference from it. A suspended user with a dead token
        // still meets the reason at sign-in, which is the delivery that has to work.
        events.publishEvent(new NotificationRequestedEvent(
                target.getUserId(), target.getFcmToken(), title, body, NotificationKind.SANCTION));
    }

    /** The rule in plain words, without naming a reporter or quoting the report. */
    private static String ruleText(ContentReport.ReasonCode reason) {
        if (reason == null) {
            return "your conduct";
        }
        return switch (reason) {
            case HARASSMENT -> "harassment or abuse";
            case SEXUAL -> "sexual or explicit content";
            case SPAM_SCAM -> "spam or a scam";
            case UNDERAGE -> "an age below 18";
            case IMPERSONATION -> "impersonation";
            case OTHER -> "your conduct";
        };
    }
}
