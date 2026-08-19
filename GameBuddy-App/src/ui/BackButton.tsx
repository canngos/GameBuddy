import { useRouter } from 'expo-router';
import { ChevronLeft } from 'lucide-react-native';
import { Pressable } from 'react-native';
import { useT } from '../i18n/useT';
import { cn } from './cn';
import { Icon } from './Icon';

/**
 * The way back, as a chevron in the top-left corner.
 *
 * **This replaced a full-width "Back" button at the bottom of nine screens.** Sitting under
 * the primary action, it gave equal weight to the two least alike things on the screen —
 * finishing, and giving up — and on the onboarding steps it put "Back" directly beneath
 * "Continue", which is a mis-tap that costs somebody the answers they just gave. Going back
 * is not an action of the same kind as the one a screen exists for; it is chrome, and it
 * belongs where every other app on the phone puts it.
 *
 * The same chevron and the same hit target as {@link BackHeader}, which uses this — so the
 * screens that have a titled header and the screens that draw their own cannot drift apart.
 *
 * `hitSlop` rather than a bigger box: 40dp already meets the minimum, and the extra 12
 * forgives the thumb reaching into a corner.
 */
export function BackButton({ className }: { className?: string }) {
  const router = useRouter();
  const t = useT();

  return (
    <Pressable
      onPress={() => router.back()}
      accessibilityRole="button"
      accessibilityLabel={t.common.back}
      hitSlop={12}
      className={cn('h-10 w-10 items-center justify-center active:opacity-60', className)}
    >
      <Icon as={ChevronLeft} size={24} tone="content" />
    </Pressable>
  );
}
