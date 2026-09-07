import { useQuery } from '@tanstack/react-query';
import { View } from 'react-native';
import { authApi } from '../api/auth';
import { useT } from '../i18n/useT';
import { DiscordButton } from '../profile/DiscordButton';
import { GoogleButton } from '../profile/GoogleButton';
import { Text } from '../ui';
import { googleAvailable } from './google';
import type { useSocialSignIn } from './social';

/**
 * The "or continue with" block, shared by the welcome and sign-in screens.
 *
 * One component rather than two copies, because the rules about *which* buttons appear are
 * fiddly enough to drift: the Google button needs a client id compiled into this build, and
 * both need the deployment to have credentials. A build offering a button the server will
 * refuse reads as a broken app rather than an unconfigured one.
 *
 * **Fails closed on the server's answer and open on the build's.** The provider list is
 * deployment configuration, cached hard — but while it is loading, the buttons this build
 * *can* do are shown anyway. A button that briefly works is better than one that appears a
 * second late under somebody's thumb.
 */
export function SocialButtons({ social }: { social: ReturnType<typeof useSocialSignIn> }) {
  const t = useT();

  const providers = useQuery({
    queryKey: ['socialProviders'],
    queryFn: authApi.socialProviders,
    staleTime: Infinity,
  });

  const offered = providers.data?.providers;
  // Undefined while loading or after a failure: show what the build supports.
  // A call, not a constant: it also answers whether the *binary* has the SDK, which an
  // over-the-air update cannot change. See `googleAvailable` in `src/session/google.ts`.
  const showGoogle = googleAvailable() && (offered === undefined || offered.includes('GOOGLE'));
  const showDiscord = offered === undefined || offered.includes('DISCORD');

  if (!showGoogle && !showDiscord) return null;

  return (
    <View className="gap-3 pt-2">
      <View className="flex-row items-center gap-3">
        {/* `h-px bg-line` rather than a border: `useHairline` returns a whole style object
            for the dark theme's border, which is the wrong primitive for a rule. */}
        <View className="h-px flex-1 bg-line" />
        <Text variant="caption">{t.auth.social.or}</Text>
        <View className="h-px flex-1 bg-line" />
      </View>

      {showGoogle && (
        <GoogleButton
          label={t.auth.social.google}
          disabled={social.pending !== null}
          onPress={() => void social.google()}
        />
      )}

      {showDiscord && (
        <DiscordButton
          label={t.auth.social.discord}
          disabled={social.pending !== null}
          onPress={() => void social.discord()}
        />
      )}
    </View>
  );
}
