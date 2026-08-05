package com.gamebuddy.match.interfaces.dto;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConversationDto {
    private String id;
    private String sender;
    private String receiver;
    private String message;
    private Instant date;
}
