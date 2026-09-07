import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { memo, useCallback, useMemo } from 'react';
import { ActivityIndicator, FlatList, View } from 'react-native';
import { socialApi } from '../../../src/api/social';
import type { GamerSummary } from '../../../src/api/types';
import { useCountryName } from '../../../src/i18n/countryNames';
import { useT } from '../../../src/i18n/useT';
import { useThemeColors } from '../../../src/theme';
import { Avatar, BackButton, Button, Card, ErrorNotice, Screen, Text } from '../../../src/ui';

export default function BlockedUsers() {
  const colors = useThemeColors();
  const t = useT();
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

  const { mutate: unblockMutate } = unblock;
  const onUnblock = useCallback((userId: string) => unblockMutate(userId), [unblockMutate]);
  const keyExtractor = useCallback((person: GamerSummary) => person.userId, []);

  const renderItem = useCallback(
    ({ item }: { item: GamerSummary }) => (
      <BlockedRow person={item} busy={unblock.isPending} onUnblock={onUnblock} />
    ),
    [unblock.isPending, onUnblock],
  );

  return (
    <Screen edges={['top', 'bottom']}>
      <BackButton />

      <FlatList
        className="flex-1"
        data={people}
        keyExtractor={keyExtractor}
        renderItem={renderItem}
        contentContainerClassName="gap-2 pb-4"
        showsVerticalScrollIndicator={false}
        ListHeaderComponent={
          <View className="gap-2 pb-4">
            <View className="gap-2 pb-2 pt-8">
              <Text variant="title">{t.settings.blocked}</Text>
              <Text variant="body" className="text-muted">
                {t.settings.blockedScreen.body}
              </Text>
            </View>

            {blocked.isPending && <ActivityIndicator color={colors.primary} />}
            {!!blocked.error && (
              <ErrorNotice error={blocked.error} onRetry={() => blocked.refetch()} />
            )}
          </View>
        }
        ListEmptyComponent={
          blocked.isPending ? null : (
            <Card className="gap-1">
              <Text variant="bodyStrong">{t.settings.blockedScreen.emptyTitle}</Text>
              <Text variant="caption">{t.settings.blockedScreen.emptyBody}</Text>
            </Card>
          )
        }
        ListFooterComponent={
          <View className="gap-2 pt-6">
            {!!unblock.error && <ErrorNotice error={unblock.error} />}
              </View>
        }
      />
    </Screen>
  );
}

const BlockedRow = memo(function BlockedRow({
  person,
  busy,
  onUnblock,
}: {
  person: GamerSummary;
  busy: boolean;
  onUnblock: (userId: string) => void;
}) {
  const t = useT();
  const localize = useCountryName();
  const unblockThis = useCallback(() => onUnblock(person.userId), [onUnblock, person.userId]);
  const meta = useMemo(
    () => [person.age, localize(person.country)].filter(Boolean).join(' · '),
    [person.age, person.country, localize],
  );

  return (
    <Card className="flex-row items-center gap-3 p-4">
      <Avatar source={person.avatar} name={person.username} colorSeed={person.userId} size={44} />
      <View className="flex-1 gap-0.5">
        <Text variant="bodyStrong">{person.username}</Text>
        <Text variant="caption">{meta}</Text>
      </View>
      <Button
        label={t.settings.blockedScreen.unblock}
        variant="secondary"
        size="md"
        className="px-4"
        disabled={busy}
        onPress={unblockThis}
      />
    </Card>
  );
});
