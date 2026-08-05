package com.gamebuddy.community.application.mapper;

import com.gamebuddy.community.infrastructure.entity.Comment;
import com.gamebuddy.community.infrastructure.entity.Community;
import com.gamebuddy.community.infrastructure.entity.Post;
import com.gamebuddy.community.interfaces.dto.CommentDto;
import com.gamebuddy.community.interfaces.dto.CommunityDto;
import com.gamebuddy.community.interfaces.dto.GamerDto;
import com.gamebuddy.community.interfaces.dto.PostDto;
import com.gamebuddy.shared.entity.Gamer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Community entities to their DTOs.
 *
 * <p>Replaces {@code BeanUtils.copyProperties}, which copied by reflection at runtime and
 * silently skipped anything whose name had drifted. Fields that need a lookup or depend
 * on who is asking are deliberately ignored here and filled in by the service; with
 * {@code -Amapstruct.unmappedTargetPolicy=ERROR}, forgetting one is a build failure.
 */
@Mapper
public interface CommunityMapper {

    @Mapping(target = "communityId", source = "communityId")
    @Mapping(target = "memberCount", expression = "java(community.getMembers().size())")
    @Mapping(target = "postCount", expression = "java(community.getPosts().size())")
    @Mapping(target = "isJoined", ignore = true)
    CommunityDto toDto(Community community);

    @Mapping(target = "communityName", source = "community.name")
    @Mapping(target = "commentCount", expression = "java(post.getComments().size())")
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "isLiked", ignore = true)
    PostDto toDto(Post post);

    @Mapping(target = "username", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "isLiked", ignore = true)
    CommentDto toDto(Comment comment);

    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "isOwner", ignore = true)
    @Mapping(target = "frame", ignore = true)
    GamerDto toDto(Gamer gamer);

    default String map(java.util.UUID id) {
        return id == null ? null : id.toString();
    }
}
