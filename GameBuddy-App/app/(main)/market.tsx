import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { Sparkles } from 'lucide-react-native';
import { useRef, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, View } from 'react-native';
import { billingApi } from '../../src/api/billing';
import { cosmeticsApi } from '../../src/api/cosmetics';
import { ApiError, Code } from '../../src/api/envelope';
import type { Cosmetic, CosmeticStore } from '../../src/api/types';
import { CoinBalance } from '../../src/market/CoinBalance';
import { CoinShop } from '../../src/market/CoinShop';
import { ConsumableShelf } from '../../src/market/ConsumableShelf';
import { SeasonPassTeaser } from '../../src/market/SeasonPassTeaser';
import { EarnCoins } from '../../src/market/EarnCoins';
import { useThemeColors } from '../../src/theme';
import {
  Card,
  ErrorNotice,
  Screen,
  Segment,
  SegmentRow,
  Text,
  feedback,
  messageOf,
  showToast,
} from '../../src/ui';

const STORE_KEY = ['cosmetics'];

/**
 * The Market: frames and banners, bought with earned coins.
 *
 * Selling avatars was the original plan and it was the wrong one — once gamers can
 * upload their own picture, a stock image everyone else can also buy is a weak thing to
 * charge for. A frame composes with whatever photo someone already chose, so buying one
 * never asks anybody to give up their own face.
 */
