/**
 * What the deck's paid actions cost, for labelling them.
 *
 * Display copies of the server's constants, never the authority: the backend charges what
 * `BoostPolicy` says, so if these drift a label is briefly wrong and nobody is charged the
 * wrong amount. Lived in `BoostButton.tsx` until the deck boost was retired — the rewind
 * price outlived the file it was sitting in.
 */

/** Coins a rewind costs off Gold. Mirrors BoostPolicy.REWIND_COST_COINS. */
export const REWIND_COST_COINS = 50;
