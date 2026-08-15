package com.gamebuddy.lobby.application.mapper;

import com.gamebuddy.lobby.infrastructure.entity.Lobby;
import com.gamebuddy.lobby.infrastructure.entity.LobbyMember;
import com.gamebuddy.lobby.infrastructure.entity.LobbyMemberStatus;
import com.gamebuddy.lobby.interfaces.dto.LobbyDto;
import com.gamebuddy.lobby.interfaces.dto.LobbyMemberDto;
import com.gamebuddy.lobby.interfaces.dto.LobbyMessageDto;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.entity.Games;
import com.gamebuddy.shared.storage.AvatarUrls;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Entities to wire shapes. Takes already-resolved neighbours (owner, game) rather than
 * repositories, so the services keep control of how many queries a screen costs.
 */
@Component
@RequiredArgsConstructor
public class LobbyMapper {

    private final AvatarUrls avatarUrls;

    /**
     * @param owner resolved by the caller; may be null when the owner's account is gone
     * @param game resolved by the caller; may be null when the catalogue row is gone
     * @param playerCount seats taken, owner included
     * @param myStatus the viewer's row, or null for a stranger
     * @param unreadCount 0 unless the caller computed it (only "mine" pays for it)
     */
    public LobbyDto toDto(
            Lobby lobby, Gamer owner, Games game, int playerCount, LobbyMemberStatus myStatus, long unreadCount) {
        LobbyDto dto = new LobbyDto();
        dto.setId(lobby.getId().toString());
        dto.setOwnerId(lobby.getOwnerId());
        if (owner != null) {
            dto.setOwnerUsername(owner.getGamerUsername());
            dto.setOwnerAvatar(avatarUrls.visibleTo(owner));
        }
        dto.setGameId(lobby.getGameId());
        if (game != null) {
            dto.setGameName(game.getGameName());
            dto.setGameIcon(game.getGameIcon());
        }
        dto.setTitle(lobby.getTitle());
        dto.setDescription(lobby.getDescription());
        dto.setRequirements(lobby.getRequirements());
        dto.setTone(lobby.getTone());
        dto.setMaxPlayers(lobby.getMaxPlayers());
        dto.setPlayerCount(playerCount);
        dto.setStartsAt(lobby.getStartsAt());
        dto.setStatus(lobby.getStatus());
        dto.setMyStatus(myStatus);
        dto.setUnreadCount(unreadCount);
        dto.setCreatedAt(lobby.getCreatedAt());
        return dto;
    }

    public LobbyMemberDto toMemberDto(LobbyMember member, Gamer gamer) {
        LobbyMemberDto dto = new LobbyMemberDto();
        dto.setUserId(member.getUserId());
        if (gamer != null) {
            dto.setUsername(gamer.getGamerUsername());
            dto.setAvatar(avatarUrls.visibleTo(gamer));
        }
        dto.setStatus(member.getStatus());
        dto.setRequestedAt(member.getRequestedAt());
        return dto;
    }

    public LobbyMessageDto toMessageDto(
            UUID id, String senderId, String senderUsername, String message, Instant date) {
        LobbyMessageDto dto = new LobbyMessageDto();
        dto.setId(id.toString());
        dto.setSenderId(senderId);
        dto.setSenderUsername(senderUsername);
        dto.setMessage(message);
        dto.setDate(date);
        return dto;
    }
}
