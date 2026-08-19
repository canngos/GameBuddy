import { useScreenScale } from "./useScreenScale";

/**
 * The standing air above a screen's headline, and above the actions at its foot.
 *
 * Roughly a dozen screens — every auth step, the onboarding forms, every edit screen —
 * open with a headline block pushed down by `pt-12` and close with an action block held
 * off by `pt-10`. Those two numbers are 48dp and 40dp: on a tall phone they are the
 * breathing room the design wants, and on a 640dp one they are nearly a seventh of the
 * viewport spent before the first word, which is what pushes the button under the fold.
 *
 * Kept here rather than copied into each screen because it is one decision wearing a
 * dozen hats. When these move, they move everywhere at once, which is the only reason
 * every one of those screens looks like it belongs to the same app today.
 *
 * Class strings rather than numbers, so a screen that needs its own value can still
 * override through `cn`, and the class *keys* (`pt-`) never change between tiers.
 */
export function useIntroPadding() {
  const { pick } = useScreenScale();

  return {
    /** Above a screen's headline block. */
    top: pick("pt-6", "pt-9", "pt-12"),
    /** Above the action block at the foot of a form. */
    footer: pick("pt-6", "pt-8", "pt-10"),
    /** The shared-component variant, which starts lower to begin with. */
    heading: pick("pt-4", "pt-6", "pt-8"),
  };
}
