package com.gamebuddy.match.application.mapper;

import com.gamebuddy.match.interfaces.dto.GamerDto;
import com.gamebuddy.match.interfaces.dto.GamesDto;
import com.gamebuddy.shared.entity.Gamer;
import com.gamebuddy.shared.entity.Games;
import java.util.Collection;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Chat and match entities to their DTOs.
 *
 * <p>Replaces {@code BeanUtils.copyProperties}, which matched by name reflectively and
 * silently produced nulls whenever a name drifted — including in
 * {@code findChatMessages}, where it copied {@code message.date} into a field of a
 * different type and left the service to patch it up afterwards.
 */
@Mapper
public interface ChatMapper {

    /*
     * No toDto(ChatMessage). The stored body is ciphertext, so building a ConversationDto
     * requires the cipher — a generated mapper would either emit the encrypted bytes or
     * need the key handed to it. The service builds it instead, decrypting in one place.
     */

    GamesDto toDto(Games game);

    List<GamesDto> toGameDtos(Collection<Games> games);

    /** Avatar, games and keywords need lookups or flattening; the service fills them in. */
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "favoriteGames", ignore = true)
    @Mapping(target = "frame", ignore = true)
    @Mapping(target = "selectedKeywords", ignore = true)
    @Mapping(target = "platforms", ignore = true)
    GamerDto toDto(Gamer gamer);
}
