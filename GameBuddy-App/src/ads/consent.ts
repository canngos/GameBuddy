import { create } from 'zustand';
import { adsSdk } from './sdk';

/**
 * Consent for advertising, via Google's User Messaging Platform.
 *
 * ## What this is for
 *
 * GameBuddy shows one advert: an optional rewarded video in the Market. It asks for a
 * *personalised* one (`requestNonPersonalizedAdsOnly: false` in `rewarded.ts`), and reading
 * the advertising identifier to personalise needs consent under the GDPR. Since January 2024
 * AdMob also refuses to serve EEA and UK traffic at all unless the publisher runs a
 * Google-certified consent platform. This app is operated from Finland, so that is not a
 * distant market — it is the home one.
 *
 * UMP ships inside the AdMob SDK, so there is no extra dependency. It decides from the IP
 * address whether this device is somewhere consent is required, downloads the form
 * configured in the AdMob console in the device's own language, shows it, and writes the
 * answer into the standard `IABTCF_*` preference keys. The Mobile Ads SDK reads those keys
 * itself and downgrades or refuses accordingly.
 *
 * **Nothing here stores a consent decision, and nothing should.** UMP owns it. This module
 * only asks "may I request an advert?" and remembers the answer for as long as the app is
 * running, so the Market and Settings can render without an async round trip. Persisting a
 * copy would only create something that can disagree with the real thing.
 *
 * ## Why this runs at startup
 *
 * Google's documented order is to gather consent as early as possible, so an advert can be
 * requested the moment one is wanted. The cost is real and worth stating: the AdMob SDK now
 * starts for everybody, including the majority who never watch a video. The alternative —
 * asking at the moment the button is tapped — keeps the SDK out of those sessions entirely
 * but puts a form load in front of the reward. The first was chosen; see section 4 of
 * `documentation/legal/PRIVACY.md`, which describes to users what this does.
 */

type ConsentState = {
  /**
   * Whether an advert may be requested at all.
   *
   * Starts `true`, which is deliberate. Outside the regulated regions UMP reports
   * `NOT_REQUIRED` and there is nothing to wait for, and a build with no AdMob in it never
   * gets as far as asking. Starting `false` would hide the advert row for a beat on every
   * launch, everywhere, to be correct for one case that resolves in milliseconds.
   */
  canRequestAds: boolean;
  /** Whether UMP wants an ongoing way for the user to reopen the form. */
  privacyOptionsRequired: boolean;
  set: (next: { canRequestAds: boolean; privacyOptionsRequired: boolean }) => void;
};

export const useAdConsent = create<ConsentState>((set) => ({
  canRequestAds: true,
  privacyOptionsRequired: false,
  set: (next) => set(next),
}));

/** Read outside React — `rewarded.ts` is not a component. */
export function canRequestAds(): boolean {
  return useAdConsent.getState().canRequestAds;
}

let gathered = false;

/**
 * Asks UMP for consent, showing the form if the region requires one.
 *
 * Runs once per app run, from the root layout. Never awaited by anything that renders: the
 * form appears over whatever is already on screen when it is ready, which is the point of
 * doing it here rather than in front of the splash.
 *
 * Never throws. A failure to reach Google means adverts do not work this session, which is
 * a missing coin faucet and not a reason to interrupt somebody using the app — the same
 * judgement `identify()` makes in `src/billing/purchases.ts`.
 */
export async function gatherAdConsent(): Promise<void> {
  if (gathered) return;
  const ads = adsSdk();
  if (!ads) return;
  gathered = true;

  try {
    const info = await ads.AdsConsent.gatherConsent({
      // Ours is an 18+ service, so this is honest rather than restrictive. It also keeps the
      // request out of the child-directed rules, the same reasoning as the advert request.
      tagForUnderAgeOfConsent: false,
      // A device sitting in Finland is already in the EEA, but a device anywhere else would
      // never see the form, and "it works on my machine" is not a test of a consent flow.
      ...(__DEV__ ? { debugGeography: ads.AdsConsentDebugGeography.EEA } : {}),
    });

    useAdConsent.getState().set({
      canRequestAds: info.canRequestAds,
      privacyOptionsRequired:
        info.privacyOptionsRequirementStatus ===
        ads.AdsConsentPrivacyOptionsRequirementStatus.REQUIRED,
    });
  } catch (error) {
    // Left as the optimistic default rather than forced to false: a network blip on launch
    // should not silently switch off a feature for the rest of the session, and the advert
    // request itself will fail safely if consent really is missing.
    if (__DEV__) console.warn('[ads] could not gather consent', error);
  }
}

/**
 * Reopens the consent form from Settings.
 *
 * UMP requires this to exist wherever it reports `privacyOptionsRequirementStatus` as
 * required — a consent that cannot be withdrawn is not consent.
 */
export async function openAdPrivacyOptions(): Promise<void> {
  const ads = adsSdk();
  if (!ads) return;

  try {
    const info = await ads.AdsConsent.showPrivacyOptionsForm();
    useAdConsent.getState().set({
      canRequestAds: info.canRequestAds,
      privacyOptionsRequired:
        info.privacyOptionsRequirementStatus ===
        ads.AdsConsentPrivacyOptionsRequirementStatus.REQUIRED,
    });
  } catch (error) {
    if (__DEV__) console.warn('[ads] could not show the privacy options form', error);
  }
}
