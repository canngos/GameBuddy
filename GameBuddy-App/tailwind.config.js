/** @type {import('tailwindcss').Config} */

// Semantic colours resolve through CSS variables so `bg-surface` means one thing in
// light mode and another in dark, without every component carrying a `dark:` twin.
// The variables live in global.css. Brand colours stay literal — #FF4D67 is #FF4D67
// in both themes.
//
// `<alpha-value>` is Tailwind's placeholder; it is what lets `bg-surface/60` work,
// which a bare `var(--surface)` would not.
const themed = (variable) => `rgb(var(${variable}) / <alpha-value>)`;

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
        brand: {
          DEFAULT: '#FF4D67',
          soft: '#E98090',
          deep: '#D93E56',
        },
        ink: {
          500: '#23252F',
          700: '#1A1B22',
          900: '#131419',
        },

        // What screens should actually use.
        canvas: themed('--canvas'),
        surface: themed('--surface'),
        raised: themed('--raised'),
        line: themed('--line'),
        content: themed('--content'),
        muted: themed('--muted'),
        field: themed('--field'),
        'field-focus': themed('--field-focus'),
        danger: themed('--danger'),
        success: themed('--success'),
      },
      fontFamily: {
        // Families, not weights. Android ignores fontWeight for custom fonts and
        // silently renders the regular face, so the weight has to be the family name.
        sans: ['Poppins_400Regular'],
        medium: ['Poppins_500Medium'],
        semibold: ['Poppins_600SemiBold'],
        bold: ['Poppins_700Bold'],
      },
      borderRadius: {
        card: '20px',
        field: '14px',
      },
      spacing: {
        // The one-off that kept recurring: minimum comfortable touch target.
        touch: '52px',
      },
    },
  },
  plugins: [],
};
