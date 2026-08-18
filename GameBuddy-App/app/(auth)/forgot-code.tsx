import { useMutation } from '@tanstack/react-query';
import { Redirect, useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { View } from 'react-native';
import { authApi } from '../../src/api/auth';
import { ApiError, Code } from '../../src/api/envelope';
import { useT } from '../../src/i18n/useT';
import { usePasswordReset } from '../../src/session/passwordReset';
import { Button, ErrorNotice, Screen, Text, TextField } from '../../src/ui';
import { codeError } from '../../src/validation';

/** How long before "Send a new code" becomes available. The server rate-limits too. */
const RESEND_COOLDOWN_SECONDS = 45;

/**
 * Step two of three: the mailed code, exchanged for a ticket.
 *
 * Deliberately close to `verify.tsx`, which is the screen the user met at signup — same
 * single field, same one-time-code autofill, same cooldown. What it must *not* share is
 * the ending: verification signs you in, and this cannot, because the code proves control
 * of the mailbox and nothing more. It hands back a ticket that only step three can spend.
 */
export default function ForgotCode() {
  const t = useT();
  const router = useRouter();
  const email = usePasswordReset((s) => s.email);
  const hold = usePasswordReset((s) => s.hold);

  const [code, setCode] = useState('');
  const [touched, setTouched] = useState(false);
  const [cooldown, setCooldown] = useState(RESEND_COOLDOWN_SECONDS);

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setTimeout(() => setCooldown((s) => s - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);

  const verify = useMutation({
    mutationFn: () => authApi.resetVerify(email, Number(code.trim())),
    onSuccess: (res) => {
      hold(res.resetToken);
      router.push('/forgot-reset');
    },
  });

  const resend = useMutation({
    mutationFn: () => authApi.sendCode(email, false),
    onSuccess: () => {
      setCooldown(RESEND_COOLDOWN_SECONDS);
      // The old code was invalidated server-side, so leaving it in the field would
      // only invite the user to submit something that can no longer work.
      setCode('');
      setTouched(false);
    },
  });

  function submit() {
    setTouched(true);
    if (codeError(code)) return;
    verify.mutate();
  }

  const expired =
    verify.error instanceof ApiError &&
    verify.error.is(Code.VERIFICATION_CODE_EXPIRED, Code.TOO_MANY_ATTEMPTS);

  // Reached directly — by a deep link, or after the store was cleared — there is no
  // address to verify against, so the flow starts where it is supposed to.
  if (!email) return <Redirect href="/forgot" />;

  return (
    <Screen scroll>
      <View className="gap-2 pb-8 pt-12">
        <Text variant="overline">{t.auth.stepTwo}</Text>
        <Text variant="title">{t.auth.verify.title}</Text>
        <Text variant="body" className="text-muted">
          {t.auth.forgot.sentCodeBefore}{' '}
          <Text variant="bodyStrong" className="text-content">
            {email}
          </Text>
          .
        </Text>
      </View>

      <View className="gap-5">
        <TextField
          label={t.auth.verify.code}
          value={code}
          // Strip anything that is not a digit: pasting from a mail client often
          // brings a trailing space or a stray character with it.
          onChangeText={(text) => setCode(text.replace(/\D/g, '').slice(0, 6))}
          error={touched ? (codeError(code)?.(t) ?? null) : null}
          keyboardType="number-pad"
          textContentType="oneTimeCode"
          autoComplete="one-time-code"
          maxLength={6}
          placeholder="123456"
          // Spaced out because a six-digit code is read back character by character
          // against the email. A style, not a class: the class would land on the
          // wrapper, and letter spacing has to reach the TextInput itself.
          style={{ letterSpacing: 8 }}
          onSubmitEditing={submit}
          returnKeyType="go"
        />

        {verify.error && <ErrorNotice error={verify.error} />}
        {resend.error && <ErrorNotice error={resend.error} />}
      </View>

      <View className="mt-auto gap-2 pt-10">
        <Button label={t.auth.forgot.verifySubmit} loading={verify.isPending} onPress={submit} />
        <Button
          // A spent or expired code cannot be retried, so the resend button stops being
          // a secondary option and becomes the only way forward.
          label={
            cooldown > 0 && !expired
              ? t.auth.verify.resendCooldown(cooldown)
              : t.auth.verify.resend
          }
          variant={expired ? 'secondary' : 'ghost'}
          disabled={cooldown > 0 && !expired}
          loading={resend.isPending}
          onPress={() => resend.mutate()}
        />
      </View>
    </Screen>
  );
}
