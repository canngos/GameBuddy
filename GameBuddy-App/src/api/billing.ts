import { api } from './client';
import type { Subscription } from './types';

/**
 * Which store product a plan maps to.
 *
 * These ids must match `Product.java` exactly, the products registered in both consoles,
 * and the offering configured in RevenueCat. A mismatch in any of the three is a purchase
 * that takes somebody's money and grants nothing.
 *
 * The prices here are display copy only. RevenueCat reports the real localised price from
 * the store, and once the offering is wired up these strings should be replaced by it —
 * showing "$7.99" to somebody who will be charged €8.99 is a store-review problem as well
 * as a trust one.
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

export const billingApi = {
  /**
   * What this account currently holds.
   *
   * The tier is derived server-side from the stored tier *and* the expiry, never read
   * straight off the row — see `SubscriptionTier.effective`. So this is the only
   * trustworthy answer to "am I Gold", and the client must not cache its own idea of it.
   *
   * **This is now the only billing endpoint.** `POST /billing/redeem` is gone: receipts go
   * to RevenueCat, which verifies them with Apple or Google and tells our backend over a
   * webhook. There is deliberately no call the app can make to assert that it bought
   * something — that would have been a free subscription for anyone willing to send the
   * request by hand.
   */
  subscription: () => api.get<Subscription>('/billing/subscription'),
};
