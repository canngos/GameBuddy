import { Check } from 'lucide-react-native';
import { Pressable, View } from 'react-native';
import { useThemeColors } from '../theme';
import { Icon } from '../ui/Icon';
import { Text } from '../ui/Text';
import { PlatformIcon } from './PlatformIcon';
import { PLATFORMS, type PlatformId } from './platforms';

type PlatformPickerProps = {
  selected: string[];
  onToggle: (id: PlatformId) => void;
};

/**
 * The five platforms, as toggles.
 *
 * **Multi-select, and it looks like one.** Every row is independently on or off with a
 * checkbox rather than a radio, because the answer genuinely is a set — somebody on PC and
 * Switch has to be able to say so, and a control that looks single-choice would teach them
 * they cannot before they try.
 *
 * Rendered from a fixed list rather than a fetched catalogue, unlike games and keywords:
 * there are five, they do not change, and a request would only add a loading state to a
 * screen that has nothing to wait for.
 */
export function PlatformPicker({ selected, onToggle }: PlatformPickerProps) {
  const colors = useThemeColors();

  return (
    <View className="gap-2">
      {PLATFORMS.map((platform) => {
        const on = selected.includes(platform.id);
        return (
          <Pressable
            key={platform.id}
            onPress={() => onToggle(platform.id)}
            accessibilityRole="checkbox"
            accessibilityState={{ checked: on }}
            accessibilityLabel={platform.label}
            className={[
              'flex-row items-center gap-3 rounded-card border p-4',
              on ? 'border-primary bg-primary/10' : 'border-line bg-raised',
            ].join(' ')}
          >
            {/* Takes the row's own colour rather than carrying its own: one drawing
                serves both states, and the icon and the label can never disagree about
                whether the row is selected. */}
            <PlatformIcon
              platform={platform.id}
              color={on ? colors.primary : colors.muted}
              size={26}
            />
            <Text
              variant="body"
              className={on ? 'flex-1 font-semibold text-primary' : 'flex-1'}
            >
              {platform.label}
            </Text>

            {/* A box, not a tick that appears from nowhere: the empty square is what says
                the others can be chosen too. */}
            <View
              className={[
                'h-6 w-6 items-center justify-center rounded-md border',
                on ? 'border-primary bg-primary' : 'border-line',
              ].join(' ')}
            >
              {on && <Icon as={Check} size={15} tone="inverse" strokeWidth={3} />}
            </View>
          </Pressable>
        );
      })}
    </View>
  );
}
