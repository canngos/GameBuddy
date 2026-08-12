import { useMutation } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { authApi } from '../../src/api/auth';
import { ApiError, Code } from '../../src/api/envelope';
import { StepHeader } from '../../src/onboarding/StepHeader';
import { useSession } from '../../src/session/store';
import { Button, ErrorNotice, Screen, TextField } from '../../src/ui';
import { usernameError } from '../../src/validation';

export default function Username() {
  const router = useRouter();
  const usernameChosen = useSession((s) => s.usernameChosen);

  const [username, setUsername] = useState('');
  const [touched, setTouched] = useState(false);

  const submit = useMutation({
    mutationFn: () => authApi.setUsername(username.trim()),
    onSuccess: () => {
      usernameChosen();
      router.replace('/profile');
    },
  });

  const taken = submit.error instanceof ApiError && submit.error.is(Code.USERNAME_EXISTS);
  const problem = touched ? usernameError(username) : null;

  function send() {
    setTouched(true);
    if (usernameError(username)) return;
    submit.mutate();
  }

  return (
    <Screen scroll>
      <StepHeader
        step={1}
        total={6}
        title="Pick a username"
        subtitle="This is what other players see. You can change it later."
      />

      <View className="gap-5">
        <TextField
          label="Username"
          value={username}
          onChangeText={setUsername}
          error={problem ?? (taken ? 'That username is taken. Try another.' : null)}
          hint="Letters, numbers and underscores."
          placeholder="headshot_hero"
          maxLength={20}
          onSubmitEditing={send}
          returnKeyType="go"
        />

        {submit.error && !taken && <ErrorNotice error={submit.error} />}
      </View>

      <View className="mt-auto pt-10">
        <Button label="Continue" loading={submit.isPending} onPress={send} />
      </View>
    </Screen>
  );
}
