import { useLangStore } from './store';

/**
 * Uppercase, in the reader's language.
 *
 * `'i'.toUpperCase()` is `'I'` — correct in six of our seven languages and wrong in
 * Turkish, where the capital of a dotted i is `İ` and `I` is a different letter with its
 * own dotless lowercase `ı`. Turkish readers notice immediately: `iletisim` shouted as
 * `ILETISIM` reads as a misspelling, not as emphasis.
 *
 * This matters here because the `overline` type style is not styled uppercase in CSS —
 * the app uppercases the text itself before rendering, so the transform is ours to get
 * right rather than the platform's.
 */
export function useUpper(): (text: string) => string {
  const lang = useLangStore((s) => s.lang);
  return (text: string) => text.toLocaleUpperCase(lang);
}

/** The same, for the few places that already know the language and are not components. */
export function upper(text: string, lang: string): string {
  return text.toLocaleUpperCase(lang);
}
