import { useRouter } from 'expo-router';
import { TriangleAlert } from 'lucide-react-native';
import { useCallback, useRef, useState } from 'react';
import { Linking } from 'react-native';
import { authApi } from '../api/auth';
import { ApiError, Code } from '../api/envelope';
import type { SocialSession } from '../api/types';
import { useT } from '../i18n/useT';
import { showToast } from '../ui';
import { GoogleUnavailableError, signInWithGoogle } from './google';
import { landingRoute } from './routes';
import { useSession } from './store';

/**
 * Signing in with Google or Discord, from the two screens that offer it.
 *
 * The two providers look different on the way out and identical on the way back:
 *
 * - **Google** never leaves the app. The account sheet returns an ID token, the token goes
 *   to the backend, a session comes back.
 * - **Discord** leaves for the system browser and returns through `gamebuddy://social`,
 *   which `app/social.tsx` picks up and exchanges. Nothing here waits for it — the deep link
 *   is a fresh entry into the app, not a resolved promise.
 *
 * **The consent detour.** A brand-new account needs the terms accepted, and the app cannot
 * know in advance whether this person is new: that is exactly what the server is being asked.
 * So the first call goes without the flag, and a `TERMS_NOT_ACCEPTED` answer is not an error
 * but a question — the sheet opens and the *same credential* is sent again with the tick.
 * Google ID tokens are good for about an hour, and the Discord ticket is deliberately left
 * unspent on that path, so the retry is free either way.
 */

/** What the consent sheet will retry with, kept together with which call to retry. */
type HeldCredential = { kind: 'google'; idToken: string } | { kind: 'discord'; ticket: string };

export function useSocialSignIn() {
  const router = useRouter();
  const t = useT();
  const signIn = useSession((s) => s.signIn);

  const [pending, setPending] = useState<'google' | 'discord' | null>(null);
  const [consentNeeded, setConsentNeeded] = useState(false);
  const [accepted, setAccepted] = useState(false);

  /**
   * The credential the consent sheet will retry with.
   *
   * A ref rather than state: nothing renders from it, and putting a Google ID token through
   * a render cycle is one more place it can be read from.
   */
  const held = useRef<HeldCredential | null>(null);

  const land = useCallback(() => {
    // Where a new account goes is not "/home": it has no username yet, and the guard would
    // bounce it straight back out. `landingRoute` is the same table `app/index.tsx` uses.
    const status = useSession.getState().status;
    if (status !== 'loading') router.replace(landingRoute[status] as never);
  }, [router]);

  const adopt = useCallback(
    async (session: SocialSession) => {
      await signIn(session.accessToken, session.userId);
      setConsentNeeded(false);
      setAccepted(false);
      held.current = null;
      land();
    },
    [land, signIn],
  );

  /**
   * Runs one sign-in attempt and turns the two expected refusals into outcomes.
   *
   * @returns true when it finished, false when it needs the consent sheet
   */
  const attempt = useCallback(
    async (credential: HeldCredential, acceptedTerms?: boolean): Promise<boolean> => {
      try {
        const session =
          credential.kind === 'google'
            ? await authApi.socialGoogle(credential.idToken, acceptedTerms)
            : await authApi.socialExchange(credential.ticket, acceptedTerms);
        await adopt(session);
        return true;
      } catch (error) {
        if (error instanceof ApiError && error.is(Code.TERMS_NOT_ACCEPTED)) {
          // Not a failure: the server is asking for the tick.
          held.current = credential;
          setConsentNeeded(true);
          return false;
        }
        throw error;
      }
    },
    [adopt],
  );

  /**
   * One place to say a sign-in did not work.
   *
   * Every path below ends either signed in or here. That is the point: both providers used to
   * have exits that returned without a word, and a button that does nothing is indistinguishable
   * from a button that is broken - which is exactly how an unregistered signing certificate went
   * unnoticed. `app/social.tsx` has always ended the Discord round trip this way; this is the
   * same courtesy for every other exit.
   */
  const complain = useCallback(
    (title: string) => showToast({ id: 'social:failed', title, icon: TriangleAlert, tone: 'danger' }),
    [],
  );

  const google = useCallback(async () => {
    if (pending) return;
    setPending('google');
    try {
      const outcome = await signInWithGoogle();
      // A second tap on a sheet that is already open. Nothing happened, nothing to report.
      if (outcome.status === 'superseded') return;
      if (outcome.status === 'unfinished') {
        complain(t.auth.social.failed);
        return;
      }
      await attempt({ kind: 'google', idToken: outcome.idToken });
    } catch (error) {
      if (error instanceof GoogleUnavailableError) {
        complain(t.auth.social.googleUnavailable);
        return;
      }
      if (error instanceof ApiError && error.is(Code.SOCIAL_EMAIL_UNVERIFIED)) {
        complain(t.auth.social.emailUnverified);
        return;
      }
      // Everything else: a dead network, a 500, a bug. Rethrowing reached nobody - this is an
      // async callback, so the throw became an unhandled rejection and the screen just sat there.
      complain(t.auth.social.failed);
    } finally {
      setPending(null);
    }
  }, [attempt, complain, pending, t]);

  const discord = useCallback(async () => {
    if (pending) return;
    setPending('discord');
    try {
      const { authorizeUrl } = await authApi.socialDiscordStart();
      // The system browser, not a WebView: a password typed into a browser this app controls
      // is a password it could have read, and the address bar is the only thing that tells
      // somebody they are really on discord.com.
      await Linking.openURL(authorizeUrl);
    } catch {
      complain(t.auth.social.failed);
    } finally {
      // Cleared immediately. The rest of this flow happens in another app and comes back as
      // a deep link, so a spinner left running here would never stop.
      setPending(null);
    }
  }, [complain, pending, t]);

  /**
   * Picks up a Discord sign-in that came back needing the terms.
   *
   * `app/social.tsx` cannot show the sheet itself — it is a spinner with no state — so it
   * hands the still-unspent ticket back to whichever screen started the flow.
   */
  const resumeWithTicket = useCallback((ticket: string) => {
    held.current = { kind: 'discord', ticket };
    setConsentNeeded(true);
  }, []);

  /** The consent sheet's Continue: the same credential, now with the tick. */
  const confirmConsent = useCallback(async () => {
    const credential = held.current;
    if (!credential || !accepted) return;
    setPending(credential.kind);
    try {
      await attempt(credential, true);
    } finally {
      setPending(null);
    }
  }, [accepted, attempt]);

  const dismissConsent = useCallback(() => {
    setConsentNeeded(false);
    setAccepted(false);
    held.current = null;
  }, []);

  return {
    google,
    discord,
    resumeWithTicket,
    pending,
    consentNeeded,
    accepted,
    setAccepted,
    confirmConsent,
    dismissConsent,
  };
}
