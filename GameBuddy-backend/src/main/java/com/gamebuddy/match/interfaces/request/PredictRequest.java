package com.gamebuddy.match.interfaces.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collection;
import java.util.List;

/**
 * What the recommendation model needs to rank a page of candidates.
 *
 * <p>{@code exclude} is everyone this gamer has already accepted or declined. Sending it
 * is what stops the feed running dry: the model's ranking is a deterministic function of
 * profiles, so without exclusions a gamer who swiped through the first page would be
 * handed the same page forever — retraining does not help, because the same profiles
 * produce the same order. Excluding before the model truncates lets the ranking descend
 * into candidates this gamer has not seen yet.
 *
 * <p>Sent as a body rather than query parameters because the exclusion list grows with
 * every swipe and would eventually exceed the URL length limit.
 *
 * @param userId the gamer to recommend for
 * @param exclude gamer ids to leave out of the ranking entirely
 * @param limit how many candidates to return
 */
public record PredictRequest(
        @JsonProperty("user_id") String userId,
        @JsonProperty("exclude") List<String> exclude,
        @JsonProperty("limit") int limit) {

    public PredictRequest(String userId, Collection<String> exclude, int limit) {
        this(userId, List.copyOf(exclude), limit);
    }
}