export default function Market() {
  const colors = useThemeColors();
  const router = useRouter();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<'EARN' | 'SHOP'>('EARN');
  const [kind, setKind] = useState<'FRAME' | 'BANNER'>('FRAME');
  const [failure, setFailure] = useState<string | null>(null);
  /** Set when a purchase was refused for want of coins, which has its own way out. */
  const [shortOfCoins, setShortOfCoins] = useState(false);

  const scrollRef = useRef<ScrollView>(null);
  const coinShopY = useRef(0);

  const store = useQuery({ queryKey: STORE_KEY, queryFn: cosmeticsApi.store });

  // Same key as GoldCard's own query, so react-query serves both from one request. Read
  // here only for the Season Pass flag — the card itself knows nothing about tiers.
  const subscription = useQuery({ queryKey: ['subscription'], queryFn: billingApi.subscription });

  /**
   * Buying answers with the whole refreshed store, so the response is written straight into
   * the cache instead of triggering a refetch. Refetching would leave a window where the
   * balance had dropped but the item still showed as buyable — the shelf disagreeing with
   * itself for a frame or two.
   *
   * The profile is invalidated rather than written, because what changed there is the coin
   * count, and it is not on screen to flicker.
   */
  const applyStore = (next: CosmeticStore) => {
    setFailure(null);
    setShortOfCoins(false);
    queryClient.setQueryData(STORE_KEY, next);
    void queryClient.invalidateQueries({ queryKey: ['me'] });
  };

  /**
   * Running out of coins is not the same kind of event as a request failing.
   *
   * It gets its own state and its own copy, because it is the one failure here with an
   * obvious remedy — and because the remedy must stay the gamer's to choose. Opening a
   * store sheet on somebody who has just been told "no" turns a refusal into a sales
   * pitch at the exact moment they are least receptive to one; this offers a button that
   * scrolls to the packs, and that is all.
   */
  const onError = (error: unknown) => {
    if (error instanceof ApiError && error.is(Code.COIN_NOT_ENOUGH)) {
      setShortOfCoins(true);
      setFailure(null);
      return;
    }
    setShortOfCoins(false);
    setFailure(messageOf(error));
  };

  const showCoinPacks = () => {
    setShortOfCoins(false);
    // -24 so the "COINS" heading is not flush against the top edge on arrival.
    scrollRef.current?.scrollTo({ y: Math.max(0, coinShopY.current - 24), animated: true });
  };

  /**
   * Buying is the only mutation left on this screen.
   *
   * Equip and unequip moved to the Inventory. They were never the same kind of act as a
   * purchase — one spends coins, the other changes how you look — and having a single
   * control in a single column do either depending on a flag meant the shelf could not be
   * read at a glance. See `app/(main)/inventory.tsx`.
   *
   * react-query hands `onSuccess` the mutation's variables alongside the response, so the
   * bought id is available to name the item — and the name is read from the *returned*
   * store rather than from the row that was tapped, because the response is the server's
   * account of what happened and the row is only what the client believed beforehand.
   */
  const buy = useMutation({
    mutationFn: cosmeticsApi.buy,
    onSuccess: (next: CosmeticStore, id: string) => {
      applyStore(next);

      const bought = [...next.frames, ...next.banners].find((item) => item.id === id);

      feedback.purchase();
      showToast({
        id: `bought:${id}`,
        title: bought ? `${bought.name} is yours` : 'Bought',
        // Says where it went, and goes there. Buying no longer puts the thing on, so
        // without this the purchase ends with an item the buyer cannot find.
        body: 'Put it on from your Inventory.',
        icon: Sparkles,
        tone: 'gold',
        onPress: () => router.push('/inventory'),
      });
    },
    onError,
  });

  const busy = buy.isPending;
  const all = kind === 'FRAME' ? (store.data?.frames ?? []) : (store.data?.banners ?? []);

  /*
   * Membership items are not on this shelf.
   *
   * The Gold frame and banner are not merchandise — the server refuses to sell them at any
   * price — so a shop was the wrong place to meet them. Every row here answers "what can I
   * buy"; theirs answered "not this", which is a worse advert for Gold than not appearing
   * at all, and it cost the shelf two of its slots to say so. They now live on the Gold
   * screen, where the art is a reason to subscribe rather than a locked row. See
   * `GoldCosmetics` in `app/(main)/gold.tsx`.
   */
  const items = all.filter((item) => !item.membershipOnly);

  return (
    <Screen scroll edges={['top']} scrollRef={scrollRef}>
      <View className="flex-row items-end justify-between pb-5 pt-8">
        <View className="gap-1">
          <Text variant="overline">MARKET</Text>
          <Text variant="title">Show off</Text>
        </View>
        {/* The balance goes in the header rather than beside each price: the one
            question a gamer has on this screen is what they can afford. It counts to its
            new value rather than jumping — see `src/market/CoinBalance.tsx`. */}
        <CoinBalance />
      </View>

      {/*
        Two halves, not one scroll.
        
        This screen was doing four jobs at once — selling coins for money, selling the
        subscription, *giving* coins away, and selling cosmetics — stacked into seven
        sections. The tab is called "Show off" and the cosmetics were last, below
        everything; the streak, which is the only reason to come back tomorrow, was the
        third of five identical rows.
        
        Earn and Shop are genuinely different intents, so they are genuinely different
        views. Gold stays above both because it is the one thing that belongs to each:
        it is bought (Shop) and it pays a monthly stipend (Earn).
      */}
      <GoldCard />

      <View className="flex-row gap-2 pb-6">
        <Segment label="Earn" active={tab === 'EARN'} onPress={() => setTab('EARN')} />
        <Segment label="Shop" active={tab === 'SHOP'} onPress={() => setTab('SHOP')} />
      </View>

      {tab === 'EARN' && <EarnCoins />}

      {tab === 'SHOP' && (
        <>
          <CoinShop
            balance={store.data?.coins ?? 0}
            onLayoutY={(y) => {
              coinShopY.current = y;
            }}
          />

          <ConsumableShelf balance={store.data?.coins ?? 0} />

          {/* Cosmetics are what everything else is spent on, and the one section that
              keeps working with an empty balance — the free frames are here. */}
          <View className="gap-1 pb-3">
            <Text variant="overline">FRAMES AND BANNERS</Text>
            <Text variant="caption">Worn on your profile, and on every card you appear in.</Text>
          </View>

          <View className="pb-5">
            <SegmentRow>
              <Segment label="Frames" active={kind === 'FRAME'} onPress={() => setKind('FRAME')} />
              <Segment label="Banners" active={kind === 'BANNER'} onPress={() => setKind('BANNER')} />
            </SegmentRow>
          </View>

          {store.isPending && <ActivityIndicator color={colors.primary} />}
          {store.error && <ErrorNotice error={store.error} onRetry={() => store.refetch()} />}

          {failure && (
            <Card className="mb-3">
              <Text variant="body" className="text-danger">
                {failure}
              </Text>
            </Card>
          )}

          {shortOfCoins && (
            <Card className="mb-3">
              <Text variant="bodyStrong">Not enough coins</Text>
              <Text variant="caption" className="mt-1">
                You have {store.data?.coins ?? 0}. Badges earn coins, or you can top up.
              </Text>
              <Pressable
                onPress={showCoinPacks}
                accessibilityRole="button"
                className="mt-3 self-start rounded-full bg-primary/15 px-4 py-2 active:opacity-70"
              >
                <Text variant="label" className="text-primary">
                  See coin packs
                </Text>
              </Pressable>
            </Card>
          )}

          {/* Conditionally *mounted*, never conditionally classed. Swapping this between
              `hidden` and `gap-3 pb-8` would change which class keys exist between renders,
              which is the defect `src/ui/hairline.ts` documents. */}
          <View className="gap-3 pb-8">
            {items.map((item) => (
              <Row
                key={item.id}
                item={item}
                balance={store.data?.coins ?? 0}
                busy={busy}
                onBuy={() => buy.mutate(item.id)}
              />
            ))}

            {/* The way to the other half of this. Wearing moved out of the shop, so the
                shop has to say where it went — a shelf full of "Owned" with no exit is how
                somebody concludes their purchase did nothing. */}
            <Pressable
              onPress={() => router.push('/inventory')}
              accessibilityRole="button"
              className="items-center rounded-card py-3 active:opacity-70"
            >
              <Text variant="label" className="text-primary">
                Equip what you own in your Inventory
              </Text>
            </Pressable>

            {/* Last, not second. It says "Coming soon", and a placeholder above the things
                that actually work was the single biggest waste of space on this screen.
                Server-switchable: absent, not empty, when the flag is off. */}
            {subscription.data?.seasonPassTeaser && <SeasonPassTeaser />}
          </View>
        </>
      )}
    </Screen>
  );
}

