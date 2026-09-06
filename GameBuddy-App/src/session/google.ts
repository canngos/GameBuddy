import {
  GoogleOneTapSignIn,
  isErrorWithCode,
  isNoSavedCredentialFoundResponse,
  isSuccessResponse,
  statusCodes,
} from 'react-native-nitro-google-signin';

/**
 * The only file in this app that imports a Google Sign-In SDK.
 *
 * Everything above it — `useSocialSignIn`, the welcome screen, the backend call — deals in
 * "an ID token or nothing". That is deliberate: the SDK landscape here moved twice in two
 * years (Google deprecated the legacy Sign-In library in favour of Credential Manager, and
 * the long-established React Native wrapper put Credential Manager behind a paid tier), and
 * the next move should be one file, not a search across the app.
 *
 * `react-native-nitro-google-signin` is the free MIT package that uses Credential Manager on
 * Android, which is what Google now requires. Its `configure` is synchronous and must run
 * before anything else, so the root layout calls {@link configureGoogle} once.
 */

/**
 * The web OAuth client id, not the Android one.
 *
 * Google mints the token on the device against the Android client and addresses it to the
 * web client, so that a backend can tell tokens meant for it from tokens meant for anybody
 * else. The backend checks exactly this value as the token's audience.
 *
 * Public information — it ships inside the app — so it is a plain public env var. Unset in a
 * clone with no Google project, which is why {@link googleAvailable} exists.
 */
const WEB_CLIENT_ID = process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID;

/** Whether this build was given a client id at all. The welcome screen hides the button otherwise. */
export const googleAvailable = !!WEB_CLIENT_ID;

/**
 * Thrown when the device cannot do Google sign-in at all.
 *
 * Separate from a cancel, because they mean opposite things to the person holding the phone:
 * one is a decision, the other is a dead end that needs explaining. Named after
 * `StoreUnavailableError`, which does the same job for billing.
 */
export class GoogleUnavailableError extends Error {
  constructor(readonly reason: 'no-play-services' | 'not-configured' | 'misconfigured') {
    super(`Google sign-in unavailable: ${reason}`);
    this.name = 'GoogleUnavailableError';
  }
}

let configured = false;

/**
 * Prepares the SDK. Safe to call more than once; does nothing without a client id.
 *
 * Called from the root layout rather than lazily at the button, because `configure` is
 * synchronous native work and the first tap should not pay for it.
 */
export function configureGoogle(): void {
  if (configured || !WEB_CLIENT_ID) return;
  GoogleOneTapSignIn.configure({ webClientId: WEB_CLIENT_ID });
  configured = true;
}

/**
 * Opens the account sheet and returns a Google ID token.
 *
 * @returns the token, or null when the person dismissed the sheet. A cancel is not an
 *   error: nothing has gone wrong and there is nothing to tell them.
 * @throws {GoogleUnavailableError} when the device or the build cannot do this at all
 */
export async function signInWithGoogle(): Promise<string | null> {
  if (!WEB_CLIENT_ID) throw new GoogleUnavailableError('not-configured');
  configureGoogle();

  try {
    // Android only; a no-op on iOS. `true` lets Google offer its own update dialog, which
    // is a fixable outcome rather than a dead end.
    await GoogleOneTapSignIn.checkPlayServices(true);

    // The explicit "Sign in with Google" sheet rather than the silent path. The silent one
    // returns `noSavedCredentialFound` for anybody who has never used Google here, which is
    // most people the first time — and a button that appears to do nothing is worse than a
    // sheet that takes one extra tap.
    let response = await GoogleOneTapSignIn.presentExplicitSignIn();

    // **Older Play Services cannot serve that request at all.** `GetSignInWithGoogleOption`
    // is newer than `GetGoogleIdOption`, and a device whose Play Services predates it
    // answers "no matching credentials" no matter which accounts are signed in — which is
    // indistinguishable from having none. Verified on the Android 14 emulator image, whose
    // Play Services is 23.18: the explicit sheet returns nothing, and the request below
    // shows the account picker for the very same account.
    //
    // So this is a fallback rather than the first choice. Modern devices get the
    // presentation Google's guidelines ask for, and older ones still get in.
    //
    // Only on `noSavedCredentialFound`, never on a cancel: somebody who dismissed the sheet
    // has answered, and reopening a second picker over that would be the app arguing.
    if (isNoSavedCredentialFoundResponse(response)) {
      response = await GoogleOneTapSignIn.createAccount();
    }

    if (!isSuccessResponse(response)) return null;
    return response.data.idToken ?? null;
  } catch (error) {
    if (isErrorWithCode(error)) {
      switch (error.code) {
        case statusCodes.SIGN_IN_CANCELLED:
        case statusCodes.IN_PROGRESS:
          // A second tap while the first sheet is still up is not a failure worth a message.
          return null;
        case statusCodes.PLAY_SERVICES_NOT_AVAILABLE:
          throw new GoogleUnavailableError('no-play-services');
        case statusCodes.DEVELOPER_ERROR:
          // The signing certificate has no OAuth client registered for it. Every build made
          // from a new keystore hits this until the SHA-1 is added in the Cloud console, and
          // it is invisible in every other way — so it gets its own reason.
          if (__DEV__) console.warn('[google] DEVELOPER_ERROR: is this build\'s SHA-1 registered?');
          throw new GoogleUnavailableError('misconfigured');
        default:
          break;
      }
    }
    throw error;
  }
}
