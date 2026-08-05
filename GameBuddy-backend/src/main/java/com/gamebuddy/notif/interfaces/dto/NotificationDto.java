package com.gamebuddy.notif.interfaces.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class NotificationDto {
    private String title;
    private String body;
    private String userIdOrTopic;
    private Boolean isTopic;
    private Instant createdDate;
}
