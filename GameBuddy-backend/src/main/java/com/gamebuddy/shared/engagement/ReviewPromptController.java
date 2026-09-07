package com.gamebuddy.shared.engagement;

import com.gamebuddy.shared.entity.Gamer;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where the app asks whether it may show the Play review card.
 *
 * <p>A POST rather than a GET, and that is not pedantry about verbs: the call changes
 * something. Asking is taking — see {@link ReviewPromptService} for why the check and the
 * record cannot be separated when Play refuses to say whether the card appeared.
 *
 * <p>The gamer comes from the principal, never the body. Beside {@code shared/funnel} for
 * the same reason: this is a cross-cutting engagement concern with no feature module to
 * live in, and it is the client that knows when the quiet moment arrived.
 *
 * <p><b>Failures are answered, not thrown.</b> The caller is about to dismiss a match
 * celebration; a review prompt that turns into an error toast would be strictly worse than
 * one that never happened.
 */
@Slf4j
@RestController
@RequestMapping("/engagement")
@RequiredArgsConstructor
@Tag(name = "Engagement", description = "The Play review ask")
public class ReviewPromptController {

    private final ReviewPromptService reviewPrompts;

    @PostMapping("/review-prompt/claim")
    public ResponseEntity<ReviewPromptResponse> claim(@AuthenticationPrincipal Gamer principal) {
        boolean due;
        try {
            due = reviewPrompts.claim(principal);
        } catch (RuntimeException e) {
            log.warn("Could not evaluate the review prompt", e);
            due = false;
        }
        return ResponseEntity.ok(new ReviewPromptResponse(due));
    }

    /**
     * Whether to ask, and nothing else.
     *
     * <p>Not the envelope the rest of the API uses. There is no failure the app could act
     * on and no message worth showing: every answer here is one boolean, and half of them
     * mean "carry on as though you had never asked".
     */
    public record ReviewPromptResponse(boolean due) {}
}