/**
 * One item on the shelf.
 *
 * Three states, and they are the whole interaction: Buy when it is for sale and affordable,
 * a disabled "Not enough coins" when it is not, and a disabled "Owned" when there is nothing
 * left to sell. Nothing here changes how anybody looks — this is a shop, and putting things
 * on happens in the Inventory.
 *
 * **"Owned" is a disabled button rather than no button.** A bare label in the column every
 * other row fills with a control reads as a rendering gap; a greyed-out button says the
 * control exists and is spent. It is the same shape as the unaffordable state for the same
 * reason — both mean "not this one, and not because something is broken".
 *
 * `owned` comes from the server and is not re-derived from `price`, because a free
 * cosmetic is owned by everyone without any purchase existing. Deciding that here as
 * well would be the same rule in two places, and the free frames — the only ones a
 * gamer with no coins can wear — are exactly what a disagreement would lock.
 */
function Row({
  item,
  balance,
  busy,
  onBuy,
}: {
  item: Cosmetic;
  balance: number;
  busy: boolean;
  onBuy: () => void;
}) {
  const isBanner = item.kind === 'BANNER';

  /*
   * Affordability only decides between Buy and "Not enough coins" — owned items never reach
   * that question, because there is nothing to charge for.
   *
   * This does **not** replace the server's refusal. `COIN_NOT_ENOUGH` is still handled in
   * `onError` above and still raises the "Not enough coins" card, because the balance here
   * is a cached figure and the server is the only thing that actually knows: a boost bought
   * on the deck in another tab, or a price that moved, both land between this render and
   * the tap. Disabling the button is the courtesy; the server check is the rule.
   */
  const affordable = item.price <= balance;

  /*
   * No membership case here any more: those items are filtered out above and shown on the
   * Gold screen instead, so everything reaching this row is genuinely for sale or already
   * free. If that filter is ever loosened, this has to learn "With Gold" again — a
   * membership item is stored at price zero and would otherwise read as "Free", which is
   * the one thing it is not.
   */
  const cost = item.price === 0 ? 'Free' : `${item.price} coins`;

  /*
   * The price wears the money colour, like every other price in the Market.
   *
   * It was `muted` — the same grey as the "Animated" tag beside it — which made the one
   * number a shopper is actually looking for the least legible thing in the row. Gold is
   * already what `ConsumableShelf` uses for its costs and `EarnCoins` for its rewards, so
   * this is the shelf catching up with the rest of the screen rather than a new idea.
   *
   * "Free" stays muted. Gold means coins here, and a free frame costs none — colouring it
   * like a price would be the same mistake in the other direction.
   */
  const costClass = cost === 'Free' ? 'text-muted' : 'font-medium text-gold';

  return (
    <Card>
      <View className="flex-row items-center gap-4">
        {/* A frame is shown against a plain disc so the ring reads as a ring rather
            than as a picture with a hole punched in it. */}
        <View
          className={
            isBanner
              ? 'h-16 w-24 overflow-hidden rounded-xl bg-raised'
              : 'h-16 w-16 items-center justify-center rounded-full bg-raised'
          }
        >
          <Image
            source={{ uri: item.image }}
            style={{ width: '100%', height: '100%' }}
            contentFit={isBanner ? 'cover' : 'contain'}
            autoplay
            transition={150}
          />
        </View>

        {/*
          Both lines truncate rather than wrap, which they now can afford to do: this column
          is about 105dp when the control reads "Not enough coins", and a bare price fits
          in that on one line where "Animated · 600 coins" did not.

          The "Animated" tag is gone from here on purpose — the preview to the left is
          *playing*, so the word was captioning something already on screen, and it was
          crowding the one thing that does need reading. It survives in the control's
          accessibility label below, where the animation cannot be seen.
        */}
        <View className="min-w-0 flex-1 gap-0.5">
          <Text variant="bodyStrong" numberOfLines={1}>
            {item.name}
          </Text>
          <Text variant="caption" numberOfLines={1} className={costClass}>
            {cost}
          </Text>
        </View>

        <Pressable
          // Owned is as disabled as unaffordable, and for a better reason: there is simply
          // nothing left to sell. The tap does nothing either way, so neither state pretends
          // otherwise.
          disabled={busy || item.owned || !affordable}
          onPress={onBuy}
          accessibilityRole="button"
          // "animated" is carried here and nowhere else on the row. Dropping the visible
          // tag was right — the preview is playing, so sighted people can see it — but
          // that is exactly the argument for keeping it in the label, because it is the
          // one difference between two frames that a screen reader could not otherwise
          // report.
          accessibilityLabel={
            item.owned
              ? `${item.name}${item.animated ? ', animated' : ''}, owned. Equip it from your Inventory.`
              : affordable
                ? `Buy ${item.name}${item.animated ? ', animated' : ''}, ${item.price} coins`
                : `${item.name}${item.animated ? ', animated' : ''}, ${item.price} coins, not enough coins`
          }
          // Spelled out for the screen reader as well as greyed for everyone else. A
          // control that is only *visually* disabled is announced as tappable and then
          // does nothing, which is worse than one that was never offered.
          accessibilityState={{ disabled: busy || item.owned || !affordable }}
          /*
           * One key set across all three branches, only the values moving. An earlier
           * version swapped `bg-primary` for `border-2 border-primary`, which is the
           * appearing-and-disappearing class-key defect `src/ui/hairline.ts` documents —
           * it happened to survive because the two states rarely alternate in place, but
           * it is the same bug. Every branch now carries a border and a background, and
           * the transparent ones are declared rather than omitted, which also makes the
           * three pills exactly the same height.
           */
          className={[
            'shrink-0 rounded-full border-2 px-4 py-2 active:opacity-70',
            item.owned || !affordable
              ? 'border-line bg-transparent'
              : 'border-transparent bg-primary',
          ].join(' ')}
        >
          <Text
            variant="label"
            numberOfLines={1}
            className={item.owned || !affordable ? 'text-muted' : 'text-white'}
          >
            {item.owned ? 'Owned' : affordable ? 'Buy' : 'Not enough coins'}
          </Text>
        </Pressable>
      </View>
    </Card>
  );
}


