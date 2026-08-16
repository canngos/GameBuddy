import type { Game } from '../api/types';
import type { PickerFilter, PickerItem } from './CataloguePicker';
import { PLATFORMS } from '../profile/platforms';

/**
 * The platform and genre filters for the game picker, and the facet values each game
 * carries.
 *
 * Shared by onboarding and by editing so the two screens cannot drift into offering
 * different filters over the same catalogue.
 */

/**
 * `OTHER` is not in {@link PLATFORMS}, deliberately — nobody is asked whether they play on
 * "Other" — but four catalogue games are tagged with it, so the filter has to offer it or
 * those games become unreachable by platform.
 */
const OTHER_PLATFORM = { id: 'OTHER', label: 'Other' };

/** Genres come from the data rather than a list here, so a new one cannot go unoffered. */
function genreOptions(games: Game[]): { id: string; label: string }[] {
  const counts = new Map<string, number>();
  for (const game of games) {
    if (game.category) {
      counts.set(game.category, (counts.get(game.category) ?? 0) + 1);
    }
  }
  // Commonest first. Twenty genres do not fit on a screen, and the order decides which
  // ones a thumb reaches without scrolling; alphabetical would put Adventure and Battle
  // Royale in front of FPS for no reason a player would recognise.
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    .map(([category]) => ({ id: category, label: category }));
}

export function gameFilters(games: Game[]): PickerFilter[] {
  const used = new Set(games.flatMap((game) => game.platforms ?? []));
  const platforms = [...PLATFORMS, OTHER_PLATFORM]
    // Keeps PLATFORMS' own order, which is roughly by how many players each has, and drops
    // any the catalogue happens not to use so the row never offers an empty result.
    .filter((platform) => used.has(platform.id))
    .map((platform) => ({ id: platform.id, label: platform.label }));

  const filters: PickerFilter[] = [];
  if (platforms.length > 1) {
    filters.push({ key: 'platform', label: 'Platform', options: platforms });
  }
  const genres = genreOptions(games);
  if (genres.length > 1) {
    filters.push({ key: 'genre', label: 'Genre', options: genres });
  }
  return filters;
}

/** One catalogue game as a picker row, with the facet values the filters read. */
export function toGameItem(game: Game): PickerItem {
  return {
    id: game.gameId,
    label: game.gameName,
    // The category is shown under the name, so it no longer needs to be duplicated into
    // `keywords` for the search to reach it — the picker searches `detail` too.
    detail: game.category,
    image: game.gameIcon || null,
    facets: {
      platform: game.platforms ?? [],
      genre: game.category ? [game.category] : [],
    },
  };
}
