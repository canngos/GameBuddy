package com.gamebuddy.auth.application.mapper;

import com.gamebuddy.auth.interfaces.request.GameRequest;
import com.gamebuddy.auth.interfaces.request.KeywordRequest;
import com.gamebuddy.shared.entity.Games;
import com.gamebuddy.shared.entity.Keywords;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Admin catalogue requests to their entities. */
@Mapper
public interface AdminCatalogueMapper {

    @Mapping(target = "gameId", expression = "java(java.util.UUID.randomUUID().toString())")
    @Mapping(target = "description", source = "gameDescription")
    @Mapping(target = "avgVote", source = "rating")
    // Popularity is derived from play counts by the catalogue job, not declared by whoever
    // adds the game — an admin marking their own favourite "popular" would skew the feed.
    @Mapping(target = "isPopular", ignore = true)
    // The inverse side of the join. Populated by gamers liking the game, never on insert.
    @Mapping(target = "gamers", ignore = true)
    // What a game runs on is a fact IGDB holds, not a judgement an admin makes, so it is
    // written by the tagging scripts rather than typed into the console. Left empty here
    // the game still appears in the picker — it sorts below the ones on your platforms
    // instead of vanishing — so an admin-added game is incomplete, never invisible.
    @Mapping(target = "platforms", ignore = true)
    Games toEntity(GameRequest request);

    @Mapping(target = "id", expression = "java(java.util.UUID.randomUUID())")
    @Mapping(target = "keywordName", source = "keyword")
    // Written by @CreationTimestamp on insert.
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "gamers", ignore = true)
    Keywords toEntity(KeywordRequest request);
}
