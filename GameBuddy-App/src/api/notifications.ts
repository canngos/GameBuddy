import { api } from './client';
import type { NotificationPreferences } from './types';

/**
 * Which kinds of notification a gamer is willing to receive.
 *
 * Four switches, not one per notification type: the decision people want to make is
 * "keep the messages, stop the community noise", and a screen with a row per event is a
 * screen nobody reads.
 */
export const notificationsApi = {
  preferences: () => api.get<NotificationPreferences>('/notif/preferences'),

  /**
   * Replaces the whole set.
   *
   * Sending all four rather than the one that changed means the screen and the server
   * cannot end up disagreeing about a switch that was never mentioned.
   */
  updatePreferences: (preferences: NotificationPreferences) =>
    api.put<NotificationPreferences>('/notif/preferences', preferences),
};
