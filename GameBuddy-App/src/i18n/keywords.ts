import type { Keyword } from '../api/types';
import type { Dictionary } from './dictionaries/en';

/**
 * A keyword's explanation, in the language in force.
 *
 * The catalogue is served from a table with one `description` column and no notion of a
 * locale, so the backend can only ever send one language — English. Every other screen in
 * the app follows the language switch, and the keyword picker is a forty-eight row list, so
 * it was the most English thing a gamer reading in Turkish or Finnish ever saw.
 *
 * Translated by `keywordName` because that is the keyword's identity: it is what the picker
 * displays, what the backend matches on, and the key both migrations write against. The
 * descriptions themselves are prose and get reworded; the name does not.
 *
 * **The server's text is the fallback, not an error.** Keywords are data, and a build meets
 * whatever the catalogue holds — including keywords added after it shipped. Those render in
 * English, which is worse than the dictionary and much better than an empty second line.
 *
 * Names are matched case-insensitively, the same way `upgrade-2026-16` writes them, so a
 * row capitalised differently in one environment still finds its copy.
 */
export function keywordDetail(t: Dictionary, keyword: Keyword): string | null {
  const translated = (t.keywords.descriptions as Record<string, string | undefined>)[
    keyword.keywordName.toLowerCase()
  ];
  return translated ?? keyword.description;
}
