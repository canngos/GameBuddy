import { Image } from 'react-native';
import { Pressable, View } from 'react-native';
import type { Candidate } from '../api/types';
import { avatarColor, avatarUri, initialsOf } from '../avatars';
import { FrameOverlay } from '../ui/FramedAvatar';
import { Text } from '../ui/Text';
import { cn } from '../ui/cn';
import { useHairline } from '../ui/hairline';

type AdmirerCardProps =
  | { locked: true; candidate?: undefined; onPress?: undefined }
  | { locked?: false; candidate: Candidate; onPress: () => void };

/**
 * One person who liked you, as a tile.
 *
 * Two states from one component, and the union type is what keeps them honest: a locked
 * tile cannot be given a candidate, and an unlocked one cannot be built without one. The
 * server sends an empty `likedYou` array while locked, so there is genuinely nothing to
 * render behind the blur — which is the point. Nothing is being hidden client-side that a
 * determined person could read out of the response.
 */
export function AdmirerCard({ locked, candidate, onPress }: AdmirerCardProps) {
  const hairline = useHairline();

  if (locked) {
    return (
      <View
        style={[{ flexBasis: '30%' }, hairline]}
        className="aspect-[3/4] items-center justify-center overflow-hidden rounded-card bg-raised"
      >
        {/* Concentric rings rather than a blurred face. A real blur would need a real
            image, and we do not have one — inventing a silhouette would be pretending to
            withhold something we were never sent. This reads as "somebody", which is the
            truth. */}
        <View className="h-12 w-12 rounded-full bg-line opacity-70" />
        <View className="mt-2 h-2 w-12 rounded-full bg-line opacity-50" />
      </View>
    );
  }

  const photo = avatarUri(candidate.avatar);

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${candidate.gamerUsername}, ${candidate.age}`}
      style={[{ flexBasis: '30%' }, hairline]}
      className="aspect-[3/4] overflow-hidden rounded-card bg-surface active:opacity-80"
    >
      <View
        className="flex-1 items-center justify-center"
        style={{ backgroundColor: photo ? undefined : avatarColor(candidate.userId) }}
      >
        <View className="h-16 w-16 items-center justify-center">
          {photo ? (
            <Image source={{ uri: photo }} className="h-16 w-16 rounded-full" />
          ) : (
            <Text variant="heading" className="text-white">
              {initialsOf(candidate.gamerUsername)}
            </Text>
          )}
          {!!candidate.frame && <FrameOverlay frame={candidate.frame} size={64} />}
        </View>
      </View>

      <View className="px-2 py-1.5">
        <Text variant="caption" numberOfLines={1} className={cn('text-content')}>
          {candidate.gamerUsername}
        </Text>
      </View>
    </Pressable>
  );
}
