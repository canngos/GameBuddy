import { NativeModules, Platform } from 'react-native';
import type { PRODUCT_CATEGORY } from 'react-native-purchases';
import type { Subscription } from '../api/types';

/**
 * The RevenueCat SDK, and the only file in the app that imports it.
 *
 * ## Why RevenueCat
 *
 * Receipt verification is security-critical, differs between Apple and Google, and has to
 * keep working through renewals, grace periods, refunds and family sharing. RevenueCat does
 * that and posts the outcome to our backend over a signed webhook, so nothing in this app —
 * and nothing in our backend — ever parses a receipt.
 *
 * `react-native-purchases-ui` is deliberately **not** installed alongside it. That package
 * exists to render RevenueCat's own paywall templates; ours is already built, themed and
 * wired into four entry points, so pulling in a second paywall system would be a large
 * native dependency to duplicate what we have.
 *
 * ## Why the SDK is loaded lazily
 *
 * This is native code. It is present in the JavaScript bundle the moment it is imported,
 * but the *native* half only exists in a development build made after it was installed —
 * so a top-level `import Purchases from 'react-native-purchases'` would crash any older
 * build at startup, before a single screen rendered. Loading it on first use instead means
 * an older build keeps working everywhere except the purchase button, which fails with a
 * clear message rather than a white screen.
 *
 * Once a build made after this install is on the device, the lazy load is invisible.
 */

/**
 * The public SDK key. Public by design — RevenueCat expects it to ship inside the app.
 *
 * **The key is checked rather than trusted, and that check is load-bearing.** RevenueCat
 * issues one key per platform — `goog_` for Google Play, `appl_` for the App Store — plus
 * `test_` keys for its own Test Store. Hand a *release* build a `test_` key and the SDK shows
 * a native "Wrong API Key" dialog and then **deliberately terminates the process**, to stop
 * test purchases reaching a shipped app.
 *
 * That kill is native, so the try/catch in `identify` below cannot intercept it — the same
 * blind spot recorded in `QA_FINDINGS.md`, where every Maestro flow died at this exact point
 * seconds after sign-in. It is also invisible in development, because it only fires on a
 * release build. A Test Store key left in an EAS profile is therefore not a dead purchase
 * button; it is the app closing itself on the first screen after sign-in.
 *
 * So a `test_` key is accepted in a debug build and refused in a release one — the same line
 * RevenueCat draws, enforced before `configure` instead of after. That keeps the Test Store
 * usable for exercising the purchase flow while the Play account is still being verified,
 * without letting the same key reach a build that would die holding it.
 */
const STORE_PREFIX = Platform.select({ android: 'goog_', ios: 'appl_', default: '' });

const RAW_API_KEY = process.env.EXPO_PUBLIC_REVENUECAT_API_KEY ?? '';

/**
 * What a build writes instead of a key when it is meant not to sell anything.
 *
 * The `lan` EAS profile inherits its env from `production`, and `extends` deep-merges rather
 * than replaces — so the only way to drop an inherited value is to overwrite it. Empty string
 * would be the obvious way to say "no key", but EAS rejects it: `eas.json is not valid —
 * "build.lan.env.EXPO_PUBLIC_REVENUECAT_API_KEY" is not allowed to be empty`, and it fails
 * validation of the whole file, so one empty value blocks every build including `production`.
 *
 * Hence a word rather than nothing. It is refused by `keyIsUsable` like any other malformed
 * key; naming it here only lets the log below tell a deliberate opt-out apart from a build
 * that lost its key by accident, which are the same silence but very different problems.
 */
const KEY_DISABLED = 'none';

function keyIsUsable(key: string): boolean {
  if (STORE_PREFIX === '') return false; // web, where there is no store to reach
  if (key.startsWith(STORE_PREFIX)) return true; // a real store key, always fine
  return __DEV__ && key.startsWith('test_'); // Test Store: debug builds only
}

const API_KEY = keyIsUsable(RAW_API_KEY) ? RAW_API_KEY : '';

/**
 * Announced at `error`, not behind `__DEV__`, and that is the point.
 *
 * A build with no usable key runs perfectly: `storeAvailable()` simply returns false and the
 * paywall never sells anything. `QA_FINDINGS.md` calls that the more dangerous of the two
 * misconfigurations for exactly that reason — it is silent, so a store build could ship
 * unable to take money and look fine until somebody tried to pay. A build that cannot sell
 * has to say so somewhere a release build is actually read: logcat and Crashlytics.
 */
