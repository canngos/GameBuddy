import { Image } from 'expo-image';
import { StyleSheet, View } from 'react-native';
import { Text } from './Text';

/** What every surface needs to draw a game: the name, and the cover if there is one. */
export type PillGame = { gameName: string; gameIcon: string | null };

/**
 * Games as pills, each with its cover art.
 *
 * **One implementation, three surfaces.** This started on the deck card, where the covers
 * were the thing that made it feel like a games app rather than a contact list — and the
 * profiles went on rendering the same games as bare names in grey pills, with `gameIcon`
 * sitting unused on a payload they had already fetched. Two places drawing the same object
 * two ways is how they drift; now the deck, the full-profile sheet and both profile screens
 * are the same code.
 *
 * Headings are deliberately *not* here. The deck labels this "Plays" in an overline and adds
 * an "in common" count; the profiles label it "Games" in a muted label. Those are different
 * sentences on different surfaces, and folding them in would mean a prop for each.
 *
 * Every cover is optional and frequently absent: the local seed leaves `game_icon` NULL for
 * all hundred games until `backfill_game_covers.py` has run. So the name is always the pill
 * and the cover is a *prefix* to it, and a game with no art falls back to its initial on the
 * same footprint — a catalogue with art and one without produce the same layout rather than
 * the pills changing shape when the backfill runs.
 */
export function GamePills({ games, trailing }: { games: PillGame[]; trailing?: React.ReactNode }) {
  return (
    <View className="flex-row flex-wrap gap-2">
      {games.map((game) => (
        <View
          key={game.gameName}
          className="max-w-full flex-row items-center gap-2 overflow-hidden rounded-full border border-primary/30 bg-primary/12 py-1 pl-1 pr-3"
        >
          {game.gameIcon ? (
            <Image
              source={{ uri: game.gameIcon }}
              // `style`, not the `h-6 w-6 rounded-full` this used to carry: expo-image is
              // not registered with NativeWind, so a className on it resolves to nothing at
              // all and the cover would render at zero size. It fails silently either way.
              style={styles.gameIcon}
              contentFit="cover"
              cachePolicy="memory-disk"
              recyclingKey={game.gameIcon}
              transition={0}
            />
          ) : (
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
      {trailing}
    </View>
  );
}

/**
 * A style rather than a class because this goes on an `expo-image`, which NativeWind does
 * not register — see the comment at the call site.
 */
const styles = StyleSheet.create({
  gameIcon: { width: 24, height: 24, borderRadius: 12 },
});
