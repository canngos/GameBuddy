package com.gamebuddy.auth.domain.event;

/**
 * Raised when a gamer's games or keywords change, so the recommender can re-cluster.
 *
 * @param userId the gamer whose profile changed
 */
public record ProfileChangedEvent(String userId) {}
