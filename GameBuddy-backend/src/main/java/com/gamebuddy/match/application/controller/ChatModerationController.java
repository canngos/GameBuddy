package com.gamebuddy.match.application.controller;

import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.common.util.Ids;
import com.gamebuddy.match.domain.service.chat.ChatModerationService;
import com.gamebuddy.match.interfaces.response.ReportedMessagesResponse;
import com.gamebuddy.shared.entity.Gamer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Admin endpoints for reported chat.
 *
 * <p>Mounted under {@code /admin} so the filter chain's {@code hasRole("ADMIN")} applies,
 * and annotated as well: two independent checks, so neither is the only thing standing
 * between a user and everyone else's private conversations.
 *
 * <p>These endpoints previously lived in auth-service and read chat straight out of
 * MongoDB. They sit with chat now, because reviewing a message means reading chat.
 */
@RestController
@RequestMapping("/admin/chat")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ChatModerationController {

    private final ChatModerationService moderationService;

    @GetMapping("/reported")
    public ResponseEntity<ReportedMessagesResponse> getReportedMessages(@AuthenticationPrincipal Gamer principal) {
        return ResponseEntity.ok(moderationService.getReportedMessages(principal));
    }

    /** Marks a report reviewed. Does not delete the message; see the service javadoc. */
    @DeleteMapping("/reported/{messageId}")
    public ResponseEntity<DefaultMessageResponse> dismissReport(
            @AuthenticationPrincipal Gamer principal, @PathVariable String messageId) {
        // Parsed here so a malformed id is a 400 rather than a 500 from the repository.
        return ResponseEntity.ok(moderationService.dismissReport(principal, Ids.uuid(messageId)));
    }
}
