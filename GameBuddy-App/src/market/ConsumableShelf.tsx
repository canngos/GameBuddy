import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { Heart, Sparkles } from 'lucide-react-native';
import { Pressable, View } from 'react-native';
import { matchApi } from '../api/match';
import type { Consumables } from '../api/types';
import { useUpper } from '../i18n/case';
import { useT } from '../i18n/useT';
import { Text, feedback, messageOf, showToast } from '../ui';
import { ConsumableIcon, type ConsumableArt } from './ConsumableIcon';

/**
 * Mirrors Consumable.java and BoostPolicy. A price shown wrong is worse than not shown.
 * Codes and prices only — the words come from the dictionary at render time.
 */
const ITEMS = [
  // `icon` is the Lucide glyph the purchase toast shows; `art` is the drawn mark in the
  // row. Two fields rather than one because `Toast.icon` is typed `LucideIcon`, and a
  // toast is not the place for a product picture anyway.
  { code: 'SUPER_LIKE' as const, icon: Sparkles, art: 'superLike' as ConsumableArt, cost: 100 },
  { code: 'EXTRA_LIKES' as const, icon: Heart, art: 'extraLikes' as ConsumableArt, cost: 200 },
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
  const t = useT();
  const upper = useUpper();

  const labels: Record<(typeof ITEMS)[number]['code'], { title: string; detail: string }> = {
    SUPER_LIKE: { title: t.market.consumables.superLike, detail: t.market.consumables.superLikeDetail },
    EXTRA_LIKES: { title: t.market.consumables.extraLikes, detail: t.market.consumables.extraLikesDetail },
  };

  const buy = useMutation({
    mutationFn: matchApi.buyConsumable,
    onSuccess: (next: Consumables, code: (typeof ITEMS)[number]['code']) => {
      // Everything that shows a balance or an allowance has just moved.
      void queryClient.invalidateQueries({ queryKey: ['cosmetics'] });
      void queryClient.invalidateQueries({ queryKey: ['me'] });
      void queryClient.invalidateQueries({ queryKey: ['allowance'] });
      void queryClient.invalidateQueries({ queryKey: ['earn'] });
      // There used to be a `setQueryData(['consumables'], next)` here. Nothing in the app
      // reads that key — grepped — so it was writing the response into a cache entry no
      // component subscribes to. Removed rather than wired up: the counts this shelf shows
      // come from `['cosmetics']`, which is invalidated two lines above.

      /*
       * These are the one purchase with nothing to show for it afterwards.
       *
       * A frame appears on the shelf as owned and a boost turns its button into a
       * countdown, so both leave evidence. A super like sits in an inventory nobody is
       * looking at — the coins simply left. Without this the tap was indistinguishable from
       * a tap that did nothing, which is why it needs saying out loud more than the others
       * do, not less.
       */
      const item = ITEMS.find((candidate) => candidate.code === code);

      feedback.purchase();
      showToast({
        // This is a mutation callback, not render — it runs once per purchase, at event
        // time, so the clock read is fine; the rule cannot see past the config object.
        // eslint-disable-next-line react-hooks/purity
        id: `consumable:${code}:${Date.now()}`,
        title: item ? t.market.consumables.addedTitle(labels[item.code].title) : t.market.consumables.added,
        body: t.market.consumables.addedBody,
        icon: item?.icon ?? Sparkles,
        tone: 'accent',
      });
    },
  });

  return (
    <View className="gap-3 pb-6">
      <Text variant="overline">{upper(t.market.consumables.header)}</Text>

      {buy.error && (
        <View className="rounded-card border border-danger/40 bg-danger/10 p-3">
          <Text variant="caption" className="text-danger">
            {messageOf(buy.error)}
          </Text>
        </View>
      )}

      {ITEMS.map((item) => {
        const affordable = balance >= item.cost;
        const { title, detail } = labels[item.code];

        return (
          <Pressable
            key={item.code}
            onPress={() => buy.mutate(item.code)}
            disabled={!affordable || buy.isPending}
            accessibilityRole="button"
            accessibilityLabel={t.market.consumables.itemA11y(title, item.cost)}
            accessibilityState={{ disabled: !affordable || buy.isPending }}
            className={[
              'flex-row items-center gap-3 rounded-card border border-line bg-raised p-4',
              affordable ? 'active:opacity-70' : 'opacity-60',
            ].join(' ')}
          >
            {/* Both of these buy a *like*, so both take the accent — the one colour in the
                palette that is allowed to mean that, and what separates this shelf from the
                gold coin packs above it at a glance. 26dp to match those. */}
            <View className="h-10 w-10 items-center justify-center rounded-full bg-surface">
              <ConsumableIcon art={item.art} size={26} />
            </View>

            <View className="flex-1 gap-0.5">
              <Text variant="bodyStrong">{title}</Text>
              <Text variant="caption">{detail}</Text>
            </View>

            <Text
              className={[
                'font-bold text-[15px] leading-[20px]',
                affordable ? 'text-gold' : 'text-muted',
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
        <Text variant="caption" className="text-gold">
          {t.market.consumables.goldHint}
        </Text>
      </Pressable>
    </View>
  );
}
