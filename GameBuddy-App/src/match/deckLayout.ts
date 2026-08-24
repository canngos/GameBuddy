import { useScreenScale } from "../ui/useScreenScale";

/**
 * How much room the deck has, as one decision.
 *
 * **Every part of the Home screen has to agree about this, or the screen stops looking like
 * one screen.** The card used to shrink on a small phone while the Pass/Match/Super row kept
 * its full size, so on a 640dp device the controls took a third of the height and the card
 * they belong to looked like a thumbnail underneath them. Each piece was individually
 * reasonable and the composition was wrong.
 *
 * So the tier is computed once and everything reads its sizes from here: the portrait and
 * padding inside {@link CandidateCard}, the button diameters in {@link DeckActions}, and the
 * gutter around the card in the Home screen itself. Changing how the deck scales is one edit
 * in one file, and nothing can be left behind.
 *
 * The tier itself comes from `useScreenScale`, which decides it once for the whole app.
 * Everything below is the deck's own interpretation of that tier.
 */
export type DeckLayout = {
  /** Anything below the roomiest tier. */
  compact: boolean;
  /** The tightest tier, for genuinely small phones and large-text users. */
  tight: boolean;
  /** Avatar diameter on the card's identity block. */
  portrait: number;
  /** Vertical padding around that identity block. */
  portraitPadding: number;
  /** Space between the portrait and the name. */
  nameGap: number;
  /** Monogram type, for accounts with no photo. */
  monogram: { fontSize: number; lineHeight: number };
  /** How many pills each list keeps before collapsing the rest into a "+N". */
  maxGames: number;
  maxKeywords: number;
  /** The Match button. The other two are sized against it. */
  matchButton: number;
  /** Pass, and Super Like. */
  sideButton: number;
  /** Undo, which is deliberately the smallest of the four. */
  rewindButton: number;
  /** Padding above and below the action row, and the gap between its buttons. */
  actionsPaddingY: number;
  actionsGap: number;
  /** Vertical gutter between the header and the card, and the card and the actions. */
  cardGutter: number;
  /** Offset of the swipe stamps from the card's top edge. */
  stampTop: number;
  /** Stamp type metrics. Inline style, not a class - the value changes with the device. */
  stampType: { fontSize: number; lineHeight: number };
};

export function useDeckLayout(): DeckLayout {
  // The tier itself is decided app-wide — see `useScreenScale`. This file owns the deck's
  // numbers, not the decision, so the deck can never disagree with the tab bar about how
  // small the screen is.
  const { compact, tight, effective, pick } = useScreenScale();

  /*
   * One pill row each, when even the tight tier is not tight enough.
   *
   * The three tiers are cut from screen height divided by the font scale, and the tight one
   * was drawn for a 640dp phone at normal text. Turn the system text size up to 1.3 on that
   * same phone and the effective height is 492 — as far below tight as tight is below roomy —
   * and the rows are taller as well as fewer. The card clipped its Style pills through the
   * middle, which reads as a rendering fault rather than a list that had to be shortened.
   *
   * Dropping to one pill per list buys back a whole row and the "+N" already says what was
   * left out, which is the same bargain the tiers make everywhere else: show less, cleanly,
   * rather than everything, broken.
   */
  const veryTight = effective < 560;

  return {
    compact,
    tight,
    portrait: pick(72, 96, 128),
    portraitPadding: pick(14, 24, 40),
    nameGap: pick(4, 8, 16),
    monogram: pick(
      { fontSize: 26, lineHeight: 32 },
      { fontSize: 34, lineHeight: 40 },
      { fontSize: 44, lineHeight: 52 },
    ),
    maxGames: veryTight ? 1 : pick(2, 2, 3),
    maxKeywords: veryTight ? 1 : pick(2, 2, 3),
    matchButton: pick(56, 64, 72),
    sideButton: pick(46, 52, 60),
    rewindButton: pick(40, 48, 56),
    actionsPaddingY: pick(8, 12, 20),
    actionsGap: pick(14, 18, 24),
    cardGutter: pick(4, 8, 12),
    // The swipe stamps were the one deck element with no tier awareness - 24/30 type at
    // top: 28 on every phone. Roomy keeps those exact numbers, so nothing moves on the
    // devices that were already right.
    stampTop: pick(14, 20, 28),
    stampType: pick(
      { fontSize: 20, lineHeight: 25 },
      { fontSize: 22, lineHeight: 28 },
      { fontSize: 24, lineHeight: 30 },
    ),
  };
}
