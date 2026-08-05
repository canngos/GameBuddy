package com.gamebuddy.shared.badge;

import com.gamebuddy.shared.entity.Gamer;
import java.util.Map;

/**
 * A module's contribution to what a gamer has done.
 *
 * <p>Badges are evaluated in the profile module, but most of what they measure belongs to
 * somebody else: posts are the community module's, messages are the match module's, and
 * a module boundary says profile may not go and count their rows itself.
 *
 * <p>So the dependency is inverted. Each module implements this for the metrics it owns
 * and Spring hands the whole set to the evaluator, which never learns who produced what.
 * Adding a mission that counts something new in the community module is then one file in
 * the community module plus one line in {@code Badge} — and crucially, no new edge in the
 * module graph.
 *
 * <p>An implementation returns only the metrics it owns. Missing ones read as zero, which
 * is the truthful answer for a gamer who has not done that thing.
 */
public interface BadgeMetricSource {

    /**
     * Measures this gamer.
     *
     * <p>Called on the badges screen and on a profile load, so it should be a handful of
     * counts and nothing more. It must not write.
     */
    Map<BadgeMetric, Integer> measure(Gamer gamer);
}
