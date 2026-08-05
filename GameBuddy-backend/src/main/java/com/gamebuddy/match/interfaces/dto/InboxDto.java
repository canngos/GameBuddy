package com.gamebuddy.match.interfaces.dto;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InboxDto {

    private String userId;
    private String username;
    private String avatar;
    private String lastMessage;
    private Instant lastMessageTime;
    private Long unreadCount;
}
