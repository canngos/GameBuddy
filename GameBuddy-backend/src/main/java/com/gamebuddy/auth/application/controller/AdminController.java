package com.gamebuddy.auth.application.controller;

import com.gamebuddy.auth.domain.service.AdminService;
import com.gamebuddy.auth.interfaces.request.GameRequest;
import com.gamebuddy.auth.interfaces.request.KeywordRequest;
import com.gamebuddy.auth.interfaces.response.GamerResponse;
import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.shared.entity.Gamer;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Moderation endpoints. {@code @PreAuthorize} enforces the role declaratively; the
 * service layer repeats the check so the documented {@code NOT_ADMIN} (140) contract
 * survives and the rule stays next to the data.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration", description = "User moderation and catalogue management")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/get/blocked/users")
    public ResponseEntity<GamerResponse> getBlockedUsers(@AuthenticationPrincipal Gamer principal) {
        return ResponseEntity.ok(adminService.getBlockedUsers(principal));
    }

    /*
     * Chat moderation (getReportedMessages / deleteReportedMessage) moved out of this
     * module. Reviewing a reported chat message means reading chat, and chat belongs to
     * the match module — admin reaching into it directly is exactly the cross-module
     * repository access the boundary rules forbid. It is rebuilt against the Postgres
     * chat tables when chat moves; see task #31.
     */

    @PostMapping("/ban/user/{userId}")
    public ResponseEntity<DefaultMessageResponse> banUser(
            @AuthenticationPrincipal Gamer principal, @PathVariable String userId) {
        return ResponseEntity.ok(adminService.banUser(principal, userId));
    }

    @PostMapping("/unban/user/{userId}")
    public ResponseEntity<DefaultMessageResponse> unbanUser(
            @AuthenticationPrincipal Gamer principal, @PathVariable String userId) {
        return ResponseEntity.ok(adminService.unbanUser(principal, userId));
    }

    @PostMapping("/add/game")
    public ResponseEntity<DefaultMessageResponse> addGame(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody GameRequest request) {
        return ResponseEntity.ok(adminService.addGame(principal, request));
    }

    @PostMapping("/add/keyword")
    public ResponseEntity<DefaultMessageResponse> addKeyword(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody KeywordRequest request) {
        return ResponseEntity.ok(adminService.addKeyword(principal, request));
    }
}
