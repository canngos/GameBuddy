package com.gamebuddy.shared.event;

/**
 * Raised when a gamer's games or keywords change — the two things the recommender ranks on.
 *
 * <p>Lives in {@code shared} rather than in the auth module that raises it because the
 * module that cares is {@code match}: an event only one module can see is a callback with
 * extra steps, and importing it across modules directly would point {@code match} at
 * {@code auth}. Same reasoning as {@link AccountDeletedEvent}.
 *
 * <p>Consumed by {@code RecommenderStalenessListener}. It used to be consumed by nothing at
 * all, which is why a gamer could change every game on their profile and go on being ranked
 * on the old ones until the next retrain.
 *
 * @param userId the gamer whose profile changed
 */
public record ProfileChangedEvent(String userId) {}
