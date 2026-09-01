import { avatarGradient } from '../avatars';
import { useIsDark } from './colors';
// Plain JS, shared with scripts/check-contrast.js — see the file's own header.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { cardThemeStops, cardThemeAnimated } = require('./cardThemes');

/** What a gamer's card is painted in: their bought theme, or the colour of their id. */
export type GamerStops = readonly [string, string];

/**
 * The two stops to draw one gamer's card in.
 *
 * <p>Every gamer already has a colour — {@link avatarGradient} derives one from their user
 * id so no card is ever grey. A theme replaces it. That is the whole feature, and stating
 * it in one function is what keeps the four surfaces that draw a card (the deck, the
 * candidate sheet, the admirer tile and the profile header) from each deciding separately
 * and drifting apart.
 *
 * <p><strong>Identity is stable on both branches</strong>, which matters more than it
 * looks: the result goes straight into `GradientView`'s `colors` prop, and a fresh array
 * every render is a changed prop — enough to re-render every card in a list forever.
 * `avatarGradient` caches by seed; the theme table is frozen module state.
 *
 * <p>An unknown slug falls back to the identity gradient rather than to a house colour: an
 * app that has not shipped a newly sold theme yet should draw the card it always drew, not
 * paint every unrecognised theme the same.
 */
export function useGamerGradient(
  userId: string | null | undefined,
  themeSlug: string | null | undefined,
): GamerStops {
  const scheme = useIsDark() ? 'dark' : 'light';
  return cardThemeStops(themeSlug, scheme) ?? avatarGradient(userId ?? '?');
}

/**
 * The stops for a theme slug, or null when the app does not know it.
 *
 * <p>Null rather than a fallback, unlike {@link useGamerGradient}: a card border is drawn
 * only when there is a theme to draw, so an app that has not shipped a newly sold theme
 * should render no border at all rather than an arbitrary colour around somebody's card.
 */
export function useCardTheme(
  slug: string | null | undefined,
): { stops: GamerStops; animated: boolean } | null {
  const scheme = useIsDark() ? 'dark' : 'light';
  const stops = cardThemeStops(slug, scheme);
  return stops ? { stops, animated: cardThemeAnimated(slug) } : null;
}
