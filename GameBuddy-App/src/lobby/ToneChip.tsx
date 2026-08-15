import { Pressable, View } from 'react-native';
import type { LobbyTone } from '../api/types';
import { Text } from '../ui';
import { cn } from '../ui/cn';

/**
 * The four moods a lobby can advertise. A fixed set, mirroring the backend's enum — the
 * labels deliberately echo the keyword vocabulary ("competitive", "chill") so the two
 * read as one app.
 */
export const TONES: { value: LobbyTone; label: string }[] = [
  { value: 'COMPETITIVE', label: 'Competitive' },
  { value: 'CHILL', label: 'Chill' },
  { value: 'CASUAL', label: 'Casual' },
  { value: 'LEARNING', label: 'Learning' },
];

export function toneLabel(tone: LobbyTone): string {
  return TONES.find((t) => t.value === tone)?.label ?? tone;
}

/** The static badge on a lobby card. */
export function ToneBadge({ tone }: { tone: LobbyTone }) {
  return (
    <View className="rounded-full bg-raised px-2.5 py-0.5">
      <Text variant="label">{toneLabel(tone)}</Text>
    </View>
  );
}

/**
 * The tappable pill, for the create form and the browse filter. Both states carry the
 * same class keys — a key that comes and goes across a state change stops NativeWind
 * painting the subtree (see `src/ui/hairline.ts`).
 */
export function ToneChip({
  tone,
  active,
  onPress,
}: {
  tone: LobbyTone;
  active: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected: active }}
      className={cn(
        'rounded-full px-3.5 py-2',
        active ? 'bg-primary' : 'bg-raised',
      )}
    >
      <Text variant="label" className={active ? 'text-white' : 'text-content'}>
        {toneLabel(tone)}
      </Text>
    </Pressable>
  );
}
