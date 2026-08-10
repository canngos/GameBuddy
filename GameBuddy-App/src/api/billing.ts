import { Platform } from 'react-native';
import { api } from './client';
import type { Purchase, Subscription } from './types';

/**
 * Which store product a plan maps to. These ids must match `Product.java` exactly and,
 * before real money moves, the products registered in both consoles.
 */
export const GOLD_PLANS = [
  {
    productId: 'gamebuddy.gold.weekly',
    label: 'Weekly',
    period: '1 week',
    price: '$3.99',
    note: null,
  },
  {
    productId: 'gamebuddy.gold.monthly',
    label: 'Monthly',
    period: '1 month',
    price: '$7.99',
    note: '3-day free trial',
  },
  {
    productId: 'gamebuddy.gold.yearly',
    label: 'Yearly',
    period: '12 months',
    price: '$39.99',
    note: 'Save 58%',
  },
] as const;

export type GoldPlan = (typeof GOLD_PLANS)[number];

/** What the backend calls this platform. Matches the `PurchasePlatform` enum. */
function platform(): 'APPLE' | 'GOOGLE' {
  return Platform.OS === 'ios' ? 'APPLE' : 'GOOGLE';
}

export const billingApi = {
  /**
   * What this account currently holds.
   *
   * The tier is derived server-side from the stored tier *and* the expiry, never read
   * straight off the row — see `SubscriptionTier.effective`. So this is the only
   * trustworthy answer to "am I Gold", and the client must not cache its own idea of it.
   */
  subscription: () => api.get<Subscription>('/billing/subscription'),

  /**
   * Hands a store receipt to the backend, which verifies it and grants the entitlement.
   *
   * **No store sheet is opened here yet.** The IAP library is not installed — that is a
   * separate task, and it needs a config plugin and a rebuild. Until then this sends a
   * development receipt, which only works because `gamebuddy.billing.sandbox=true` is set
   * locally; with it off, `PurchaseService` has no verifier for the platform and refuses
   * every purchase rather than granting one. Nothing here can accidentally work in
   * production.
   *
   * When the library lands, the only change is where `receipt` comes from.
   */
  redeem: (productId: string, receipt: string) =>
    api.post<Purchase>('/billing/redeem', { platform: platform(), productId, receipt }),
};

/**
 * A receipt for local testing.
 *
 * Deliberately obvious in the logs and in the database: anything that turns up with this
 * shape was never paid for. It is unique per attempt because `SandboxReceiptVerifier`
 * derives the store transaction id from the receipt, and a repeated id is correctly
 * rejected as a replay by the unique constraint.
 */
export function developmentReceipt(productId: string): string {
  return `dev-${productId}-${Date.now()}`;
}
