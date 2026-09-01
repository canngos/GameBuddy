import { useMutation, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { Coins, Crown } from 'lucide-react-native';
import { useCallback } from 'react';
import { billingApi } from '../api/billing';
import { cosmeticsApi } from '../api/cosmetics';
import { tNow } from '../i18n/useT';
import * as feedback from '../ui/feedback';
import { showToast } from '../ui/toast';
import { PurchaseCancelledError, entitlementArrived, purchase as openStoreSheet } from './purchases';

/**
 * Every query whose answer depends on what this account owns.
 *
 * Listed once because forgetting one is invisible in testing and obvious to a user: buying
 * Gold and finding the deck still capped at yesterday's number reads as the purchase not
 * working, which is the impression a paywall can least afford.
 *
 * Exported because redeeming a promotion code grants exactly the same things a purchase
 * does, and a second list written next to this one would agree with it on the day it was
 * written and not for much longer.
 */
export const ENTITLEMENT_QUERIES = [
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
        // A failed check is not a failed purchase. The store has already taken the money
        // by this point; one 500 or dropped connection rethrowing out of here rendered
        // "purchase failed" to somebody who had paid - the exact message the comment
        // below exists to prevent. A check that errors counts as "not yet".
        try {
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
        } catch (error) {
          if (__DEV__) console.warn('[billing] entitlement check failed; still waiting', error);
        }
        await sleep(POLL_INTERVAL_MS);
      }

      // Not an error. The purchase is real and RevenueCat will keep trying to tell us, so
      // the honest thing is "this is on its way", not "this failed" — which would invite
      // exactly the second purchase we must avoid.
      return { granted: false, cancelled: false };
    },
    onSuccess: (result) => {
      void refresh();

      /*
       * The success moment, fired on the entitlement *arriving* rather than on the sheet
       * closing.
       *
       * That distinction is the whole reason this sits here and not next to the `buy` call
       * on the paywall. The sheet closing means the store took the money; it does not mean
       * the account owns anything yet, because only RevenueCat's webhook grants it.
       * Celebrating at the sheet is celebrating a payment, and if the webhook is slow the
       * next thing the user sees is a card saying their purchase is still on its way — a
       * congratulation followed by a wait.
       *
       * `onSuccess` runs for all three outcomes, including a cancel, which is why this is
       * gated on `granted` rather than on merely having got here.
       *
       * **Note this path is untested.** RevenueCat is not configured for this project yet,
       * so nothing reaches it in practice; it is wired now because it costs one branch and
       * because the coin-spending moments it mirrors are verified. Exercise it when the
       * store is live before trusting it.
       */
      if (!result.granted) return;

      feedback.purchase();
      // `tNow()` rather than `useT()`: a toast is a one-shot surface fired from an async
      // continuation, so the snapshot at this instant is exactly what it should say.
      const t = tNow();
      showToast({
        id: `entitlement:${kind}`,
        title: kind === 'coins' ? t.billing.coinsAdded : t.billing.goldYours,
        body: kind === 'coins' ? t.billing.coinsAddedBody : t.billing.goldYoursBody,
        icon: kind === 'coins' ? Coins : Crown,
        tone: 'gold',
      });
    },
  });

  return {
    buy: buy.mutate,
    isPending: buy.isPending,
    error: buy.error,
    /** True when the sheet completed but the purchase had not arrived before the timeout. */
    awaitingEntitlement: buy.data?.granted === false && !buy.data.cancelled,
    /** True once the entitlement is genuinely ours. Was previously computed and thrown away. */
    granted: buy.data?.granted === true,
    reset: buy.reset,
  };
}
