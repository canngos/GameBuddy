package com.gamebuddy.community.domain.service;

import com.gamebuddy.common.base.BaseBody;
import com.gamebuddy.common.base.Status;
import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.enums.TransactionCode;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.util.Ids;
import com.gamebuddy.community.infrastructure.entity.*;
import com.gamebuddy.community.infrastructure.entity.ContentReport.ContentType;
import com.gamebuddy.community.infrastructure.repository.*;
import com.gamebuddy.community.interfaces.dto.ReportDto;
import com.gamebuddy.community.interfaces.dto.ReportsResponseBody;
import com.gamebuddy.community.interfaces.request.ReportRequest;
import com.gamebuddy.community.interfaces.response.ReportsResponse;
import com.gamebuddy.shared.entity.*;
import com.gamebuddy.shared.repository.*;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final GamerRepository gamerRepository;
    private final Clock clock;

    @Override
    @Transactional
    public DefaultMessageResponse reportPost(Gamer principal, String postId, ReportRequest request) {
        Gamer reporter = reload(principal);
        Post post = postRepository
                .findById(Ids.uuid(postId))
                .orElseThrow(() -> new BusinessException(TransactionCode.POST_NOT_FOUND));

        // You have to be able to see something to report it.
        requireMember(post.getCommunity(), reporter);
        return fileReport(ContentType.POST, post.getPostId(), post.getOwner(), reporter, request);
    }

    @Override
    @Transactional
    public DefaultMessageResponse reportComment(Gamer principal, String commentId, ReportRequest request) {
        Gamer reporter = reload(principal);
        Comment comment = commentRepository
                .findById(Ids.uuid(commentId))
                .orElseThrow(() -> new BusinessException(TransactionCode.COMMENT_NOT_FOUND));

        requireMember(comment.getPost().getCommunity(), reporter);
        return fileReport(ContentType.COMMENT, comment.getCommentId(), comment.getOwner(), reporter, request);
    }

    /**
     * Reports a gamer.
     *
     * <p>No visibility check, unlike a post or a comment. Anyone can be shown anyone on
     * the deck, and the profiles that most need reporting are exactly the ones the
     * reporter has no relationship with — requiring a match first would mean accepting
     * somebody before you can complain about them.
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
     * Removes the content and closes every open report against it.
     *
     * <p>Closing them together matters: popular content attracts many reports, and
     * resolving them one at a time would leave a queue of duplicates pointing at
     * something that no longer exists.
     */
    @Override
    @Transactional
    public DefaultMessageResponse actionReport(Gamer principal, String reportId) {
        Gamer admin = requireAdmin(principal);
        ContentReport report = requireReport(reportId);

        switch (report.getContentType()) {
            case POST ->
                postRepository.findById(report.getContentId()).ifPresent(post -> {
                    post.getCommunity().getPosts().remove(post);
                    postRepository.delete(post);
                });
            case COMMENT ->
                commentRepository.findById(report.getContentId()).ifPresent(comment -> {
                    comment.getPost().getComments().remove(comment);
                    commentRepository.delete(comment);
                });
            // Nothing to delete: the content is a person. Actioning it means the moderator
            // has judged the complaint founded and closes the queue entry; the sanction —
            // a ban — is a separate, deliberate call to the admin endpoints, because
            // "remove this" and "remove this account" should not be the same button.
            //
            // Explicit rather than a default branch. The `else` this replaced treated
            // anything that was not a POST as a comment, so a profile report would have
            // gone looking for a comment with a gamer's id.
            case PROFILE -> log.info("Profile report {} actioned against {}", reportId, report.getAuthorId());
        }

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

        // Worded from what actually happened. A profile report closes without removing
        // anything, and telling a moderator "content removed" when nothing was would
        // leave them believing the account had been dealt with.
        String what = report.getContentType() == ContentType.PROFILE ? "Report upheld" : "Content removed";
        return DefaultMessageResponse.of(what + " and " + siblings.size() + " report(s) closed");
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
     * Builds the moderator's view, including the text being complained about.
     *
     * <p>The chat equivalent used to blank the message body when it was reported, leaving
     * moderators a queue of rows that said nothing. The content is shown here, and the
     * author's standing across all their content comes with it so the decision can be
     * about the gamer rather than the single item.
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

        dto.setContent(currentText(report));
        return dto;
    }

    /** Null once the content has been removed, which is a legitimate state for a closed report. */
    private String currentText(ContentReport report) {
        return switch (report.getContentType()) {
            case POST ->
                postRepository
                        .findById(report.getContentId())
                        .map(Post::getBody)
                        .orElse(null);
            case COMMENT ->
                commentRepository
                        .findById(report.getContentId())
                        .map(Comment::getMessage)
                        .orElse(null);
            // A profile has no body text. What the moderator needs to look at is the
            // account itself, and `authorUsername` on the DTO is what points them at it.
            case PROFILE -> null;
        };
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

    private void requireMember(Community community, Gamer gamer) {
        if (!community.hasMember(gamer) && gamer.getRole() != Role.ADMIN) {
            throw new BusinessException(TransactionCode.NOT_MEMBER);
        }
    }
}
