import { Platform } from 'react-native';
import { canRequestAds } from './consent';
import { adsSdk } from './sdk';

/**
 * Rewarded video.
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
 * ## Consent comes first
 *
 * The request below asks for a personalised advert, which in the EEA and the UK is only
 * allowed once the user has said so. That conversation happens in `consent.ts` at startup,
 * not here; this module only reads the verdict. If it is no, no advert is requested at all —
 * asking anyway would be the violation, and Google would refuse to fill it regardless.
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

let initialised = false;

/** Re-exported so the Market can keep importing one module. See `sdk.ts`. */
export { adsAvailable } from './sdk';

export type RewardedOutcome =
  /** Watched to the end. AdMob's callback is on its way to our backend. */
  | 'earned'
  /** Closed early, or no ad was available. Nothing was earned and nothing is wrong. */
  | 'dismissed'
  /** Consent for advertising was refused or never given. Recoverable, from Settings. */
  | 'consentRequired'
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
  const ads = adsSdk();
  if (!ads) return 'unavailable';
  if (!canRequestAds()) return 'consentRequired';

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
