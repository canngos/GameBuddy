package com.gamebuddy.profile.application.mapper;

import com.gamebuddy.profile.interfaces.dto.*;
import com.gamebuddy.shared.entity.Avatars;
import com.gamebuddy.shared.entity.Games;
import com.gamebuddy.shared.entity.Keywords;
import java.util.Collection;
import java.util.List;
import org.mapstruct.Mapper;

/**
 * Catalogue entities to their DTOs.
 *
 * <p>Replaces {@code BeanUtils.copyProperties}, which matched fields reflectively at
 * runtime: a renamed field silently stopped being copied and the response quietly
 * carried a null. These mappings are generated at compile time, and
 * {@code -Amapstruct.unmappedTargetPolicy=ERROR} turns an unmapped target into a build
 * failure.
 */
@Mapper
public interface ProfileCatalogueMapper {

    GamesDto toDto(Games game);

    List<GamesDto> toGameDtos(Collection<Games> games);

    KeywordsDto toDto(Keywords keyword);

    List<KeywordsDto> toKeywordDtos(Collection<Keywords> keywords);

    // Achievements were mapped here from a catalogue table. Badges have no table — the
    // catalogue is the Badge enum — so BadgeService builds their DTOs directly.

    AvatarsDto toDto(Avatars avatar);

    List<AvatarsDto> toAvatarDtos(Collection<Avatars> avatars);
}
