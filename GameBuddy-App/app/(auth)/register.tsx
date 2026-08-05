import { useMutation } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { authApi } from '../../src/api/auth';
import { ApiError, Code } from '../../src/api/envelope';
import { Button, ErrorNotice, Screen, Text, TextField } from '../../src/ui';
import { emailError, passwordError } from '../../src/validation';

export default function Register() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [touched, setTouched] = useState(false);

  const emailProblem = touched ? emailError(email) : null;
  const passwordProblem = touched ? passwordError(password) : null;

  const register = useMutation({
    mutationFn: () => authApi.register(email.trim(), password),
    onSuccess: () => {
      // Replace, not push: the account now exists and the code has been sent, so
      // coming back here to submit the same address again can only cause confusion.
      router.replace({ pathname: '/verify', params: { email: email.trim() } });
    },
  });

  function submit() {
    setTouched(true);
    if (emailError(email) || passwordError(password)) return;
    register.mutate();
  }

  // The address is already taken *and verified*. Sending them to sign in is more
  // useful than repeating the error, since it is almost always their own account.
  const emailTaken =
    register.error instanceof ApiError && register.error.is(Code.EMAIL_EXISTS);

  return (
    <Screen scroll>
      <View className="gap-2 pb-8 pt-12">
        <Text variant="overline">STEP 1</Text>
        <Text variant="title">Create your account</Text>
        <Text variant="body" className="text-muted">
          We will email a six-digit code to confirm the address.
        </Text>
      </View>

      <View className="gap-5">
        <TextField
          label="Email"
          value={email}
          onChangeText={setEmail}
          error={emailProblem}
          keyboardType="email-address"
          textContentType="emailAddress"
          autoComplete="email"
          placeholder="you@example.com"
        />

        <TextField
          label="Password"
          value={password}
          onChangeText={setPassword}
          error={passwordProblem}
          hint="At least 8 characters, with a letter and a number."
          secure
          textContentType="newPassword"
          autoComplete="new-password"
          onSubmitEditing={submit}
          returnKeyType="go"
        />

        {register.error && !emailTaken && <ErrorNotice error={register.error} />}

        {emailTaken && (
          <View className="gap-3 rounded-card border border-danger/40 bg-danger/10 p-4">
            <Text variant="bodyStrong" className="text-danger">
              That email is already registered.
            </Text>
            <Button
              label="Sign in instead"
              variant="secondary"
              size="md"
              onPress={() => router.replace('/login')}
            />
          </View>
        )}
      </View>

      <View className="mt-auto gap-2 pt-10">
        <Button label="Continue" loading={register.isPending} onPress={submit} />
        <Button label="Back" variant="ghost" onPress={() => router.back()} />
      </View>
    </Screen>
  );
}
