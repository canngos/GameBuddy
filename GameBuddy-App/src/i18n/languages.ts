/**
 * The seven languages, matching the website's catalogue exactly.
 *
 * The web dictionaries (`GameBuddy-Web/src/i18n`) were written with this in mind — their
 * keys are named for meaning rather than for where they sit on a page, precisely so the
 * copy could move here. English is the source language and the fallback.
 */
export const LANGS = ['en', 'fi', 'sv', 'de', 'fr', 'es', 'tr'] as const;

export type Lang = (typeof LANGS)[number];

export const DEFAULT_LANG: Lang = 'en';

/**
 * What each language calls itself.
 *
 * Never the English name: somebody who has accidentally set the app to Turkish is
 * looking for "Türkçe" in the list, not for "Turkish" — a word they may not recognise
 * in a language they cannot currently read.
 */
export const LANG_NAMES: Record<Lang, string> = {
  en: 'English',
  fi: 'Suomi',
  sv: 'Svenska',
  de: 'Deutsch',
  fr: 'Français',
  es: 'Español',
  tr: 'Türkçe',
};

export function isLang(value: string | null | undefined): value is Lang {
  return !!value && (LANGS as readonly string[]).includes(value);
}

/**
 * The phone's language, if the app speaks it.
 *
 * Read through `Intl` rather than `expo-localization`: the resolved locale is already
 * available — Hermes here ships full ICU, which is how the lobby renders time zones —
 * and adding a native module for one string would mean a new native build for every
 * developer and every CI runner.
 *
 * Only the base tag is used. A phone set to `de-AT` gets German, `sv-FI` gets Swedish;
 * regional variants of a language we translate are still that language, and refusing
 * them would drop an Austrian user into English for no reason.
 *
 * @returns the matching language, or null when the phone speaks something else — which
 *     is not a failure, it is the case English exists for
 */
export function deviceLang(): Lang | null {
  try {
    const locale = Intl.DateTimeFormat().resolvedOptions().locale;
    const base = locale?.split(/[-_]/)[0]?.toLowerCase();
    return isLang(base) ? base : null;
  } catch {
    // An environment without Intl is one that cannot tell us anything about the user.
    return null;
  }
}
