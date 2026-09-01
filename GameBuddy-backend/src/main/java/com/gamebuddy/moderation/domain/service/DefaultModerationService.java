package com.gamebuddy.moderation.domain.service;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.util.Ids;
import com.gamebuddy.moderation.infrastructure.entity.ContentReport;
import com.gamebuddy.moderation.infrastructure.entity.ContentReport.ContentType;
import com.gamebuddy.moderation.infrastructure.repository.ContentReportRepository;
import com.gamebuddy.moderation.interfaces.dto.ReportDto;
import com.gamebuddy.moderation.interfaces.dto.ReportsResponseBody;
import com.gamebuddy.moderation.interfaces.request.ReportRequest;
import com.gamebuddy.moderation.interfaces.response.ReportsResponse;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultModerationService implements ModerationService {

    /**
     * How long a report may sit before the promise made in the terms is broken.
     *
     * <p>Apple requires a commitment to act on reports within 24 hours, and the terms make
     * one. This constant is that promise expressed where it can actually be measured.
     */
    private static final Duration REVIEW_SLA = Duration.ofHours(24);

    private final ContentReportRepository reportRepository;
    private final GamerRepository gamerRepository;
    private final Clock clock;

    /**
     * Reports a gamer.
     *
     * <p>No visibility check. Anyone can be shown anyone on the deck, and the profiles
     * that most need reporting are exactly the ones the reporter has no relationship with
     * — requiring a match first would mean accepting somebody before you can complain
     * about them.
     *
     * <p>The gamer's own id is both the content and the author. See
     * {@link ContentType#PROFILE}.
     */
    @Override
    @Transactional
    public DefaultMessageResponse reportProfile(Gamer principal, String userId, ReportRequest request) {
        Gamer reporter = reload(principal);
        Gamer target = gamerRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(TransactionCode.USER_NOT_FOUND));

        // A deleted account keeps its row so other people's content keeps its references,
        // but there is nobody left to moderate.
        if (target.getDeletedAt() != null) {
            throw new BusinessException(TransactionCode.ACCOUNT_DELETED);
        }

        return fileReport(ContentType.PROFILE, Ids.uuid(target.getUserId()), target.getUserId(), reporter, request);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportsResponse getOpenReports(Gamer principal, Pageable pageable) {
        requireAdmin(principal);

        List<ContentReport> reports =
                reportRepository.findAllByStatusOrderByCreatedAtAsc(ContentReport.Status.OPEN, pageable);
        Map<String, Gamer> authors =
                gamerRepository
                        .findAllById(
                                reports.stream().map(ContentReport::getAuthorId).collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(Gamer::getUserId, g -> g));

        List<ReportDto> dtos =
                reports.stream().map(report -> toDto(report, authors)).toList();

        ReportsResponse response = new ReportsResponse();
        ReportsResponseBody body = new ReportsResponseBody();
        body.setReports(dtos);
        response.setBody(new BaseBody<>(body));
        response.setStatus(new Status(TransactionCode.DEFAULT_100));
        return response;
    }

    /**
     * Upholds the report and closes every open report against the same content.
     *
     * <p>Closing them together matters: one target attracts many reports, and resolving
     * them one at a time would leave a queue of duplicates.
     *
     * <p>Nothing is deleted here any more. A PROFILE report's content is a person, and the
     * sanction — a ban — is a separate, deliberate call to the admin endpoints, because
     * "uphold this" and "remove this account" should not be the same button. Historical
     * POST/COMMENT reports can still be actioned; their content went down with the
     * community tables, so upholding them is bookkeeping.
     */
    @Override
    @Transactional
    public DefaultMessageResponse actionReport(Gamer principal, String reportId) {
        Gamer admin = requireAdmin(principal);
        ContentReport report = requireReport(reportId);

        List<ContentReport> siblings = reportRepository.findAllByContentTypeAndContentIdAndStatus(
                report.getContentType(), report.getContentId(), ContentReport.Status.OPEN);
        siblings.forEach(open -> close(open, admin, ContentReport.Status.ACTIONED));
        reportRepository.saveAll(siblings);

        log.info(
                "{} actioned {} {} on report {}",
                admin.getUserId(),
                report.getContentType(),
                report.getContentId(),
                reportId);

        return DefaultMessageResponse.of("Report upheld and " + siblings.size() + " report(s) closed");
    }

    @Override
    @Transactional
    public DefaultMessageResponse dismissReport(Gamer principal, String reportId) {
        Gamer admin = requireAdmin(principal);
        ContentReport report = requireReport(reportId);

        close(report, admin, ContentReport.Status.DISMISSED);
        reportRepository.save(report);
        return DefaultMessageResponse.of("Report dismissed");
    }

    @Override
    @Transactional(readOnly = true)
    public long openReportCount() {
        return reportRepository.countByStatus(ContentReport.Status.OPEN);
    }

    @Override
    @Transactional(readOnly = true)
    public long oldestOpenReportHours() {
        return reportRepository
                .oldestCreatedAt(ContentReport.Status.OPEN)
                .map(oldest -> Duration.between(oldest, clock.instant()).toHours())
                .orElse(0L);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> reporterIdsWithActionedReports() {
        return reportRepository.reporterIdsByStatus(ContentReport.Status.ACTIONED);
    }

    // =======================================================================

    private DefaultMessageResponse fileReport(
            ContentType type, UUID contentId, String authorId, Gamer reporter, ReportRequest request) {

        if (authorId.equals(reporter.getUserId())) {
            throw new BusinessException(TransactionCode.INVALID_REQUEST, "you cannot report your own content");
        }
        // One report per gamer per item, so a single person cannot inflate the count.
        if (reportRepository.existsByContentTypeAndContentIdAndReporterId(type, contentId, reporter.getUserId())) {
            throw new BusinessException(TransactionCode.ALREADY_REPORTED);
        }

        ContentReport report = new ContentReport();
        report.setContentType(type);
        report.setContentId(contentId);
        report.setAuthorId(authorId);
        report.setReporterId(reporter.getUserId());
        report.setReason(request.getReason());
        reportRepository.save(report);

        return DefaultMessageResponse.of("Reported. A moderator will review it.");
    }

    private void close(ContentReport report, Gamer admin, ContentReport.Status status) {
        report.setStatus(status);
        report.setReviewedBy(admin.getUserId());
        report.setReviewedAt(clock.instant());
    }

    /**
     * Builds the moderator's view. The author's standing across all their content comes
     * with it, so the decision can be about the gamer rather than the single item.
     */
    private ReportDto toDto(ContentReport report, Map<String, Gamer> authors) {
        ReportDto dto = new ReportDto();
        dto.setReportId(report.getId().toString());
        dto.setContentType(report.getContentType().name());
        dto.setContentId(report.getContentId().toString());
        dto.setAuthorId(report.getAuthorId());
        dto.setReporterId(report.getReporterId());
        dto.setReason(report.getReason());
        dto.setStatus(report.getStatus().name());
        dto.setCreatedAt(report.getCreatedAt());

        Gamer author = authors.get(report.getAuthorId());
        dto.setAuthorUsername(author == null ? null : author.getGamerUsername());
        dto.setAuthorOpenReportCount(
                reportRepository.countByAuthorIdAndStatus(report.getAuthorId(), ContentReport.Status.OPEN));

        Duration open = Duration.between(report.getCreatedAt(), clock.instant());
        dto.setAgeHours(open.toHours());
        dto.setOverdue(open.compareTo(REVIEW_SLA) > 0 && report.getStatus() == ContentReport.Status.OPEN);

        // A profile has no body text — `authorUsername` is what points the moderator at
        // the account. Historical POST/COMMENT rows lost their text with the community
        // tables, which is a legitimate state for a report whose surface was retired.
        dto.setContent(null);
        return dto;
    }

    private ContentReport requireReport(String reportId) {
        return reportRepository
                .findById(Ids.uuid(reportId))
                .orElseThrow(() -> new BusinessException(TransactionCode.MESSAGE_NOT_REPORTED));
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
