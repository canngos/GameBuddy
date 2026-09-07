import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { memo, useCallback } from 'react';
import { Alert, FlatList, View } from 'react-native';
import { adminApi } from '../../src/api/admin';
import type { BlockedUser } from '../../src/api/types';
import { Avatar, Button, Card, ErrorNotice, Screen, Text } from '../../src/ui';

/**
 * Banned accounts, and the way back.
 *
 * Only the banned are listed. Browsing every account would make this a directory of the
 * user base — searchable, screenshot-able, and far more than moderating needs. Banning
 * happens from a report, where there is a reason on screen; this tab exists so the
 * decision can be seen afterwards and undone.
 *
 * Unbanning is one tap and no confirmation, deliberately: it restores access, and the
 * asymmetry is the point — the destructive direction asks, the merciful one does not.
 */
export default function AccountsScreen() {
  const queryClient = useQueryClient();

  const banned = useQuery({
    queryKey: ['admin', 'banned'],
    queryFn: () => adminApi.blockedUsers(),
    staleTime: 30_000,
  });

  const unban = useMutation({
    mutationFn: (userId: string) => adminApi.unbanUser(userId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'banned'] });
      void queryClient.invalidateQueries({ queryKey: ['admin', 'analytics'] });
    },
  });

  // Stable, so the extracted row below can memoise. It was an inline arrow inside
  // `renderItem`, which is also why the row could not be a component at all.
  const { mutate: unbanMutate } = unban;
  const confirmUnban = useCallback((userId: string, username: string | null) =>
    Alert.alert(`Restore ${username ?? 'this account'}?`, 'They will be able to sign in and be matched again.', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Restore', onPress: () => unbanMutate(userId) },
    ]),
    [unbanMutate],
  );

  const rows = banned.data?.blockedUsers ?? [];

  const keyExtractor = useCallback((row: BlockedUser) => row.userId, []);
  const renderRow = useCallback(
    ({ item }: { item: BlockedUser }) => (
      <BannedRow row={item} busy={unban.isPending} onRestore={confirmUnban} />
    ),
    [unban.isPending, confirmUnban],
  );

  return (
    <Screen edges={['top']}>
      <View className="pb-3 pt-2">
        <Text variant="overline">Moderation</Text>
        <Text variant="title">Banned accounts</Text>
      </View>

      {banned.error && <ErrorNotice error={banned.error} onRetry={() => void banned.refetch()} />}
      {unban.error && <ErrorNotice error={unban.error} />}

      <FlatList
        data={rows}
        keyExtractor={keyExtractor}
        showsVerticalScrollIndicator={false}
        contentContainerClassName="pb-8"
        onRefresh={() => void banned.refetch()}
        refreshing={banned.isFetching}
        ListEmptyComponent={
          banned.isLoading ? null : (
            <Text variant="body" className="mt-8 text-center text-muted">
              Nobody is banned. Accounts you ban from a report appear here.
            </Text>
          )
        }
        renderItem={renderRow}
      />
    </Screen>
  );
}

/**
 * One banned account.
 *
 * Extracted so it can be memoised at all: it used to be inline JSX inside `renderItem`,
 * which meant there was no component to wrap and every row rebuilt whenever anything on
 * the screen changed.
 */
const BannedRow = memo(function BannedRow({
  row,
  busy,
  onRestore,
}: {
  row: BlockedUser;
  busy: boolean;
  onRestore: (userId: string, username: string | null) => void;
}) {
  const restore = useCallback(
    () => onRestore(row.userId, row.username),
    [onRestore, row.userId, row.username],
  );

  return (
    <Card className="mb-3">
      <View className="flex-row items-center">
        <Avatar source={row.avatar} name={row.username ?? '?'} size={44} />
        <View className="ml-3 flex-1">
          <Text variant="bodyStrong">{row.username ?? 'No username'}</Text>
          {/* The address is shown because it is the only stable way to tell two
              similar usernames apart when reversing a ban. */}
          <Text variant="caption">{row.email ?? row.userId}</Text>
        </View>
      </View>

      <View className="mt-4">
        <Button label="Restore access" variant="secondary" onPress={restore} disabled={busy} />
      </View>
    </Card>
  );
});
