import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { ActivityIndicator, Alert, Pressable, ScrollView, View } from 'react-native';
import { socialApi } from '../../src/api/social';
import type { GamerSummary } from '../../src/api/types';
import { useThemeColors } from '../../src/theme';
import { BackHeader, Button, Card, ErrorNotice, FramedAvatar, Screen, Text } from '../../src/ui';

/**
 * Everybody you have added, on a screen of their own.
 *
 * This used to be a section at the bottom of Profile, under a "FRIENDS" heading, which
 * made the profile two things at once: a description of you, and a list of other people.
 * The counter in the stat row said the same number twice, and on an account with any
 * friends at all the actual profile — games, platforms, keywords — was pushed off the
 * screen by names.
 *
 * So the count became the way in, the same as Badges beside it. Pending friend requests
 * deliberately stay on Profile: they are the one thing there that is waiting on an
 * answer, and burying an actionable prompt one tap deeper is how it gets missed.
 */
export default function Friends() {
  const router = useRouter();
  const colors = useThemeColors();
  const queryClient = useQueryClient();

  const query = useQuery({ queryKey: ['friends'], queryFn: socialApi.friends });
  const friends = query.data ?? [];

  /**
   * Removing a friend drops them back to being a match.
   *
   * The match survives on purpose: unfriending is not blocking, and silently severing a
   * mutual match as well would be a bigger action than the button says.
   */
  const remove = useMutation({
    mutationFn: (userId: string) => socialApi.remove(userId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['friends'] });
      // The messages screen splits on friendship, so this row moves from one section to
      // the other. Without this it stays under Friends until the next poll.
      void queryClient.invalidateQueries({ queryKey: ['inbox'] });
      // Profile shows the count this screen is reached from.
      void queryClient.invalidateQueries({ queryKey: ['me'] });
    },
  });

  const confirmRemove = (person: GamerSummary) =>
    Alert.alert(
      `Remove ${person.username}?`,
      'They go back to being a match — you can still message each other, and either of you can send a new friend request.',
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Remove', style: 'destructive', onPress: () => remove.mutate(person.userId) },
      ],
    );

  return (
    <Screen edges={['top']}>
      <BackHeader title="Friends" subtitle={friends.length > 0 ? `${friends.length}` : undefined} />

      {query.isPending && <ActivityIndicator color={colors.primary} className="mt-6" />}

      <ScrollView
        className="mt-4"
        contentContainerClassName="gap-3 pb-8"
        showsVerticalScrollIndicator={false}
      >
        {query.error && <ErrorNotice error={query.error} onRetry={() => query.refetch()} />}
        {remove.error && <ErrorNotice error={remove.error} />}

        {!query.isPending && friends.length === 0 && (
          <Card className="gap-1">
            <Text variant="bodyStrong">No friends yet</Text>
            <Text variant="caption">
              You can add someone as a friend once you have matched with them.
            </Text>
          </Card>
        )}

        {friends.map((person) => (
          <Card key={person.userId} className="flex-row items-center gap-3 p-4">
            {/* The name and picture open them; only the button does anything else. A row
                that is entirely one big target would make Remove the easiest thing on it
                to hit by accident. The padding stays on the card so this reads exactly
                as it did before it became tappable. */}
            <Pressable
              onPress={() =>
                router.push({
                  pathname: '/gamer/[userId]',
                  params: { userId: person.userId, username: person.username },
                } as never)
              }
              accessibilityRole="button"
              accessibilityLabel={`View ${person.username}'s profile`}
              className="flex-1 flex-row items-center gap-3 active:opacity-70"
            >
              <FramedAvatar
                frame={person.frame}
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
            </Pressable>

            {/* Ghost, and it asks first. Removing a friend is not dangerous — they stay a
                match — but it is not undoable in one tap either, and a solid button next
                to somebody's name reads as the point of the row. */}
            <Button
              label="Remove"
              variant="ghost"
              size="md"
              className="px-3"
              disabled={remove.isPending}
              onPress={() => confirmRemove(person)}
            />
          </Card>
        ))}
      </ScrollView>
    </Screen>
  );
}
