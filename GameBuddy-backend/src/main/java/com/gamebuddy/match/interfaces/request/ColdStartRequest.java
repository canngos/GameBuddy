package com.gamebuddy.match.interfaces.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collection;
import java.util.List;

/**
 * Asks the model to rank from a profile rather than from a trained user vector.
 *
 * <p>The model is a artefact trained offline, so it only knows gamers who existed at the
 * last training run. {@code /predict} answers an unknown id with an empty list — which,
 * before this existed on the caller's side, meant <em>every</em> gamer who signed up
 * since the last retrain opened the app to an empty deck. The model service has always
 * offered this endpoint for exactly that case; nothing called it.
 *
 * <p>Game and keyword <em>names</em>, not ids: the model was trained on the names in
 * {@code gamebuddy_model/catalogue.py} and has never seen our UUIDs.
 *
 * @param userId the gamer to recommend for
 * @param games their favourite games, by name
 * @param keywords their keywords, by name
 * @param exclude gamer ids to leave out of the ranking entirely
 * @param limit how many candidates to return
 */
public record ColdStartRequest(
        @JsonProperty("user_id") String userId,
        @JsonProperty("games") List<String> games,
        @JsonProperty("keywords") List<String> keywords,
        @JsonProperty("exclude") List<String> exclude,
        @JsonProperty("limit") int limit) {

    public ColdStartRequest(
            String userId,
            Collection<String> games,
            Collection<String> keywords,
            Collection<String> exclude,
            int limit) {
        this(userId, List.copyOf(games), List.copyOf(keywords), List.copyOf(exclude), limit);
    }
}
