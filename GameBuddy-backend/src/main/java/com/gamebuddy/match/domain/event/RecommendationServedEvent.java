package com.gamebuddy.match.domain.event;

import com.gamebuddy.match.infrastructure.entity.ImpressionSource;
import java.util.List;

/**
 * A page of recommendations was returned to a gamer.
 *
 * <p>Published rather than written inline so that recording an impression can never slow
 * down or fail the request that produced it. Analytics must not be able to break the feed.
 *
 * @param userId the gamer the page was built for
 * @param candidates who appeared on it, in the order they were shown
 */
public record RecommendationServedEvent(String userId, List<ServedCandidate> candidates) {

    /**
     * @param candidateId the gamer who was shown
     * @param position zero-based slot on the page
     * @param source whether the model chose them or exploration did
     */
    public record ServedCandidate(String candidateId, int position, ImpressionSource source) {}
}
