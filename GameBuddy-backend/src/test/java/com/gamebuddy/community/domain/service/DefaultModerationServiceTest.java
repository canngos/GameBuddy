package com.gamebuddy.community.domain.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gamebuddy.common.enums.Role;
import com.gamebuddy.common.exception.BusinessException;
import com.gamebuddy.community.infrastructure.entity.ContentReport;
import com.gamebuddy.community.infrastructure.entity.ContentReport.ContentType;
import com.gamebuddy.community.infrastructure.repository.CommentRepository;
import com.gamebuddy.community.infrastructure.repository.ContentReportRepository;
import com.gamebuddy.community.infrastructure.repository.PostRepository;
import com.gamebuddy.community.interfaces.request.ReportRequest;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.repository.GamerRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultModerationServiceTest {

    @InjectMocks
    private DefaultModerationService moderationService;

    @Mock
    private ContentReportRepository reportRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private GamerRepository gamerRepository;

    private Gamer reporter;
    private Gamer target;

    @BeforeEach
    void setUp() {
        reporter = newGamer();
        target = newGamer();
        when(gamerRepository.findById(reporter.getUserId())).thenReturn(Optional.of(reporter));
        when(gamerRepository.findById(target.getUserId())).thenReturn(Optional.of(target));
        when(reportRepository.save(any(ContentReport.class))).thenAnswer(i -> i.getArgument(0));
    }

    /** Ids are UUIDs, which is what lets a profile report reuse the uuid content_id column. */
    private static Gamer newGamer() {
        Gamer gamer = new Gamer();
        gamer.setUserId(UUID.randomUUID().toString());
        gamer.setGamerUsername("g" + UUID.randomUUID().toString().substring(0, 6));
        gamer.setRole(Role.USER);
        return gamer;
    }

    private static ReportRequest request(String reason) {
        ReportRequest r = new ReportRequest();
        r.setReason(reason);
        return r;
    }

    @Nested
    class ReportingAProfile {

        @Test
        @DisplayName("the reported gamer is both the content and its author")
        void testReportProfile_whenValid_FilesAgainstTheGamerThemselves() {
            moderationService.reportProfile(reporter, target.getUserId(), request("Sexual content"));

            ArgumentCaptor<ContentReport> saved = ArgumentCaptor.captor();
            verify(reportRepository).save(saved.capture());
            ContentReport report = saved.getValue();

            assertEquals(ContentType.PROFILE, report.getContentType());
            // A profile is the one piece of content whose author is the content. The
            // moderator queue reads authorId to find the account; contentId is what the
            // one-report-per-reporter constraint keys on.
            assertEquals(target.getUserId(), report.getAuthorId());
            assertEquals(UUID.fromString(target.getUserId()), report.getContentId());
            assertEquals(reporter.getUserId(), report.getReporterId());
            assertEquals("Sexual content", report.getReason());
            assertEquals(ContentReport.Status.OPEN, report.getStatus());
        }

        @Test
        @DisplayName("no match or membership is needed — the deck shows strangers")
        void testReportProfile_whenNoRelationship_StillAllowed() {
            // Deliberately the opposite of a post, which requires membership of the
            // community it is in. The profiles that most need reporting are the ones the
            // reporter has no relationship with, and requiring a match first would mean
            // accepting somebody before you could complain about them.
            assertDoesNotThrow(
                    () -> moderationService.reportProfile(reporter, target.getUserId(), request("Harassment")));
            verify(reportRepository).save(any(ContentReport.class));
        }

        @Test
        void testReportProfile_whenReportingYourself_ReturnErrorCode148() {
            String self = reporter.getUserId();
            ReportRequest req = request("Spam");

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> moderationService.reportProfile(reporter, self, req));
            assertEquals(148, ex.getTransactionCode().getId());
            verify(reportRepository, never()).save(any());
        }

        @Test
        @DisplayName("one report each, so nobody can inflate the count against someone")
        void testReportProfile_whenAlreadyReported_ReturnErrorCode157() {
            when(reportRepository.existsByContentTypeAndContentIdAndReporterId(
                            eq(ContentType.PROFILE), any(UUID.class), eq(reporter.getUserId())))
                    .thenReturn(true);
            String id = target.getUserId();
            ReportRequest req = request("Spam");

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> moderationService.reportProfile(reporter, id, req));
            assertEquals(157, ex.getTransactionCode().getId());
        }

        @Test
        void testReportProfile_whenGamerUnknown_ReturnErrorCode103() {
            when(gamerRepository.findById("nobody")).thenReturn(Optional.empty());
            ReportRequest req = request("Spam");

            BusinessException ex = assertThrows(
                    BusinessException.class, () -> moderationService.reportProfile(reporter, "nobody", req));
            assertEquals(103, ex.getTransactionCode().getId());
        }

        @Test
        @DisplayName("a deleted account has nobody left to moderate")
        void testReportProfile_whenAccountDeleted_ReturnErrorCode156() {
            target.setDeletedAt(Instant.now());
            String id = target.getUserId();
            ReportRequest req = request("Spam");

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> moderationService.reportProfile(reporter, id, req));
            assertEquals(156, ex.getTransactionCode().getId());
        }
    }

    @Nested
    class ActioningAProfileReport {

        private Gamer admin;
        private ContentReport report;

        @BeforeEach
        void setUp() {
            admin = newGamer();
            admin.setRole(Role.ADMIN);
            when(gamerRepository.findById(admin.getUserId())).thenReturn(Optional.of(admin));

            report = new ContentReport();
            report.setId(UUID.randomUUID());
            report.setContentType(ContentType.PROFILE);
            report.setContentId(UUID.fromString(target.getUserId()));
            report.setAuthorId(target.getUserId());
            report.setReporterId(reporter.getUserId());
            when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
            when(reportRepository.findAllByContentTypeAndContentIdAndStatus(
                            ContentType.PROFILE, report.getContentId(), ContentReport.Status.OPEN))
                    .thenReturn(List.of(report));
        }

        @Test
        @DisplayName("a profile report does not go looking for a comment to delete")
        void testActionReport_whenProfile_DeletesNothing() {
            // The dispatch used to be `if POST … else comment`, so anything that was not a
            // post was treated as one — a profile report would have queried the comment
            // table with a gamer's id.
            moderationService.actionReport(admin, report.getId().toString());

            verify(postRepository, never()).delete(any());
            verify(commentRepository, never()).delete(any());
            verify(commentRepository, never()).findById(any(UUID.class));
        }

        @Test
        void testActionReport_whenProfile_ClosesTheReport() {
            moderationService.actionReport(admin, report.getId().toString());

            assertEquals(ContentReport.Status.ACTIONED, report.getStatus());
            assertEquals(admin.getUserId(), report.getReviewedBy());
            assertNotNull(report.getReviewedAt());
        }

        @Test
        @DisplayName("the message says what happened, not that content was removed")
        void testActionReport_whenProfile_DoesNotClaimContentWasRemoved() {
            // Telling a moderator "content removed" when nothing was would leave them
            // believing the account had been dealt with.
            String message = moderationService
                    .actionReport(admin, report.getId().toString())
                    .getBody()
                    .getData()
                    .getMessage();

            assertTrue(message.startsWith("Report upheld"), message);
        }

        @Test
        void testActionReport_whenNotAdmin_ReturnErrorCode140() {
            String id = report.getId().toString();

            BusinessException ex =
                    assertThrows(BusinessException.class, () -> moderationService.actionReport(reporter, id));
            assertEquals(140, ex.getTransactionCode().getId());
        }
    }
}
