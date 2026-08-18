import { useRouter } from 'expo-router';
import { Zap } from 'lucide-react-native';
import { memo, useCallback, useEffect, useMemo } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import Animated, {
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import type { Lobby } from '../api/types';
import { useT } from '../i18n/useT';
import { useThemeColors } from '../theme';
import { Avatar, Card, Icon, Text } from '../ui';
import { startsLabel } from './startsAt';
import { ToneBadge } from './ToneChip';

/** Card radius, mirroring `rounded-card` — the frame below has to trace the same shape. */
const CARD_RADIUS = 24;

/**
 * One lobby, in the browse feed or the "mine" strip. The same card everywhere, because a
 * lobby looks the same whether or not you are in it — only the badge row differs.
 *
 * A boosted lobby wears an animated frame. That is the whole point of paying for one: the
 * deck boost it replaced promoted a face inside somebody else's stack, so the buyer had no
 * way to tell it had done anything. Here the owner can see their own lobby wearing it, at
 * the top of the list, for as long as the lobby is open.
 */
export const LobbyCard = memo(function LobbyCard({ lobby }: { lobby: Lobby }) {
  const router = useRouter();
  const t = useT();
  const seats = `${lobby.playerCount}/${lobby.maxPlayers}`;
  const full = lobby.playerCount >= lobby.maxPlayers;

  const open = useCallback(
    () => router.push({ pathname: '/lobby/[lobbyId]', params: { lobbyId: lobby.id } } as never),
    [router, lobby.id],
  );

  // `startsLabel` builds a `Date` and reads `Date.now()`. It was doing that on every render
  // of every card in an infinite feed; the answer only changes when the timestamp does.
  // What a non-OPEN lobby says about itself on a card; OPEN says the start time instead.
  const when = useMemo(() => {
    if (lobby.status === 'OPEN') return t.lobby.card.plays(startsLabel(lobby.startsAt, t));
    const statusLabels: Record<string, string> = {
      LOCKED: t.lobby.card.statusLocked,
      ENDED: t.lobby.card.statusEnded,
      CANCELLED: t.lobby.card.statusCancelled,
    };
    return statusLabels[lobby.status] ?? lobby.status;
  }, [lobby.status, lobby.startsAt, t]);

  return (
    <Card className="gap-0 p-0">
      {lobby.boosted && <BoostedFrame />}
      <Pressable
        onPress={open}
        accessibilityRole="button"
        className="gap-3 p-4 active:opacity-70"
      >
        <View className="flex-row items-center gap-3">
          <Avatar
            source={lobby.gameIcon}
            name={lobby.gameName ?? '?'}
            colorSeed={lobby.gameId}
            size={44}
          />
          <View className="flex-1 gap-0.5">
            <Text variant="bodyStrong" numberOfLines={1}>
              {lobby.title}
            </Text>
            <Text variant="caption" numberOfLines={1}>
              {lobby.gameName ?? t.lobby.card.unknownGame} ·{' '}
              {t.lobby.card.byOwner(lobby.ownerUsername ?? t.lobby.card.unknownOwner)}
            </Text>
          </View>
          {lobby.unreadCount > 0 && (
            <View className="min-w-6 items-center rounded-full bg-primary px-1.5 py-0.5">
              <Text variant="label" className="text-white">
                {lobby.unreadCount > 99 ? '99+' : lobby.unreadCount}
              </Text>
            </View>
          )}
        </View>

        <View className="flex-row flex-wrap items-center gap-2">
          {/* Gold, not accent. The palette reserves accent for like/match/admirer, and a
              boost is a thing bought with coins — the same meaning gold already carries
              for coins, membership and badge tiers. */}
          {lobby.boosted && (
            <View className="flex-row items-center gap-1 rounded-full bg-gold/15 px-2.5 py-0.5">
              <Icon as={Zap} size={12} tone="gold" strokeWidth={2.5} />
              <Text variant="label" className="text-gold">
                {t.lobby.boost.badge}
              </Text>
            </View>
          )}
          <ToneBadge tone={lobby.tone} />
          <View className="rounded-full bg-raised px-2.5 py-0.5">
            <Text variant="label">
              {full ? t.lobby.card.seatsFull(seats) : t.lobby.card.seatsPlayers(seats)}
            </Text>
          </View>
          <View className="rounded-full bg-raised px-2.5 py-0.5">
            <Text variant="label">{when}</Text>
          </View>
          {lobby.myStatus === 'PENDING' && (
            <View className="rounded-full bg-raised px-2.5 py-0.5">
              <Text variant="label">{t.lobby.card.requested}</Text>
            </View>
          )}
        </View>
      </Pressable>
    </Card>
  );
});

/**
 * The pulsing accent border around a boosted lobby.
 *
 * An overlay rather than a border on the card itself: a border would take a pixel of layout
 * and shift every row on the list the moment somebody boosts. This traces the card's own
 * radius on top of it and nothing under it moves.
 *
 * Two of this project's documented traps are load-bearing here — see `src/ui/Burst.tsx`:
 * `className` never goes on an `Animated.View` (NativeWind does not resolve it there), so
 * this is plain styles the whole way down; and it is `pointerEvents="none"` so the card
 * underneath still takes the tap.
 *
 * The pulse is slow on purpose. This sits in a scrolling feed, and something blinking
 * quickly in a list reads as a warning rather than as a highlight.
 */
function BoostedFrame() {
  const colors = useThemeColors();
  const pulse = useSharedValue(0.35);

  useEffect(() => {
    pulse.value = withRepeat(
      withTiming(1, { duration: 1400, easing: Easing.inOut(Easing.quad) }),
      // -1 repeats forever; `true` reverses, so it breathes rather than snapping back to
      // dim at the end of each cycle.
      -1,
      true,
    );
  }, [pulse]);

  const style = useAnimatedStyle(() => ({ opacity: pulse.value }));

  return (
    <Animated.View
      pointerEvents="none"
      style={[
        StyleSheet.absoluteFill,
        {
          borderRadius: CARD_RADIUS,
          borderWidth: 2,
          borderColor: colors.gold,
        },
        style,
      ]}
    />
  );
}
