import { View } from 'react-native';
import { useT } from '../i18n/useT';
import { cn } from '../ui/cn';
import { Text } from '../ui/Text';

type SelectionCountProps = {
  picked: number;
  minimum: number;
};

/**
 * Progress towards a minimum, for the two pick-from-a-list steps.
 *
 * Dots rather than "0 of 3" alone, because the requirement is the thing people miss:
 * a row of unfilled dots shows how many are still needed without being read.
 */
export function SelectionCount({ picked, minimum }: SelectionCountProps) {
  const t = useT();
  const remaining = minimum - picked;

  return (
    <View className="flex-row items-center justify-center gap-2">
      <View className="flex-row gap-1">
        {Array.from({ length: minimum }, (_, i) => (
          <View
            key={i}
            className={cn('h-1.5 w-1.5 rounded-full', i < picked ? 'bg-primary' : 'bg-line')}
          />
        ))}
      </View>
      <Text variant="caption">
        {remaining > 0 ? t.onboarding.moreToGo(remaining) : t.settings.selected(picked)}
      </Text>
    </View>
  );
}
