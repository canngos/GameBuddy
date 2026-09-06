import { useLocalSearchParams, useRouter } from "expo-router";
import { Gamepad2, MessagesSquare, Users } from "lucide-react-native";
import { useEffect, useRef, useState } from "react";
import { View } from "react-native";
import { LanguageButton, LanguagePicker } from "../../src/i18n/LanguagePicker";
import { SocialButtons } from "../../src/session/SocialButtons";
import { SocialConsentSheet } from "../../src/session/SocialConsentSheet";
import { useSocialSignIn } from "../../src/session/social";
import { useT } from "../../src/i18n/useT";
import { Button, Icon, Screen, Text, useScreenScale } from "../../src/ui";

/**
 * Three claims, each tied to something the product actually does — the recommender,
 * lobbies, the match-before-chat rule. Generic marketing copy here would be writing a
 * cheque the app has to cash on the next screen.
 *
 * The middle one used to advertise age separation. That stopped being worth a third of
 * this screen when the app went 18-only: `AgeBand` still refuses to pair a minor with an
 * adult, but registration already refuses minors, so the guarantee is invisible to
 * everybody who can read it. The slot went to the thing people can actually do.
 */
const PITCH_ICONS = [Gamepad2, Users, MessagesSquare];

export default function Welcome() {
  const { pick } = useScreenScale();
  const router = useRouter();
  const t = useT();
  const [picking, setPicking] = useState(false);
  const social = useSocialSignIn();

  // A Discord sign-in that came back needing the terms. `app/social.tsx` is a spinner with
  // no state of its own, so it hands the still-unspent ticket back here and the consent
  // sheet opens where the flow started.
  const { socialTicket } = useLocalSearchParams<{ socialTicket?: string }>();
  const resumed = useRef<string | null>(null);
  const { resumeWithTicket } = social;
  useEffect(() => {
    if (!socialTicket || resumed.current === socialTicket) return;
    resumed.current = socialTicket;
    resumeWithTicket(socialTicket);
  }, [socialTicket, resumeWithTicket]);

  // Paired here rather than in the dictionary: an icon is not copy, and a translator
  // opening the Finnish file should not meet a Lucide import.
  const pitch = [
    {
      icon: PITCH_ICONS[0],
      title: t.welcome.matchedTitle,
      body: t.welcome.matchedBody,
    },
    {
      icon: PITCH_ICONS[1],
      title: t.welcome.lobbyTitle,
      body: t.welcome.lobbyBody,
    },
    {
      icon: PITCH_ICONS[2],
      title: t.welcome.chatTitle,
      body: t.welcome.chatBody,
    },
  ];

  return (
    <Screen scroll>
      {/* Top left, above everything. This is the first screen of the app and the only
          one somebody who does not read English can be certain to reach, so the way
          out of English has to be here and has to be visible without reading. */}
      <View className="flex-row pt-2">
        <LanguageButton onPress={() => setPicking(true)} />
      </View>

      <LanguagePicker visible={picking} onClose={() => setPicking(false)} />

      <View
        className={`flex-1 justify-center ${pick("gap-6", "gap-8", "gap-10")} ${pick("py-6", "py-9", "py-12")}`}
      >
        <View className="gap-3">
          {/* Two weights on one line: the brand reads as a mark rather than a heading.
              This keeps `brand` — the wordmark is the one use of the pink that is identity
              rather than a role, so it survives the demotion described in
              `src/theme/gradients.ts`. Everything else on this screen moved to `primary`. */}
          <Text variant="display" className="text-content">
            {t.welcome.titleTop}
          </Text>
          <View className="flex-row items-baseline gap-2">
            <Text variant="display" className="text-brand">
              GameBuddy
            </Text>
            <View className="h-3 w-3 rounded-full bg-brand" />
          </View>
          <Text variant="body" className="mt-2 text-muted">
            {t.welcome.tagline}
          </Text>
        </View>

        <View className="gap-3">
          {pitch.map((item) => (
            <View
              key={item.title}
              className="flex-row items-center gap-4 rounded-card bg-raised p-4"
            >
              <View className="h-11 w-11 items-center justify-center rounded-full bg-primary/10">
                <Icon as={item.icon} size={20} tone="primary" />
              </View>
              <View className="flex-1 gap-0.5">
                <Text variant="bodyStrong">{item.title}</Text>
                <Text variant="caption">{item.body}</Text>
              </View>
            </View>
          ))}
        </View>
      </View>

      <View className="gap-3">
        <Button
          label={t.welcome.createAccount}
          onPress={() => router.push("/register")}
        />
        <Button
          label={t.welcome.haveAccount}
          variant="secondary"
          onPress={() => router.push("/login")}
        />

        <SocialButtons social={social} />
      </View>

      <SocialConsentSheet
        visible={social.consentNeeded}
        busy={social.pending !== null}
        accepted={social.accepted}
        onAccepted={social.setAccepted}
        onContinue={() => void social.confirmConsent()}
        onDismiss={social.dismissConsent}
      />
    </Screen>
  );
}
