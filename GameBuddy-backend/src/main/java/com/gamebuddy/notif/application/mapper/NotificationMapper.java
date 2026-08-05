package com.gamebuddy.notif.application.mapper;

import com.gamebuddy.notif.infrastructure.entity.Notification;
import com.gamebuddy.notif.interfaces.dto.NotificationDto;
import java.util.Collection;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Notification entities to their DTOs, replacing BeanUtils.copyProperties. */
@Mapper
public interface NotificationMapper {

    @Mapping(target = "userIdOrTopic", source = "recipient")
    NotificationDto toDto(Notification notification);

    List<NotificationDto> toDtos(Collection<Notification> notifications);
}
