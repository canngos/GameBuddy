import { useRouter } from 'expo-router';
import { memo, useCallback, useMemo } from 'react';
import { Pressable, View } from 'react-native';
import type { Lobby } from '../api/types';
import { Avatar, Card, Text } from '../ui';
import { startsLabel } from './startsAt';
import { ToneBadge } from './ToneChip';

/** What a non-OPEN lobby says about itself on a card. OPEN says the start time instead. */
const STATUS_LABEL: Record<string, string> = {
  LOCKED: 'Team locked',
  ENDED: 'Played',
  CANCELLED: 'Called off',
};

/**
 * One lobby, in the browse feed or the "mine" strip. The same card everywhere, because a
 * lobby looks the same whether or not you are in it — only the badge row differs.
 */
export const LobbyCard = memo(function LobbyCard({ lobby }: { lobby: Lobby }) {
  const router = useRouter();
  const seats = `${lobby.playerCount}/${lobby.maxPlayers}`;
  const full = lobby.playerCount >= lobby.maxPlayers;

  const open = useCallback(
    () => router.push({ pathname: '/lobby/[lobbyId]', params: { lobbyId: lobby.id } } as never),
    [router, lobby.id],
  );

  // `startsLabel` builds a `Date` and reads `Date.now()`. It was doing that on every render
  // of every card in an infinite feed; the answer only changes when the timestamp does.
  const when = useMemo(
    () =>
      lobby.status === 'OPEN'
        ? `Plays ${startsLabel(lobby.startsAt)}`
        : (STATUS_LABEL[lobby.status] ?? lobby.status),
    [lobby.status, lobby.startsAt],
  );

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
              {lobby.gameName ?? 'Unknown game'} · by {lobby.ownerUsername ?? 'someone'}
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
            <Text variant="label">{full ? `${seats} full` : `${seats} players`}</Text>
          </View>
          <View className="rounded-full bg-raised px-2.5 py-0.5">
            <Text variant="label">{when}</Text>
          </View>
          {lobby.myStatus === 'PENDING' && (
            <View className="rounded-full bg-raised px-2.5 py-0.5">
              <Text variant="label">Requested</Text>
            </View>
          )}
        </View>
      </Pressable>
    </Card>
  );
});
