import { useMutation } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { authApi } from '../../src/api/auth';
import { ApiError, Code } from '../../src/api/envelope';
import { openPrivacy, openTerms } from '../../src/legal';
import { useT } from '../../src/i18n/useT';
import { Button, Checkbox, ErrorNotice, Screen, Text, TextField } from '../../src/ui';
import { emailError, passwordError } from '../../src/validation';

export default function Register() {
  const t = useT();
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [accepted, setAccepted] = useState(false);
  const [touched, setTouched] = useState(false);

  const emailProblem = touched ? emailError(email) : null;
  const passwordProblem = touched ? passwordError(password) : null;
  // Only complained about once they have tried to continue. Showing "you must accept"
  // before anyone has had a chance to read it is nagging, not guidance.
  const consentProblem = touched && !accepted;

  const register = useMutation({
    mutationFn: () => authApi.register(email.trim(), password, accepted),
    onSuccess: () => {
      // Replace, not push: the account now exists and the code has been sent, so
      // coming back here to submit the same address again can only cause confusion.
      router.replace({ pathname: '/verify', params: { email: email.trim() } });
    },
  });

  function submit() {
    setTouched(true);
    if (emailError(email) || passwordError(password) || !accepted) return;
    register.mutate();
  }

  // The address is already taken *and verified*. Sending them to sign in is more
  // useful than repeating the error, since it is almost always their own account.
  const emailTaken =
    register.error instanceof ApiError && register.error.is(Code.EMAIL_EXISTS);

  return (
    <Screen scroll>
      <View className="gap-2 pb-8 pt-12">
        <Text variant="overline">{t.auth.stepOne}</Text>
        <Text variant="title">{t.auth.register.title}</Text>
        <Text variant="body" className="text-muted">
          We will email a six-digit code to confirm the address.
        </Text>
      </View>

      <View className="gap-5">
        <TextField
          label={t.auth.register.email}
          value={email}
          onChangeText={setEmail}
          error={emailProblem}
          keyboardType="email-address"
          textContentType="emailAddress"
          autoComplete="email"
          placeholder="you@example.com"
        />

        <TextField
          label={t.auth.register.password}
          value={password}
          onChangeText={setPassword}
          error={passwordProblem}
          hint={t.auth.register.passwordHint}
          secure
          textContentType="newPassword"
          autoComplete="new-password"
          onSubmitEditing={submit}
          returnKeyType="go"
        />

        <View className="gap-2">
          <Checkbox
            checked={accepted}
            onChange={setAccepted}
            accessibilityLabel="I am 18 or over and accept the Terms and Privacy Policy"
          >
            <Text variant="body">
              I confirm I am <Text variant="bodyStrong">18 years of age or older</Text> and I
              accept the{' '}
              <Text variant="bodyStrong" className="text-primary" onPress={openTerms}>
                Terms of Service
              </Text>{' '}
              and{' '}
              <Text variant="bodyStrong" className="text-primary" onPress={openPrivacy}>
                Privacy Policy
              </Text>
              .
            </Text>
          </Checkbox>

          {consentProblem && (
            <Text variant="caption" className="text-danger">
              You need to accept this to create an account.
            </Text>
          )}
        </View>

        {register.error && !emailTaken && <ErrorNotice error={register.error} />}

        {emailTaken && (
          <View className="gap-3 rounded-card border border-danger/40 bg-danger/10 p-4">
            <Text variant="bodyStrong" className="text-danger">
              That email is already registered.
            </Text>
            <Button
              label={t.auth.register.signInInstead}
              variant="secondary"
              size="md"
              onPress={() => router.replace('/login')}
            />
          </View>
        )}
      </View>

      <View className="mt-auto gap-2 pt-10">
        <Button label={t.common.continue} loading={register.isPending} onPress={submit} />
        <Button label={t.common.back} variant="ghost" onPress={() => router.back()} />
      </View>
    </Screen>
  );
}
