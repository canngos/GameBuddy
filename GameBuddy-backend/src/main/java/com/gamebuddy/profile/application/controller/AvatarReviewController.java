package com.gamebuddy.profile.application.controller;

import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.profile.domain.service.AvatarReviewService;
import com.gamebuddy.profile.interfaces.response.AvatarImageResponse;
import com.gamebuddy.profile.interfaces.response.PendingAvatarsResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The avatar review queue.
 *
 * <p>Under {@code /admin} with the rest of the console, but owned by this module because
 * approving an avatar is avatar business: it promotes an object between buckets and moves a
 * status that {@code AvatarUploadService} defines. Putting it in a separate admin module
 * would mean two modules deciding when an image becomes public.
 */
@RestController
@RequestMapping("/admin/avatars")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration", description = "User moderation and catalogue management")
public class AvatarReviewController {

    private final AvatarReviewService reviewService;

    @GetMapping("/pending")
    public ResponseEntity<PendingAvatarsResponse> pending() {
        return ResponseEntity.ok(reviewService.pending());
    }

    /**
     * The image itself, inlined in JSON so the client can authenticate for it.
     *
     * <p>{@code no-store}, because this is an unscreened image being shown to a moderator.
     * It should not survive in a proxy or a disk cache once the verdict is in.
     */
    @GetMapping("/{userId}/image")
    public ResponseEntity<AvatarImageResponse> image(@PathVariable String userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviewService.imageUnderReview(userId));
    }

    @PostMapping("/{userId}/approve")
    public ResponseEntity<DefaultMessageResponse> approve(@PathVariable String userId) {
        return ResponseEntity.ok(reviewService.approve(userId));
    }

    @PostMapping("/{userId}/reject")
    public ResponseEntity<DefaultMessageResponse> reject(@PathVariable String userId) {
        return ResponseEntity.ok(reviewService.reject(userId));
    }
}
