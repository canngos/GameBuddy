package com.gamebuddy.shared.badge;

import com.gamebuddy.shared.entity.Gamer;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Everything the app can say it knows about a gamer, gathered from every module at once.
 *
 * <p>Extracted because there were three copies of this loop — one in the badge evaluator,
 * one in the earn screen, and a third about to be written for missions. They agreed today
 * and there was no mechanism by which they would go on agreeing: the badge one warned about
 * a collision, the earn one silently took the larger value, and a fourth caller would have
 * written a fourth policy.
 *
 * <p><strong>Larger wins a collision.</strong> Two modules claiming the same metric is a
 * bug — each is supposed to own what it counts — so the merge picks the bigger number and
 * complains, on the grounds that under-reporting a metric silently withholds something
 * somebody earned, and over-reporting it at worst hands out a badge early. Neither is
 * right; only one of them generates a support ticket.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GamerMetrics {

    private final List<BadgeMetricSource> sources;

    /**
     * Measures this gamer across every module.
     *
     * <p>Runs on the badges screen, on any profile load and on every read of the earn
     * screen, so it is a dozen or so indexed counts and must stay that way. A source that
     * starts doing real work here slows down three screens at once.
     */
    public Map<BadgeMetric, Integer> measure(Gamer gamer) {
        Map<BadgeMetric, Integer> merged = new EnumMap<>(BadgeMetric.class);
        for (BadgeMetricSource source : sources) {
            source.measure(gamer)
                    .forEach((metric, value) -> merged.merge(metric, value, (a, b) -> {
                        if (!a.equals(b)) {
                            log.warn("Two sources disagree on {} for {}: {} and {}", metric, gamer.getUserId(), a, b);
                        }
                        return Math.max(a, b);
                    }));
        }
        return merged;
    }
}
