import { useMutation } from "@tanstack/react-query";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useEffect, useState } from "react";
import { View } from "react-native";
import { authApi } from "../../src/api/auth";
import { ApiError, Code } from "../../src/api/envelope";
import { useSession } from "../../src/session/store";
import { useT } from "../../src/i18n/useT";
import {
  Button,
  ErrorNotice,
  Screen,
  Text,
  TextField,
  useIntroPadding,
} from "../../src/ui";
import { codeError } from "../../src/validation";

/** How long before "Send a new code" becomes available. The server rate-limits too. */
const RESEND_COOLDOWN_SECONDS = 45;

export default function Verify() {
  const intro = useIntroPadding();
  const t = useT();
  const router = useRouter();
  const { email } = useLocalSearchParams<{ email: string }>();
  const adoptToken = useSession((s) => s.adoptToken);

  // The type above is a promise the router cannot keep: a direct deep link opens this
  // screen with no params at all, and verifying `undefined` posts a request that can
  // only be refused. Back to the start instead.
  useEffect(() => {
    if (!email) router.replace("/welcome" as never);
  }, [email, router]);

  const [code, setCode] = useState("");
  const [touched, setTouched] = useState(false);
  const [cooldown, setCooldown] = useState(RESEND_COOLDOWN_SECONDS);

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setTimeout(() => setCooldown((s) => s - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);

  const verify = useMutation({
    mutationFn: async () => {
      const session = await authApi.verify(email, Number(code.trim()));
      // Verification issues the token, and is also the only way back in for an account
      // that stalled halfway through onboarding — login refuses those with code 109.
      // So the destination is whatever the server says is missing, not a fixed start.
      await adoptToken(session.accessToken, session.userId);
    },
    // The guards redirect from here to the right screen for the resolved status.
    onSuccess: () => router.replace("/"),
  });

  const resend = useMutation({
    mutationFn: () => authApi.sendCode(email),
    onSuccess: () => {
      setCooldown(RESEND_COOLDOWN_SECONDS);
      // The old code was invalidated server-side, so leaving it in the field would
      // only invite the user to submit something that can no longer work.
      setCode("");
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

  return (
    <Screen scroll>
      <View className={`gap-2 pb-8 ${intro.top}`}>
        <Text variant="overline">{t.auth.stepTwo}</Text>
        <Text variant="title">{t.auth.verify.title}</Text>
        <Text variant="body" className="text-muted">
          {t.auth.verify.sentCodeBefore}{" "}
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
          onChangeText={(t) => setCode(t.replace(/\D/g, "").slice(0, 6))}
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

      <View className={`mt-auto gap-2 ${intro.footer}`}>
        <Button
          label={t.auth.verify.submit}
          loading={verify.isPending}
          onPress={submit}
        />
        <Button
          // A spent or expired code cannot be retried, so the resend button stops being
          // a secondary option and becomes the only way forward.
          label={
            cooldown > 0 && !expired
              ? t.auth.verify.resendCooldown(cooldown)
              : t.auth.verify.resend
          }
          variant={expired ? "secondary" : "ghost"}
          disabled={cooldown > 0 && !expired}
          loading={resend.isPending}
          onPress={() => resend.mutate()}
        />
      </View>
    </Screen>
  );
}
