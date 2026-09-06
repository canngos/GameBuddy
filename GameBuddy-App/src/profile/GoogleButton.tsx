import { Pressable, View } from 'react-native';
import Svg, { Path } from 'react-native-svg';
import { Text } from '../ui';
import { cn } from '../ui/cn';

/**
 * Google's own sign-in button.
 *
 * **Deliberately outside the design system**, exactly like {@link DiscordButton} beside it
 * and for the same reason: Google's identity guidelines fix the mark, the wording ("Sign in
 * with Google") and the two permitted button styles. A button that says Google's name in
 * GameBuddy's violet would be our button wearing their brand.
 *
 * This is the light variant — white surface, #747775 border, #1F1F1F label — which Google
 * permits on any background and which is the one that stays legible on both of this app's
 * themes. The dark variant would need the ground behind it to be dark, and the welcome
 * screen is not always. Measured rather than assumed: #1F1F1F on white is 16.1:1.
 *
 * The four-colour "G" is reproduced exactly. Google's guidelines forbid recolouring it, so
 * this is the one mark in the app that does not take a theme tint — and the reason the paths
 * are four separate fills rather than one.
 *
 * **The surface lives on the inner View, not on the Pressable.** A `style` callback on a
 * NativeWind-wrapped Pressable is silently dropped; DiscordButton's header records the same
 * trap, which cost a render of white-on-nothing once already.
 */
const BORDER = '#747775';
const LABEL = '#1F1F1F';

/** The Google "G", in the four brand colours. Never recoloured, never flattened. */
const G_BLUE =
  'M23.52 12.2727C23.52 11.4218 23.4436 10.6036 23.3018 9.81818H12V14.46H18.4582C18.18 15.96 17.3345 17.2309 16.0636 18.0818V21.0955H19.9418C22.2109 19.0055 23.52 15.9273 23.52 12.2727Z';
const G_GREEN =
  'M12 24C15.24 24 17.9564 22.9255 19.9418 21.0955L16.0636 18.0818C14.9891 18.8018 13.6145 19.2273 12 19.2273C8.87455 19.2273 6.22909 17.1164 5.28546 14.28H1.27637V17.3918C3.25091 21.3109 7.30909 24 12 24Z';
const G_YELLOW =
  'M5.28545 14.28C5.04545 13.56 4.90909 12.7909 4.90909 12C4.90909 11.2091 5.04545 10.44 5.28545 9.72V6.60818H1.27636C0.463636 8.22818 0 10.0609 0 12C0 13.9391 0.463636 15.7718 1.27636 17.3918L5.28545 14.28Z';
const G_RED =
  'M12 4.77273C13.7618 4.77273 15.3436 5.37818 16.5873 6.56727L20.0291 3.12545C17.9509 1.18636 15.2345 0 12 0C7.30909 0 3.25091 2.68909 1.27637 6.60818L5.28546 9.72C6.22909 6.88364 8.87455 4.77273 12 4.77273Z';

export function GoogleButton({
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
    >
      {({ pressed }) => (
        <View
          className={cn(
            'h-touch flex-row items-center justify-center gap-3 rounded-full border',
            disabled && 'opacity-50',
          )}
          style={{
            backgroundColor: pressed ? '#F2F2F2' : '#FFFFFF',
            borderColor: BORDER,
          }}
        >
          <Svg width={18} height={18} viewBox="0 0 24 24">
            <Path d={G_BLUE} fill="#4285F4" />
            <Path d={G_GREEN} fill="#34A853" />
            <Path d={G_YELLOW} fill="#FBBC05" />
            <Path d={G_RED} fill="#EA4335" />
          </Svg>
          <Text variant="button" style={{ color: LABEL }}>
            {label}
          </Text>
        </View>
      )}
    </Pressable>
  );
}
