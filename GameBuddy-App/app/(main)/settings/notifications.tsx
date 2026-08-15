import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Linking, Switch, View } from 'react-native';
import { notificationsApi } from '../../../src/api/notifications';
import type { NotificationPreferences } from '../../../src/api/types';
import {
  permissionState,
  requestSystemPermission,
  type PermissionState,
} from '../../../src/notifications/permission';
import { registerDeviceToken } from '../../../src/notifications/usePushRegistration';
import { useFocusEffect } from 'expo-router';
import { useThemeColors } from '../../../src/theme';
import {
  BackHeader,
  Button,
  Card,
  ErrorNotice,
  Screen,
  Text,
  useSoundEnabled,
} from '../../../src/ui';

const KEY = ['notificationPreferences'];

/**
 * Which notifications a gamer wants.
 *
 * <p>Two layers, and the screen has to show both or it lies. The operating system's
 * permission sits above everything here: with it denied, none of these switches can
 * deliver anything however they are set. A settings screen that shows four cheerful
 * toggles while the OS is silently dropping every notification is worse than no screen at
 * all, so the state of that permission is the first thing on it.
 */
export default function NotificationSettings() {
  const colors = useThemeColors();
  const queryClient = useQueryClient();
  const [system, setSystem] = useState<PermissionState | null>(null);
  const [asking, setAsking] = useState(false);

  const prefs = useQuery({ queryKey: KEY, queryFn: notificationsApi.preferences });

  /**
   * Asks the system, from here rather than the primer.
   *
   * <p>Registers the token on success: permission granted after startup means the launch
   * that would normally have sent it has already happened, and without this the account
   * would sit with permission and a dead token until the app was next restarted.
   */
  const enable = async () => {
    setAsking(true);
    try {
      if (await requestSystemPermission()) await registerDeviceToken();
    } finally {
      setSystem(await permissionState());
      setAsking(false);
    }
  };

  // Re-read on focus, because the way to change it is to leave for the system settings
  // and come back. Without this the banner would still claim notifications were off.
  useFocusEffect(
    useCallback(() => {
      void permissionState().then(setSystem);
    }, []),
  );

  const save = useMutation({
    mutationFn: notificationsApi.updatePreferences,
    // The server answers with what it stored; writing that into the cache means a switch
    // shows the row rather than the tap.
    onSuccess: (saved) => queryClient.setQueryData(KEY, saved),
    onError: () => queryClient.invalidateQueries({ queryKey: KEY }),
  });

  const toggle = (key: keyof NotificationPreferences) => {
    if (!prefs.data) return;
    const next = { ...prefs.data, [key]: !prefs.data[key] };
    // Optimistic: a switch that waits for a round trip before moving feels broken.
    // onError puts it back by refetching.
    queryClient.setQueryData(KEY, next);
    save.mutate(next);
  };

  return (
    <Screen scroll edges={['top']}>
      <BackHeader title="Notifications" />

      {prefs.isPending && <ActivityIndicator color={colors.primary} />}
      {prefs.error && <ErrorNotice error={prefs.error} onRetry={() => prefs.refetch()} />}
      {save.error && <ErrorNotice error={save.error} />}

      {/* Anything other than 'granted' means nothing gets delivered, and both of those
          states have to say so. The banner used to check for 'denied' alone, which missed
          the commonest way to end up here: turning GameBuddy's notifications off in
          Android's own settings leaves the permission ungranted but still askable, which
          reads as 'undetermined'. That showed four confident switches over a channel the
          system was silently dropping — the exact lie this banner exists to prevent. */}
      {system !== null && system !== 'granted' && (
        <Card className="mb-4 gap-3">
          <View className="gap-1">
            <Text variant="bodyStrong">Notifications are off for GameBuddy</Text>
            <Text variant="caption">
              Android is blocking them, so nothing below can reach you until they are turned
              back on.
            </Text>
          </View>
          {system === 'undetermined' ? (
            // The system will still ask, so ask — sending somebody to Settings for a
            // dialog we can show right here is a detour with a worse success rate.
            <Button
              label="Turn on notifications"
              variant="secondary"
              size="md"
              loading={asking}
              onPress={enable}
            />
          ) : (
            // openSettings, not a request: once the system prompt is spent, asking again
            // does nothing and would look like the button was broken.
            <Button
              label="Open system settings"
              variant="secondary"
              size="md"
              onPress={() => void Linking.openSettings()}
            />
          )}
        </Card>
      )}

      {prefs.data && (
        <View className="gap-3 pb-8">
          <Text variant="overline">WHAT TO SEND</Text>

          <Card className="gap-1">
            <Row
              title="Messages"
              body="When someone you matched with sends you a message."
              value={prefs.data.messages}
              onToggle={() => toggle('messages')}
            />
            <Divider />
            <Row
              title="Matches and friends"
              body="New matches, friend requests and answers, lobby activity, badges you earn."
              value={prefs.data.social}
              onToggle={() => toggle('social')}
            />
            <Divider />
            <Row
              title="Reminders"
              body="The occasional nudge when you have been away and something is waiting."
              value={prefs.data.reminders}
              onToggle={() => toggle('reminders')}
            />
          </Card>

          <Text variant="caption">
            Turning everything off here keeps the app quiet without switching notifications off
            for it entirely — so anything you turn back on later still works.
          </Text>
        </View>
      )}

      <SoundSetting />
    </Screen>
  );
}

/**
 * The sound switch, in its own group below the four above.
 *
 * **Separated on purpose, because it is a different kind of setting.** The four above are
 * account preferences: they live on the server, travel with the account, and decide what the
 * backend *sends*. This one is stored on the device and decides what this phone *plays* —
 * someone with a work phone and a personal one will reasonably want it on for one and off
 * for the other, which an account-level flag cannot express. Putting it in the same card
 * would imply it behaves like its neighbours.
 *
 * It is also not a fifth field on `NotificationPreferences`: that endpoint sends all four
 * values on every write rather than a delta, so adding to it is a backend change, and this
 * needs none.
 */
function SoundSetting() {
  const enabled = useSoundEnabled((s) => s.enabled);
  const setEnabled = useSoundEnabled((s) => s.setEnabled);

  return (
    <View className="gap-3 pb-8">
      <Text variant="overline">ON THIS PHONE</Text>

      <Card className="gap-1">
        <Row
          title="Sounds"
          body="A short cue when something happens in the app — a match, a purchase, a message."
          value={enabled}
          onToggle={() => setEnabled(!enabled)}
        />
      </Card>

      <Text variant="caption">
        Cues never play over the silent switch, and they never interrupt music you are already
        listening to.
      </Text>
    </View>
  );
}

function Row({
  title,
  body,
  value,
  onToggle,
}: {
  title: string;
  body: string;
  value: boolean;
  onToggle: () => void;
}) {
  const colors = useThemeColors();

  return (
    <View className="flex-row items-center gap-4 py-3">
      <View className="flex-1 gap-0.5">
        <Text variant="bodyStrong">{title}</Text>
        <Text variant="caption">{body}</Text>
      </View>
      <Switch
        value={value}
        onValueChange={onToggle}
        accessibilityLabel={title}
        trackColor={{ true: colors.primary, false: colors.line }}
        thumbColor={colors.surface}
      />
    </View>
  );
}

function Divider() {
  return <View className="h-px bg-line" />;
}
