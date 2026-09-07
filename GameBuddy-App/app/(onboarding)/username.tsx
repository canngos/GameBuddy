import { useMutation } from "@tanstack/react-query";
import { useRouter } from "expo-router";
import { useState } from "react";
import { View } from "react-native";
import { authApi } from "../../src/api/auth";
import { ApiError, Code } from "../../src/api/envelope";
import { useT } from "../../src/i18n/useT";
import { StepHeader } from "../../src/onboarding/StepHeader";
import { useSession } from "../../src/session/store";
import {
  Button,
  ErrorNotice,
  Screen,
  TextField,
  useIntroPadding,
} from "../../src/ui";
import { usernameError } from "../../src/validation";

export default function Username() {
  const intro = useIntroPadding();
  const router = useRouter();
  const t = useT();
  const usernameChosen = useSession((s) => s.usernameChosen);

  const [username, setUsername] = useState("");
  const [touched, setTouched] = useState(false);

  const submit = useMutation({
    mutationFn: () => authApi.setUsername(username.trim()),
    onSuccess: () => {
      usernameChosen();
      router.replace("/profile");
    },
  });

  const taken =
    submit.error instanceof ApiError && submit.error.is(Code.USERNAME_EXISTS);
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
        title={t.onboarding.username.title}
        subtitle={t.onboarding.username.subtitle}
      />

      <View className="gap-5">
        <TextField
          label={t.onboarding.username.label}
          value={username}
          onChangeText={setUsername}
          error={
            (problem ? problem(t) : null) ??
            (taken ? t.onboarding.username.taken : null)
          }
          hint={t.onboarding.username.hint}
          placeholder={t.onboarding.username.placeholder}
          maxLength={20}
          onSubmitEditing={send}
          returnKeyType="go"
        />

        {submit.error && !taken && <ErrorNotice error={submit.error} />}
      </View>

      <View className={`mt-auto ${intro.footer}`}>
        <Button
          label={t.common.continue}
          loading={submit.isPending}
          onPress={send}
        />
      </View>
    </Screen>
  );
}
