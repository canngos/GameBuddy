import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams } from 'expo-router';
import { ActivityIndicator, Alert, ScrollView, View } from 'react-native';
import { communityApi } from '../../../src/api/community';
import type { CommunityMember } from '../../../src/api/types';
import { useSession } from '../../../src/session/store';
import { useThemeColors } from '../../../src/theme';
import {
  BackHeader,
  Button,
  Card,
  ErrorNotice,
  FramedAvatar,
  Screen,
  Text,
} from '../../../src/ui';

/**
 * Who is in a community, and — if you own it — who you can hand it to.
 *
 * Transfer is here rather than in the community's overflow menu because it needs a
 * person, and this is the only screen that has the list of them.
 */
export default function CommunityMembers() {
  const colors = useThemeColors();
  const queryClient = useQueryClient();
  const myId = useSession((s) => s.userId);
  const { communityId, name } = useLocalSearchParams<{ communityId: string; name?: string }>();

  const members = useQuery({
    queryKey: ['communityMembers', communityId],
    queryFn: () => communityApi.members(communityId),
  });

  const isOwner = (members.data ?? []).some((m) => m.isOwner && m.userId === myId);

  const transfer = useMutation({
    mutationFn: (newOwnerId: string) =>
      communityApi.transferOwnership(communityId, newOwnerId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['communityMembers', communityId] });
    },
  });

  function confirmTransfer(member: CommunityMember) {
    Alert.alert(
      `Make ${member.gamerUsername} the owner?`,
      'They will be able to delete the community and everything in it. You will be an ordinary member, and only they can hand it back.',
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Hand it over', style: 'destructive', onPress: () => transfer.mutate(member.userId) },
      ],
    );
  }

  // The owner first, then everyone else. Alphabetical below that, since the backend
  // returns the join table's order, which is nobody's idea of an order.
  const sorted = [...(members.data ?? [])].sort((a, b) => {
    if (a.isOwner !== b.isOwner) return a.isOwner ? -1 : 1;
    return a.gamerUsername.localeCompare(b.gamerUsername);
  });

  return (
    <Screen edges={['top']}>
      <BackHeader title="Members" subtitle={name || undefined} />

      {members.isPending && <ActivityIndicator color={colors.primary} />}
      {members.error && <ErrorNotice error={members.error} onRetry={() => members.refetch()} />}
      {!!transfer.error && (
        <View className="pb-3">
          <ErrorNotice error={transfer.error} />
        </View>
      )}

      <ScrollView contentContainerClassName="gap-2 pb-8" showsVerticalScrollIndicator={false}>
        {sorted.map((member) => (
          <Card key={member.userId} className="gap-3">
            <View className="flex-row items-center gap-3">
              <FramedAvatar
                frame={member.frame}
                source={member.avatar}
                name={member.gamerUsername}
                colorSeed={member.userId}
                size={44}
              />
              <View className="flex-1 gap-0.5">
                <Text variant="bodyStrong">{member.gamerUsername}</Text>
                <Text variant="caption">
                  {[member.isOwner ? 'Owner' : null, member.userId === myId ? 'You' : null]
                    .filter(Boolean)
                    .join(' · ') || 'Member'}
                </Text>
              </View>
            </View>

            {isOwner && !member.isOwner && (
              <Button
                label="Make owner"
                variant="secondary"
                size="md"
                disabled={transfer.isPending}
                onPress={() => confirmTransfer(member)}
              />
            )}
          </Card>
        ))}
      </ScrollView>
    </Screen>
  );
}
