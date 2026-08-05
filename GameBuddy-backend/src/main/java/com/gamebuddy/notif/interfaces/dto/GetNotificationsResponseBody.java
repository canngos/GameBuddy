package com.gamebuddy.notif.interfaces.dto;

import com.gamebuddy.common.base.BaseModel;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetNotificationsResponseBody implements BaseModel {
    private List<NotificationDto> userNotifications;
}