if (API_KEY === '' && Platform.OS !== 'web') {
  if (RAW_API_KEY === KEY_DISABLED) {
    // Asked for. Said once so the absence of a paywall is explainable, but not at `error`:
    // shouting about a build behaving exactly as configured is how real errors get skimmed.
    console.info('[billing] purchases are switched off in this build (LAN/testing profile).');
  } else {
    console.error(
      RAW_API_KEY === ''
        ? '[billing] no EXPO_PUBLIC_REVENUECAT_API_KEY in this build — purchases are disabled.'
        : RAW_API_KEY.startsWith('test_')
          ? '[billing] a RevenueCat Test Store key cannot be used in a release build — RevenueCat ' +
            'closes the app rather than allow it. Purchases are disabled in this build instead.'
          : `[billing] ignoring EXPO_PUBLIC_REVENUECAT_API_KEY: expected it to start with "${STORE_PREFIX}". ` +
            'Purchases are disabled in this build rather than closing it.',
    );
  }
}

type PurchasesSdk = typeof import('react-native-purchases').default;

/** `undefined` = not tried yet, `null` = tried and the native module is not in this build. */
let sdkCache: PurchasesSdk | null | undefined;
let configuredFor: string | null = null;

function sdk(): PurchasesSdk | null {
  if (sdkCache !== undefined) return sdkCache;

  // Check the native side first, and do it directly.
  //
  // Importing the package is not a test of anything: `react-native-purchases` reads
  // `NativeModules.RNPurchases`, which is simply `undefined` in a build that does not
  // contain the native module — no error, no warning. So a try/catch around the import
  // would always succeed, `storeAvailable()` would report true on a build that cannot
  // purchase, and the failure would surface as an obscure crash inside `configure`
  // instead of a disabled button with a reason next to it.
  if (!NativeModules.RNPurchases) {
    if (__DEV__) {
      console.warn('[billing] RNPurchases native module is missing — this build cannot purchase');
    }
    sdkCache = null;
    return sdkCache;
  }

  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    sdkCache = (require('react-native-purchases') as typeof import('react-native-purchases')).default;
  } catch (error) {
    if (__DEV__) console.warn('[billing] react-native-purchases failed to load', error);
    sdkCache = null;
  }
  return sdkCache;
}

/** Whether a real store sheet can be opened on this build. */
export function storeAvailable(): boolean {
  return sdk() !== null && API_KEY !== '' && Platform.OS !== 'web';
}

/**
 * Identifies the buyer to RevenueCat. Call after sign-in, before any purchase.
 *
 * **This is what makes billing work at all**, and it is easy to mistake for bookkeeping.
 * The id given here is what arrives at our webhook as `app_user_id`, and it is the only
 * thing that says which account to grant. Skip it and RevenueCat invents an anonymous id
 * (`$RCAnonymousID:…`), the webhook has nowhere to deliver, and `RevenueCatService` logs an
 * error while somebody sits there having paid.
 *
 * Safe to call repeatedly; it configures once and only logs in when the user changes.
 */
export async function identify(userId: string): Promise<void> {
  const Purchases = sdk();
  if (!Purchases || !API_KEY) return;
  if (configuredFor === userId) return;

  try {
    if (configuredFor === null) {
      // appUserID up front, so the very first purchase is already attributed. Configuring
      // anonymously and logging in afterwards leaves a window where a fast purchase is
      // recorded against an anonymous id.
      await Purchases.configure({ apiKey: API_KEY, appUserID: userId });
    } else {
      await Purchases.logIn(userId);
    }
    configuredFor = userId;
  } catch (error) {
    // Never fatal. Failing to reach RevenueCat must not stop somebody using the app; it
    // only means purchases will not work until it succeeds.
    if (__DEV__) console.warn('[billing] could not identify to RevenueCat', error);
  }
}

/** Clears the identity on sign-out, so the next account does not inherit this one's. */
export async function forgetIdentity(): Promise<void> {
  const Purchases = sdk();
  if (!Purchases || configuredFor === null) return;
  try {
    await Purchases.logOut();
  } catch (error) {
    if (__DEV__) console.warn('[billing] could not log out of RevenueCat', error);
  }
  configuredFor = null;
}

/**
 * Why the store cannot be reached, as a code the UI can translate.
 *
 * The English `message` stays for logs and Crashlytics; `useErrorText` recognises the
 * error by name and maps `reason` to the reader's language, so this module never has to
 * import i18n.
 */
