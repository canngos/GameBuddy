package com.gamebuddy.match.domain.client;

import com.gamebuddy.match.interfaces.request.ColdStartRequest;
import com.gamebuddy.match.interfaces.request.PredictRequest;
import com.gamebuddy.match.interfaces.response.PredictResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Asks the Python model (GameBuddy-Model) which gamers to recommend.
 *
 * <p>Replaces the Spring Cloud OpenFeign client, whose configured URL in every checked-in
 * profile was the literal placeholder {@code # enter ai prediction service url} — so this
 * call had never worked in any environment built from this configuration.
 */
@HttpExchange
public interface PredictClient {

    /**
     * Ranks candidates for one gamer.
     *
     * <p>Takes a body rather than a {@code user_id} query parameter: the request carries
     * the set of gamers already decided on, which grows without bound as someone swipes.
     */
    @PostExchange("/predict")
    PredictResponse predict(@RequestBody PredictRequest request);

    /**
     * Ranks from a profile, for a gamer the trained artefact has never seen.
     *
     * <p>The model is trained offline, so it only knows gamers who existed at the last
     * run. {@code /predict} answers an unknown id with an empty list rather than an
     * error, which is safe but silent: without this fallback every account created since
     * the last retrain — which at launch is all of them — opens the app to an empty deck.
     */
    @PostExchange("/predict/cold-start")
    PredictResponse predictColdStart(@RequestBody ColdStartRequest request);
}
