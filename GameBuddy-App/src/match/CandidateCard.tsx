import { useQuery } from '@tanstack/react-query';
import { ChevronUp } from 'lucide-react-native';
import { useMemo } from 'react';
import { Image, View } from 'react-native';
import { avatarGradient, avatarUri, initialsOf } from '../avatars';
import { profileApi } from '../api/catalogue';
import type { Candidate } from '../api/types';
import { PlatformIcon } from '../profile/PlatformIcon';
import { useThemeColors } from '../theme';
import { platformIdOf } from '../profile/platforms';
import { Avatar } from '../ui/Avatar';
import { FrameOverlay } from '../ui/FramedAvatar';
import { cn } from '../ui/cn';
import { lift } from '../ui/elevation';
import { GradientView } from '../ui/Gradient';
import { useHairline } from '../ui/hairline';
import { Icon } from '../ui/Icon';
import { Text } from '../ui/Text';

type CandidateCardProps = {
  candidate: Candidate;
  /** Cards behind the top one are inert and slightly dimmed. */
  muted?: boolean;
};

/**
 * How many pills survive on the card. Everything past this is behind the tap.
 *
 * These are deliberately small. The detail block is about 290dp once the identity block has
 * taken its half, and a gamer may have picked a dozen games — the old card put all of them
 * in a `ScrollView`, which technically fitted and practically did not: it sliced the last
 * row through the middle at the card's edge, hid the scrollbar, and asked somebody to
 * scroll vertically inside a deck whose whole interaction is dragging horizontally. A card
 * in a swipe deck has one job, which is to be readable in the second before a decision.
 */
const MAX_GAMES = 3;
const MAX_KEYWORDS = 3;

/**
 * One gamer, as a card. This is the app's front door.
 *
 * The identity block is a per-user gradient rather than a photo, because there is still no
 * avatar art hosted (see `src/avatars.ts`). It is the *same* gradient the avatar uses —
 * `avatarGradient`, off the same hash — so a person is one colour wherever they appear,
 * and the block becomes the photo the moment images exist.
 *
 * Under it sits a scrim, and that is not decoration. The gradient is seeded from a user id,
 * so its lightness is whatever the hash produced; white text over an unlucky pale hue is
 * unreadable. A fixed dark scrim behind the name makes the contrast independent of the
 * seed, which is the only way to keep this legible for every user rather than most of them.
 *
 * **The detail block is a summary, not the whole profile.** It never scrolls and never
 * clips: it shows the first few of each list and says how many it is holding back. The full
 * lists are one tap away in {@link CandidateSheet}. Removing the scroll also removed a
 * gesture problem for free — the card's `ScrollView` and the deck's pan gesture were
 * competing for the same vertical drag, and the pan has no axis constraint to lose with.
 */
