package com.gamebuddy.moderation.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gamebuddy.moderation.config.ModerationProperties;
import com.gamebuddy.moderation.domain.service.ReportPolicy.Tally;
import com.gamebuddy.moderation.infrastructure.entity.ContentReport;
import com.gamebuddy.moderation.infrastructure.entity.ContentReport.ContentType;
import com.gamebuddy.moderation.infrastructure.entity.ContentReport.ReasonCode;
import com.gamebuddy.shared.entity.Gamer;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The weighting rules, in isolation. This is where "weighed, not counted" is actually
 * defined, so the edges — a brigade of new accounts, a serial false reporter, the prior for
 * someone with no history — are pinned here rather than discovered in production.
 */
@DisplayName("ReportPolicy")
class ReportPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

    private final ModerationProperties properties = new ModerationProperties();
    private final ReportPolicy policy = new ReportPolicy(properties);

    private Gamer reporter(int upheld, int dismissed, Duration age) {
        Gamer g = new Gamer();
        g.setUserId(UUID.randomUUID().toString());
        g.setReportsUpheld(upheld);
        g.setReportsDismissed(dismissed);
        g.setCreatedDate(NOW.minus(age));
        return g;
    }

    private ContentReport reportBy(String reporterId) {
        ContentReport r = new ContentReport();
        r.setContentType(ContentType.PROFILE);
        r.setReporterId(reporterId);
        r.setCreatedAt(NOW);
        return r;
    }

    @Test
    @DisplayName("a reporter with no history weighs 1.0 — a newcomer is trusted, not doubted")
    void newcomerWeighsOne() {
        Gamer fresh = reporter(0, 0, Duration.ofDays(30));
        assertEquals(0, new BigDecimal("1.000").compareTo(policy.weightOf(fresh, NOW)));
    }

    @Test
    @DisplayName("dismissals sink a reporter's weight, but never to nothing")
    void dismissalsSinkWeight() {
        Gamer crier = reporter(0, 10, Duration.ofDays(30));
        BigDecimal weight = policy.weightOf(crier, NOW);
        assertTrue(weight.compareTo(new BigDecimal("0.25")) < 0, "ten dismissals, nothing upheld, weighs little");
        assertTrue(weight.compareTo(BigDecimal.ZERO) > 0, "but is never silenced entirely");
    }

    @Test
    @DisplayName("an account younger than a week weighs half — a brigade is made of new accounts")
    void newAccountsAreHalved() {
        Gamer established = reporter(0, 0, Duration.ofDays(30));
        Gamer fresh = reporter(0, 0, Duration.ofHours(2));
        assertEquals(
                0,
                policy.weightOf(established, NOW)
                        .multiply(new BigDecimal("0.5"))
                        .setScale(3, java.math.RoundingMode.HALF_UP)
                        .compareTo(policy.weightOf(fresh, NOW)));
    }

    @Test
    @DisplayName("three established reporters cross the hide line; three fresh accounts do not")
    void hidingTakesWeightNotCount() {
        Map<String, Gamer> established = Map.of(
                "a", reporter(0, 0, Duration.ofDays(30)),
                "b", reporter(0, 0, Duration.ofDays(30)),
                "c", reporter(0, 0, Duration.ofDays(30)));
        List<ContentReport> reports =
                established.keySet().stream().map(this::reportBy).toList();
        Tally strong = policy.tally(reports, established, NOW);
        assertEquals(3, strong.distinctReporters());
        assertTrue(policy.warrantsHiding(strong), "three trusted reporters (score 3.0) hide the profile");

        Map<String, Gamer> brigade = Map.of(
                "x", reporter(0, 0, Duration.ofHours(1)),
                "y", reporter(0, 0, Duration.ofHours(1)),
                "z", reporter(0, 0, Duration.ofHours(1)));
        List<ContentReport> brigadeReports =
                brigade.keySet().stream().map(this::reportBy).toList();
        Tally weak = policy.tally(brigadeReports, brigade, NOW);
        assertEquals(3, weak.distinctReporters());
        assertFalse(policy.warrantsHiding(weak), "three brand-new accounts (score 1.5) do not");
    }

    @Test
    @DisplayName("the same reporter reporting twice counts once")
    void oneReporterCountsOnce() {
        Gamer solo = reporter(0, 0, Duration.ofDays(30));
        Map<String, Gamer> reporters = Map.of(solo.getUserId(), solo);
        List<ContentReport> twice = List.of(reportBy(solo.getUserId()), reportBy(solo.getUserId()));
        Tally tally = policy.tally(twice, reporters, NOW);
        assertEquals(1, tally.distinctReporters());
    }

    @Test
    @DisplayName("only an underage report is urgent on its own")
    void onlyUnderageIsUrgent() {
        assertTrue(policy.isUrgent(ReasonCode.UNDERAGE));
        assertFalse(policy.isUrgent(ReasonCode.HARASSMENT));
        assertFalse(policy.isUrgent(ReasonCode.SEXUAL));
    }

    @Test
    @DisplayName("stale reports outside the window do not count towards a hide")
    void staleReportsAreIgnored() {
        Gamer old = reporter(0, 0, Duration.ofDays(30));
        ContentReport ancient = reportBy(old.getUserId());
        ancient.setCreatedAt(NOW.minus(Duration.ofDays(30)));
        Tally tally = policy.tally(List.of(ancient), Map.of(old.getUserId(), old), NOW);
        assertEquals(0, tally.distinctReporters(), "a month-old complaint is history, not a live flood");
    }
}
