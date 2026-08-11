import { useMutation, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { billingApi } from '../api/billing';
import { cosmeticsApi } from '../api/cosmetics';
import { PurchaseCancelledError, entitlementArrived, purchase as openStoreSheet } from './purchases';

/**
 * Every query whose answer depends on what this account owns.
 *
 * Listed once because forgetting one is invisible in testing and obvious to a user: buying
 * Gold and finding the deck still capped at yesterday's number reads as the purchase not
 * working, which is the impression a paywall can least afford.
 */
const ENTITLEMENT_QUERIES = [
  ['subscription'],
  ['admirers'],
  ['allowance'],
  ['me'],
  ['cosmetics'],
  ['recommendations'],
];

/**
 * How long to wait for the purchase to appear on our side after the store sheet closes.
 *
 * This wait exists because of how the money travels: the store charges, RevenueCat
 * verifies, RevenueCat posts a webhook to our backend, and only then do we know. The user
 * has paid before any of the last three have happened. Usually it is a second or two.
 *
 * Ten seconds is a compromise between two bad screens. Give up too early and somebody who
 * has just paid is told nothing happened; wait much longer and they are staring at a
 * spinner wondering whether to tap Buy again — and tapping Buy again after paying is the
 * single worst thing this flow can invite.
 */
const ENTITLEMENT_TIMEOUT_MS = 10_000;
const POLL_INTERVAL_MS = 1_000;

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * What kind of thing is being bought, which decides what "it arrived" means.
 *
 * A subscription is a tier, and its arrival is visible as `tier === 'GOLD'`. A coin pack
 * is a balance, and there is no flag to look for — only a number that should have gone up.
 * They cannot share a check, and using the subscription one for coins would wait ten
 * seconds and then always report a timeout on a purchase that worked perfectly.
 */
export type PurchaseKind = 'subscription' | 'coins';

/** The balance before buying, so an increase can be recognised. */
async function coinBalance(queryClient: QueryClient): Promise<number> {
  const store = await queryClient.fetchQuery({
    queryKey: ['cosmetics'],
    queryFn: cosmeticsApi.store,
    staleTime: 0,
  });
  return store.coins;
}

/**
 * Buys a product and waits for it to land on our side.
 *
 * There is no receipt here and nothing is persisted, which is the point of RevenueCat: if
 * this app dies between the charge and the entitlement, RevenueCat still has the purchase
 * and still delivers the webhook. Recovery stopped being the client's job.
 */
export function usePurchase(kind: PurchaseKind = 'subscription') {
  const queryClient = useQueryClient();

  const refresh = useCallback(async () => {
    await Promise.all(
      ENTITLEMENT_QUERIES.map((queryKey) => queryClient.invalidateQueries({ queryKey })),
    );
  }, [queryClient]);

  const buy = useMutation({
    mutationFn: async (productId: string) => {
      // Read before the sheet opens. Afterwards the webhook may already have landed, and
      // a "baseline" taken then would include the coins we are waiting for.
      const baseline = kind === 'coins' ? await coinBalance(queryClient) : 0;

      try {
        await openStoreSheet(productId);
      } catch (error) {
        // Backing out is a decision, not a failure. Surfacing "purchase failed" to somebody
        // who deliberately tapped Cancel is both wrong and slightly alarming.
        if (error instanceof PurchaseCancelledError) return { granted: false, cancelled: true };
        throw error;
      }

      // Poll rather than trust. The sheet closing means the store took the money, not that
      // our backend knows about it.
      const deadline = Date.now() + ENTITLEMENT_TIMEOUT_MS;
      while (Date.now() < deadline) {
        if (kind === 'coins') {
          if ((await coinBalance(queryClient)) > baseline) return { granted: true, cancelled: false };
        } else {
          const subscription = await queryClient.fetchQuery({
            queryKey: ['subscription'],
            queryFn: billingApi.subscription,
            staleTime: 0,
          });
          if (entitlementArrived(subscription)) return { granted: true, cancelled: false };
        }
        await sleep(POLL_INTERVAL_MS);
      }

      // Not an error. The purchase is real and RevenueCat will keep trying to tell us, so
      // the honest thing is "this is on its way", not "this failed" — which would invite
      // exactly the second purchase we must avoid.
      return { granted: false, cancelled: false };
    },
    onSuccess: () => {
      void refresh();
    },
  });

  return {
    buy: buy.mutate,
    isPending: buy.isPending,
    error: buy.error,
    /** True when the sheet completed but the purchase had not arrived before the timeout. */
    awaitingEntitlement: buy.data?.granted === false && !buy.data.cancelled,
    reset: buy.reset,
  };
}
