import { NativeModules, Platform } from 'react-native';

/**
 * Rewarded video, and the only file in the app that imports the AdMob SDK.
 *
 * ## The coins are not granted here
 *
 * This module shows an advert and reports back whether it was watched. It never tells the
 * backend to pay anything, and there is deliberately no endpoint it could tell. AdMob's own
 * servers call `GET /ads/reward` with an ECDSA signature over the query string, and that
 * callback is the only thing that moves a coin — see `RewardedAdController`.
 *
 * The distinction is the whole design. An app that could say "I watched an advert, pay me"
 * is an app anyone can impersonate with one HTTP request, and the coins would be free for
 * the price of reading our traffic. So the client's honest report below is used only to
 * decide what to show on screen; the balance arrives from the server a moment later.
 *
 * ## Test ids, always, in development
 *
 * The ids below are Google's published test units, and using them while developing is not
 * a convenience — it is required. Ads served to our own devices from our own live unit are
 * invalid traffic, and clicking them is the most common way a publisher's AdMob account is
 * banned before it has earned anything. The real ids belong to release builds only, which
 * is why the swap is keyed to `__DEV__` rather than left to whoever is testing to remember.
 *
 * ## Why the SDK is loaded lazily
 *
 * Native code, exactly like `react-native-purchases`: present in the JS bundle as soon as
 * it is imported, but the native half only exists in a development build made after it was
 * installed. A top-level import would crash every older build at startup. Loading on first
 * use means an older build keeps working everywhere except the watch button, which reports
 * that it is unavailable instead of showing a white screen.
 */

/** Google's published test rewarded unit. Serves a real ad that pays nobody. */
const TEST_REWARDED_UNIT_ID = Platform.select({
  android: 'ca-app-pub-3940256099942544/5224354917',
  ios: 'ca-app-pub-3940256099942544/1712485313',
  default: 'ca-app-pub-3940256099942544/5224354917',
});

/**
 * The live rewarded unit, from the AdMob console.
 *
 * Empty until the account is approved and the unit is created. While it is empty the test
 * unit is used even in a release build — a missing id would otherwise fail to load an ad
 * and leave the button spinning, which is worse than showing a test ad to nobody.
 */
const LIVE_REWARDED_UNIT_ID = process.env.EXPO_PUBLIC_ADMOB_REWARDED_UNIT_ID ?? '';

export function rewardedUnitId(): string {
  return __DEV__ || !LIVE_REWARDED_UNIT_ID ? TEST_REWARDED_UNIT_ID : LIVE_REWARDED_UNIT_ID;
}

/**
 * What one advert pays, for the label on the button.
 *
 * A copy of `CoinFaucet.REWARDED_AD_COINS`, and only ever used to render a number. The
 * server pays whatever its own constant says, so if these two drift the button is briefly
 * wrong and nobody is paid the wrong amount.
 */
export const REWARDED_AD_COINS = 20;

type AdsModule = typeof import('react-native-google-mobile-ads');

/** `undefined` = not tried yet, `null` = tried and the native module is not in this build. */
let sdkCache: AdsModule | null | undefined;
let initialised = false;

function sdk(): AdsModule | null {
  if (sdkCache !== undefined) return sdkCache;

  // The native side is checked directly, for the same reason as in `purchases.ts`:
  // requiring the package is not a test of anything, because it reads a NativeModules entry
  // that is simply `undefined` in a build without the native half. A try/catch around the
  // require would always succeed and the failure would surface later as an obscure crash
  // rather than a disabled button with a reason beside it.
  if (!NativeModules.RNGoogleMobileAdsModule) {
    if (__DEV__) {
      console.warn('[ads] RNGoogleMobileAds native module is missing — this build cannot show ads');
    }
    sdkCache = null;
    return null;
  }

  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    sdkCache = require('react-native-google-mobile-ads') as AdsModule;
  } catch {
    sdkCache = null;
  }
  return sdkCache;
}

/** Whether this build can show an advert at all. Drives whether the card is offered. */
export function adsAvailable(): boolean {
  return sdk() !== null;
}

export type RewardedOutcome =
  /** Watched to the end. AdMob's callback is on its way to our backend. */
  | 'earned'
  /** Closed early, or no ad was available. Nothing was earned and nothing is wrong. */
  | 'dismissed'
  /** This build has no AdMob in it, or the SDK failed to start. */
  | 'unavailable';

/**
 * Shows one rewarded advert.
 *
 * @param userId the signed-in gamer, handed to AdMob as the server-side verification
 *     `user_id` so their callback names the account to pay. Without it the callback arrives
 *     with nobody attached and the reward is dropped.
 */
export async function showRewardedAd(userId: string): Promise<RewardedOutcome> {
  const ads = sdk();
  if (!ads) return 'unavailable';

  try {
    if (!initialised) {
      await ads.default().initialize();
      initialised = true;
    }

    const ad = ads.RewardedAd.createForAdRequest(rewardedUnitId(), {
      // Ours is an 18+ app, so this is honest rather than restrictive — but it also keeps
      // us out of the child-directed rules that would otherwise apply to the request.
      requestNonPersonalizedAdsOnly: false,
      serverSideVerificationOptions: { userId },
    });

    return await new Promise<RewardedOutcome>((resolve) => {
      let earned = false;

      // Every listener is unsubscribed on the way out. A rewarded ad is created fresh each
      // time, so leaving them attached leaks one set per advert watched.
      const off: (() => void)[] = [];
      const finish = (outcome: RewardedOutcome) => {
        off.forEach((unsubscribe) => unsubscribe());
        resolve(outcome);
      };

      off.push(
        ad.addAdEventListener(ads.RewardedAdEventType.LOADED, () => ad.show()),
        ad.addAdEventListener(ads.RewardedAdEventType.EARNED_REWARD, () => {
          // Only a note to ourselves about what to show next. The coins come from the
          // server-side callback, which has already been sent by the time this fires.
          earned = true;
        }),
        ad.addAdEventListener(ads.AdEventType.CLOSED, () => finish(earned ? 'earned' : 'dismissed')),
        ad.addAdEventListener(ads.AdEventType.ERROR, () => finish('dismissed')),
      );

      ad.load();
    });
  } catch (error) {
    if (__DEV__) console.warn('[ads] rewarded ad failed', error);
    return 'unavailable';
  }
}
