package com.gamebuddy.community.interfaces.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GamerDto {

    private String userId;
    private String gamerUsername;
    private String avatar;
    /**
     * The frame this gamer is wearing, as a resolved URL, or null for none.
     *
     * <p>Null is the common case and not an error state — the client draws the avatar
     * bare. Rendered only where the avatar is large enough to carry a ring; a frame around
     * a 24px comment thumbnail is noise rather than status.
     */
    private String frame;

    private Boolean isOwner;
}
