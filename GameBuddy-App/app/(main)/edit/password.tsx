import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { View } from 'react-native';
import { authApi } from '../../../src/api/auth';
import { ApiError, Code } from '../../../src/api/envelope';
import { useSession } from '../../../src/session/store';
import { Button, Card, Screen, Text, TextField } from '../../../src/ui';
import { EditScreen } from '../../../src/ui/EditScreen';
import { passwordError } from '../../../src/validation';

export default function EditPassword() {
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
          <Text variant="title">Password changed</Text>
        </View>

        <Card className="gap-2">
          <Text variant="bodyStrong">You have been signed out everywhere</Text>
          <Text variant="caption">
            Changing your password ends every session, on this device and any other.
            That is deliberate — if someone else had your old password, they are out
            too.
          </Text>
        </Card>

        <View className="mt-auto pt-10">
          <Button label="Sign in again" onPress={() => void signOut()} />
        </View>
      </Screen>
    );
  }

  return (
    <EditScreen
      title="Change password"
      subtitle="This signs you out on every device, including this one."
      saveLabel="Change password"
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
          label="Current password"
          value={current}
          onChangeText={setCurrent}
          error={wrongCurrent ? 'That is not your current password.' : null}
          secure
          textContentType="password"
          autoComplete="current-password"
        />

        <TextField
          label="New password"
          value={next}
          onChangeText={setNext}
          error={problem ?? (unchanged ? 'Pick something different from the old one.' : null)}
          hint="At least 8 characters, with a letter and a number."
          secure
          textContentType="newPassword"
          autoComplete="new-password"
        />
      </View>
    </EditScreen>
  );
}
