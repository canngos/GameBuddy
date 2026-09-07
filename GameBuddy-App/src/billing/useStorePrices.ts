import { queryOptions, useQuery } from '@tanstack/react-query';
import { useCallback } from 'react';
import {
  COIN_PACKS,
  FALLBACK_CURRENCY,
  GOLD_PLANS,
  fallbackPriceText,
  type PriceLookup,
  type ResolvedPrice,
  type StoreProductId,
} from '../api/billing';
import { fetchStorePrices, storeAvailable } from './purchases';

/**
 * Real, localised prices for everything this app sells.
 *
 * ## Why this exists
 *
 * Every price used to be a string in the JavaScript bundle. That is wrong for everyone
 * outside the United States — a Turkish buyer read "$8.99" and was charged in lira — and it
 * made a price change an app release rather than a console edit. The store already knows the
 * answer, in the buyer's own currency, including whatever regional tier applies to them.
 *
 * ## Why a query rather than a context or a cached promise
 *
 * The Market and the Gold screen are separate routes, and both want the same four coin
 * packs and three plans. A shared query key dedupes that for free — the same trick
 * `GoldCosmetics` already uses to share `['cosmetics']` with the Market. A context would
 * have to be hoisted to the root layout and would then wrap every signed-out screen for the
 * benefit of two. A module-level promise cannot re-render anything when it resolves, and
 * cannot be thrown away when `logIn` changes which account — and so which currency — the
 * store is quoting.
 */
const ALL_PRODUCT_IDS: StoreProductId[] = [
  ...GOLD_PLANS.map((plan) => plan.productId),
  ...COIN_PACKS.map((pack) => pack.productId),
];

/** The bundled figures, by id, for when the store cannot be asked. */
const FALLBACK_USD: Record<StoreProductId, number> = {
  ...Object.fromEntries(GOLD_PLANS.map((plan) => [plan.productId, plan.usd])),
  ...Object.fromEntries(COIN_PACKS.map((pack) => [pack.productId, pack.usd])),
} as Record<StoreProductId, number>;

function fallbackFor(productId: StoreProductId, pending: boolean): ResolvedPrice {
  const usd = FALLBACK_USD[productId];
  return {
    text: fallbackPriceText(usd),
    amount: usd,
    currency: FALLBACK_CURRENCY,
    source: 'fallback',
    pending,
  };
}

/**
 * Shared by the hook and the prefetch in `app/(main)/_layout.tsx`, so the two cannot
 * disagree about how long the answer is good for.
 */
export function storePricesQuery() {
  return queryOptions({
    queryKey: ['storePrices'] as const,
    queryFn: () => fetchStorePrices(ALL_PRODUCT_IDS),
    // A build with no key or no native module will never have an answer, and asking costs a
    // pointless round trip through the lazy loader on every mount.
    enabled: storeAvailable(),
    // Prices do not move during a session, and the SDK caches them natively underneath.
    staleTime: 30 * 60 * 1000,
    gcTime: Infinity,
    // `fetchStorePrices` resolves rather than throwing, and already carries its own six
    // second deadline. A retry here could only turn one wait into two.
    retry: false,
  });
}

/**
 * Looks up one product's price.
 *
 * The returned function is stable, and it always answers: there is no id it can be handed
 * that has no fallback, because the parameter type is the union of the ids we sell. A blank
 * price is unreachable rather than guarded against.
 */
export function useStorePrices(): PriceLookup {
  const { data } = useQuery(storePricesQuery());

  return useCallback(
    (productId: StoreProductId): ResolvedPrice => {
      // Not "no data yet" — a build that cannot buy anything is already at its final
      // answer, so it renders the bundled price on the first frame rather than a
      // placeholder that would never resolve. That is most development and LAN builds.
      const pending = data === undefined && storeAvailable();
      if (pending) return fallbackFor(productId, true);

      const priced = data?.find((price) => price.productId === productId);
      if (!priced) return fallbackFor(productId, false);

      return {
        text: priced.priceString,
        amount: priced.amount,
        currency: priced.currencyCode,
        source: 'store',
        pending: false,
      };
    },
    [data],
  );
}
