package com.gamebuddy.match.interfaces.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * The model's response.
 *
 * <p>The snake_case field names come from the Python service. They were previously
 * mirrored as Java fields named {@code user_id} and {@code sim_users}, which reads as a
 * naming-convention violation everywhere they were used; the mapping is declared here
 * instead.
 *
 * @param userId the gamer the recommendation was computed for
 * @param similarUsers ids of the recommended gamers, most similar first
 */
public record PredictResponse(
        @JsonProperty("user_id") String userId,
        @JsonProperty("sim_users") List<String> similarUsers) {

    public List<String> similarUsers() {
        return similarUsers == null ? List.of() : similarUsers;
    }
}
