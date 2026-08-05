package com.gamebuddy.auth.application.mapper;

import com.gamebuddy.auth.interfaces.dto.GamerDto;
import com.gamebuddy.shared.entity.Gamer;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Gamer to {@link GamerDto}.
 *
 * <p>{@code avatar} is not mapped here: the entity holds an avatar id, while the DTO
 * carries the resolved image, and resolving it needs a repository lookup. The service
 * fills it in after mapping — see {@code DefaultAdminService#getBlockedUsers}.
 */
@Mapper
public interface GamerMapper {

    @Mapping(target = "username", source = "gamerUsername")
    @Mapping(target = "avatar", ignore = true)
    GamerDto toDto(Gamer gamer);

    List<GamerDto> toDtoList(List<Gamer> gamers);
}
