package com.gamebuddy.lobby.infrastructure.entity;

/**
 * The mood a lobby is advertising, as a filterable fact.
 *
 * <p>A fixed enum rather than a join to the {@code keywords} catalogue: a lobby needs
 * exactly one tone, and it has to be a column the browse feed can filter on. The labels
 * deliberately mirror the keyword vocabulary ("competitive", "chill", "casual") so the two
 * vocabularies read as one app, but joining the table would allow a "loot goblin" lobby
 * and put a moderation question where a dropdown answers it.
 */
public enum LobbyTone {
    COMPETITIVE,
    CHILL,
    CASUAL,
    LEARNING
}
