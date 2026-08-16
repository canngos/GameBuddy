package com.gamebuddy.profile.interfaces.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GamesDto {
    private String gameId;
    private String gameName;
    private String gameIcon;
    private String category;
    private Float avgVote;
    private String description;

    /**
     * What this game is played on, as Platform enum NAMES.
     *
     * <p>Names rather than labels, deliberately. {@code Platform.label()} exists so the
     * client never has to invent a spelling, and the app already keys off enum names in
     * {@code src/profile/platforms.ts} — sending "PlayStation" here would give the app two
     * sources of truth for one word and no way to tell which is authoritative.
     *
     * <p>Sorted, so the same game serialises identically on every request. The entity holds
     * a Set loaded in whatever order the join returns, which would otherwise let a response
     * body differ between two calls that mean exactly the same thing.
     *
     * <p>Empty rather than null for a game with no platforms — the picker filters this list
     * and an absent value would make every consumer null-check a collection.
     */
    private List<String> platforms;
}
