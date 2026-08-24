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
import { useUpper } from '../../../src/i18n/case';
import { useT } from '../../../src/i18n/useT';
import { useThemeColors } from '../../../src/theme';
import {
  BackHeader,
  Button,
  Card,
  ErrorNotice,
  Screen,
  Text,
  useHapticsEnabled,
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
  const t = useT();
  const upper = useUpper();
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
      setSystem(await permissionState());
    } catch (error) {
      // permissionState rejects on devices without Play Services, and it used to do so
      // from inside the finally - before setAsking(false), leaving the button spinning
      // until the screen was left. The focus handler re-reads the state anyway.
      console.warn('[notifications] could not enable', error);
    } finally {
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
      <BackHeader title={t.settings.notificationsScreen.title} />

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
            <Text variant="bodyStrong">{t.settings.notificationsScreen.offTitle}</Text>
            <Text variant="caption">{t.settings.notificationsScreen.offBody}</Text>
          </View>
          {system === 'undetermined' ? (
            // The system will still ask, so ask — sending somebody to Settings for a
            // dialog we can show right here is a detour with a worse success rate.
            <Button
              label={t.settings.notificationsScreen.turnOn}
              variant="secondary"
              size="md"
              loading={asking}
              onPress={enable}
            />
          ) : (
            // openSettings, not a request: once the system prompt is spent, asking again
            // does nothing and would look like the button was broken.
            <Button
              label={t.settings.notificationsScreen.openSystem}
              variant="secondary"
              size="md"
              onPress={() => void Linking.openSettings()}
            />
          )}
        </Card>
      )}

      {prefs.data && (
        <View className="gap-3 pb-8">
          <Text variant="overline">{upper(t.settings.notificationsScreen.whatToSend)}</Text>

          <Card className="gap-1">
            <Row
              title={t.settings.notificationsScreen.messagesTitle}
              body={t.settings.notificationsScreen.messagesBody}
              value={prefs.data.messages}
              onToggle={() => toggle('messages')}
            />
            <Divider />
            <Row
              title={t.settings.notificationsScreen.socialTitle}
              body={t.settings.notificationsScreen.socialBody}
              value={prefs.data.social}
              onToggle={() => toggle('social')}
            />
            <Divider />
            <Row
              title={t.settings.notificationsScreen.remindersTitle}
              body={t.settings.notificationsScreen.remindersBody}
              value={prefs.data.reminders}
              onToggle={() => toggle('reminders')}
            />
          </Card>

          <Text variant="caption">{t.settings.notificationsScreen.quietNote}</Text>
        </View>
      )}

      <SoundSetting />
    </Screen>
  );
}

/**
 * The device switches, in their own group below the four above.
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
  const t = useT();
  const upper = useUpper();
  const enabled = useSoundEnabled((s) => s.enabled);
  const setEnabled = useSoundEnabled((s) => s.setEnabled);
  const haptics = useHapticsEnabled((s) => s.enabled);
  const setHaptics = useHapticsEnabled((s) => s.setEnabled);

  return (
    <View className="gap-3 pb-8">
      <Text variant="overline">{upper(t.settings.notificationsScreen.onThisPhone)}</Text>

      <Card className="gap-1">
        <Row
          title={t.settings.notificationsScreen.soundsTitle}
          body={t.settings.notificationsScreen.soundsBody}
          value={enabled}
          onToggle={() => setEnabled(!enabled)}
        />
        <Divider />
        {/* This switch is the only thing standing between the app and buzzing somebody who
            does not want it. Android's own "touch feedback" setting used to be, and that is
            precisely the problem it replaced: the app cannot read it, so it could not tell
            a person who had turned it off from a device where the feedback simply never
            arrived. See `src/ui/haptics.ts`. */}
        <Row
          title={t.settings.notificationsScreen.vibrationTitle}
          body={t.settings.notificationsScreen.vibrationBody}
          value={haptics}
          onToggle={() => setHaptics(!haptics)}
        />
      </Card>

      <Text variant="caption">{t.settings.notificationsScreen.cuesNote}</Text>
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
