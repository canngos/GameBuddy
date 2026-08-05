package com.gamebuddy.notif.application.controller;

import com.gamebuddy.notif.domain.service.NotificationService;
import com.gamebuddy.notif.interfaces.dto.NotificationPreferencesDto;
import com.gamebuddy.notif.interfaces.response.GetNotificationsResponse;
import com.gamebuddy.notif.interfaces.response.NotificationPreferencesResponse;
import com.gamebuddy.shared.entity.Gamer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Both send endpoints are service-to-service and require {@code ROLE_INTERNAL}; see
 * {@code SecurityConfig}. The history endpoint requires a user token and returns only the
 * caller's own notifications.
 *
 * <p>{@code @Valid} was missing from every request body, so the {@code @NotBlank}
 * constraints on the request classes were decorative: a notification with no title or an
 * empty topic went straight through to Firebase.
 */
@RestController
@RequestMapping("/notif")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /*
     * POST /notif/token and POST /notif/topic are gone.
     *
     * They existed so other services could ask this one to send a push, over HTTP,
     * authenticated with a shared X-Internal-Api-Key because there was no user principal
     * to authenticate. In one process there is nothing to call across: modules publish a
     * NotificationRequestedEvent and NotificationDispatcher delivers it directly.
     *
     * Deleting them removes an endpoint that could push arbitrary text to any device or
     * broadcast to every user, and whose only protection was a shared secret sitting in
     * five services' configuration.
     */

    /**
     * The caller's own notification history.
     *
     * <p>The user id is no longer a path variable: it was
     * {@code GET /notif/showall/{userId}} on a {@code permitAll} route, so anyone could
     * read anyone's history.
     */
    @GetMapping("/showall")
    public ResponseEntity<GetNotificationsResponse> showAll(
            @AuthenticationPrincipal Gamer principal, @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(notificationService.showAll(principal, pageable));
    }

    /** The four switches shown on the Notifications settings screen. */
    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferencesResponse> getPreferences(@AuthenticationPrincipal Gamer principal) {
        return ResponseEntity.ok(notificationService.getPreferences(principal));
    }

    /**
     * Replaces them.
     *
     * <p>PUT with the whole set rather than PATCH with one switch: the screen holds all
     * four and sending the lot means it and the server cannot disagree about one that was
     * never mentioned.
     */
    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreferencesResponse> updatePreferences(
            @AuthenticationPrincipal Gamer principal, @RequestBody NotificationPreferencesDto preferences) {
        return ResponseEntity.ok(notificationService.updatePreferences(principal, preferences));
    }
}
