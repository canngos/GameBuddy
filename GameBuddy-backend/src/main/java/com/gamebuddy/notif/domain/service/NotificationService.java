package com.gamebuddy.notif.domain.service;

import com.gamebuddy.common.interfaces.DefaultMessageResponse;
import com.gamebuddy.notif.interfaces.dto.NotificationPreferencesDto;
import com.gamebuddy.notif.interfaces.request.SendNotificationTokenRequest;
import com.gamebuddy.notif.interfaces.request.SendNotificationTopicRequest;
import com.gamebuddy.notif.interfaces.response.GetNotificationsResponse;
import com.gamebuddy.notif.interfaces.response.NotificationPreferencesResponse;
import com.gamebuddy.shared.entity.Gamer;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    DefaultMessageResponse sendToToken(SendNotificationTokenRequest tokenRequest);

    DefaultMessageResponse sendToTopic(SendNotificationTopicRequest topicRequest);

    /**
     * The caller's own notification history, plus broadcasts.
     *
     * <p>This used to take a user id from the path and no principal at all, so any caller
     * could read any gamer's history by guessing an id.
     */
    GetNotificationsResponse showAll(Gamer principal, Pageable pageable);

    /** Which categories this gamer wants. */
    NotificationPreferencesResponse getPreferences(Gamer principal);

    /** Replaces the whole set; see {@code NotificationPreferencesDto} for why not a delta. */
    NotificationPreferencesResponse updatePreferences(Gamer principal, NotificationPreferencesDto preferences);
}
