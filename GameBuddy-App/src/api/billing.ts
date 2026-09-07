import { api } from './client';
import type { MyPromoCodes, PromoRedemption, Subscription } from './types';

/**
 * Which store product a plan maps to.
 *
 * These ids must match `Product.java` exactly and the products registered in both consoles
 * and in RevenueCat. A mismatch in any of the three is a purchase that takes somebody's
 * money and grants nothing.
 *
 * **`usd` is not the price.** The store is the authority on price — it owns currency,
 * regional tiers and tax — and `useStorePrices` reads the real localised figure from it.
 * These numbers are the last resort, shown only when the store cannot be reached at all,
 * and they are a plain number rather than a formatted string so that nothing is ever
 * tempted to parse a currency back out of one. They still have to be kept in step with the
 * consoles, because they are what a buyer sees when the store is down.
 */
export const GOLD_PLANS = [
  {
    productId: 'gamebuddy.gold.weekly',
    label: 'Weekly',
    period: '1 week',
    usd: 3.99,
    note: null,
  },
  {
    productId: 'gamebuddy.gold.monthly',
    label: 'Monthly',
    period: '1 month',
    usd: 7.99,
    note: '3-day free trial',
  },
  {
    productId: 'gamebuddy.gold.yearly',
    label: 'Yearly',
    period: '12 months',
    usd: 39.99,
    note: null,
  },
] as const;

export type GoldPlan = (typeof GOLD_PLANS)[number];

/**
 * Coin packs. Consumables, so they grant a balance rather than an entitlement.
 *
 * `coins` must match `Product.java` — the backend grants from its own table, so a wrong
 * number here does not shortchange anybody, it just advertises the wrong amount, which is
 * worse in its own way.
 *
 * The prices doubled on 2026-09-07 and `gamebuddy.coins.7000` was added at the top. The
 * reasoning is in `upgrade-2026-44-shelf-reprice.sql`: priced against the pack people
 * actually buy, a coin was worth $0.0030, which put the dearest thing in the shop at $4.50
 * against a market that charges $5.99–$12.99 for one avatar decoration. The largest pack
 * exists because the ceiling on what a willing buyer could spend in one go was ours rather
 * than theirs, and it sits below Gold yearly on purpose — a coin pack costing the same as a
 * year of Gold invites the comparison and loses it.
 */
export const COIN_PACKS = [
  { productId: 'gamebuddy.coins.500', coins: 500, usd: 3.99 },
  { productId: 'gamebuddy.coins.1200', coins: 1200, usd: 7.99 },
  { productId: 'gamebuddy.coins.3000', coins: 3000, usd: 16.99 },
  { productId: 'gamebuddy.coins.7000', coins: 7000, usd: 34.99 },
] as const;

export type CoinPack = (typeof COIN_PACKS)[number];

/** Every id this app can ask the store to price. */
export type StoreProductId = GoldPlan['productId'] | CoinPack['productId'];

/** A price that is ready to put on screen, and honest about where it came from. */
export type ResolvedPrice = {
  /** What to render. Never empty. */
  text: string;
  /** The same figure as a number, for arithmetic only. Never render this. */
  amount: number;
  /** ISO-4217. Two prices may only be compared when these match. */
  currency: string;
  /** Two prices may only be compared when these match, too — see `bonusPercent`. */
  source: 'store' | 'fallback';
  /** True while the store has not answered and `text` is not worth showing yet. */
  pending: boolean;
};

export type PriceLookup = (productId: StoreProductId) => ResolvedPrice;

/** The currency the `usd` fallbacks are quoted in. */
export const FALLBACK_CURRENCY = 'USD';

/** Formats a fallback. Only ever reached when the store could not be asked. */
export const fallbackPriceText = (usd: number) => `$${usd.toFixed(2)}`;

/**
 * How much better value a pack is than the smallest one, as a percentage, or null when it
 * is not meaningfully better — or when the two prices cannot honestly be compared.
 *
 * Rounded down, and anything under 5% returns null: "2% more coins" is not a reason to
 * spend more money and putting a badge on it only teaches people to ignore the badges.
 *
 * **It refuses rather than guesses**, on three counts, because a badge is decoration and a
 * missing one costs nothing while a wrong one sits next to real money:
 *
 *   - different currencies — a ratio across two of them is meaningless;
 *   - different `source` — this is the one that actually bites. A live store price beside a
 *     bundled USD fallback yields a confident number computed from two different worlds,
 *     and when the store's currency happens to also be USD the currency check alone lets it
 *     through;
 *   - either side still pending — a badge that appears, changes, then disappears is worse
 *     than one that arrives a moment late.
 */
export function bonusPercent(
  pack: { coins: number; price: ResolvedPrice },
  base: { coins: number; price: ResolvedPrice },
): number | null {
  if (pack.coins === base.coins) return null;
  if (pack.price.pending || base.price.pending) return null;
  if (pack.price.source !== base.price.source) return null;
  if (pack.price.currency !== base.price.currency) return null;
  if (!(pack.price.amount > 0) || !(base.price.amount > 0)) return null;

  const rate = (coins: number, amount: number) => coins / amount;
  const better = Math.floor(
    (rate(pack.coins, pack.price.amount) / rate(base.coins, base.price.amount) - 1) * 100,
  );
  return better >= 5 ? better : null;
}

/**
 * The coin packs with their prices and badges resolved, in shelf order.
 *
 * Built here rather than in the screen so that the badge and the price it is derived from
 * cannot be worked out from two different snapshots of the same query.
 */
export function coinPackRows(priceOf: PriceLookup) {
  const base = { coins: COIN_PACKS[0].coins, price: priceOf(COIN_PACKS[0].productId) };
  return COIN_PACKS.map((pack) => {
    const price = priceOf(pack.productId);
    return { pack, price, bonus: bonusPercent({ coins: pack.coins, price }, base) };
  });
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

/**
 * Promotion codes, from the gamer's side.
 *
 * The neighbouring comment about `POST /billing/redeem` still stands and this is not a way
 * back to it. That endpoint let the app assert a purchase; this one sends a string the
 * server looks up. Everything about what it is worth, who it was for and whether there is
 * any left is decided there, against rows an administrator wrote.
 */
export const promoApi = {
  /** What is waiting for this account, and what it has already used. */
  mine: () => api.get<MyPromoCodes>('/billing/promo-codes'),

  /**
   * Redeems a code, and answers with the resulting balance and expiry rather than only
   * what the code was worth — the cached balance on this side may be minutes old, and
   * adding to it locally is how the number ends up wrong in the moment somebody is
   * watching it.
   */
  redeem: (code: string) => api.post<PromoRedemption>('/billing/promo-codes/redeem', { code }),
};
