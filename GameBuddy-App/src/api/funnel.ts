import { api } from './client';

/**
 * The funnel steps only the client can see.
 *
 * Opening a paywall and asking for a store sheet happen entirely on the device; the server
 * never learns about them unless it is told. Everything downstream — a granted
 * subscription, a spent coin — is recorded server-side and is deliberately not reported
 * from here, because a number the client asserts is a number the client can be wrong about.
 */
export type FunnelStep =
  | 'PAYWALL_VIEWED'
  | 'CHECKOUT_STARTED'
  | 'COIN_SHOP_VIEWED'
  | 'PAYWALL_TRIGGERED';

/**
 * Reports a step, and never fails.
 *
 * Fire-and-forget on purpose. A dropped event costs a fraction of a point on a dashboard;
 * an analytics call that rejects, retries or throws costs the user the thing they were
 * doing. Nothing awaits this and nothing branches on it.
 */
export function trackFunnel(step: FunnelStep): void {
  void api.post(`/analytics/funnel/${step}`).catch((error) => {
    if (__DEV__) console.warn('[funnel] could not record', step, error);
  });
}
