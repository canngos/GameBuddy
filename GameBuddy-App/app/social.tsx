import { useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useRef } from 'react';
import { ActivityIndicator, View } from 'react-native';
import { authApi } from '../src/api/auth';
import { ApiError, Code } from '../src/api/envelope';
import { useT } from '../src/i18n/useT';
import { landingRoute } from '../src/session/routes';
import { useSocialPending } from '../src/session/socialPending';
import { useSession } from '../src/session/store';
import { Screen, Text, showToast } from '../src/ui';
import { TriangleAlert } from 'lucide-react-native';

/**
 * Where a Discord sign-in comes back to: `gamebuddy://social`.
 *
 * **At the app root, not inside `(auth)`.** Two reasons, and the second is the load-bearing
 * one. Route groups do not appear in the URL, so `app/(auth)/social.tsx` would also be
 * `/social` — but the `(auth)` layout guards on `signedOut`, and this same callback has to
 * work for a signed-in gamer re-authenticating before deleting their account. A guarded
 * screen would redirect that person away mid-flow.
 *
 * The backend redirects here with a status and, on success, a **second** ticket — the one
 * that travelled through Discord and the browser is already spent. See `SocialLoginTicket`.
 *
 * Renders a spinner and nothing else. Everything this screen does is decide where to go.
 */
export default function SocialCallback() {
  const router = useRouter();
  const t = useT();
  const { status, ticket } = useLocalSearchParams<{ status?: string; ticket?: string }>();
  const signIn = useSession((s) => s.signIn);

  /**
   * Guards against running twice.
   *
   * The params stay put for as long as the screen is mounted and a re-render must not spend
   * the ticket again — the second attempt would fail, and the failure would be reported over
   * a sign-in that had already worked.
   */
  const handled = useRef(false);

  useEffect(() => {
    if (handled.current) return;
    handled.current = true;

    const leave = (to: string) => router.replace(to as never);
    const complain = (title: string) => {
      showToast({ id: 'social:failed', title, icon: TriangleAlert, tone: 'danger' });
      leave('/welcome');
    };

    // A deliberate change of mind. Back to where they started, quietly: a red banner for a
    // decision somebody made on purpose is the app arguing with them.
    if (status === 'cancelled') {
      leave('/welcome');
      return;
    }
    if (status === 'email_unverified') {
      complain(t.auth.social.emailUnverified);
      return;
    }
    if (status !== 'ok' || !ticket) {
      complain(t.auth.social.failed);
      return;
    }

    void (async () => {
      try {
        const session = await authApi.socialExchange(ticket);
        await signIn(session.accessToken, session.userId);
        const resolved = useSession.getState().status;
        // A brand-new account has no username yet, so "/home" would bounce straight back
        // out of the guard. Same table `app/index.tsx` uses.
        leave(resolved === 'loading' ? '/welcome' : landingRoute[resolved]);
      } catch (error) {
        if (error instanceof ApiError && error.is(Code.TERMS_NOT_ACCEPTED)) {
          // Not a failure: a new account needs the tick, and that is the consent step's job.
          // The ticket survives a terms refusal by design, so the retry there still works, and
          // it travels in the store rather than the URL, like everything else the step holds.
          useSocialPending.getState().hold({ kind: 'discord', ticket });
          leave('/consent');
          return;
        }
        if (error instanceof ApiError && error.is(Code.SOCIAL_EMAIL_UNVERIFIED)) {
          complain(t.auth.social.emailUnverified);
          return;
        }
        complain(t.auth.social.failed);
      }
    })();
  }, [router, signIn, status, t, ticket]);

  return (
    <Screen>
      <View className="flex-1 items-center justify-center gap-4">
        <ActivityIndicator />
        <Text variant="caption">{t.auth.social.finishing}</Text>
      </View>
    </Screen>
  );
}
