import { Pressable, View } from 'react-native';
import { Text } from './Text';

/**
 * A row of two or more pills, one of which is on.
 *
 * Lifted out of the Market when the Inventory needed the same Frames/Banners switch. It was
 * already used twice on that one screen — Earn/Shop and Frames/Banners — so a third copy in
 * a second file was the point at which two of them would eventually stop matching.
 *
 * Not a tab bar and not a radio group: it switches what a section *shows* without navigating
 * and without submitting anything, which is why it announces itself as a button carrying
 * `selected` rather than as a tab.
 */

export function SegmentRow({ children }: { children: React.ReactNode }) {
  return <View className="flex-row gap-2">{children}</View>;
}

export function Segment({
  label,
  active,
  onPress,
}: {
  label: string;
  active: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected: active }}
      // Both branches carry the same class keys and only the values move. A class that
      // appears on one state and not the other stops NativeWind painting the subtree —
      // see `src/ui/hairline.ts`, which is the same defect twice over.
      className={
        active
          ? 'flex-1 items-center rounded-full bg-primary py-2.5'
          : 'flex-1 items-center rounded-full bg-raised py-2.5'
      }
    >
      <Text variant="label" className={active ? 'text-white' : 'text-muted'}>
        {label}
      </Text>
    </Pressable>
  );
}
