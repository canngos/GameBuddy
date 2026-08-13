import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { ActivityIndicator, View } from 'react-native';
import { socialApi } from '../../../src/api/social';
import { useThemeColors } from '../../../src/theme';
import { Avatar, Button, Card, ErrorNotice, Screen, Text } from '../../../src/ui';

export default function BlockedUsers() {
  const router = useRouter();
  const colors = useThemeColors();
  const queryClient = useQueryClient();

  const blocked = useQuery({ queryKey: ['blocked'], queryFn: socialApi.blocked });

  const unblock = useMutation({
    mutationFn: (userId: string) => socialApi.unblock(userId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['blocked'] });
      // Unblocking makes them pairable again, so the cached feed is stale.
      void queryClient.invalidateQueries({ queryKey: ['recommendations'] });
    },
  });

  const people = blocked.data ?? [];

  return (
    <Screen scroll edges={['top', 'bottom']}>
      <View className="gap-2 pb-6 pt-8">
        <Text variant="title">Blocked</Text>
        <Text variant="body" className="text-muted">
          Blocking works both ways and covers everything: they are not shown to you, you
          are not shown to them, and neither of you can message the other.
        </Text>
      </View>

      {blocked.isPending && <ActivityIndicator color={colors.primary} />}
      {blocked.error && <ErrorNotice error={blocked.error} onRetry={() => blocked.refetch()} />}

      {!blocked.isPending && people.length === 0 && (
        <Card className="gap-1">
          <Text variant="bodyStrong">Nobody is blocked</Text>
          <Text variant="caption">
            You can block someone from their profile if you need to.
          </Text>
        </Card>
      )}

      <View className="gap-2">
        {people.map((person) => (
          <Card key={person.userId} className="flex-row items-center gap-3 p-4">
            <Avatar
              source={person.avatar}
              name={person.username}
              colorSeed={person.userId}
              size={44}
            />
            <View className="flex-1 gap-0.5">
              <Text variant="bodyStrong">{person.username}</Text>
              <Text variant="caption">
                {[person.age, person.country].filter(Boolean).join(' · ')}
              </Text>
            </View>
            <Button
              label="Unblock"
              variant="secondary"
              size="md"
              className="px-4"
              disabled={unblock.isPending}
              onPress={() => unblock.mutate(person.userId)}
            />
          </Card>
        ))}
      </View>

      {unblock.error && (
        <View className="pt-4">
          <ErrorNotice error={unblock.error} />
        </View>
      )}

      <View className="mt-auto pt-10">
        <Button label="Back" variant="ghost" onPress={() => router.back()} />
      </View>
    </Screen>
  );
}
