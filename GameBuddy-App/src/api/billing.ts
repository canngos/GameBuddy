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

/**
 * Coin packs. Consumables, so they grant a balance rather than an entitlement.
 *
 * `coins` must match `Product.java` — the backend grants from its own table, so a wrong
 * number here does not shortchange anybody, it just advertises the wrong amount, which is
 * worse in its own way. The three ids and amounts are checked against the enum.
 *
 * `bonus` is presentation, computed from the per-coin rate against the smallest pack. It
 * is not a second source of truth: change a price or an amount and the badge follows.
 */
export const COIN_PACKS = [
  { productId: 'gamebuddy.coins.500', coins: 500, price: '$1.99' },
  { productId: 'gamebuddy.coins.1200', coins: 1200, price: '$3.99' },
  { productId: 'gamebuddy.coins.3000', coins: 3000, price: '$8.99' },
] as const;

export type CoinPack = (typeof COIN_PACKS)[number];

/**
 * How much better value a pack is than the smallest one, as a percentage, or null when it
 * is not meaningfully better.
 *
 * Rounded down, and anything under 5% returns null: "2% more coins" is not a reason to
 * spend more money and putting a badge on it only teaches people to ignore the badges.
 */
export function bonusPercent(pack: CoinPack): number | null {
  const base = COIN_PACKS[0];
  if (pack.productId === base.productId) return null;

  const rate = (p: CoinPack) => p.coins / Number(p.price.replace(/[^0-9.]/g, ''));
  const better = Math.floor((rate(pack) / rate(base) - 1) * 100);
  return better >= 5 ? better : null;
}

export const billingApi = {
  /**
   * What this account currently holds.
   *
   * The tier is derived server-side from the stored tier *and* the expiry, never read
   * straight off the row — see `SubscriptionTier.effective`. So this is the only
   * trustworthy answer to "am I Gold", and the client must not cache its own idea of it.
   *
   * `POST /billing/redeem` is gone: receipts go to RevenueCat, which verifies them with
   * Apple or Google and tells our backend over a webhook. There is deliberately no call
   * the app can make to assert that it bought something — that would have been a free
   * subscription for anyone willing to send the request by hand.
   */
  subscription: () => api.get<Subscription>('/billing/subscription'),

  /**
   * Reports that the day-3 prompt was actually put on screen, so it never returns.
   *
   * The server does not mark it on the read, because the answer can be fetched and then
   * thrown away by a navigation or a backgrounded app — and burning the single showing on
   * a prompt nobody saw is the worse of the two mistakes.
   *
   * Grants nothing, so it is safe to fire and forget. A failure here means somebody may
   * see the prompt once more, which is not worth an error in front of them.
   */
  markUpgradePromptSeen: () => api.post<{ message: string }>('/billing/upgrade-prompt/seen'),
};
