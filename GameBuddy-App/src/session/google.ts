import { TurboModuleRegistry } from 'react-native';
import type * as GoogleSignInSdk from 'react-native-nitro-google-signin';

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
 *
 * **The package is loaded lazily and never at module scope** — see {@link sdk} for why that
 * is a correctness requirement here rather than a micro-optimisation.
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

type GoogleSdk = typeof GoogleSignInSdk;

let sdkCache: GoogleSdk | null | undefined;

/**
 * The SDK, or null on a build whose binary does not contain it.
 *
 * **This has to be a lazy require, and the reason is an over-the-air update.** The JS in an
 * update is served to every install whose `runtimeVersion` matches, and the policy here is
 * `appVersion` — so every build carrying version 1.0.0 gets the same bundle whether or not
 * its binary has these native modules. A build made before Google sign-in shipped is one of
 * them.
 *
 * Nitro does not degrade quietly the way RevenueCat does. `react-native-nitro-modules` calls
 * `TurboModuleRegistry.getEnforcing('NitroModules')` *while it is being evaluated*, so a
 * static `import` of the Google package throws during module evaluation — before any
 * try/catch of ours, and before the root layout's `ErrorBoundary` exists to catch it. Since
 * {@link configureGoogle} runs at module scope in `app/_layout.tsx`, that is a crash on
 * launch, shipped over the air, to people whose installed app was working perfectly.
 *
 * So: check the native side first with the *non-throwing* registry lookup, then require
 * behind a try/catch. Same shape as `sdk()` in `src/billing/purchases.ts`, for the same
 * reason — a disabled button with an explanation beats an obscure crash.
 */
function sdk(): GoogleSdk | null {
  if (sdkCache !== undefined) return sdkCache;

  // `get` returns null where `getEnforcing` throws. That difference is the whole guard.
  if (TurboModuleRegistry.get('NitroModules') == null) {
    if (__DEV__) {
      console.warn('[google] NitroModules is missing — this build cannot sign in with Google');
    }
    sdkCache = null;
    return sdkCache;
  }

  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    sdkCache = require('react-native-nitro-google-signin') as GoogleSdk;
  } catch (error) {
    if (__DEV__) console.warn('[google] react-native-nitro-google-signin failed to load', error);
    sdkCache = null;
  }
  return sdkCache;
}

/**
 * Whether this build can sign in with Google at all. The welcome screen hides the button
 * otherwise — on a clone with no client id, and on a build made before this shipped.
 */
export function googleAvailable(): boolean {
  return !!WEB_CLIENT_ID && sdk() !== null;
}

/**
 * Thrown when the device cannot do Google sign-in at all.
 *
 * Separate from a cancel, because they mean opposite things to the person holding the phone:
 * one is a decision, the other is a dead end that needs explaining. Named after
 * `StoreUnavailableError`, which does the same job for billing.
 */
export class GoogleUnavailableError extends Error {
  constructor(
    readonly reason:
      | 'no-play-services'
      | 'not-configured'
      | 'misconfigured'
      // The binary predates Google sign-in. Reachable only if something calls this without
      // checking {@link googleAvailable} first, since the button is hidden in that case.
      | 'not-in-this-build',
  ) {
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
  // Returns rather than throws on a build without the SDK. This runs at module scope in the
  // root layout, where a throw is a white screen on launch and nothing else.
  const google = sdk();
  if (!google) return;
  google.GoogleOneTapSignIn.configure({ webClientId: WEB_CLIENT_ID });
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
  const google = sdk();
  if (!google) throw new GoogleUnavailableError('not-in-this-build');
  configureGoogle();

  try {
    // Android only; a no-op on iOS. `true` lets Google offer its own update dialog, which
    // is a fixable outcome rather than a dead end.
    await google.GoogleOneTapSignIn.checkPlayServices(true);

    // The explicit "Sign in with Google" sheet rather than the silent path. The silent one
    // returns `noSavedCredentialFound` for anybody who has never used Google here, which is
    // most people the first time — and a button that appears to do nothing is worse than a
    // sheet that takes one extra tap.
    let response = await google.GoogleOneTapSignIn.presentExplicitSignIn();

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
    if (google.isNoSavedCredentialFoundResponse(response)) {
      response = await google.GoogleOneTapSignIn.createAccount();
    }

    if (!google.isSuccessResponse(response)) return null;
    return response.data.idToken ?? null;
  } catch (error) {
    if (google.isErrorWithCode(error)) {
      switch (error.code) {
        case google.statusCodes.SIGN_IN_CANCELLED:
        case google.statusCodes.IN_PROGRESS:
          // A second tap while the first sheet is still up is not a failure worth a message.
          return null;
        case google.statusCodes.PLAY_SERVICES_NOT_AVAILABLE:
          throw new GoogleUnavailableError('no-play-services');
        case google.statusCodes.DEVELOPER_ERROR:
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
