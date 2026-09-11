package com.gamebuddy.moderation.domain.service;

import com.gamebuddy.moderation.config.ModerationProperties;
import com.gamebuddy.moderation.infrastructure.entity.ContentReport;
import com.gamebuddy.moderation.infrastructure.entity.ContentReport.ReasonCode;
import com.gamebuddy.shared.entity.Gamer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The arithmetic of a case: how much each report counts, and what the total triggers.
 *
 * <p>Pure, on purpose. Nothing here reads a repository or sends anything; it takes the
 * reports and the reporters and answers with numbers and a verdict. That keeps the rules
 * testable as rules, and keeps the only sharp edge in moderation — an automatic action
 * against a person — in one file that is short enough to read in full.
 *
 * <p>The principle behind the weights: <strong>reports are weighed, never counted.</strong>
 * A count is what a brigade games — ten accounts made this morning, one target — and a
 * count is what a serial false reporter inflates. So each report carries its reporter's
 * standing, and standing is earned by being right before.
 */
@Component
@RequiredArgsConstructor
public class ReportPolicy {

    /**
     * The prior: how many "upheld" a reporter is credited with before they have any
     * history. Two, so that a newcomer weighs 1.0 and the first dismissal costs them a
     * third rather than everything — somebody's first report being wrong is not evidence
     * of anything.
     */
    private static final int PRIOR_UPHELD = 2;

    private final ModerationProperties properties;

    /**
     * How much this reporter's word counts, between 0 and 1.
     *
     * <p>{@code (upheld + 2) / (upheld + dismissed + 2)}, halved for an account younger
     * than the configured age. Ten dismissals with nothing upheld leave a reporter at
     * about a sixth — still counted, still reviewed, but it takes many of them to move a
     * case, which is the point.
     */
    public BigDecimal weightOf(Gamer reporter, Instant now) {
        int upheld = reporter.getReportsUpheld() + PRIOR_UPHELD;
        int total = upheld + reporter.getReportsDismissed();
        BigDecimal weight = BigDecimal.valueOf(upheld).divide(BigDecimal.valueOf(total), 3, RoundingMode.HALF_UP);

        Instant created = reporter.getCreatedDate();
        boolean young = created != null && Duration.between(created, now).compareTo(properties.getNewAccountAge()) < 0;
        if (young) {
            weight = weight.multiply(properties.getNewAccountWeight()).setScale(3, RoundingMode.HALF_UP);
        }
        return weight;
    }

    /** Whether a reporter has had enough reports dismissed to be told so. Exactly at the line, once. */
    public boolean hasReachedLowTrustNotice(Gamer reporter) {
        return reporter.getReportsUpheld() == 0 && reporter.getReportsDismissed() == properties.getLowTrustNotice();
    }

    /**
     * The case's standing: distinct reporters within the window, and the sum of their
     * weights. A reporter with two reports in the case counts once, at their weight.
     */
    public Tally tally(List<ContentReport> reports, Map<String, Gamer> reporters, Instant now) {
        Instant since = now.minus(properties.getHideWindow());
        Map<String, BigDecimal> weights = new HashMap<>();
        for (ContentReport report : reports) {
            if (report.getCreatedAt() == null || report.getCreatedAt().isBefore(since)) {
                continue;
            }
            Gamer reporter = reporters.get(report.getReporterId());
            if (reporter == null) {
                // Deleted since. Their report still stands, at the weight of a stranger.
                weights.putIfAbsent(report.getReporterId(), BigDecimal.ONE);
                continue;
            }
            weights.putIfAbsent(report.getReporterId(), weightOf(reporter, now));
        }
        BigDecimal score = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Tally(weights.size(), score.setScale(3, RoundingMode.HALF_UP));
    }

    /** Whether the tally has crossed the line where the target is hidden pending review. */
    public boolean warrantsHiding(Tally tally) {
        return tally.distinctReporters() >= properties.getHideReporters()
                && tally.score().compareTo(properties.getHideScore()) >= 0;
    }

    /**
     * Whether a report makes its case urgent on its own. Only a report that someone is a
     * child does: the child-safety policy says those are acted on without proof and
     * ahead of everything, and one such report is enough to look now.
     */
    public boolean isUrgent(ReasonCode reason) {
        return reason == ReasonCode.UNDERAGE;
    }

    /** Distinct reporters in the window and the sum of their weights. */
    public record Tally(int distinctReporters, BigDecimal score) {}
}
