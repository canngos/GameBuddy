import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, View } from 'react-native';
import { communityApi } from '../../../src/api/community';
import { ApiError, Code } from '../../../src/api/envelope';
import type { Community } from '../../../src/api/types';
import { useThemeColors } from '../../../src/theme';
import {
  Avatar,
  BackHeader,
  Button,
  Card,
  ErrorNotice,
  Screen,
  Text,
  TextField,
} from '../../../src/ui';

/**
 * The directory.
 *
 * Filtering is local because the backend has no search: `/community/get/communities`
 * returns every community there is, in one unpaged list. That is fine while the number
 * is small and is the reason this screen loads everything up front — but it is the first
 * thing that will need a server-side search when it stops being small.
 */
export default function BrowseCommunities() {
  const colors = useThemeColors();
  const [term, setTerm] = useState('');

  const communities = useQuery({ queryKey: ['communities'], queryFn: communityApi.all });

  const { joined, rest } = useMemo(() => {
    const needle = term.trim().toLowerCase();
    const matching = (communities.data ?? []).filter(
      (c) =>
        needle.length === 0 ||
        c.name.toLowerCase().includes(needle) ||
        c.description.toLowerCase().includes(needle),
    );
    return {
      joined: matching.filter((c) => c.isJoined),
      rest: matching.filter((c) => !c.isJoined),
    };
  }, [communities.data, term]);

  const nothingMatched =
    !communities.isPending && joined.length === 0 && rest.length === 0;

  return (
    <Screen edges={['top']}>
      <BackHeader title="Communities" />

      <TextField
        value={term}
        onChangeText={setTerm}
        placeholder="Search communities"
        autoCorrect={false}
        autoCapitalize="none"
      />

      {communities.isPending && <ActivityIndicator color={colors.primary} className="mt-6" />}
      {communities.error && (
        <View className="pt-4">
          <ErrorNotice error={communities.error} onRetry={() => communities.refetch()} />
        </View>
      )}

      <ScrollView
        className="mt-4"
        contentContainerClassName="gap-5 pb-8"
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        {rest.length > 0 && (
          <Section title={term.trim() ? 'RESULTS' : 'DISCOVER'} communities={rest} />
        )}
        {joined.length > 0 && <Section title="ALREADY JOINED" communities={joined} />}

        {nothingMatched && (
          <Card className="gap-2">
            <Text variant="bodyStrong">
              {term.trim() ? 'No matches' : 'No communities yet'}
            </Text>
            <Text variant="caption">
              {term.trim()
                ? 'Nothing here goes by that name. Try a shorter search.'
                : 'Nobody has made one. Being first is not a bad place to be.'}
            </Text>
          </Card>
        )}
      </ScrollView>
    </Screen>
  );
}

function Section({ title, communities }: { title: string; communities: Community[] }) {
  return (
    <View className="gap-2">
      <Text variant="overline">{title}</Text>
      {communities.map((community) => (
        <CommunityRow key={community.communityId} community={community} />
      ))}
    </View>
  );
}

function CommunityRow({ community }: { community: Community }) {
  const router = useRouter();
  const queryClient = useQueryClient();

  const join = useMutation({
    mutationFn: async () => {
      try {
        await communityApi.join(community.communityId);
      } catch (error) {
        // Already in it — the desired state, reached by someone else's tap or a stale
        // list. Refusing here would be pedantry about how we got there.
        if (error instanceof ApiError && error.is(Code.ALREADY_MEMBER)) return;
        throw error;
      }
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['communities'] });
      void queryClient.invalidateQueries({ queryKey: ['communityFeed'] });
    },
  });

  return (
    <Card className="gap-3">
      <Pressable
        // Only a member can open a community — its posts and its members are both
        // 403 to everyone else, so the page would be a wall of refusals.
        onPress={
          community.isJoined
            ? () =>
                router.push({
                  pathname: '/community/[communityId]',
                  params: { communityId: community.communityId },
                } as never)
            : undefined
        }
        accessibilityRole={community.isJoined ? 'button' : undefined}
        className="flex-row items-center gap-3 active:opacity-70"
      >
        <Avatar
          source={community.communityAvatar}
          name={community.name}
          colorSeed={community.communityId}
          size={48}
        />
        <View className="flex-1 gap-0.5">
          <Text variant="bodyStrong" numberOfLines={1}>
            {community.name}
          </Text>
          <Text variant="caption" numberOfLines={2}>
            {community.description}
          </Text>
          <Text variant="caption">
            {plural(community.memberCount, 'member')} · {plural(community.postCount, 'post')}
          </Text>
        </View>
      </Pressable>

      {!community.isJoined && (
        <Button label="Join" size="md" loading={join.isPending} onPress={() => join.mutate()} />
      )}
      {!!join.error && <ErrorNotice error={join.error} />}
    </Card>
  );
}

function plural(count: number, noun: string): string {
  return `${count} ${noun}${count === 1 ? '' : 's'}`;
}
