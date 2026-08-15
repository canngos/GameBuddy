package com.gamebuddy.profile.application.mapper;

import com.gamebuddy.profile.interfaces.dto.GamerDto;
import com.gamebuddy.shared.entity.Gamer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Gamer entities to their DTOs. */
@Mapper
public interface ProfileMapper {

    /**
     * {@code avatar} is left out: the entity stores an avatar id and the DTO carries the
     * resolved image, which needs a lookup. The service resolves them in one batched
     * query rather than one per friend.
     */
    @Mapping(target = "username", source = "gamerUsername")
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "frame", ignore = true)
    GamerDto toDto(Gamer gamer);
}
