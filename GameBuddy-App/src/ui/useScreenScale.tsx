import { createContext, useContext, useMemo, type ReactNode } from "react";
import { PixelRatio, useWindowDimensions } from "react-native";

/**
 * How much room this device has, decided once for the whole app.
 *
 * **Chrome that does not scale with the screen is what makes a small phone look broken.** The
 * deck learned this the hard way: the card shrank on a 640dp device while the Pass/Match row
 * kept its full size, so the controls took a third of the height and the card they belong to
 * looked like a thumbnail beneath them. The tab bar had the same problem — a fixed 64dp bar is
 * a tenth of a tall phone and a sixth of a short one.
 *
 * Each piece is individually reasonable at its designed size; the *composition* is what breaks.
 * So the tier is decided here, once, and every surface derives its own numbers from it. Nothing
 * shares a magic number, and nothing is left behind when the thresholds move.
 *
 * **Tiers rather than one multiplier**, deliberately. Multiplying everything by a single factor
 * keeps the proportions identical and takes body text down with it, which on a small phone is
 * the one thing that must not shrink. Stepping sizes down while the type scale holds keeps the
 * composition and the readability.
 *
 * **Provided, not computed per component.** `Text` alone has ~68 importers, and
 * `useWindowDimensions` subscribes to dimension events per call — hundreds of subscriptions to
 * answer a question whose answer is the same for all of them. The provider subscribes once and
 * everything else reads context.
 *
 * Divided by the font scale, so somebody running large text gets the tighter layout on a phone
 * that would otherwise be comfortable — their text is bigger everywhere, which is the same
 * shortfall arriving by a different route. The OS setting is honoured, never overridden.
 */

/** Below this the app stops using its roomiest sizes. A typical 5.8" phone is 760dp. */
const COMPACT_HEIGHT = 800;

/** Below this it goes tighter again — small phones, and large-text users on ordinary ones. */
const TIGHT_HEIGHT = 700;

export type ScreenScale = {
  /** Anything below the roomiest tier. */
  compact: boolean;
  /** The tightest tier. */
  tight: boolean;
  /**
   * Screen height in dp, divided by the font scale.
   *
   * The raw number the tiers are cut from, for the rare surface that needs a finer answer
   * than three steps. The deck's card is the one that does: at font scale 1.3 a 640dp phone
   * comes out at 492 effective, which is *far* below the 700 the tight tier was drawn for,
   * and its rows of pills were clipped mid-pill rather than dropping one.
   *
   * Reach for `pick` first. A surface reading this number is choosing to own a threshold of
   * its own, and every one of those is a place the app can stop agreeing with itself.
   */
  effective: number;
  /**
   * Choose a value per tier, tightest first.
   *
   * Reads as three columns and keeps the three variants of a measurement on one line, which is
   * the only way they stay in proportion to each other as they are tuned.
   */
  pick: <T>(tight: T, compact: T, roomy: T) => T;
};

/**
 * The roomiest tier.
 *
 * A default rather than a thrown error, so a component rendered outside the provider — a test,
 * a Storybook-style gallery screen — draws at full size instead of crashing. Nothing in the app
 * renders outside it; this is a safety net, not a supported mode.
 */
const ROOMY: ScreenScale = {
  compact: false,
  tight: false,
  effective: COMPACT_HEIGHT,
  pick: (_tight, _compact, roomy) => roomy,
};

const ScreenScaleContext = createContext<ScreenScale>(ROOMY);

export function ScreenScaleProvider({ children }: { children: ReactNode }) {
  const { height } = useWindowDimensions();

  const value = useMemo<ScreenScale>(() => {
    const effective = height / PixelRatio.getFontScale();
    const tight = effective < TIGHT_HEIGHT;
    const compact = effective < COMPACT_HEIGHT;

    return {
      compact,
      tight,
      effective,
      pick: <T,>(t: T, c: T, r: T): T => (tight ? t : compact ? c : r),
    };
    // Rotation and the large-text setting both land here as a height change, which is exactly
    // when every size in the app should be reconsidered.
  }, [height]);

  return (
    <ScreenScaleContext.Provider value={value}>
      {children}
    </ScreenScaleContext.Provider>
  );
}

export function useScreenScale(): ScreenScale {
  return useContext(ScreenScaleContext);
}
