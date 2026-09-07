import { useRouter } from "expo-router";
import { useEffect, useState } from "react";
import { View } from "react-native";
import { useT } from "../../src/i18n/useT";
import { ConsentText } from "../../src/session/ConsentText";
import { useSocialSignIn } from "../../src/session/social";
import { useSocialPending } from "../../src/session/socialPending";
import { useSession } from "../../src/session/store";
import { Button, Checkbox, Screen, Text, useIntroPadding } from "../../src/ui";

/**
 * The first step of the tunnel for an account made with Google or Discord.
 *
 * The registration form asks this on the form itself, so a social sign-up asks it in the
 * same place in the journey: as the screen before the username, laid out like the steps
 * that follow it. It used to be a sheet over the welcome screen, and on Android the welcome
 * screen's elevated buttons drew straight through it — but the sheet was the wrong shape
 * regardless. This is not an interruption to what somebody was doing; it is the start of
 * what they are about to do.
 *
 * It lives in `(auth)` because there is no account yet: the server refuses to create one
 * without the tick, and rightly. What arrives here is the held credential; what leaves is a
 * session, and `landingRoute` then takes it to the username step.
 */
export default function Consent() {
  const intro = useIntroPadding();
  const router = useRouter();
  const t = useT();
  const social = useSocialSignIn();
  const credential = useSocialPending((s) => s.credential);
  const release = useSocialPending((s) => s.release);
  const [accepted, setAccepted] = useState(false);

  // Nothing to consent to without a credential: a cold start onto this route, or the back
  // stack after the token was spent. Welcome is where that person actually is. Only while
  // signed out, so a session arriving underneath does not race the landing route.
  useEffect(() => {
    if (!credential && useSession.getState().status === "signedOut") {
      router.replace("/welcome");
    }
  }, [credential, router]);

  if (!credential) return null;

  return (
    <Screen scroll>
      <View className={`gap-2 pb-6 ${intro.heading}`}>
        <Text variant="title">{t.auth.social.consentTitle}</Text>
        <Text variant="body" className="text-muted">
          {t.auth.social.consentBody}
        </Text>
      </View>

      <Checkbox
        checked={accepted}
        onChange={setAccepted}
        accessibilityLabel={t.auth.register.consentA11y}
      >
        <ConsentText />
      </Checkbox>

      <View className={`mt-auto gap-2 ${intro.footer}`}>
        <Button
          label={t.auth.social.continue}
          // Disabled rather than refused on tap: there is exactly one thing to do here and
          // nothing to explain about why it did not work.
          disabled={!accepted}
          loading={social.pending !== null}
          onPress={() => void social.confirmConsent()}
        />
        <Button
          label={t.common.cancel}
          variant="ghost"
          onPress={() => {
            release();
            router.back();
          }}
        />
      </View>
    </Screen>
  );
}
