package com.gamebuddy.lobby.interfaces.dto;

import com.gamebuddy.lobby.infrastructure.entity.LobbyMemberStatus;
import com.gamebuddy.lobby.infrastructure.entity.LobbyStatus;
import com.gamebuddy.lobby.infrastructure.entity.LobbyTone;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * One lobby as a client sees it: the card in the browse feed and the header of the detail
 * screen are both this.
 */
@Getter
@Setter
public class LobbyDto {

    private String id;
    private String ownerId;
    private String ownerUsername;
    private String ownerAvatar;
    private String gameId;
    private String gameName;
    private String gameIcon;
    private String title;
    private String description;
    private String requirements;
    private LobbyTone tone;
    private int maxPlayers;
    /** Seats taken, owner included. The browse card's "3/5". */
    private int playerCount;

    private Instant startsAt;
    private LobbyStatus status;
    /** The viewer's own standing, or null for a stranger browsing. */
    private LobbyMemberStatus myStatus;
    /** Chat messages newer than the viewer's watermark. Only filled on "mine". */
    private long unreadCount;
    /**
     * Whether the owner paid to pin this to the top of the list.
     *
     * <p>A boolean rather than the timestamp: the client draws a frame and the server does
     * the ordering, so when it was bought is nobody else's business.
     */
    private boolean boosted;

    private Instant createdAt;
}
