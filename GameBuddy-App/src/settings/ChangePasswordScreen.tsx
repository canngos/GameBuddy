import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { View } from 'react-native';
import { authApi } from '../api/auth';
import { ApiError, Code } from '../api/envelope';
import { useT } from '../i18n/useT';
import { useSession } from '../session/store';
import { Button, Card, Screen, Text, TextField } from '../ui';
import { EditScreen } from '../ui/EditScreen';
import { passwordError } from '../validation';

/**
 * Changing your own password.
 *
 * Shared by the app's Settings and by the moderator console. Nothing in it is specific to
 * either — it is the same endpoint, the same validation, and the same consequence — and
 * the moderator is the one account for which changing the password is the *only* way to
 * rotate it, since the bootstrap deliberately refuses to.
 */
export function ChangePasswordScreen() {
  const t = useT();
  const signOut = useSession((s) => s.signOut);

  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [touched, setTouched] = useState(false);

  const problem = touched ? passwordError(next) : null;
  // Checked here as well as by the backend (PASSWORD_SAME, 112), because being told
  // "that is the same password" after a round trip is worse than being told now.
  const unchanged = next.length > 0 && next === current;

  const save = useMutation({
    mutationFn: () => authApi.changePassword(current, next),
  });

  const wrongCurrent =
    save.error instanceof ApiError && save.error.is(Code.CURRENT_PASSWORD_WRONG);

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
      title={t.settings.passwordScreen.title}
      subtitle={t.settings.passwordScreen.subtitle}
      saveLabel={t.settings.passwordScreen.title}
      onSave={() => {
        setTouched(true);
        if (passwordError(next) || unchanged || current.length === 0) return;
        save.mutate();
      }}
      saving={save.isPending}
      canSave={current.length > 0 && next.length > 0}
      // The wrong-current-password case is shown on its own field instead.
      error={wrongCurrent ? undefined : save.error}
    >
      <View className="gap-5">
        <TextField
          label={t.settings.passwordScreen.current}
          value={current}
          onChangeText={setCurrent}
          error={wrongCurrent ? t.settings.passwordScreen.wrongCurrent : null}
          secure
          textContentType="password"
          autoComplete="current-password"
        />

        <TextField
          label={t.settings.passwordScreen.next}
          value={next}
          onChangeText={setNext}
          error={
            (problem ? problem(t) : null) ??
            (unchanged ? t.settings.passwordScreen.sameAsOld : null)
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
