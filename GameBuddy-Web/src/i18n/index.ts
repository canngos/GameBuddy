/**
 * Seven languages, one dictionary each.
 *
 * ## Why a plain object rather than an i18n library
 *
 * There is no runtime here. Every page is rendered once at build time, so the whole job is
 * "pick a string by key" — and the libraries that do that well are solving problems this
 * site does not have: locale negotiation at request time, lazy-loading catalogues, plural
 * rules across a large surface. A typed object gives the one thing that actually matters at
 * this size, which is that **a missing key is a TypeScript error rather than a blank space
 * on a page nobody who speaks that language will ever check.**
 *
 * ## The languages
 *
 * English is the source and the default; it is the language the app itself is written in.
 * Finnish and Swedish because the launch market is Finland, which is officially bilingual.
 * German, French, Spanish and Turkish for reach — and because these translations are
 * intended to become the app's own catalogue later, which is why the keys are named for
 * meaning rather than for where they happen to appear.
 *
 * ## What is *not* translated
 *
 * The terms and the privacy policy. They stay in English on purpose — they are binding
 * documents, and a mistranslated liability or GDPR clause is a document that says something
 * nobody intended. `LegalNotice` states that plainly on the page rather than leaving a
 * visitor to discover it.
 */

export const LANGS = ['en', 'fi', 'sv', 'de', 'fr', 'es', 'tr'] as const;
export type Lang = (typeof LANGS)[number];

export const DEFAULT_LANG: Lang = 'en';

/** What each language calls itself. Never the English name — nobody looks for "Finnish". */
export const LANG_NAMES: Record<Lang, string> = {
  en: 'English',
  fi: 'Suomi',
  sv: 'Svenska',
  de: 'Deutsch',
  fr: 'Français',
  es: 'Español',
  tr: 'Türkçe',
};

import { de } from './de';
import { en } from './en';
import { es } from './es';
import { fi } from './fi';
import { fr } from './fr';
import { sv } from './sv';
import { tr } from './tr';

/**
 * Every dictionary is typed against the English one, so adding a key to `en.ts` breaks the
 * build until all six others have it. That is the point: the alternative is a site that
 * quietly renders `undefined` in Turkish for a month.
 */
export type Dictionary = typeof en;

const dictionaries: Record<Lang, Dictionary> = { en, fi, sv, de, fr, es, tr };

/** Dotted key lookup — `t(lang, 'hero.title')`. */
export function t(lang: Lang, key: DictKey): string {
  const parts = key.split('.');
  let value: unknown = dictionaries[lang];
  for (const part of parts) {
    value = (value as Record<string, unknown>)?.[part];
  }
  // Falling back to English rather than to the key itself. A visitor seeing one English
  // sentence among six translated ones has lost very little; a visitor seeing
  // "hero.subtitle" has been shown a bug.
  if (typeof value !== 'string') {
    let fallback: unknown = en;
    for (const part of parts) fallback = (fallback as Record<string, unknown>)?.[part];
    return typeof fallback === 'string' ? fallback : key;
  }
  return value;
}

/** The dictionary itself, for the places that iterate a list rather than read one string. */
export function dict(lang: Lang): Dictionary {
  return dictionaries[lang];
}

/** Every dotted path in the dictionary, as a union. This is what makes a typo a build error. */
type Paths<T> = T extends string
  ? []
  : { [K in keyof T]: [K, ...Paths<T[K]>] }[keyof T];
type Join<T extends unknown[]> = T extends [infer F]
  ? F
  : T extends [infer F, ...infer R]
    ? F extends string
      ? `${F}.${Join<R>}`
      : never
    : string;
export type DictKey = Join<Paths<Dictionary>>;

/**
 * The URL for a path in a given language.
 *
 * English has no prefix — `/support`, not `/en/support`. That is `prefixDefaultLocale: false`
 * in `astro.config.mjs`, and the reason is that the bare domain is what gets shared and
 * linked to; sending it to a redirect costs a round trip on the most common entry point.
 */
export function localeUrl(lang: Lang, path = ''): string {
  const clean = path.replace(/^\/+/, '');
  const prefix = lang === DEFAULT_LANG ? '' : `/${lang}`;
  return clean ? `${prefix}/${clean}` : prefix || '/';
}
