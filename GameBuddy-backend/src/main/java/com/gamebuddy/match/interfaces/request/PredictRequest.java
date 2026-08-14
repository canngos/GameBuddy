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
 * <p>{@code include} is the opposite question and comes from a different place: the set of
 * gamers who satisfy a Gold filter. The model cannot work that out — it knows nothing about
 * countries or activity — so the backend evaluates the filter and says who is eligible. It
 * used to send the complement instead, which grows with the population rather than with the
 * answer; above the model's ten-thousand cap the request was refused and the user was told
 * the recommender was unavailable. See {@code GamerRepository#findIdsMatchingFilters}.
 *
 * <p><b>Null and empty are different answers here.</b> Null means "not filtering"; empty
 * means "the filter matched nobody" and must produce an empty deck rather than an unfiltered
 * one.
 *
 * <p>Sent as a body rather than query parameters because both lists grow with the population
 * and would exceed the URL length limit.
 *
 * @param userId the gamer to recommend for
 * @param exclude gamer ids to leave out of the ranking entirely
 * @param include the only gamer ids eligible for this ranking, or null for no restriction
 * @param limit how many candidates to return
 */
public record PredictRequest(
        @JsonProperty("user_id") String userId,
        @JsonProperty("exclude") List<String> exclude,
        @JsonProperty("include") List<String> include,
        @JsonProperty("limit") int limit) {

    public PredictRequest(String userId, Collection<String> exclude, Collection<String> include, int limit) {
        this(userId, List.copyOf(exclude), include == null ? null : List.copyOf(include), limit);
    }
}
