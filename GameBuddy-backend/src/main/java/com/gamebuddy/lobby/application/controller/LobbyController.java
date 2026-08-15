package com.gamebuddy.lobby.application.controller;

import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.lobby.domain.service.LobbyService;
import com.gamebuddy.lobby.infrastructure.entity.LobbyTone;
import com.gamebuddy.lobby.interfaces.request.CreateLobbyRequest;
import com.gamebuddy.lobby.interfaces.request.UpdateLobbyRequest;
import com.gamebuddy.lobby.interfaces.response.LobbiesResponse;
import com.gamebuddy.lobby.interfaces.response.LobbyResponse;
import com.gamebuddy.shared.entity.Gamer;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/lobby")
@RequiredArgsConstructor
public class LobbyController {

    private final LobbyService lobbyService;

    /** @param startingSoon narrows to lobbies planned to begin within the next 15 minutes */
    @GetMapping("/browse")
    public ResponseEntity<LobbiesResponse> browse(
            @AuthenticationPrincipal Gamer principal,
            @RequestParam(required = false) String gameId,
            @RequestParam(required = false) LobbyTone tone,
            @RequestParam(defaultValue = "false") boolean startingSoon,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(lobbyService.browse(principal, gameId, tone, startingSoon, pageable));
    }

    @GetMapping("/mine")
    public ResponseEntity<LobbiesResponse> mine(@AuthenticationPrincipal Gamer principal) {
        return ResponseEntity.ok(lobbyService.mine(principal));
    }

    @GetMapping("/{lobbyId}")
    public ResponseEntity<LobbyResponse> get(@AuthenticationPrincipal Gamer principal, @PathVariable UUID lobbyId) {
        return ResponseEntity.ok(lobbyService.get(principal, lobbyId));
    }

    /** Gold only — the service answers SUBSCRIPTION_REQUIRED and the client shows the paywall. */
    @PostMapping("/create")
    public ResponseEntity<LobbyResponse> create(
            @AuthenticationPrincipal Gamer principal, @Valid @RequestBody CreateLobbyRequest request) {
        return ResponseEntity.ok(lobbyService.create(principal, request));
    }

    @PutMapping("/{lobbyId}")
    public ResponseEntity<DefaultMessageResponse> update(
            @AuthenticationPrincipal Gamer principal,
            @PathVariable UUID lobbyId,
            @Valid @RequestBody UpdateLobbyRequest request) {
        return ResponseEntity.ok(lobbyService.update(principal, lobbyId, request));
    }

    @PostMapping("/{lobbyId}/join")
    public ResponseEntity<DefaultMessageResponse> join(
            @AuthenticationPrincipal Gamer principal, @PathVariable UUID lobbyId) {
        return ResponseEntity.ok(lobbyService.join(principal, lobbyId));
    }

    @PostMapping("/{lobbyId}/requests/{userId}/accept")
    public ResponseEntity<DefaultMessageResponse> accept(
            @AuthenticationPrincipal Gamer principal, @PathVariable UUID lobbyId, @PathVariable String userId) {
        return ResponseEntity.ok(lobbyService.accept(principal, lobbyId, userId));
    }

    @PostMapping("/{lobbyId}/requests/{userId}/reject")
    public ResponseEntity<DefaultMessageResponse> reject(
            @AuthenticationPrincipal Gamer principal, @PathVariable UUID lobbyId, @PathVariable String userId) {
        return ResponseEntity.ok(lobbyService.reject(principal, lobbyId, userId));
    }

    @PostMapping("/{lobbyId}/leave")
    public ResponseEntity<DefaultMessageResponse> leave(
            @AuthenticationPrincipal Gamer principal, @PathVariable UUID lobbyId) {
        return ResponseEntity.ok(lobbyService.leave(principal, lobbyId));
    }

    @DeleteMapping("/{lobbyId}/kick/{userId}")
    public ResponseEntity<DefaultMessageResponse> kick(
            @AuthenticationPrincipal Gamer principal, @PathVariable UUID lobbyId, @PathVariable String userId) {
        return ResponseEntity.ok(lobbyService.kick(principal, lobbyId, userId));
    }

    @PostMapping("/{lobbyId}/lock")
    public ResponseEntity<DefaultMessageResponse> lock(
            @AuthenticationPrincipal Gamer principal, @PathVariable UUID lobbyId) {
        return ResponseEntity.ok(lobbyService.lock(principal, lobbyId));
    }

    @PostMapping("/{lobbyId}/unlock")
    public ResponseEntity<DefaultMessageResponse> unlock(
            @AuthenticationPrincipal Gamer principal, @PathVariable UUID lobbyId) {
        return ResponseEntity.ok(lobbyService.unlock(principal, lobbyId));
    }

    @PostMapping("/{lobbyId}/end")
    public ResponseEntity<DefaultMessageResponse> end(
            @AuthenticationPrincipal Gamer principal, @PathVariable UUID lobbyId) {
        return ResponseEntity.ok(lobbyService.end(principal, lobbyId));
    }

    /** Only reachable while OPEN; a LOCKED lobby must be unlocked first — see the service. */
    @PostMapping("/{lobbyId}/cancel")
    public ResponseEntity<DefaultMessageResponse> cancel(
            @AuthenticationPrincipal Gamer principal, @PathVariable UUID lobbyId) {
        return ResponseEntity.ok(lobbyService.cancel(principal, lobbyId));
    }
}
