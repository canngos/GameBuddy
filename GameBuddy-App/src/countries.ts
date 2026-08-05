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

export const COUNTRIES: string[] = [
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
];

export function searchCountries(query: string): string[] {
  const q = query.trim().toLowerCase();
  if (!q) return [...SUGGESTED_COUNTRIES, ...COUNTRIES.filter(notSuggested)];

  // Prefix matches first: typing "ind" should offer India before Indonesia, and both
  // before anything that merely contains "ind".
  const prefix: string[] = [];
  const contains: string[] = [];
  for (const country of COUNTRIES) {
    const lower = country.toLowerCase();
    if (lower.startsWith(q)) prefix.push(country);
    else if (lower.includes(q)) contains.push(country);
  }
  return [...prefix, ...contains];
}

const suggested = new Set<string>(SUGGESTED_COUNTRIES);
function notSuggested(country: string): boolean {
  return !suggested.has(country);
}