export function CandidateCard({ candidate, muted = false }: CandidateCardProps) {
  const hasPhoto = !!avatarUri(candidate.avatar);
  const hairline = useHairline();

  const games = candidate.favoriteGames ?? [];
  const keywords = candidate.selectedKeywords ?? [];

  const { ordered, shared } = useSharedFirst(games);

  return (
    <View
      className={cn('flex-1 overflow-hidden rounded-card bg-surface', muted && 'opacity-60')}
      style={[lift('lg'), hairline]}
    >
      {/*
        `flex-1` on the identity block and natural height on the detail block below, and
        that order matters more than it looks.

        The obvious arrangement is the opposite one — fixed identity, `flex-1` detail — and
        it is what shipped. It cannot work: the detail block's height depends on how many
        rows the game names happen to wrap onto, which is a property of somebody else's
        library, so a fixed remainder is sometimes too small and the last row gets sliced
        through the middle at the card's edge. Sizing the detail to its content and letting
        the *portrait* absorb whatever is left over inverts that: the block that can safely
        change height is the one that does.
      */}
      <View className="flex-1 justify-center overflow-hidden">
        <GradientView
          colors={avatarGradient(candidate.userId)}
          direction="diagonal"
          className="absolute inset-0"
          pointerEvents="none"
        />
        {/* Bottom-weighted, so the top of the block keeps its colour and the name sits on
            something dark enough to read against. Pure black at low alpha rather than a
            token: this sits on the candidate's own hue in both themes and must not follow
            `canvas` into white. */}
        <GradientView
          colors={['rgba(0,0,0,0)', 'rgba(0,0,0,0.55)']}
          direction="vertical"
          className="absolute inset-0"
          pointerEvents="none"
        />

        <View className="items-center justify-center px-6 py-10">
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

          <Text variant="title" numberOfLines={1} className="mt-4 text-center text-white">
            {candidate.gamerUsername}
          </Text>
          <Text className="font-medium text-[14px] leading-[20px] text-white/85">
            {[candidate.age, candidate.country].filter(Boolean).join(' · ')}
          </Text>

          {/* Platforms live up here as bare glyphs rather than as a "PLAYS ON" section
              below. They are a hard constraint on playing together, so they belong on the
              card — but as three words in their own labelled block they cost about a fifth
              of the detail area to say "PC", which is what pushed Style off the bottom.
              As icons on the portrait they cost a single 16px row and read faster. */}
          {!!candidate.platforms?.length && (
            <PlatformGlyphs platforms={candidate.platforms} />
          )}
        </View>
      </View>

      {/* No `flex-1`: this is exactly as tall as what is in it, which is what stops it
          being clipped. Everything past the caps is behind the tap. */}
      <View className="gap-4 p-5 pt-4">
        <GameRow
          games={ordered.slice(0, MAX_GAMES)}
          hidden={ordered.length - MAX_GAMES}
          shared={shared}
        />

        <KeywordRow
          title="Style"
          items={keywords.slice(0, MAX_KEYWORDS)}
          hidden={keywords.length - MAX_KEYWORDS}
        />

        {/* Not on the card behind. It is inert and dimmed, and inviting a tap on something
            that cannot be tapped is worse than saying nothing. */}
        {!muted && (
          <View className="flex-row items-center justify-center gap-1.5">
            <Text variant="caption">Tap for full profile</Text>
            <Icon as={ChevronUp} size={14} tone="muted" />
          </View>
        )}
      </View>
    </View>
  );
}

/**
 * The candidate's games, with the ones you also play moved to the front.
 *
 * This exists because of the cap above: when only four of twelve survive, *which* four is
 * the whole question. Four arbitrary games say almost nothing; the four you have in common
 * are the reason to swipe right, and they were previously as likely to be cut as kept.
 *
 * Matched on name rather than id, because the deck's DTO flattens games to
 * `{ gameName, gameIcon }` and carries no id at all (see `Candidate` in `api/types.ts`).
 * Both sides come from the same catalogue — `backfill_game_covers.py` canonicalised the
 * titles from IGDB — so the names agree. Lower-cased and trimmed anyway, because a match
 * that fails here is invisible: it does not error, it just quietly stops prioritising.
 */
function useSharedFirst(games: Candidate['favoriteGames']) {
  // The deck already holds this in cache — the profile tab, the filter sheet and the deck
  // all read the same key — so this is a cache read rather than a request per card.
  const me = useQuery({ queryKey: ['me'], queryFn: profileApi.me });
  const mine = me.data?.games;

  return useMemo(() => {
    const own = new Set((mine ?? []).map((game) => game.gameName.trim().toLowerCase()));
    if (own.size === 0) return { ordered: games, shared: 0 };

    const isShared = (name: string) => own.has(name.trim().toLowerCase());
    const both = games.filter((game) => isShared(game.gameName));
    const rest = games.filter((game) => !isShared(game.gameName));

    return { ordered: [...both, ...rest], shared: both.length };
  }, [games, mine]);
}

/**
 * The games, with their cover art.
 *
 * The covers were already on the payload and thrown away — `favoriteGames` has carried
 * `gameIcon` all along and this rendered only the name in a grey pill. Cover art is the
 * most game-like thing the app owns, and it was arriving unused on the deck's own request.
 *
 * Every cover is optional and frequently absent: the local seed leaves `game_icon` NULL for
 * all hundred games until `backfill_game_covers.py` has been run against the database. So
 * the pill has to look deliberate with no art at all, which is why the name is always the
 * pill and the cover is a *prefix* to it rather than the pill being a bare tile.
 *
 * Exported so {@link CandidateSheet} draws the same pills from the same code — it passes
 * the full list and `hidden={0}`, which is the only difference between the two surfaces.
 */
