import {
  ChakraPetch_500Medium,
  ChakraPetch_600SemiBold,
  ChakraPetch_700Bold,
} from '@expo-google-fonts/chakra-petch';
import {
  Poppins_400Regular,
  Poppins_500Medium,
  Poppins_600SemiBold,
  Poppins_700Bold,
} from '@expo-google-fonts/poppins';

/**
 * The map handed to `useFonts`.
 *
 * Two families, doing different jobs. **Poppins is what you read** — body copy, labels,
 * hints, every string long enough to be a sentence. **Chakra Petch is what you notice** —
 * screen titles, the match moment, and all numerals.
 *
 * The split is the point. Chakra Petch is angular and squared-off, which is what makes it
 * read as a games product rather than a social app with a pink button; it is also what
 * makes it tiring at 13px across a paragraph. A display face on six things is a brand, and
 * on everything is a costume.
 *
 * Numerals get it too, and that is not decoration: coin balances and stat counts tick
 * while you watch them, and Chakra Petch's figures are near enough to tabular that a
 * changing number does not shove its neighbours around.
 *
 * The keys are the family names Tailwind's `font-*` utilities resolve to; see the
 * `fontFamily` block in tailwind.config.js. They have to stay in step, and so does the
 * `font-family` class group in `src/ui/cn.ts`.
 */
export const fontAssets = {
  Poppins_400Regular,
  Poppins_500Medium,
  Poppins_600SemiBold,
  Poppins_700Bold,
  ChakraPetch_500Medium,
  ChakraPetch_600SemiBold,
  ChakraPetch_700Bold,
};
