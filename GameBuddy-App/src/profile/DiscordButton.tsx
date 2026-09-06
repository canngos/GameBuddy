import { Pressable, View } from 'react-native';
import Svg, { Path } from 'react-native-svg';
import { Text } from '../ui';
import { cn } from '../ui/cn';

/**
 * Discord's own sign-in button.
 *
 * **Deliberately outside the design system**, which is the thing to understand before
 * touching it. Every other button draws from `src/theme/tokens.js` so `npm run check` can
 * gate its contrast; this one is Discord's asset, and their guidelines fix the colour
 * (#5865F2, "blurple"), the mark and the wording. A button that says "sign in with Discord"
 * in GameBuddy's violet would be our button wearing their name.
 *
 * The hard-coded hex is therefore not an oversight and must not be "corrected" to a token.
 * It is measured rather than assumed: white on #5865F2 is 4.61:1 and on the pressed
 * #4752C4 it is 6.42:1, both clearing AA for normal text — so the check the token gate would
 * have run has been done by hand and written down.
 *
 * Discord publishes no button image to bundle (unlike Valve, whose guidelines require their
 * supplied PNG), so being official here means reproducing the specification: their mark,
 * their blurple, their words.
 *
 * **The colour lives on the inner View, not on the Pressable.** A `style` callback on a
 * NativeWind-wrapped Pressable is silently dropped — the first version of this rendered a
 * white logo and white label on no background at all. `Button` splits the same way for a
 * related reason; see the note in `src/ui/Button.tsx`.
 */
const BLURPLE = '#5865F2';

/** The Discord mark, as they draw it. Not recoloured beyond the flat white they specify. */
const DISCORD_MARK =
  'M20.317 4.3698a19.7913 19.7913 0 00-4.8851-1.5152.0741.0741 0 00-.0785.0371c-.211.3753-.4447.8648-.6083 1.2495-1.8447-.2762-3.68-.2762-5.4868 0-.1636-.3933-.4058-.8742-.6177-1.2495a.077.077 0 00-.0785-.037 19.7363 19.7363 0 00-4.8852 1.515.0699.0699 0 00-.0321.0277C.5334 9.0458-.319 13.5799.0992 18.0578a.0824.0824 0 00.0312.0561c2.0528 1.5076 4.0413 2.4228 5.9929 3.0294a.0777.0777 0 00.0842-.0276c.4616-.6304.8731-1.2952 1.226-1.9942a.076.076 0 00-.0416-.1057c-.6528-.2476-1.2743-.5495-1.8722-.8923a.077.077 0 01-.0076-.1277c.1258-.0943.2517-.1923.3718-.2914a.0743.0743 0 01.0776-.0105c3.9278 1.7933 8.18 1.7933 12.0614 0a.0739.0739 0 01.0785.0095c.1202.099.246.1981.3728.2924a.077.077 0 01-.0066.1276 12.2986 12.2986 0 01-1.873.8914.0766.0766 0 00-.0407.1067c.3604.698.7719 1.3628 1.225 1.9932a.076.076 0 00.0842.0286c1.961-.6067 3.9495-1.5219 6.0023-3.0294a.077.077 0 00.0313-.0552c.5004-5.177-.8382-9.6739-3.5485-13.6604a.061.061 0 00-.0312-.0286zM8.02 15.3312c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9555-2.4189 2.157-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.9555 2.4189-2.1569 2.4189zm7.9748 0c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9554-2.4189 2.1569-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.946 2.4189-2.1568 2.4189Z';

export function DiscordButton({
  label,
  onPress,
  disabled = false,
}: {
  label: string;
  onPress: () => void;
  disabled?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled }}
      // Press feedback as a class, like Button does — the darker blurple Discord specifies
      // is not expressible here, so the standard dim stands in for it.
      className={cn('rounded-full', disabled ? 'opacity-40' : 'active:opacity-90')}
    >
      <View
        className="min-h-touch flex-row items-center justify-center gap-3 rounded-full px-6 py-4"
        style={{ backgroundColor: BLURPLE }}
      >
        <Svg width={22} height={22} viewBox="0 0 24 24">
          <Path d={DISCORD_MARK} fill="#FFFFFF" />
        </Svg>
        {/* White explicitly rather than through a token: the ground is Discord's blurple in
            both themes, so a colour that flips with the app's theme would be wrong in one. */}
        <Text variant="button" style={{ color: '#FFFFFF' }}>
          {label}
        </Text>
      </View>
    </Pressable>
  );
}
