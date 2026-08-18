import { NativeModules } from 'react-native';

/**
 * The AdMob SDK, loaded lazily, and the only place in the app that requires it.
 *
 * Native code, exactly like `react-native-purchases`: present in the JS bundle as soon as
 * it is imported, but the native half only exists in a development build made after it was
 * installed. A top-level import would crash every older build at startup. Loading on first
 * use means an older build keeps working everywhere except the advert button, which reports
 * that it is unavailable instead of showing a white screen.
 *
 * This lives on its own rather than inside `rewarded.ts` because consent needs the same
 * guard, and two copies of a check this subtle is two chances to get it wrong. Both
 * `rewarded.ts` and `consent.ts` go through here.
 */

export type AdsModule = typeof import('react-native-google-mobile-ads');

/** `undefined` = not tried yet, `null` = tried and the native module is not in this build. */
let cache: AdsModule | null | undefined;

export function adsSdk(): AdsModule | null {
  if (cache !== undefined) return cache;

  // The native side is checked directly, for the same reason as in `purchases.ts`:
  // requiring the package is not a test of anything, because it reads a NativeModules entry
  // that is simply `undefined` in a build without the native half. A try/catch around the
  // require would always succeed and the failure would surface later as an obscure crash
  // rather than a disabled button with a reason beside it.
  if (!NativeModules.RNGoogleMobileAdsModule) {
    if (__DEV__) {
      console.warn('[ads] RNGoogleMobileAds native module is missing — this build cannot show ads');
    }
    cache = null;
    return null;
  }

  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    cache = require('react-native-google-mobile-ads') as AdsModule;
  } catch {
    cache = null;
  }
  return cache;
}

/** Whether this build can show an advert at all. Drives whether the card is offered. */
export function adsAvailable(): boolean {
  return adsSdk() !== null;
}
