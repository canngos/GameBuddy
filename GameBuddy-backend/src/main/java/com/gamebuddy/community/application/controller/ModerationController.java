package com.gamebuddy.community.application.controller;

import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.community.domain.service.ModerationService;
import com.gamebuddy.community.interfaces.request.ReportRequest;
import com.gamebuddy.community.interfaces.response.ReportsResponse;
import com.gamebuddy.shared.entity.Gamer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Reporting community content, and the moderator queue that answers it.
 *
 * <p>The moderation routes sit under {@code /community/admin} so the ingress can restrict
 * them by source address alongside auth-service's {@code /admin}.
 */
@RestController
@RequestMapping("/community")
@RequiredArgsConstructor
public class ModerationController {

    private final ModerationService moderationService;

    @PostMapping("/report/post/{postId}")
    public ResponseEntity<DefaultMessageResponse> reportPost(
            @AuthenticationPrincipal Gamer principal,
            @PathVariable String postId,
            @Valid @RequestBody ReportRequest request) {
        return ResponseEntity.ok(moderationService.reportPost(principal, postId, request));
    }

    /**
     * Reports a gamer's profile.
     *
     * <p>Under {@code /community} despite not being community content, because that is
     * where the report queue is. One queue a moderator checks beats two, one of which they
     * forget.
     */
    @PostMapping("/report/profile/{userId}")
    public ResponseEntity<DefaultMessageResponse> reportProfile(
            @AuthenticationPrincipal Gamer principal,
            @PathVariable String userId,
            @Valid @RequestBody ReportRequest request) {
        return ResponseEntity.ok(moderationService.reportProfile(principal, userId, request));
    }

    @PostMapping("/report/comment/{commentId}")
    public ResponseEntity<DefaultMessageResponse> reportComment(
            @AuthenticationPrincipal Gamer principal,
            @PathVariable String commentId,
            @Valid @RequestBody ReportRequest request) {
        return ResponseEntity.ok(moderationService.reportComment(principal, commentId, request));
    }

    /** The moderation queue, oldest first. */
    @GetMapping("/admin/reports")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReportsResponse> getOpenReports(
            @AuthenticationPrincipal Gamer principal, @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(moderationService.getOpenReports(principal, pageable));
    }

    /** Removes the content and closes every open report against it. */
    @PostMapping("/admin/reports/{reportId}/action")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DefaultMessageResponse> actionReport(
            @AuthenticationPrincipal Gamer principal, @PathVariable String reportId) {
        return ResponseEntity.ok(moderationService.actionReport(principal, reportId));
    }

    @PostMapping("/admin/reports/{reportId}/dismiss")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DefaultMessageResponse> dismissReport(
            @AuthenticationPrincipal Gamer principal, @PathVariable String reportId) {
        return ResponseEntity.ok(moderationService.dismissReport(principal, reportId));
    }
}
