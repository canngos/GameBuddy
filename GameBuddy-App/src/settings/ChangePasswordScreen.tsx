import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { View } from 'react-native';
import { authApi } from '../api/auth';
import { ApiError, Code } from '../api/envelope';
import { profileApi } from '../api/catalogue';
import { useT } from '../i18n/useT';
import { useSession } from '../session/store';
import { Button, Card, Screen, Text, TextField } from '../ui';
import { EditScreen } from '../ui/EditScreen';
import { normalisePassword, passwordError } from '../validation';

/**
 * Changing your own password — or setting a first one.
 *
 * Shared by the app's Settings and by the moderator console. Nothing in it is specific to
 * either — it is the same endpoint, the same validation, and the same consequence — and
 * the moderator is the one account for which changing the password is the *only* way to
 * rotate it, since the bootstrap deliberately refuses to.
 *
 * **Two modes, because an account signed up with Google has no password at all.** They are
 * genuinely different actions rather than one action with an empty field: setting a first
 * password proves nothing and ends no sessions, while changing one proves the old password
 * and signs every device out. The server enforces the same split across two endpoints; this
 * screen picks its mode from `hasPassword` on the profile.
 */
export function ChangePasswordScreen() {
  const t = useT();
  const signOut = useSession((s) => s.signOut);
  const queryClient = useQueryClient();

  // Already cached by the Profile tab and by Settings, so this is a read rather than a
  // fetch in the normal case. Undefined while it loads: the change mode is the safe guess,
  // because it is what every account created before social sign-in existed needs.
  const me = useQuery({ queryKey: ['me'], queryFn: profileApi.me });
  const settingFirst = me.data?.hasPassword === false;

  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [touched, setTouched] = useState(false);

  const problem = touched ? passwordError(next) : null;
  // Checked here as well as by the backend (PASSWORD_SAME, 112), because being told
  // "that is the same password" after a round trip is worse than being told now.
  // Compared as they will be sent, so a password that differs only by a stray space is
  // caught here rather than by the backend's PASSWORD_SAME after a round trip.
  const unchanged = next.length > 0 && normalisePassword(next) === normalisePassword(current);

  const save = useMutation({
    mutationFn: () =>
      settingFirst
        ? authApi.setPassword(normalisePassword(next))
        : authApi.changePassword(normalisePassword(current), normalisePassword(next)),
    onSuccess: () => {
      // Only the first-password path stays signed in, and only it changes what the
      // settings screen should draw next.
      if (settingFirst) void queryClient.invalidateQueries({ queryKey: ['me'] });
    },
  });

  const wrongCurrent =
    save.error instanceof ApiError && save.error.is(Code.CURRENT_PASSWORD_WRONG);

  // Setting a first password keeps every session alive on purpose — see the server's
  // `setPassword`. So there is nothing to explain and nowhere to send anybody: the screen
  // simply reports it and the back button works.
  if (save.isSuccess && settingFirst) {
    return (
      <Screen scroll edges={['top', 'bottom']}>
        <View className="gap-2 pb-6 pt-8">
          <Text variant="title">{t.settings.passwordScreen.setTitle}</Text>
        </View>

        <Card className="gap-2">
          <Text variant="bodyStrong">{t.settings.passwordScreen.setDoneTitle}</Text>
          <Text variant="caption">{t.settings.passwordScreen.setDoneBody}</Text>
        </Card>
      </Screen>
    );
  }

  /**
   * Changing the password destroys **every** session, including this one:
   * `changePwd` calls `revokeIssuedTokens()` and then deletes every row for the
   * address. Navigating back would leave the app holding a dead token and the next
   * request would fail as an expired session, which reads like something broke.
   *
   * So the success state is explicit, and signing out is the only way onward.
   */
  if (save.isSuccess) {
    return (
      <Screen scroll edges={['top', 'bottom']}>
        <View className="gap-2 pb-6 pt-8">
          <Text variant="title">{t.settings.passwordScreen.changedTitle}</Text>
        </View>

        <Card className="gap-2">
          <Text variant="bodyStrong">{t.settings.passwordScreen.signedOutTitle}</Text>
          <Text variant="caption">{t.settings.passwordScreen.signedOutBody}</Text>
        </Card>

        <View className="mt-auto pt-10">
          <Button label={t.settings.passwordScreen.signInAgain} onPress={() => void signOut()} />
        </View>
      </Screen>
    );
  }

  return (
    <EditScreen
      title={settingFirst ? t.settings.passwordScreen.setTitle : t.settings.passwordScreen.title}
      subtitle={
        settingFirst ? t.settings.passwordScreen.setSubtitle : t.settings.passwordScreen.subtitle
      }
      saveLabel={settingFirst ? t.settings.passwordScreen.setTitle : t.settings.passwordScreen.title}
      onSave={() => {
        setTouched(true);
        if (passwordError(next)) return;
        if (!settingFirst && (unchanged || current.length === 0)) return;
        save.mutate();
      }}
      saving={save.isPending}
      canSave={next.length > 0 && (settingFirst || current.length > 0)}
      // The wrong-current-password case is shown on its own field instead.
      error={wrongCurrent ? undefined : save.error}
    >
      <View className="gap-5">
        {/* Absent rather than disabled when there is no password: a greyed-out field asking
            for something that does not exist is a puzzle, not a form. */}
        {!settingFirst && (
          <TextField
            label={t.settings.passwordScreen.current}
            value={current}
            onChangeText={setCurrent}
            error={wrongCurrent ? t.settings.passwordScreen.wrongCurrent : null}
            secure
            textContentType="password"
            autoComplete="current-password"
          />
        )}

        <TextField
          label={t.settings.passwordScreen.next}
          value={next}
          onChangeText={setNext}
          error={
            (problem ? problem(t) : null) ??
            (!settingFirst && unchanged ? t.settings.passwordScreen.sameAsOld : null)
          }
          hint={t.auth.register.passwordHint}
          secure
          textContentType="newPassword"
          autoComplete="new-password"
        />
      </View>
    </EditScreen>
  );
}