export function GameRow({
  games,
  hidden = 0,
  shared = 0,
}: {
  games: { gameName: string; gameIcon: string | null }[];
  hidden?: number;
  shared?: number;
}) {
  if (games.length === 0) return null;

  return (
    <View className="gap-2">
      <View className="flex-row items-center justify-between gap-3">
        <Text variant="overline">PLAYS</Text>
        {/* The payoff of ordering by shared games: say so, or the reordering is invisible
            and reads as an arbitrary sort. */}
        {shared > 0 && (
          <Text variant="caption" className="text-primary">
            {shared} in common
          </Text>
        )}
      </View>
      <View className="flex-row flex-wrap gap-2">
        {games.map((game) => (
          <View
            key={game.gameName}
            className="max-w-full flex-row items-center gap-2 overflow-hidden rounded-full border border-primary/30 bg-primary/12 py-1 pl-1 pr-3"
          >
            {game.gameIcon ? (
              <Image
                source={{ uri: game.gameIcon }}
                resizeMode="cover"
                className="h-6 w-6 rounded-full"
              />
            ) : (
              // The game's initial, on the same footprint the cover would occupy — so a
              // catalogue with art and one without produce the same layout rather than
              // the pills changing shape when the backfill runs.
              <View className="h-6 w-6 items-center justify-center rounded-full bg-primary/25">
                <Text className="font-semibold text-[11px] leading-[14px] text-primary">
                  {game.gameName.slice(0, 1).toUpperCase()}
                </Text>
              </View>
            )}
            <Text variant="caption" numberOfLines={1} className="shrink text-primary">
              {game.gameName}
            </Text>
          </View>
        ))}
        {hidden > 0 && <MorePill count={hidden} />}
      </View>
    </View>
  );
}

/**
 * Platforms as glyphs alone, for the portrait block.
 *
 * White rather than a token, like everything else on this block: it sits on the candidate's
 * own hue in both themes and must not follow `content` into black. Labelled for a screen
 * reader, which cannot see a controller.
 */
function PlatformGlyphs({ platforms }: { platforms: string[] }) {
  const known = platforms.map((platform) => ({ platform, id: platformIdOf(platform) }));

  return (
    <View
      className="mt-3 flex-row items-center gap-3"
      accessibilityLabel={`Plays on ${platforms.join(', ')}`}
    >
      {known.map(({ platform, id }) =>
        id ? <PlatformIcon key={platform} platform={id} color="#FFFFFF" size={16} /> : null,
      )}
    </View>
  );
}

/** What they play on, with the hand-drawn hardware glyphs rather than bare labels. */
export function PlatformRow({ platforms }: { platforms: string[] }) {
  const colors = useThemeColors();

  return (
    <View className="gap-2">
      <Text variant="overline">PLAYS ON</Text>
      <View className="flex-row flex-wrap gap-2">
        {platforms.map((platform) => {
          // Tolerant of both the enum name and the label — the two DTOs disagree about
          // which they send. See `platformIdOf`.
          const id = platformIdOf(platform);
          return (
            <View
              key={platform}
              className="flex-row items-center gap-2 rounded-full bg-raised px-3 py-1.5"
            >
              {id && <PlatformIcon platform={id} color={colors.content} size={16} />}
              <Text variant="caption" className="text-content">
                {platform}
              </Text>
            </View>
          );
        })}
      </View>
    </View>
  );
}

export function KeywordRow({
  title,
  items,
  hidden = 0,
}: {
  title: string;
  items: string[];
  hidden?: number;
}) {
  if (items.length === 0) return null;

  return (
    <View className="gap-2">
      <Text variant="overline">{title.toUpperCase()}</Text>
      <View className="flex-row flex-wrap gap-2">
        {items.map((item) => (
          <View key={item} className="max-w-full rounded-full bg-raised px-3 py-1.5">
            <Text variant="caption" numberOfLines={1} className="text-content">
              {item}
            </Text>
          </View>
        ))}
        {hidden > 0 && <MorePill count={hidden} />}
      </View>
    </View>
  );
}

/**
 * "+3".
 *
 * Outlined rather than filled so it reads as a count of what is missing rather than as
 * another item of the same kind — it is the only pill in the row that is not a thing this
 * person plays or is.
 */
function MorePill({ count }: { count: number }) {
  return (
    <View className="rounded-full border border-line px-3 py-1.5">
      <Text variant="caption" className="text-muted">
        +{count}
      </Text>
    </View>
  );
}
