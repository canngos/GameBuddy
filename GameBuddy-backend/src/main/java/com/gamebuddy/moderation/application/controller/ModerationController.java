package com.gamebuddy.moderation.application.controller;

import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.moderation.domain.service.ModerationService;
import com.gamebuddy.moderation.interfaces.request.ReportRequest;
import com.gamebuddy.moderation.interfaces.response.ReportsResponse;
import com.gamebuddy.shared.entity.Gamer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profile reports, and the moderator queue.
 *
 * <p><b>The paths still say {@code /community}</b>, deliberately, though the community
 * module is gone: installed app builds call {@code /community/report/profile} from the
 * chat screen and the console calls {@code /community/admin/reports}, and a URL is an
 * annotation here but an update on their side. The admin routes also stay under a path
 * the ingress already restricts by source address alongside {@code /admin}.
 */
@RestController
@RequestMapping("/community")
@RequiredArgsConstructor
public class ModerationController {

    private final ModerationService moderationService;

    /** Reports a gamer's profile — the picture, the name, what they wrote about themselves. */
    @PostMapping("/report/profile/{userId}")
    public ResponseEntity<DefaultMessageResponse> reportProfile(
            @AuthenticationPrincipal Gamer principal,
            @PathVariable String userId,
            @Valid @RequestBody ReportRequest request) {
        return ResponseEntity.ok(moderationService.reportProfile(principal, userId, request));
    }

    /** The moderation queue, oldest first. */
    @GetMapping("/admin/reports")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReportsResponse> getOpenReports(
            @AuthenticationPrincipal Gamer principal, @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(moderationService.getOpenReports(principal, pageable));
    }

    /** Upholds the report and closes every open report against the same content. */
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
