import { ScrollView, View } from 'react-native';
import { avatarColor, avatarUri, initialsOf } from '../avatars';
import type { Candidate } from '../api/types';
import { Avatar } from '../ui/Avatar';
import { FrameOverlay } from '../ui/FramedAvatar';
import { cn } from '../ui/cn';
import { lift } from '../ui/elevation';
import { useHairline } from '../ui/hairline';
import { Text } from '../ui/Text';

type CandidateCardProps = {
  candidate: Candidate;
  /** Cards behind the top one are inert and slightly dimmed. */
  muted?: boolean;
};

/**
 * One gamer, as a card.
 *
 * The top half is a colour block rather than a photo — there is no avatar art hosted
 * yet (see `src/avatars.ts`), and a stack of identical grey placeholders would make
 * every candidate look the same, which is precisely the wrong failure for a deck you
 * are meant to choose between. Colouring by user id at least makes them distinct, and
 * the block becomes the photo the moment images exist.
 */
export function CandidateCard({ candidate, muted = false }: CandidateCardProps) {
  const hasPhoto = !!avatarUri(candidate.avatar);
  const tint = avatarColor(candidate.userId);
  const hairline = useHairline();

  return (
    <View
      className={cn('flex-1 overflow-hidden rounded-card bg-surface', muted && 'opacity-60')}
      style={[lift('lg'), hairline]}
    >
      {/* Identity block */}
      <View
        className="items-center justify-center px-6 py-10"
        style={{ backgroundColor: hasPhoto ? undefined : tint }}
      >
        {/* The frame goes over whichever portrait we drew — a photo, or the oversized
            monogram below. Someone who paid for a ring should see it whether or not
            they have uploaded a picture yet. */}
        <View className="h-32 w-32 items-center justify-center">
          {hasPhoto ? (
            <Avatar source={candidate.avatar} name={candidate.gamerUsername} size={128} />
          ) : (
            <View className="h-32 w-32 items-center justify-center rounded-full bg-white/25">
              <Text className="font-bold text-[44px] leading-[52px] text-white">
                {initialsOf(candidate.gamerUsername)}
              </Text>
            </View>
          )}
          <FrameOverlay frame={candidate.frame} size={128} />
        </View>

        <Text className="mt-4 font-bold text-[26px] leading-[34px] text-white">
          {candidate.gamerUsername}
        </Text>
        <Text className="font-medium text-[14px] leading-[20px] text-white/85">
          {[candidate.age, candidate.country].filter(Boolean).join(' · ')}
        </Text>
      </View>

      {/* Detail block. Scrollable because a gamer may have picked far more than the
          three games and five keywords onboarding demanded. */}
      <ScrollView
        className="flex-1"
        contentContainerClassName="gap-5 p-5"
        showsVerticalScrollIndicator={false}
        // The pan gesture owns horizontal movement; this only claims vertical.
        nestedScrollEnabled
      >
        <Section title="Plays" items={candidate.favoriteGames?.map((g) => g.gameName) ?? []} accent />
        {/* Omitted entirely when unknown rather than shown empty — accounts predating the
            field have nothing here, and "Plays on: —" reads as an answer when it is not
            one. Above Style because it is a hard constraint on playing together, where
            style is a preference. */}
        {!!candidate.platforms?.length && <Section title="Plays on" items={candidate.platforms} />}
        <Section title="Style" items={candidate.selectedKeywords ?? []} />
      </ScrollView>
    </View>
  );
}

function Section({
  title,
  items,
  accent = false,
}: {
  title: string;
  items: string[];
  accent?: boolean;
}) {
  if (items.length === 0) return null;

  return (
    <View className="gap-2">
      <Text variant="overline">{title.toUpperCase()}</Text>
      <View className="flex-row flex-wrap gap-2">
        {items.map((item) => (
          <View
            key={item}
            className={cn(
              'rounded-full px-3 py-1.5',
              accent ? 'bg-brand/12 border border-brand/30' : 'bg-raised',
            )}
          >
            <Text variant="caption" className={accent ? 'text-brand' : 'text-content'}>
              {item}
            </Text>
          </View>
        ))}
      </View>
    </View>
  );
}
