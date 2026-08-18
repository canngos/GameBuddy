import { useMutation } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { authApi } from '../../src/api/auth';
import { useT } from '../../src/i18n/useT';
import { usePasswordReset } from '../../src/session/passwordReset';
import { Button, ErrorNotice, Screen, Text, TextField } from '../../src/ui';
import { emailError } from '../../src/validation';

/**
 * Step one of three: which address to send the code to.
 *
 * **The copy has to stay conditional** — "if that address has an account" rather than
 * "we sent you a code". `/auth/sendCode` answers identically for an address that has an
 * account and one that does not, precisely so nobody can use it to discover who is
 * registered here; a screen that said "sent!" only when the account existed would give
 * that away again in the one place the user can see it.
 */
export default function Forgot() {
  const t = useT();
  const router = useRouter();
  const begin = usePasswordReset((s) => s.begin);

  const [email, setEmail] = useState('');
  const [touched, setTouched] = useState(false);

  const send = useMutation({
    mutationFn: () => authApi.sendCode(email.trim(), false),
    onSuccess: () => {
      begin(email.trim());
      router.push('/forgot-code');
    },
  });

  function submit() {
    setTouched(true);
    if (emailError(email)) return;
    send.mutate();
  }

  return (
    <Screen scroll>
      <View className="gap-2 pb-8 pt-12">
        <Text variant="overline">{t.auth.stepOne}</Text>
        <Text variant="title">{t.auth.forgot.title}</Text>
        <Text variant="body" className="text-muted">
          {t.auth.forgot.subtitle}
        </Text>
      </View>

      <View className="gap-5">
        <TextField
          label={t.auth.forgot.email}
          value={email}
          onChangeText={setEmail}
          error={touched ? (emailError(email)?.(t) ?? null) : null}
          keyboardType="email-address"
          autoCapitalize="none"
          textContentType="emailAddress"
          autoComplete="email"
          placeholder={t.auth.emailPlaceholder}
          onSubmitEditing={submit}
          returnKeyType="go"
        />

        {send.error && <ErrorNotice error={send.error} />}
      </View>

      <View className="mt-auto gap-2 pt-10">
        <Button label={t.auth.forgot.submit} loading={send.isPending} onPress={submit} />
        <Button label={t.common.back} variant="ghost" onPress={() => router.back()} />
      </View>
    </Screen>
  );
}