/**
 * The membership, at the top of the Market.
 *
 * One of the four places the paywall is reachable from, and the least urgent of them —
 * somebody on this screen is already thinking about how their profile looks, which is
 * the mood Gold's cosmetics speak to. The pressing ones are the limit sheet and the
 * admirers screen, where the thing Gold removes is the thing currently in the way.
 *
 * Shows status rather than a pitch once somebody has subscribed. A paywall that keeps
 * selling to an existing member reads as a system that does not know who they are.
 */
function GoldCard() {
  const router = useRouter();
  const subscription = useQuery({
    queryKey: ['subscription'],
    queryFn: billingApi.subscription,
  });

  const isGold = subscription.data?.tier === 'GOLD';

  return (
    <Pressable
      onPress={() => router.push('/gold')}
      accessibilityRole="button"
      accessibilityLabel={isGold ? 'Your Gold membership' : 'Get GameBuddy Gold'}
      className="mb-5 active:opacity-80"
    >
      <Card>
        <View className="flex-row items-center justify-between gap-4">
          <View className="flex-1 gap-1">
            <Text variant="overline" className="text-gold">
              GAMEBUDDY GOLD
            </Text>
            <Text variant="heading">{isGold ? 'You are a member' : 'See who likes you'}</Text>
            <Text variant="caption">
              {isGold
                ? 'The Gold frame and banner are yours while your membership lasts.'
                : 'No daily limit, advanced filters, and the Gold frame and banner.'}
            </Text>
          </View>
          <View className="h-2 w-2 rotate-45 border-r-2 border-t-2 border-muted" />
        </View>
      </Card>
    </Pressable>
  );
}
