import { Pressable, View } from 'react-native';
import type { LobbyTone } from '../api/types';
import type { Dictionary } from '../i18n/dictionaries/en';
import { useT } from '../i18n/useT';
import { Text } from '../ui';
import { cn } from '../ui/cn';

/**
 * The four moods a lobby can advertise. A fixed set, mirroring the backend's enum — the
 * labels deliberately echo the keyword vocabulary ("competitive", "chill") so the two
 * read as one app. Values only: the labels live in the dictionary, resolved at render,
 * because a module-level label array is evaluated before anybody has chosen a language.
 */
export const TONES: LobbyTone[] = ['COMPETITIVE', 'CHILL', 'CASUAL', 'LEARNING'];

export function toneLabel(tone: LobbyTone, t: Dictionary): string {
  const labels: Record<LobbyTone, string> = {
    COMPETITIVE: t.lobby.tones.competitive,
    CHILL: t.lobby.tones.chill,
    CASUAL: t.lobby.tones.casual,
    LEARNING: t.lobby.tones.learning,
  };
  return labels[tone] ?? tone;
}

/** The static badge on a lobby card. */
export function ToneBadge({ tone }: { tone: LobbyTone }) {
  const t = useT();
  return (
    <View className="rounded-full bg-raised px-2.5 py-0.5">
      <Text variant="label">{toneLabel(tone, t)}</Text>
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
  const t = useT();
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
        {toneLabel(tone, t)}
      </Text>
    </Pressable>
  );
}
