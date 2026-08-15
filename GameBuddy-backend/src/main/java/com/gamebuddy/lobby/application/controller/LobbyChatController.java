package com.gamebuddy.lobby.application.controller;

import com.gamebuddy.lobby.domain.service.LobbyChatService;
import com.gamebuddy.lobby.interfaces.request.LobbyMessageRequest;
import com.gamebuddy.lobby.interfaces.response.LobbyMessageResponse;
import com.gamebuddy.lobby.interfaces.response.LobbyMessagesResponse;
import com.gamebuddy.shared.entity.Gamer;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP send, socket receive — the same posture as 1:1 chat, for the same reason: a dropped
 * socket slows chat down, it never breaks it. Arrivals reach the team on
 * {@code /user/queue/lobby}.
 */
@RestController
@RequestMapping("/lobby")
@RequiredArgsConstructor
public class LobbyChatController {

    private final LobbyChatService lobbyChatService;

    @GetMapping("/{lobbyId}/messages")
    public ResponseEntity<LobbyMessagesResponse> messages(
            @AuthenticationPrincipal Gamer principal, @PathVariable UUID lobbyId) {
        return ResponseEntity.ok(lobbyChatService.messages(principal, lobbyId));
    }

    @PostMapping("/{lobbyId}/messages/send")
    public ResponseEntity<LobbyMessageResponse> send(
            @AuthenticationPrincipal Gamer principal,
            @PathVariable UUID lobbyId,
            @Valid @RequestBody LobbyMessageRequest request) {
        return ResponseEntity.ok(lobbyChatService.send(principal, lobbyId, request.getMessage()));
    }
}
