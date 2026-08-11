import { NativeModules, Platform } from 'react-native';
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

/** The public SDK key. Public by design — RevenueCat expects it to ship inside the app. */
const API_KEY = process.env.EXPO_PUBLIC_REVENUECAT_API_KEY ?? '';

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
    // eslint-disable-next-line @typescript-eslint/no-var-requires
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

export class StoreUnavailableError extends Error {
  constructor(message = 'Purchases are not available in this build yet.') {
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
 */
export async function purchase(productId: string): Promise<void> {
  const Purchases = sdk();
  if (!Purchases) {
    throw new StoreUnavailableError(
      'This build cannot make purchases. It was installed before in-app purchases were added.',
    );
  }
  if (!API_KEY) {
    throw new StoreUnavailableError('No RevenueCat key is configured in this build.');
  }

  const products = await Purchases.getProducts([productId]);
  const product = products.find((candidate) => candidate.identifier === productId);
  if (!product) {
    // The id is not sold on this store. Ours to fix — the plan list and the store
    // disagree — so it is logged loudly rather than shown as a payment failure.
    if (__DEV__) console.error('[billing] store does not offer', productId);
    throw new StoreUnavailableError('That plan is not available right now.');
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
