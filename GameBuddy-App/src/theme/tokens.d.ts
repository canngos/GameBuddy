/**
 * Types for `tokens.js`.
 *
 * Hand-written because the source has to stay `.js` — see the comment at the top of that
 * file. Keep the two in step; there is nothing checking that for you.
 */

export type Scheme = 'light' | 'dark';

/** Semantic token names, as written in `tokens.js` and used as `--name` / `bg-name`. */
export type TokenName =
  | 'canvas'
  | 'surface'
  | 'raised'
  | 'elevated'
  | 'line'
  | 'content'
  | 'muted'
  | 'field'
  | 'field-focus'
  | 'primary'
  | 'accent'
  | 'gold'
  | 'online'
  | 'danger'
  | 'success';

export declare const brand: {
  readonly DEFAULT: string;
  readonly soft: string;
  readonly deep: string;
};

export declare const semantic: Record<TokenName, Record<Scheme, string>>;

/** `#rrggbb` → `"r g b"`. */
export declare function channels(hex: string): string;

/** Every token for one theme, as `--name: r g b` pairs. */
export declare function cssVars(scheme: Scheme): Record<string, string>;
