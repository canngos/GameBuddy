import { useMutation } from '@tanstack/react-query';
import { Redirect, useRouter } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { authApi } from '../../src/api/auth';
import { useT } from '../../src/i18n/useT';
import { usePasswordReset } from '../../src/session/passwordReset';
import { Button, Card, ErrorNotice, Screen, Text, TextField } from '../../src/ui';
import { normalisePassword, passwordError } from '../../src/validation';

/**
 * Step three of three: the new password.
 *
 * Mirrors `ChangePasswordScreen` — same field, same hint, same success card — minus the
 * current-password field, which is the whole point of being here. It ends the same way it
 * does there, and for the same reason: the reset revokes every token the account had, so
 * there is nothing to navigate back into. The only exit is signing in with what was just
 * chosen.
 */
export default function ForgotReset() {
  const t = useT();
  const router = useRouter();
  const email = usePasswordReset((s) => s.email);
  const ticket = usePasswordReset((s) => s.ticket);
  const clear = usePasswordReset((s) => s.clear);

  const [password, setPassword] = useState('');
  const [touched, setTouched] = useState(false);

  const save = useMutation({
    mutationFn: () => authApi.resetPassword(email, ticket, normalisePassword(password)),
  });

  function submit() {
    setTouched(true);
    if (passwordError(password)) return;
    save.mutate();
  }

  if (save.isSuccess) {
    return (
      <Screen scroll>
        <View className="gap-2 pb-6 pt-12">
          <Text variant="title">{t.settings.passwordScreen.changedTitle}</Text>
        </View>

        <Card className="gap-2">
          <Text variant="bodyStrong">{t.auth.forgot.doneTitle}</Text>
          <Text variant="caption">{t.auth.forgot.doneBody}</Text>
        </Card>

        <View className="mt-auto pt-10">
          <Button
            label={t.auth.forgot.signIn}
            onPress={() => {
              // Cleared here rather than in the mutation's `onSuccess`. React Query runs
              // that callback before it flips the observer to success, so emptying the
              // store there races the guard below: one render sees no ticket and no
              // success yet, and sends the user back to step one. Clearing on the way out
              // cannot be raced, and the ticket it holds until then is already spent.
              clear();
              // replace, not push: the three screens behind this one are all spent — a
              // used ticket, a burnt code — and the back gesture must not return to them.
              router.replace('/login');
            }}
          />
        </View>
      </Screen>
    );
  }

  // No ticket means this screen was reached without step two, so there is nothing it can
  // spend. Sending the user back to the code entry keeps whatever address they gave.
  //
  // Only while nothing has been attempted: once a reset is in flight the ticket may be
  // spent and cleared under us, and navigating away from a request whose result decides
  // what the user sees next is never right.
  if (!ticket && save.isIdle) return <Redirect href={email ? '/forgot-code' : '/forgot'} />;

  const problem = touched ? passwordError(password) : null;

  return (
    <Screen scroll>
      <View className="gap-2 pb-8 pt-12">
        <Text variant="overline">{t.auth.stepThree}</Text>
        <Text variant="title">{t.auth.forgot.resetTitle}</Text>
        <Text variant="body" className="text-muted">
          {t.auth.forgot.resetSubtitle}
        </Text>
      </View>

      <View className="gap-5">
        <TextField
          label={t.settings.passwordScreen.next}
          value={password}
          onChangeText={setPassword}
          error={problem ? problem(t) : null}
          hint={t.auth.register.passwordHint}
          secure
          textContentType="newPassword"
          autoComplete="new-password"
          onSubmitEditing={submit}
          returnKeyType="go"
        />

        {save.error && <ErrorNotice error={save.error} />}
      </View>

      <View className="mt-auto pt-10">
        <Button label={t.auth.forgot.resetSubmit} loading={save.isPending} onPress={submit} />
      </View>
    </Screen>
  );
}
