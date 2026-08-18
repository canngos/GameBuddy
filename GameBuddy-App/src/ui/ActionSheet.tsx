import { Pressable, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useT } from '../i18n/useT';
import { Button } from './Button';
import { Card } from './Card';
import { Text } from './Text';

export type SheetAction = {
  label: string;
  onPress: () => void;
  /** Destructive actions are drawn in danger red; everything else is a ghost row. */
  destructive?: boolean;
};

type ActionSheetProps = {
  title?: string;
  actions: SheetAction[];
  onCancel: () => void;
};

/**
 * A list of actions over a dimmed screen.
 *
 * **The generic sibling of `ReportSheet`, and absolutely positioned for the same reason:**
 * Reanimated's entering animations do not run inside a `Modal` on Android, and a sibling
 * that covers the screen behaves identically without that trap. Mount it as the last child
 * of a `Screen` — inside a ScrollView's content view, "the whole screen" means the whole
 * scrollable content and the sheet scrolls away with the page.
 *
 * Exists so a profile does not have to spell out every rarely-wanted action as a button in
 * the layout. Report and block are things somebody needs once and needs to find instantly;
 * a stack of red buttons under every profile made the common case — looking at a person —
 * read as a moderation form.
 */
export function ActionSheet({ title, actions, onCancel }: ActionSheetProps) {
  const t = useT();
  // The overlay covers the safe area too, so the sheet has to hold itself clear of the
  // navigation bar; without this Cancel sits under a three-button Android layout.
  const insets = useSafeAreaInsets();

  return (
    <View className="absolute inset-0 justify-end bg-black/50">
      {/* Tapping the dimmed area dismisses, which is what everyone tries first. */}
      <Pressable
        className="flex-1"
        onPress={onCancel}
        accessibilityRole="button"
        accessibilityLabel={t.common.cancel}
      />
      <View className="p-4" style={{ paddingBottom: insets.bottom + 16 }}>
        <Card className="gap-2">
          {!!title && (
            <Text variant="bodyStrong" className="pb-1">
              {title}
            </Text>
          )}

          {actions.map((action) => (
            <Button
              key={action.label}
              label={action.label}
              variant={action.destructive ? 'danger' : 'ghost'}
              size="md"
              onPress={action.onPress}
            />
          ))}

          <Button label={t.common.cancel} variant="secondary" size="md" onPress={onCancel} />
        </Card>
      </View>
    </View>
  );
}