export type StoreUnavailableReason = 'unavailable' | 'notInBuild' | 'noKey' | 'productMissing';

export class StoreUnavailableError extends Error {
  constructor(
    public readonly reason: StoreUnavailableReason = 'unavailable',
    message = 'Purchases are not available in this build yet.',
  ) {
    super(message);
    this.name = 'StoreUnavailableError';
  }
}

/** Thrown when the buyer backed out. Not a failure, and must not be shown as one. */
export class PurchaseCancelledError extends Error {
  constructor() {
    super('Purchase cancelled.');
    this.name = 'PurchaseCancelledError';
  }
}

/**
 * Opens the store sheet for a product and resolves once the store has taken payment.
 *
 * Resolving does **not** mean the account is Gold — RevenueCat still has to tell our
 * backend. `usePurchase` waits for that separately.
 *
 * Products are fetched by id rather than through an Offering. Our backend keys entitlements
 * off the store product id in `Product.java`, so the id is the contract; an Offering adds a
 * layer of indirection that would have to agree with it. Offerings become worth it when
 * prices need localising or plans need changing without an app release — see the note on
 * `GOLD_PLANS`.
 *
 * Fetching by id is not quite as literal as it sounds on Google Play, and both wrinkles are
 * handled below: the category has to be named, and a subscription comes back under
 * `<productId>:<basePlanId>` rather than the id that was asked for.
 */
export async function purchase(productId: string): Promise<void> {
  const Purchases = sdk();
  if (!Purchases) {
    throw new StoreUnavailableError(
      'notInBuild',
      'This build cannot make purchases. It was installed before in-app purchases were added.',
    );
  }
  if (!API_KEY) {
    throw new StoreUnavailableError('noKey', 'No RevenueCat key is configured in this build.');
  }

  // The category has to be stated. `getProducts` defaults to SUBSCRIPTION, so asking for a
  // coin pack without it queries the wrong half of the store and comes back empty — the
  // paywall would have reported "not available right now" for all three packs while Play was
  // selling them perfectly well. Gold is a subscription; coin packs are not.
  //
  // The split is read off the id prefix, which holds for all six ids in `Product.java` but is
  // a convention rather than a guarantee: a subscription added later under some other prefix
  // would be looked up as a one-time product and silently come back empty. Anything sold as a
  // recurring plan has to be named here too.
  //
  // The literals are written out rather than referenced through the `PRODUCT_CATEGORY` enum
  // because naming an enum *value* imports the package, which is exactly what the lazy load
  // above exists to avoid; `PRODUCT_CATEGORY` is imported as a type only, so this stays
  // checked against the SDK while compiling to two plain strings.
  const category: PRODUCT_CATEGORY = productId.startsWith('gamebuddy.gold.')
    ? ('SUBSCRIPTION' as PRODUCT_CATEGORY)
    : ('NON_SUBSCRIPTION' as PRODUCT_CATEGORY);

  const products = await Purchases.getProducts([productId], category);

  // Google Play splits a subscription into a product and one or more *base plans*, and
  // RevenueCat names the pair `<productId>:<basePlanId>` — so asking for
  // `gamebuddy.gold.monthly` returns something whose identifier is
  // `gamebuddy.gold.monthly:monthly`. An `===` test therefore missed every Gold plan and
  // threw StoreUnavailableError before the store sheet ever opened. Exact match is still
  // tried first, because Apple and the coin packs return the bare id.
  const product =
    products.find((candidate) => candidate.identifier === productId) ??
    products.find((candidate) => candidate.identifier.startsWith(`${productId}:`));

  if (!product) {
    // The id is not sold on this store. Ours to fix — the plan list and the store
    // disagree — so it is logged loudly rather than shown as a payment failure.
    if (__DEV__) console.error('[billing] store does not offer', productId);
    throw new StoreUnavailableError('productMissing', 'That plan is not available right now.');
  }

  try {
    await Purchases.purchaseStoreProduct(product);
  } catch (error) {
    if (isCancellation(error)) throw new PurchaseCancelledError();
    throw error;
  }
}

/** RevenueCat reports a user backing out as an error with `userCancelled` set. */
function isCancellation(error: unknown): boolean {
  return typeof error === 'object' && error !== null && 'userCancelled' in error
    ? Boolean((error as { userCancelled?: unknown }).userCancelled)
    : false;
}

/** Whether the backend now reports the entitlement this purchase was supposed to grant. */
export function entitlementArrived(subscription: Subscription | undefined): boolean {
  return subscription?.tier === 'GOLD';
}
