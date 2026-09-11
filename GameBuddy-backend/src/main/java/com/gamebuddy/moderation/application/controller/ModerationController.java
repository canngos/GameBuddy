package com.gamebuddy.moderation.application.controller;

import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.util.Ids;
import com.gamebuddy.moderation.domain.service.ModerationService;
import com.gamebuddy.moderation.interfaces.request.ReportRequest;
import com.gamebuddy.moderation.interfaces.request.ResolveCaseRequest;
import com.gamebuddy.moderation.interfaces.response.CaseDetailResponse;
import com.gamebuddy.moderation.interfaces.response.CasesResponse;
import com.gamebuddy.shared.entity.Gamer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Filing reports, and the case queue that answers them.
 *
 * <p><b>The paths are kept, deliberately.</b> Installed builds call
 * {@code POST /community/report/profile/{id}} and {@code POST /messages/report/{id}}, and
 * a URL is an annotation here but an app update on their side. The message-report path used
 * to live in chat's controller; it moves here so that <em>filing</em> a report — which is
 * moderation's job — no longer makes the chat module depend on this one. Chat still owns
 * reading the conversation, which is where the plaintext is; moderation calls into it.
 *
 * <p>The admin routes sit under paths the ingress already restricts by source address,
 * alongside {@code /admin}, and carry {@code hasRole('ADMIN')} as well: two checks, so
 * neither is the only thing between a user and someone else's account.
 */
@RestController
@RequiredArgsConstructor
public class ModerationController {

    private final ModerationService moderationService;

    /** Reports a gamer's profile — the picture, the name, what they wrote about themselves. */
    @PostMapping("/community/report/profile/{userId}")
    public ResponseEntity<DefaultMessageResponse> reportProfile(
            @AuthenticationPrincipal Gamer principal,
            @PathVariable String userId,
            @Valid @RequestBody ReportRequest request) {
        return ResponseEntity.ok(moderationService.reportProfile(principal, userId, request));
    }

    /**
     * Reports a chat message the caller received.
     *
     * <p>The body is optional so a build shipped before structured reasons — which posts
     * nothing — still works: a missing reason is treated as "other" without demanding a
     * note. New builds send a {@link ReportRequest}.
     */
    @PostMapping("/messages/report/{messageId}")
    public ResponseEntity<DefaultMessageResponse> reportMessage(
            @AuthenticationPrincipal Gamer principal,
            @PathVariable String messageId,
            @Valid @RequestBody(required = false) ReportRequest request) {
        // Parsed here so a malformed id is a 400 rather than a 500 out of the repository.
        return ResponseEntity.ok(moderationService.reportMessage(
                principal, Ids.uuid(messageId), request == null ? new ReportRequest() : request));
    }

    /** The moderation queue: open and urgent cases, urgent first. */
    @GetMapping("/community/admin/cases")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CasesResponse> getCases(
            @AuthenticationPrincipal Gamer principal, @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(moderationService.getCases(principal, limit));
    }

    /** One case in full: its reports, the target's history, and the decrypted context. */
    @GetMapping("/community/admin/cases/{caseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CaseDetailResponse> getCase(
            @AuthenticationPrincipal Gamer principal, @PathVariable String caseId) {
        return ResponseEntity.ok(moderationService.getCase(principal, caseId));
    }

    /** Resolves a case with one decision from the ladder. */
    @PostMapping("/community/admin/cases/{caseId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DefaultMessageResponse> resolveCase(
            @AuthenticationPrincipal Gamer principal,
            @PathVariable String caseId,
            @Valid @RequestBody ResolveCaseRequest request) {
        return ResponseEntity.ok(moderationService.resolveCase(principal, caseId, request));
    }
}
