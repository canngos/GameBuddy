import { useRouter } from 'expo-router';
import { memo, useCallback, useMemo } from 'react';
import { Pressable, View } from 'react-native';
import type { Lobby } from '../api/types';
import { useT } from '../i18n/useT';
import { Avatar, Card, Text } from '../ui';
import { startsLabel } from './startsAt';
import { ToneBadge } from './ToneChip';

/**
 * One lobby, in the browse feed or the "mine" strip. The same card everywhere, because a
 * lobby looks the same whether or not you are in it — only the badge row differs.
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
