import { useMutation } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { authApi } from '../../src/api/auth';
import { ApiError, Code } from '../../src/api/envelope';
import { useSession } from '../../src/session/store';
import { useT } from '../../src/i18n/useT';
import { Button, ErrorNotice, Screen, Text, TextField } from '../../src/ui';

export default function Login() {
  const t = useT();
  const router = useRouter();
  const signIn = useSession((s) => s.signIn);

  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');

  const login = useMutation({
    mutationFn: async () => {
      const session = await authApi.login(identifier.trim(), password);
      await signIn(session.accessToken, session.userId);
    },
    onSuccess: () => router.replace('/home'),
  });

  /**
   * Two refusals are not really failures, and both are recovered the same way.
   *
   * 106 — the address was never confirmed. 109 — it was, but onboarding stopped before
   * the profile was filled in. Neither can be fixed by retyping the password, and in
   * both cases the account is unreachable until a fresh code is verified, because
   * verification is the only other thing that issues a token.
   */
  const needsCode =
    login.error instanceof ApiError &&
    login.error.is(Code.USER_NOT_VERIFIED, Code.USER_NOT_COMPLETED);

  const sendCode = useMutation({
    mutationFn: () => authApi.sendCode(identifier.trim()),
    onSuccess: () =>
      router.replace({ pathname: '/verify', params: { email: identifier.trim() } }),
  });

  const canSubmit = identifier.trim().length > 0 && password.length > 0;

  return (
    <Screen scroll>
      <View className="gap-2 pb-8 pt-12">
        <Text variant="title">{t.auth.login.title}</Text>
        <Text variant="body" className="text-muted">
          {t.auth.login.subtitle}
        </Text>
      </View>

      <View className="gap-5">
        <TextField
          label={t.auth.login.identifier}
          value={identifier}
          onChangeText={setIdentifier}
          textContentType="username"
          autoComplete="username"
          placeholder={t.auth.emailPlaceholder}
        />

        <TextField
          label={t.auth.login.password}
          value={password}
          onChangeText={setPassword}
          secure
          textContentType="password"
          autoComplete="current-password"
          onSubmitEditing={() => canSubmit && login.mutate()}
          returnKeyType="go"
        />

        {login.error && !needsCode && <ErrorNotice error={login.error} />}

        {needsCode && (
          <View className="gap-3 rounded-card border border-danger/40 bg-danger/10 p-4">
            <Text variant="bodyStrong" className="text-danger">
              {(login.error as ApiError).message}
            </Text>
            <Text variant="caption">
              {/* Only useful if the identifier is the email — sendCode looks up by
                  address, so a username here comes back as "User not found". */}
              {t.auth.login.needsCode}
            </Text>
            <Button
              label={t.auth.login.emailCode}
              variant="secondary"
              size="md"
              loading={sendCode.isPending}
              onPress={() => sendCode.mutate()}
            />
            {sendCode.error && <ErrorNotice error={sendCode.error} />}
          </View>
        )}
      </View>

      <View className="mt-auto gap-2 pt-10">
        <Button
          label={t.auth.login.submit}
          loading={login.isPending}
          disabled={!canSubmit}
          onPress={() => login.mutate()}
        />
        <Button label={t.common.back} variant="ghost" onPress={() => router.back()} />
      </View>
    </Screen>
  );
}
