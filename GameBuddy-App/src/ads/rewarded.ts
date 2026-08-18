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

const RAW_REWARDED_UNIT_ID = process.env.EXPO_PUBLIC_ADMOB_REWARDED_UNIT_ID ?? '';

/**
 * What a build writes instead of a unit id when it is meant not to show real adverts.
 *
 * The same word, for the same reason, as `KEY_DISABLED` in `src/billing/purchases.ts` — see
 * the comment there. The `gate` and `lan` profiles inherit their env from `production` and
 * `extends` deep-merges rather than replaces, so the only way to drop the real unit is to
 * overwrite it; empty string is the obvious way to say "none" and EAS rejects it outright:
 * `"build.lan.env.EXPO_PUBLIC_ADMOB_REWARDED_UNIT_ID" is not allowed to be empty`. That
 * fails validation of the whole file, so one empty value blocks every build — including
 * `production`, which is how it announced itself.
 */
const UNIT_DISABLED = 'none';

/** Every real unit id starts this way; so does Google's test one. */
const UNIT_PREFIX = 'ca-app-pub-';

/**
 * The live rewarded unit, from the AdMob console.
 *
 * Empty when this build is not meant to have one. In that case the test unit is used even in
 * a release build — a missing id would otherwise fail to load an ad and leave the button
 * spinning, which is worse than showing a test ad to nobody.
 *
 * **That fallback is silent, and the silence cost us the feature.** Internal testers watched
 * adverts and were never paid: the variable was set nowhere — not in `.env`, not in
 * `eas.json` — so every build shipped on the test unit, and Google's test units never call a
 * publisher's server-side verification endpoint. The advert played, the client reported
 * `earned`, and no callback ever reached `/ads/reward` to move a coin or spend a daily view.
 * Hence the warning below: the fallback stays, but a release build no longer takes it
 * quietly.
 *
 * Google's own test id is refused here as well as an absent one. It is a legal-looking value
 * that a person could reasonably paste in, and it reproduces exactly the failure above —
 * adverts that play and never pay. Whatever is true of no id must be true of that id too.
 */
const LIVE_REWARDED_UNIT_ID =
  RAW_REWARDED_UNIT_ID.startsWith(UNIT_PREFIX) && RAW_REWARDED_UNIT_ID !== TEST_REWARDED_UNIT_ID
    ? RAW_REWARDED_UNIT_ID
    : '';

/** True when this build shows adverts that can actually pay. */
export function rewardsAreLive(): boolean {
  return !__DEV__ && !!LIVE_REWARDED_UNIT_ID;
}

export function rewardedUnitId(): string {
  if (__DEV__ || !LIVE_REWARDED_UNIT_ID) {
    if (!__DEV__) {
      if (RAW_REWARDED_UNIT_ID === UNIT_DISABLED) {
        // Asked for. Said once so a build with no earnable adverts is explainable, but not
        // at `warn`: shouting about a build behaving exactly as configured is how the real
        // warning below gets skimmed past.
        console.info('[ads] rewarded adverts are on the test unit in this build (LAN/gate profile).');
      } else {
        console.warn(
          RAW_REWARDED_UNIT_ID === ''
            ? '[ads] EXPO_PUBLIC_ADMOB_REWARDED_UNIT_ID is unset in a release build — ' +
                'falling back to the test unit, which never fires the reward callback. ' +
                'Set it in eas.json and configure SSV on the unit in the AdMob console.'
            : `[ads] ignoring EXPO_PUBLIC_ADMOB_REWARDED_UNIT_ID "${RAW_REWARDED_UNIT_ID}": ` +
                (RAW_REWARDED_UNIT_ID === TEST_REWARDED_UNIT_ID
                  ? 'that is Google\'s test unit, which never fires the reward callback, so ' +
                    'nobody would be paid for watching one.'
                  : `expected it to start with "${UNIT_PREFIX}".`),
        );
      }
    }
    return TEST_REWARDED_UNIT_ID;
  }
  return LIVE_REWARDED_UNIT_ID;
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
  /** Closed early. Nothing was earned and nothing is wrong. */
  | 'dismissed'
  /**
   * The advert could not be loaded or shown.
   *
   * Its own outcome rather than being folded into `dismissed`, which is what it used to be.
   * The two look identical to this module and could not be less alike to the person
   * holding the phone: one is them closing an advert, the other is the button doing
   * nothing. Reported as the same thing, a misconfigured ad unit was indistinguishable
   * from a gamer changing their mind — so nothing was shown, nothing was logged, and the
   * feature was simply broken in a way no screen could say out loud.
   */
  | 'error'
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
        ad.addAdEventListener(ads.AdEventType.ERROR, (error) => {
          // The code is the difference between "nobody had an advert to serve right now",
          // which is ordinary and passes, and "this unit does not belong to this app",
          // which is a build that will never show one. Both reach the same screen, so the
          // detail is logged rather than shown.
          console.warn('[ads] rewarded ad failed to load or show', error);
          finish('error');
        }),
      );

      ad.load();
    });
  } catch (error) {
    if (__DEV__) console.warn('[ads] rewarded ad failed', error);
    return 'unavailable';
  }
}
