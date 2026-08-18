/**
 * Countries offered during onboarding.
 *
 * A picker rather than a text field. The column is free-form `varchar(255)`, so
 * nothing server-side stops "usa", "U.S.A." and "United States" all existing at once
 * — and the moment we want to show someone's country next to their name, or filter on
 * it, that mess has to be untangled retroactively. Constraining the input is much
 * cheaper than cleaning it later.
 *
 * `SUGGESTED_COUNTRIES` is only a convenience ordering for the top of the picker —
 * home market first, then the largest English-speaking and European gaming markets.
 * It is not a distribution plan and nothing downstream reads it: the app ships
 * worldwide, and `COUNTRIES` below is the actual set anyone can choose from.
 *
 * Deliberately *not* copied from `gamebuddy_model/catalogue.py`, whose weighting
 * describes the synthetic population the recommender was evaluated against, not where
 * real users are.
 */
import { countryName } from './i18n/countryNames';
import type { Lang } from './i18n/languages';

export const SUGGESTED_COUNTRIES = [
  'Finland',
  'Sweden',
  'Norway',
  'Denmark',
  'Germany',
  'United Kingdom',
  'United States',
  'Netherlands',
  'France',
  'Poland',
  'Spain',
  'Italy',
  'Canada',
  'Brazil',
  'Australia',
] as const;

export const COUNTRIES = [
  'Afghanistan', 'Albania', 'Algeria', 'Andorra', 'Angola', 'Argentina', 'Armenia',
  'Australia', 'Austria', 'Azerbaijan', 'Bahamas', 'Bahrain', 'Bangladesh', 'Barbados',
  'Belarus', 'Belgium', 'Belize', 'Benin', 'Bhutan', 'Bolivia', 'Bosnia and Herzegovina',
  'Botswana', 'Brazil', 'Brunei', 'Bulgaria', 'Burkina Faso', 'Burundi', 'Cambodia',
  'Cameroon', 'Canada', 'Cape Verde', 'Chad', 'Chile', 'China', 'Colombia', 'Comoros',
  'Costa Rica', 'Croatia', 'Cuba', 'Cyprus', 'Czechia', 'Denmark', 'Djibouti',
  'Dominican Republic', 'Ecuador', 'Egypt', 'El Salvador', 'Estonia', 'Eswatini',
  'Ethiopia', 'Fiji', 'Finland', 'France', 'Gabon', 'Gambia', 'Georgia', 'Germany',
  'Ghana', 'Greece', 'Guatemala', 'Guinea', 'Guyana', 'Haiti', 'Honduras', 'Hungary',
  'Iceland', 'India', 'Indonesia', 'Iran', 'Iraq', 'Ireland', 'Israel', 'Italy',
  'Ivory Coast', 'Jamaica', 'Japan', 'Jordan', 'Kazakhstan', 'Kenya', 'Kosovo',
  'Kuwait', 'Kyrgyzstan', 'Laos', 'Latvia', 'Lebanon', 'Liberia', 'Libya',
  'Liechtenstein', 'Lithuania', 'Luxembourg', 'Madagascar', 'Malawi', 'Malaysia',
  'Maldives', 'Mali', 'Malta', 'Mauritania', 'Mauritius', 'Mexico', 'Moldova',
  'Monaco', 'Mongolia', 'Montenegro', 'Morocco', 'Mozambique', 'Myanmar', 'Namibia',
  'Nepal', 'Netherlands', 'New Zealand', 'Nicaragua', 'Niger', 'Nigeria',
  'North Macedonia', 'Norway', 'Oman', 'Pakistan', 'Palestine', 'Panama',
  'Papua New Guinea', 'Paraguay', 'Peru', 'Philippines', 'Poland', 'Portugal', 'Qatar',
  'Romania', 'Russia', 'Rwanda', 'Saudi Arabia', 'Senegal', 'Serbia', 'Seychelles',
  'Sierra Leone', 'Singapore', 'Slovakia', 'Slovenia', 'Somalia', 'South Africa',
  'South Korea', 'South Sudan', 'Spain', 'Sri Lanka', 'Sudan', 'Suriname', 'Sweden',
  'Switzerland', 'Syria', 'Taiwan', 'Tajikistan', 'Tanzania', 'Thailand', 'Togo',
  'Trinidad and Tobago', 'Tunisia', 'Turkey', 'Turkmenistan', 'Uganda', 'Ukraine',
  'United Arab Emirates', 'United Kingdom', 'United States', 'Uruguay', 'Uzbekistan',
  'Venezuela', 'Vietnam', 'Yemen', 'Zambia', 'Zimbabwe',
] as const;

/**
 * The canonical set, as a type. `src/i18n/countryNames.ts` types each language's map
 * against this, so adding a country here is a build error there until every language
 * names it — the same lockstep the dictionaries enforce.
 */
export type CountryName = (typeof COUNTRIES)[number];

/**
 * Search and ordering in the reader's language, over canonical English values.
 *
 * Matches the localized name *and* the English one — someone may know either ("Deu…"
 * and "Ger…" both find Deutschland) — and always returns the canonical English names,
 * because those are what the API stores; rendering localizes at display time.
 */
export function searchCountries(query: string, lang: Lang): CountryName[] {
  const q = query.trim().toLowerCase();
  if (!q) {
    // Suggested stay pinned in their curated order; the rest collate by localized name.
    const rest = COUNTRIES.filter(notSuggested).sort((a, b) =>
      countryName(a, lang).localeCompare(countryName(b, lang), lang),
    );
    return [...SUGGESTED_COUNTRIES, ...rest];
  }

  // Prefix matches first: typing "ind" should offer India before Indonesia, and both
  // before anything that merely contains "ind".
  const prefix: CountryName[] = [];
  const contains: CountryName[] = [];
  for (const country of COUNTRIES) {
    const en = country.toLowerCase();
    const local = countryName(country, lang).toLocaleLowerCase(lang);
    if (en.startsWith(q) || local.startsWith(q)) prefix.push(country);
    else if (en.includes(q) || local.includes(q)) contains.push(country);
  }
  return [...prefix, ...contains];
}

const suggested = new Set<string>(SUGGESTED_COUNTRIES);
function notSuggested(country: string): boolean {
  return !suggested.has(country);
}
