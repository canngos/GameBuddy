import {
  Poppins_400Regular,
  Poppins_500Medium,
  Poppins_600SemiBold,
  Poppins_700Bold,
} from '@expo-google-fonts/poppins';

/**
 * The map handed to `useFonts`. Four weights, not the eighteen the old Android app
 * shipped in `res/font/` — the rest were never referenced by a layout.
 *
 * The keys are the family names Tailwind's `font-*` utilities resolve to; see the
 * `fontFamily` block in tailwind.config.js. They have to stay in step.
 */
export const fontAssets = {
  Poppins_400Regular,
  Poppins_500Medium,
  Poppins_600SemiBold,
  Poppins_700Bold,
};
