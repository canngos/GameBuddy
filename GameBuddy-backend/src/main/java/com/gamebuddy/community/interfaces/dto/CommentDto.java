package com.gamebuddy.community.interfaces.dto;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommentDto {
    private String commentId;
    private String username;
    private String avatar;
    private String message;
    private Integer likeCount;

    /**
     * Whether the gamer asking has liked this comment.
     *
     * <p>The post DTO always had this and the comment DTO did not, so a client could
     * render a comment's like count but had no way to know whether the button under it
     * should be filled in — and no way to tell "like" from "unlike" without guessing.
     */
    private Boolean isLiked;

    private Instant updatedDate;
}
