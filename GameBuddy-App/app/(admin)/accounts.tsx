import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, FlatList, View } from 'react-native';
import { adminApi } from '../../src/api/admin';
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

  const confirmUnban = (userId: string, username: string | null) =>
    Alert.alert(`Restore ${username ?? 'this account'}?`, 'They will be able to sign in and be matched again.', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Restore', onPress: () => unban.mutate(userId) },
    ]);

  const rows = banned.data?.blockedUsers ?? [];

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
        keyExtractor={(row) => row.userId}
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
        renderItem={({ item }) => (
          <Card className="mb-3">
            <View className="flex-row items-center">
              <Avatar source={item.avatar} name={item.username ?? '?'} size={44} />
              <View className="ml-3 flex-1">
                <Text variant="bodyStrong">{item.username ?? 'No username'}</Text>
                {/* The address is shown because it is the only stable way to tell two
                    similar usernames apart when reversing a ban. */}
                <Text variant="caption">{item.email ?? item.userId}</Text>
              </View>
            </View>

            <View className="mt-4">
              <Button
                label="Restore access"
                variant="secondary"
                onPress={() => confirmUnban(item.userId, item.username)}
                disabled={unban.isPending}
              />
            </View>
          </Card>
        )}
      />
    </Screen>
  );
}
