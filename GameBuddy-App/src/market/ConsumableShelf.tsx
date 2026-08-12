import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { Pressable, View } from 'react-native';
import { matchApi } from '../api/match';
import type { Consumables } from '../api/types';
import { Text, messageOf } from '../ui';

/** Mirrors Consumable.java and BoostPolicy. A price shown wrong is worse than not shown. */
const ITEMS = [
  {
    code: 'SUPER_LIKE' as const,
    icon: '💫',
    title: 'Super Like',
    detail: 'They are told straight away, and it stands out.',
    cost: 100,
  },
  {
    code: 'EXTRA_LIKES' as const,
    icon: '❤️',
    title: '5 more likes today',
    detail: 'On top of your daily cap. Today only.',
    cost: 200,
  },
];

/**
 * Things bought with coins that get used up.
 *
 * Sits between the cosmetics shelf and the coin packs: these are what the earned currency
 * is *for*, and a gamer who has just seen a frame they cannot afford is exactly the person
 * who should see that coins do more than buy frames.
 *
 * Boost and Rewind are not listed. They are bought where they are used — the deck — and a
 * boost bought in a shop, twenty minutes before anyone opens the app, is thirty minutes
 * spent on nobody. Unlocking an admirer is not here either, for the stronger version of
 * the same reason: it is bought against a face, on the screen showing that face.
 */
export function ConsumableShelf({ balance }: { balance: number }) {
  const queryClient = useQueryClient();
  const router = useRouter();

  const buy = useMutation({
    mutationFn: matchApi.buyConsumable,
    onSuccess: (next: Consumables) => {
      // Everything that shows a balance or an allowance has just moved.
      void queryClient.invalidateQueries({ queryKey: ['cosmetics'] });
      void queryClient.invalidateQueries({ queryKey: ['me'] });
      void queryClient.invalidateQueries({ queryKey: ['allowance'] });
      void queryClient.invalidateQueries({ queryKey: ['earn'] });
      void queryClient.setQueryData(['consumables'], next);
    },
  });

  return (
    <View className="gap-3 pb-6">
      <View className="gap-1">
        <Text variant="overline">USE YOUR COINS</Text>
        <Text variant="caption">Spent when you use them, not owned forever.</Text>
      </View>

      {buy.error && (
        <View className="rounded-card border border-danger/40 bg-danger/10 p-3">
          <Text variant="caption" className="text-danger">
            {messageOf(buy.error)}
          </Text>
        </View>
      )}

      {ITEMS.map((item) => {
        const affordable = balance >= item.cost;

        return (
          <Pressable
            key={item.code}
            onPress={() => buy.mutate(item.code)}
            disabled={!affordable || buy.isPending}
            accessibilityRole="button"
            accessibilityLabel={`${item.title}, ${item.cost} coins`}
            accessibilityState={{ disabled: !affordable || buy.isPending }}
            className={[
              'flex-row items-center gap-3 rounded-card border border-line bg-raised p-4',
              affordable ? 'active:opacity-70' : 'opacity-60',
            ].join(' ')}
          >
            <View className="h-10 w-10 items-center justify-center rounded-full bg-surface">
              <Text className="text-[16px] leading-[20px]">{item.icon}</Text>
            </View>

            <View className="flex-1 gap-0.5">
              <Text variant="bodyStrong">{item.title}</Text>
              <Text variant="caption">{item.detail}</Text>
            </View>

            <Text
              className={[
                'font-bold text-[15px] leading-[20px]',
                affordable ? 'text-brand' : 'text-muted',
              ].join(' ')}
            >
              {item.cost}
            </Text>
          </Pressable>
        );
      })}

      {/* Named rather than hidden. Somebody comparing 200 coins for five likes against a
          subscription that removes the cap entirely should be able to make that comparison
          — and the ones who keep buying five at a time are exactly who Gold is for. */}
      <Pressable
        onPress={() => router.push('/gold')}
        accessibilityRole="button"
        className="items-center rounded-card py-2 active:opacity-70"
      >
        <Text variant="caption" className="text-brand">
          Buying likes often? Gold removes the limit
        </Text>
      </Pressable>
    </View>
  );
}
