import { View } from 'react-native';
import { Screen } from './Screen';
import { Text } from './Text';

type ComingSoonProps = {
  overline: string;
  title: string;
  /** What this tab will do, in the user's terms. */
  blurb: string;
  /** The pieces still to build. Kept concrete so the screen is not a shrug. */
  items: string[];
  /** Shown when something outside this screen has to happen first. */
  blockedBy?: string;
};

/**
 * A tab that exists in the navigation but is not built yet.
 *
 * Deliberately not a mock-up. A screen full of placeholder rows looks finished in a
 * screenshot and then misleads everyone — including whoever picks the work up — about
 * how much is left. This says what is missing, in order.
 */
export function ComingSoon({ overline, title, blurb, items, blockedBy }: ComingSoonProps) {
  return (
    <Screen scroll edges={['top']}>
      <View className="gap-2 pb-8 pt-8">
        <Text variant="overline">{overline}</Text>
        <Text variant="title">{title}</Text>
        <Text variant="body" className="text-muted">
          {blurb}
        </Text>
      </View>

      <View className="gap-3 rounded-card bg-raised p-5">
        <Text variant="label" className="text-muted">
          Still to build
        </Text>
        {items.map((item) => (
          <View key={item} className="flex-row gap-3">
            <View className="mt-2 h-1.5 w-1.5 rounded-full bg-brand" />
            <Text variant="body" className="flex-1">
              {item}
            </Text>
          </View>
        ))}
      </View>

      {blockedBy && (
        <View className="mt-4 rounded-card border border-brand/30 bg-brand/5 p-4">
          <Text variant="caption">{blockedBy}</Text>
        </View>
      )}
    </Screen>
  );
}
