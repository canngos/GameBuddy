/** @type {import('tailwindcss').Config} */

// Semantic colours resolve through CSS variables so `bg-surface` means one thing in
// light mode and another in dark, without every component carrying a `dark:` twin.
// Brand colours stay literal — #FF4D67 is #FF4D67 in both themes.
//
// `<alpha-value>` is Tailwind's placeholder; it is what lets `bg-surface/60` work,
// which a bare `var(--surface)` would not.
const plugin = require('tailwindcss/plugin');
const tokens = require('./src/theme/tokens');

const themed = (variable) => `rgb(var(${variable}) / <alpha-value>)`;

// The token names, mapped to the var() form. Generated rather than listed, so adding a
// token to src/theme/tokens.js is the only edit adding a token needs.
const semanticColors = Object.fromEntries(
  Object.keys(tokens.semantic).map((name) => [name, themed(`--${name}`)]),
);

// The `:root` / `.dark:root` blocks that used to be written by hand in global.css.
// Emitted here so they cannot drift from the JS palette in src/theme/colors.ts, which is
// generated from the same file.
//
// This only lands because `@tailwind base` is still the first line of global.css. Do not
// remove it.
const tokenVars = plugin(({ addBase }) => {
  addBase({
    ':root': tokens.cssVars('light'),
    '.dark:root': tokens.cssVars('dark'),
  });
});

module.exports = {
  content: ['./app/**/*.{ts,tsx}', './src/**/*.{ts,tsx}'],
  presets: [require('nativewind/preset')],
  // Driven by the app's own theme setting rather than the OS, so the in-app override
  // works. See src/theme/scheme.ts.
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // Carried over from the original Android app's colors.xml.
        brand: tokens.brand,
        ink: {
          500: '#23252F',
          700: '#1A1B22',
          900: '#131419',
        },

        // What screens should actually use. See src/theme/tokens.js.
        ...semanticColors,
      },
      fontFamily: {
        // Families, not weights. Android ignores fontWeight for custom fonts and
        // silently renders the regular face, so the weight has to be the family name.
        sans: ['Poppins_400Regular'],
        medium: ['Poppins_500Medium'],
        semibold: ['Poppins_600SemiBold'],
        bold: ['Poppins_700Bold'],

        // The display face. Same families-not-weights rule — `font-display-bold`, never
        // `font-display font-bold`, which would silently render the medium cut on Android.
        // Keep in step with src/theme/typography.ts and the class groups in src/ui/cn.ts.
        display: ['ChakraPetch_500Medium'],
        'display-semibold': ['ChakraPetch_600SemiBold'],
        'display-bold': ['ChakraPetch_700Bold'],
      },
      borderRadius: {
        // Softened with the redesign. The old 20/14 read as "app chrome"; a little more
        // curvature reads as a surface you could pick up, which is what the deck card and
        // the market shelves need. Anything past this starts looking like a toy.
        card: '24px',
        field: '16px',
      },
      spacing: {
        // The one-off that kept recurring: minimum comfortable touch target.
        touch: '52px',
      },
    },
  },
  plugins: [tokenVars],
};
