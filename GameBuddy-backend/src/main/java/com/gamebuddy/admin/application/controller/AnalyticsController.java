package com.gamebuddy.admin.application.controller;

import com.gamebuddy.admin.domain.service.AnalyticsService;
import com.gamebuddy.admin.interfaces.response.AnalyticsResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * How the product is doing, for the one person who runs it.
 *
 * <p>Aggregates only. Nothing here identifies a gamer — the console shows how many minors
 * are on the platform, never which accounts they are — because a dashboard is the screen
 * most likely to be left open, screenshotted and pasted somewhere.
 *
 * <p>{@code UserDirectoryController}, added alongside promotion codes, is the one place in
 * this module that does name accounts, and it is not a relaxation of the rule above: a code
 * has to be addressed to somebody, and it is a search behind ADMIN rather than anything
 * this screen shows.
 */
@RestController
@RequestMapping("/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration", description = "User moderation and catalogue management")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping
    public ResponseEntity<AnalyticsResponse> snapshot() {
        return ResponseEntity.ok(analyticsService.snapshot());
    }
}
