package com.gamebuddy.moderation.domain.service;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.ratelimit.RateLimiter;
import com.gamebuddy.common.util.Ids;
import com.gamebuddy.match.domain.service.chat.ChatModerationService;
import com.gamebuddy.match.interfaces.dto.MessageForReport;
import com.gamebuddy.match.interfaces.dto.ReportedMessageDto;
import com.gamebuddy.moderation.config.ModerationProperties;
import com.gamebuddy.moderation.domain.service.ReportPolicy.Tally;
import com.gamebuddy.moderation.infrastructure.entity.ContentReport;
import com.gamebuddy.moderation.infrastructure.entity.ContentReport.ContentType;
import com.gamebuddy.moderation.infrastructure.entity.ContentReport.ReasonCode;
import com.gamebuddy.moderation.infrastructure.entity.ModerationAction;
import com.gamebuddy.moderation.infrastructure.entity.ModerationCase;
import com.gamebuddy.moderation.infrastructure.repository.ContentReportRepository;
import com.gamebuddy.moderation.infrastructure.repository.ModerationActionRepository;
import com.gamebuddy.moderation.infrastructure.repository.ModerationCaseRepository;
import com.gamebuddy.moderation.interfaces.dto.CaseDetailDto;
import com.gamebuddy.moderation.interfaces.dto.CaseSummaryDto;
import com.gamebuddy.moderation.interfaces.request.ReportRequest;
import com.gamebuddy.moderation.interfaces.request.ResolveCaseRequest;
import com.gamebuddy.moderation.interfaces.response.CaseDetailResponse;
import com.gamebuddy.moderation.interfaces.response.CasesResponse;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.event.NotificationKind;
import com.gamebuddy.shared.event.NotificationRequestedEvent;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Reports become cases, and a case is one decision about one person.
 *
 * <p>The old queue was a list of rows a moderator worked through one at a time, which meant
 * the second person to review the same account saw none of what the first had read, and a
 * report was counted rather than weighed. This is the redesign: every report about a gamer
 * joins the one open case against them, the case carries a weighted score the automatic
 * policy reads, and a moderator resolves the whole case with a single step from the ladder
 * in {@link ModerationAction.Action}.
 *
 * <p>What is automatic is deliberately weak — {@link ReportPolicy} can hide a profile from
 * decks and mark a case urgent, and nothing more. Every sanction that touches an account is
 * a person's decision, carried out by {@link SanctionExecutor}, which is also the only thing
 * that tells the target what rule they broke.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultModerationService implements ModerationService {

    private static final Duration REVIEW_SLA = Duration.ofHours(24);

    private final ContentReportRepository reportRepository;
    private final ModerationCaseRepository caseRepository;
    private final ModerationActionRepository actionRepository;
    private final GamerRepository gamerRepository;
    private final ChatModerationService chatModeration;
    private final ReportPolicy policy;
    private final SanctionExecutor sanctions;
    private final ModerationProperties properties;
    private final RateLimiter reportRateLimiter;
    private final ApplicationEventPublisher events;
    private final ObjectMapper json;
    private final Clock clock;

    // === Filing ============================================================

    @Override
    @Transactional
    public DefaultMessageResponse reportProfile(Gamer principal, String userId, ReportRequest request) {
        Gamer reporter = reload(principal);
        Gamer target = gamerRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));
        if (target.getDeletedAt() != null) {
            throw new BusinessException(TransactionCode.ACCOUNT_DELETED);
        }

        ReasonCode reason = reasonOf(request);
        String evidence = ReportEvidence.ofProfile(target, clock.instant()).toJson(json);
        return file(
                reporter, target, ContentType.PROFILE, Ids.uuid(target.getUserId()), null, reason, request, evidence);
    }

    @Override
    @Transactional
    public DefaultMessageResponse reportMessage(Gamer principal, UUID messageId, ReportRequest request) {
        Gamer reporter = reload(principal);

        // Chat owns the conversation: it checks the reporter received the message and hands
        // back who sent it and the ids either side. Decryption stays on chat's audited path.
        MessageForReport described =
                chatModeration.prepareReport(messageId, reporter.getUserId(), properties.getContextMessages());

        Gamer target = gamerRepository
                .findById(described.senderId())
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));

        ReasonCode reason = reasonOf(request);
        String evidence = ReportEvidence.ofMessage(
                        described.messageId(), described.roomId(), described.contextIds(), clock.instant())
                .toJson(json);
        return file(
                reporter,
                target,
                ContentType.MESSAGE,
                described.messageId(),
                described.roomId(),
                reason,
                request,
                evidence);
    }

    /**
     * The shared filing path: budget, self-report guard, one-open-case-per-target, dedup,
     * the row, then the policy.
     */
    private DefaultMessageResponse file(
            Gamer reporter,
            Gamer target,
            ContentType type,
            UUID contentId,
            UUID roomId,
            ReasonCode reason,
            ReportRequest request,
            String evidence) {

        if (target.getUserId().equals(reporter.getUserId())) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "you cannot report yourself");
        }
        // Required only when the reporter explicitly chose OTHER. A legacy build sends no
        // code at all and is defaulted to OTHER above; refusing it for a missing note would
        // break reporting for everyone who has not updated yet.
        if (request.getReasonCode() == ReasonCode.OTHER
                && (request.getNote() == null || request.getNote().isBlank())) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "a note is required for 'other'");
        }
        Instant now = clock.instant();
        ModerationCase moderationCase = openCaseFor(target.getUserId(), now);

        // One report per reporter per case: reporting the same person twice while a case is
        // open adds nothing and would double their weight. Checked before the budget so a
        // duplicate tap does not burn a permit -- the in-memory limiter is not transactional,
        // so a token spent here is not refunded when this throws.
        if (reportRepository.existsByCaseIdAndReporterId(moderationCase.getId(), reporter.getUserId())) {
            throw new BusinessException(TransactionCode.ALREADY_REPORTED);
        }

        // Per-reporter budget. The one lever a single account has left for flooding the queue,
        // since reports are already one per reporter per case. Generous; see the config.
        if (!reportRateLimiter.tryAcquire(reporter.getUserId())) {
            throw new BusinessException(TransactionCode.RATE_LIMITED);
        }

        ContentReport report = new ContentReport();
        report.setContentType(type);
        report.setContentId(contentId);
        report.setAuthorId(target.getUserId());
        report.setReporterId(reporter.getUserId());
        report.setReasonCode(reason);
        report.setNote(request.getNote() != null ? request.getNote() : request.getReason());
        report.setReason(request.getReason());
        report.setRoomId(roomId);
        report.setEvidence(evidence);
        report.setCaseId(moderationCase.getId());
        report.setStatus(ContentReport.Status.OPEN);
        reportRepository.save(report);

        applyPolicy(moderationCase, target, reason, now);
        caseRepository.save(moderationCase);
        gamerRepository.save(target);

        return DefaultMessageResponse.of("Reported. A moderator will review it.");
    }

    /** The open case for a target, or a fresh one. Serialised per target so two reports do not race to create it. */
    private ModerationCase openCaseFor(String targetId, Instant now) {
        // Held for the rest of the transaction, so a concurrent first report against the same
        // target waits here rather than both inserting a case and tripping the unique index.
        caseRepository.lockTarget(targetId);
        return caseRepository.findOpenForUpdate(targetId).orElseGet(() -> {
            ModerationCase created = new ModerationCase();
            created.setId(UUID.randomUUID());
            created.setTargetId(targetId);
            created.setStatus(ModerationCase.Status.OPEN);
            created.setOpenedAt(now);
            created.setLastReportAt(now);
            return caseRepository.save(created);
        });
    }

    /** Recomputes the case's standing from its reports and applies the automatic, reversible actions. */
    private void applyPolicy(ModerationCase moderationCase, Gamer target, ReasonCode reason, Instant now) {
        List<ContentReport> reports = reportRepository.findByCaseIdOrderByCreatedAtAsc(moderationCase.getId());
        Map<String, Gamer> reporters =
                gamerRepository
                        .findAllById(reports.stream()
                                .map(ContentReport::getReporterId)
                                .collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(Gamer::getUserId, g -> g));

        Tally tally = policy.tally(reports, reporters, now);
        moderationCase.setWeightedScore(tally.score());
        moderationCase.setDistinctReporters(tally.distinctReporters());
        moderationCase.setLastReportAt(now);

        boolean urgent = policy.isUrgent(reason);
        if (policy.warrantsHiding(tally) && !moderationCase.isAutoHidden()) {
            // Out of every deck until a person decides. Reversible, and undone by whatever
            // decision closes the case. Not a verdict — several people said the same thing.
            target.setHiddenFromDiscovery(true);
            moderationCase.setAutoHidden(true);
            urgent = true;
            log.info(
                    "Auto-hid {} pending review: {} distinct reporters, score {}",
                    target.getUserId(),
                    tally.distinctReporters(),
                    tally.score());
        }
        if (urgent) {
            moderationCase.setStatus(ModerationCase.Status.URGENT);
        }
    }

    // === The queue =========================================================

    @Override
    @Transactional(readOnly = true)
    public CasesResponse getCases(Gamer principal, int limit) {
        requireAdmin(principal);
        List<ModerationCase> cases = caseRepository.queue(
                List.of(ModerationCase.Status.OPEN, ModerationCase.Status.URGENT), PageRequest.of(0, limit));
        Map<String, Gamer> targets =
                gamerRepository
                        .findAllById(
                                cases.stream().map(ModerationCase::getTargetId).collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(Gamer::getUserId, g -> g));

        List<CaseSummaryDto> summaries =
                cases.stream().map(c -> summarise(c, targets)).toList();
        CasesResponse response = new CasesResponse();
        CasesResponse.Body body = new CasesResponse.Body();
        body.setCases(summaries);
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public CaseDetailResponse getCase(Gamer principal, String caseId) {
        Gamer moderator = requireAdmin(principal);
        ModerationCase moderationCase = caseRepository
                .findById(Ids.uuid(caseId))
                .orElseThrow(() -> new BusinessException(TransactionCode.CASE_NOT_FOUND));

        Gamer target = gamerRepository.findById(moderationCase.getTargetId()).orElse(null);
        List<ContentReport> reports = reportRepository.findByCaseIdOrderByCreatedAtAsc(moderationCase.getId());
        Map<String, Gamer> reporters =
                gamerRepository
                        .findAllById(reports.stream()
                                .map(ContentReport::getReporterId)
                                .collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(Gamer::getUserId, g -> g));

        Instant now = clock.instant();
        CaseDetailDto detail = new CaseDetailDto();
        detail.setSummary(summarise(moderationCase, target == null ? Map.of() : Map.of(target.getUserId(), target)));
        if (target != null) {
            detail.setTargetUsername(target.getGamerUsername());
            detail.setTargetAvatarKey(target.getAvatarKey());
            detail.setTargetAvatarStatus(
                    target.getAvatarStatus() == null
                            ? null
                            : target.getAvatarStatus().name());
            detail.setTargetJoinedAt(target.getCreatedDate());
            detail.setTargetSuspended(target.isSuspended(now));
            detail.setTargetSuspendedUntil(target.getSuspendedUntil());
        }

        List<CaseDetailDto.ReportItemDto> items = new ArrayList<>();
        Set<UUID> messageIds = new LinkedHashSet<>();
        for (ContentReport report : reports) {
            CaseDetailDto.ReportItemDto item = new CaseDetailDto.ReportItemDto();
            item.setReportId(report.getId().toString());
            item.setContentType(report.getContentType().name());
            item.setReasonCode(
                    report.getReasonCode() == null
                            ? null
                            : report.getReasonCode().name());
            item.setNote(report.getNote());
            item.setReporterId(report.getReporterId());
            Gamer reporter = reporters.get(report.getReporterId());
            item.setReporterWeight(
                    reporter == null ? "1.000" : policy.weightOf(reporter, now).toPlainString());
            item.setCreatedAt(report.getCreatedAt());
            item.setEvidence(report.getEvidence());
            items.add(item);

            collectContextIds(report, messageIds);
        }
        detail.setReports(items);

        // The one audited plaintext read: the union of every message these reports pointed at.
        if (!messageIds.isEmpty()) {
            List<ReportedMessageDto> context = chatModeration.readForModerator(moderator, new ArrayList<>(messageIds));
            detail.setMessageContext(context.stream()
                    .map(m -> {
                        CaseDetailDto.ReportedMessageContextDto dto = new CaseDetailDto.ReportedMessageContextDto();
                        dto.setMessageId(m.id());
                        dto.setSenderId(m.senderId());
                        dto.setSenderUsername(m.senderUsername());
                        dto.setMessage(m.message());
                        dto.setSentAt(m.sentAt());
                        dto.setReported(m.reportedAt() != null);
                        return dto;
                    })
                    .toList());
        }

        detail.setHistory(
                actionRepository
                        .findByTargetIdOrderByCreatedAtDesc(moderationCase.getTargetId(), PageRequest.of(0, 20))
                        .stream()
                        .map(this::toActionDto)
                        .toList());

        CaseDetailResponse response = new CaseDetailResponse();
        CaseDetailResponse.Body body = new CaseDetailResponse.Body();
        body.setCaseDetail(detail);
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    // === The decision ======================================================

    @Override
    @Transactional
    public DefaultMessageResponse resolveCase(Gamer principal, String caseId, ResolveCaseRequest request) {
        Gamer moderator = requireAdmin(principal);
        ModerationCase moderationCase = caseRepository
                .findById(Ids.uuid(caseId))
                .orElseThrow(() -> new BusinessException(TransactionCode.CASE_NOT_FOUND));
        if (!moderationCase.isOpen()) {
            throw new BusinessException(TransactionCode.CASE_NOT_FOUND);
        }

        ModerationAction.Action action = request.getAction();
        if (action == ModerationAction.Action.UNBAN) {
            // UNBAN is an audit shape for lifting a block from the accounts tab, not a way to
            // close a case; a case is not the place to un-ban someone. Reject it here so the
            // enum's own documentation ("Never a case outcome") is actually enforced.
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "a case cannot be resolved by unbanning");
        }
        List<ContentReport> reports = reportRepository.findByCaseIdOrderByCreatedAtAsc(moderationCase.getId());
        ReasonCode reason = request.getReasonCode() != null ? request.getReasonCode() : dominantReason(reports);

        Gamer target = gamerRepository
                .findById(moderationCase.getTargetId())
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));

        sanctions.apply(
                target,
                moderationCase.getId(),
                moderator.getUserId(),
                action,
                reason,
                request.getNote(),
                request.isRemovePhoto());

        // The decision supersedes the automatic hide, whichever way it went: a ban blocks the
        // account anyway, and anything lighter means this person is not to be hidden by a
        // machine any more. (SanctionExecutor already saved the target; this is one more field.)
        if (moderationCase.isAutoHidden() || target.isHiddenFromDiscovery()) {
            target.setHiddenFromDiscovery(false);
            gamerRepository.save(target);
        }

        Instant now = clock.instant();
        closeReports(
                reports,
                moderator,
                action.upholds() ? ContentReport.Status.ACTIONED : ContentReport.Status.DISMISSED,
                now);
        settleReporters(reports, action.upholds());

        moderationCase.setStatus(ModerationCase.Status.CLOSED);
        moderationCase.setClosedAt(now);
        moderationCase.setClosedBy(moderator.getUserId());
        moderationCase.setOutcome(action.name());
        caseRepository.save(moderationCase);

        return DefaultMessageResponse.of("Case resolved: " + action.name());
    }

    private void closeReports(List<ContentReport> reports, Gamer admin, ContentReport.Status status, Instant now) {
        for (ContentReport report : reports) {
            report.setStatus(status);
            report.setReviewedBy(admin.getUserId());
            report.setReviewedAt(now);
        }
        reportRepository.saveAll(reports);
    }

    /**
     * Updates each distinct reporter's standing and tells them, once, what came of it.
     *
     * <p>One pass, not two: a reporter who has just crossed the low-trust line hears the
     * pointed "your reports are not landing" notice instead of the generic thank-you, never
     * both for the same decision. The feedback is what keeps honest people reporting; the
     * low-trust notice is what a serial false reporter needs to hear.
     */
    private void settleReporters(List<ContentReport> reports, boolean upheld) {
        Set<String> reporterIds =
                reports.stream().map(ContentReport::getReporterId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<Gamer> reporters = gamerRepository.findAllById(reporterIds);
        for (Gamer reporter : reporters) {
            String title;
            String body;
            if (upheld) {
                reporter.setReportsUpheld(reporter.getReportsUpheld() + 1);
                title = "We reviewed your report";
                body = "Thanks for the report you sent. We reviewed it and took action.";
            } else {
                reporter.setReportsDismissed(reporter.getReportsDismissed() + 1);
                if (policy.hasReachedLowTrustNotice(reporter)) {
                    // At the line, once. Their reports are still accepted and still reviewed;
                    // this only tells them the ones so far have not been landing.
                    title = "About your reports";
                    body = "Several reports you sent were reviewed and found not to break the rules. "
                            + "Please report only genuine problems so we can act on them quickly.";
                } else {
                    title = "We reviewed your report";
                    body = "Thanks for the report you sent. We reviewed it and did not find a rule was broken.";
                }
            }
            events.publishEvent(new NotificationRequestedEvent(
                    reporter.getUserId(), reporter.getFcmToken(), title, body, NotificationKind.REPORT_RESOLVED));
        }
        gamerRepository.saveAll(reporters);
    }

    // === Read by the admin module ==========================================

    @Override
    @Transactional(readOnly = true)
    public long openReportCount() {
        return caseRepository.countByStatusIn(List.of(ModerationCase.Status.OPEN, ModerationCase.Status.URGENT));
    }

    @Override
    @Transactional(readOnly = true)
    public long oldestOpenReportHours() {
        return caseRepository
                .oldestOpenAt()
                .map(oldest -> Duration.between(oldest, clock.instant()).toHours())
                .orElse(0L);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> reporterIdsWithActionedReports() {
        return reportRepository.reporterIdsByStatus(ContentReport.Status.ACTIONED);
    }

    // === Helpers ===========================================================

    private ReasonCode reasonOf(ReportRequest request) {
        // A build shipped before reasons were structured sends only the free-text reason.
        return request.getReasonCode() != null ? request.getReasonCode() : ReasonCode.OTHER;
    }

    /** The reason a moderator resolves under when they do not pick one: the most reported. */
    private ReasonCode dominantReason(List<ContentReport> reports) {
        return reports.stream()
                .map(ContentReport::getReasonCode)
                .filter(r -> r != null)
                .collect(Collectors.groupingBy(r -> r, Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(ReasonCode.OTHER);
    }

    private void collectContextIds(ContentReport report, Set<UUID> into) {
        if (report.getContentType() != ContentType.MESSAGE) {
            return;
        }
        ReportEvidence evidence = ReportEvidence.fromJson(json, report.getEvidence());
        if (evidence != null && evidence.contextIds() != null) {
            into.addAll(evidence.contextIds());
        } else {
            into.add(report.getContentId());
        }
    }

    private CaseSummaryDto summarise(ModerationCase moderationCase, Map<String, Gamer> targets) {
        CaseSummaryDto dto = new CaseSummaryDto();
        dto.setCaseId(moderationCase.getId().toString());
        dto.setTargetId(moderationCase.getTargetId());
        Gamer target = targets.get(moderationCase.getTargetId());
        dto.setTargetUsername(target == null ? null : target.getGamerUsername());
        dto.setStatus(moderationCase.getStatus().name());
        dto.setWeightedScore(moderationCase.getWeightedScore());
        dto.setDistinctReporters(moderationCase.getDistinctReporters());
        dto.setOpenedAt(moderationCase.getOpenedAt());
        long ageHours =
                Duration.between(moderationCase.getOpenedAt(), clock.instant()).toHours();
        dto.setAgeHours(ageHours);
        dto.setOverdue(moderationCase.isOpen() && ageHours >= REVIEW_SLA.toHours());
        dto.setAutoHidden(moderationCase.isAutoHidden());
        dto.setPriorSanctions(actionRepository
                .findByTargetIdOrderByCreatedAtDesc(moderationCase.getTargetId(), PageRequest.of(0, 50))
                .size());
        return dto;
    }

    private CaseDetailDto.ModerationActionDto toActionDto(ModerationAction action) {
        CaseDetailDto.ModerationActionDto dto = new CaseDetailDto.ModerationActionDto();
        dto.setAction(action.getAction().name());
        dto.setReasonCode(
                action.getReasonCode() == null ? null : action.getReasonCode().name());
        dto.setNote(action.getNote());
        dto.setActorId(action.getActorId());
        dto.setCreatedAt(action.getCreatedAt());
        dto.setExpiresAt(action.getExpiresAt());
        return dto;
    }

    private Gamer reload(Gamer principal) {
        return gamerRepository
                .findById(principal.getUserId())
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));
    }

    private Gamer requireAdmin(Gamer principal) {
        Gamer admin = reload(principal);
        if (admin.getRole() != Role.ADMIN) {
            throw new BusinessException(TransactionCode.NOT_ADMIN);
        }
        return admin;
    }
}
