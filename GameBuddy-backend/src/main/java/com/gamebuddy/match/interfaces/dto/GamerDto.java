package com.gamebuddy.match.interfaces.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GamerDto {
    private String userId;
    private String gamerUsername;
    private Integer age;
    private String country;
    private String gender;
    private String avatar;
    /**
     * The frame this gamer is wearing, as a resolved URL, or null for none.
     *
     * <p>Null is the common case and not an error state — the client draws the avatar
     * bare. Rendered only where the avatar is large enough to carry a ring; a frame around
     * a 24px comment thumbnail is noise rather than status.
     */
    private String frame;

    private List<GamesDto> favoriteGames;
    private List<String> selectedKeywords;

    /**
     * What this gamer plays on, as human-readable labels.
     *
     * <p>Labels rather than enum names, for the same reason the keywords above are names
     * rather than ids: the deck renders these directly, and a client that has to translate
     * {@code PLAYSTATION} into "PlayStation" is a client that will eventually spell it
     * differently from every other screen.
     *
     * <p>Empty for accounts that pre-date the field. The card omits the row entirely rather
     * than showing "Plays on: —", because an absent answer is not information.
     */
    private List<String> platforms;

    /**
     * Whether this gamer's like was a super like.
     *
     * <p>Only ever true on the "who liked you" list, which is the one place the question
     * means anything: everywhere else this DTO describes somebody who has not necessarily
     * liked you at all, and the field is left false rather than being made nullable to say
     * "not applicable". A boolean that is false in the deck costs a word on the wire and
     * spares every caller a null check.
     */
    private boolean superLike;
}
