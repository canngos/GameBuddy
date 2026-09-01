import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { Sparkles } from 'lucide-react-native';
import { memo, useCallback, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { billingApi } from '../../src/api/billing';
import { profileApi } from '../../src/api/catalogue';
import { cosmeticsApi } from '../../src/api/cosmetics';
import { ApiError, Code } from '../../src/api/envelope';
import type { Bundle, Cosmetic, CosmeticKind, CosmeticStore } from '../../src/api/types';
import { useUpper } from '../../src/i18n/case';
import { useT } from '../../src/i18n/useT';
import { CoinBalance } from '../../src/market/CoinBalance';
import { CoinShop } from '../../src/market/CoinShop';
import { BundlePreview } from '../../src/market/BundlePreview';
import { BundleShelf } from '../../src/market/BundleShelf';
import { ConsumableShelf } from '../../src/market/ConsumableShelf';
import { CosmeticPreview } from '../../src/market/CosmeticPreview';
import { EarnCoins } from '../../src/market/EarnCoins';
import { useGamerGradient, useThemeColors } from '../../src/theme';
import { ThemeSwatch } from '../../src/market/ThemeSwatch';
import {
  Card,
  ErrorNotice,
  GradientView,
  Screen,
  Segment,
  SegmentRow,
  Text,
  feedback,
  messageOf,
  showToast,
} from '../../src/ui';

const STORE_KEY = ['cosmetics'];

/** The image fills its already-sized box. A constant, so it is not a new prop per row. */
const FILL = StyleSheet.create({ fill: { width: '100%', height: '100%' } }).fill;

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
  const t = useT();
  const upper = useUpper();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<'EARN' | 'SHOP'>('EARN');
  const [kind, setKind] = useState<CosmeticKind>('FRAME');
  const [failure, setFailure] = useState<string | null>(null);
  /** Set when a purchase was refused for want of coins, which has its own way out. */
  const [shortOfCoins, setShortOfCoins] = useState(false);
  /**
   * The item being looked at up close, or null.
   *
   * Holds the item rather than its id so the sheet keeps rendering the thing that was
   * tapped even as the store refetches underneath it — an id would have to be looked up
   * again on every render, and a purchase rewrites that list.
   */
  const [preview, setPreview] = useState<Cosmetic | null>(null);
  /** The set being looked at up close. Its own state: a bundle is not one of the items. */
  const [bundlePreview, setBundlePreview] = useState<Bundle | null>(null);

  const scrollRef = useRef<ScrollView>(null);
  const coinShopY = useRef(0);

  const store = useQuery({ queryKey: STORE_KEY, queryFn: cosmeticsApi.store });

  // The shopper's own face, for the preview sheet. Same key the profile and the deck's
  // filters already use, so this is served from cache rather than being a new request.
  const me = useQuery({ queryKey: ['me'], queryFn: profileApi.me });

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
    // Both answers below are rendered on the shelf, behind the preview. Closing it is what
    // lets the refusal actually be read — and what puts the "see coin packs" way out within
    // reach of the tap that follows.
    setPreview(null);
    setBundlePreview(null);
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
      // The preview exists to help decide, and the decision has now been made. Leaving it
      // up would show a "Buy" button for something already bought.
      setPreview(null);

      const bought = [...next.frames, ...next.banners, ...next.themes].find((item) => item.id === id);

      feedback.purchase();
      showToast({
        id: `bought:${id}`,
        title: bought ? t.market.shop.boughtTitle(bought.name) : t.market.shop.bought,
        // Says where it went, and goes there. Buying no longer puts the thing on, so
        // without this the purchase ends with an item the buyer cannot find.
        body: t.market.shop.boughtBody,
        icon: Sparkles,
        tone: 'gold',
        onPress: () => router.push('/inventory'),
      });
    },
    onError,
  });

  /**
   * Buying a set. Its own mutation because it hits its own endpoint, but it lives here
   * rather than in the shelf so the shelf and the preview sheet share one — two would mean
   * two toasts and two in-flight flags for the same purchase.
   */
  const buyBundle = useMutation({
    mutationFn: cosmeticsApi.buyBundle,
    onSuccess: (next: CosmeticStore, id: string) => {
      applyStore(next);
      setBundlePreview(null);

      const bought = next.bundles.find((b) => b.id === id);
      feedback.purchase();
      showToast({
        id: `bundle:${id}`,
        title: bought ? t.market.shop.boughtTitle(bought.name) : t.market.shop.bought,
        body: t.market.shop.boughtBody,
        icon: Sparkles,
        tone: 'gold',
        onPress: () => router.push('/inventory'),
      });
    },
    onError,
  });

  const busy = buy.isPending || buyBundle.isPending;
  // Memoised so it is a stable input to the memo below; the `?? []` fallback minted a
  // fresh array every render while the store was still loading.
  const all = useMemo(
    () => {
      const shelves: Record<CosmeticKind, Cosmetic[] | undefined> = {
        FRAME: store.data?.frames,
        BANNER: store.data?.banners,
        THEME: store.data?.themes,
      };
      return shelves[kind] ?? [];
    },
    [kind, store.data],
  );

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
  const items = useMemo(() => all.filter((item) => !item.membershipOnly), [all]);

  // Stable, so the memoised rows below can bail out instead of rebuilding the whole shelf
  // whenever anything on this screen changes.
  // `mutate`, not the mutation object: the object is a fresh literal every render, and
  // these rows are .map()ed into a ScrollView, so all of them re-rendered together.
  const { mutate: buyMutate } = buy;
  const onBuy = useCallback((id: string) => buyMutate(id), [buyMutate]);
  const onPreview = useCallback((item: Cosmetic) => setPreview(item), []);
  const onBundlePreview = useCallback((bundle: Bundle) => setBundlePreview(bundle), []);

  return (
    <Screen scroll edges={['top']} scrollRef={scrollRef}>
      <View className="flex-row items-end justify-between pb-5 pt-8">
        <View className="gap-1">
          <Text variant="overline">{upper(t.market.shop.header)}</Text>
          <Text variant="title">{t.market.shop.title}</Text>
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
        <Segment label={t.market.shop.earnTab} active={tab === 'EARN'} onPress={() => setTab('EARN')} />
        <Segment label={t.market.shop.shopTab} active={tab === 'SHOP'} onPress={() => setTab('SHOP')} />
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

          {/* Sets, above the individual shelves: a bundle is an offer about rows further
              down, and mixing it in with them would make those look priced twice. */}
          <BundleShelf
            bundles={store.data?.bundles ?? []}
            balance={store.data?.coins ?? 0}
            busy={busy}
            onBuy={buyBundle.mutate}
            onPreview={onBundlePreview}
          />

          {/* Cosmetics are what everything else is spent on, and the one section that
              keeps working with an empty balance — the free frames are here. */}
          <View className="gap-1 pb-3">
            <Text variant="overline">{upper(t.market.shop.framesAndBanners)}</Text>
            <Text variant="caption">{t.market.shop.framesAndBannersBlurb}</Text>
          </View>

          <View className="pb-5">
            <SegmentRow>
              <Segment
                label={t.market.shop.frames}
                active={kind === 'FRAME'}
                onPress={() => setKind('FRAME')}
              />
              <Segment
                label={t.market.shop.banners}
                active={kind === 'BANNER'}
                onPress={() => setKind('BANNER')}
              />
              <Segment
                label={t.market.shop.themes}
                active={kind === 'THEME'}
                onPress={() => setKind('THEME')}
              />
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
              <Text variant="bodyStrong">{t.market.shop.notEnoughCoins}</Text>
              <Text variant="caption" className="mt-1">
                {t.market.shop.notEnoughCoinsBody(store.data?.coins ?? 0)}
              </Text>
              <Pressable
                onPress={showCoinPacks}
                accessibilityRole="button"
                className="mt-3 self-start rounded-full bg-primary/15 px-4 py-2 active:opacity-70"
              >
                <Text variant="label" className="text-primary">
                  {t.market.shop.seeCoinPacks}
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
                onBuy={onBuy}
                onPreview={onPreview}
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
                {t.market.shop.equipInInventory}
              </Text>
            </Pressable>
          </View>
        </>
      )}

      {/* A `Modal`, so it is unaffected by sitting inside this screen's ScrollView — see the
          component for why that rules out the app's usual sheet pattern here. */}
      {/* Buying from the sheet closes it — the decision has been made, and leaving it up
          would offer a Buy button for something already owned. */}
      <BundlePreview
        bundle={bundlePreview}
        avatar={me.data?.avatar}
        username={me.data?.username}
        userId={me.data?.userId}
        affordable={(bundlePreview?.price ?? 0) <= (store.data?.coins ?? 0)}
        busy={busy}
        onBuy={(id) => {
          setBundlePreview(null);
          buyBundle.mutate(id);
        }}
        onClose={() => setBundlePreview(null)}
      />

      <CosmeticPreview
        item={preview}
        avatar={me.data?.avatar}
        username={me.data?.username}
        userId={me.data?.userId}
        wornFrame={me.data?.frame}
        affordable={(preview?.price ?? 0) <= (store.data?.coins ?? 0)}
        busy={busy}
        onBuy={onBuy}
        onClose={() => setPreview(null)}
      />
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
const Row = memo(function Row({
  item,
  balance,
  busy,
  onBuy,
  onPreview,
}: {
  item: Cosmetic;
  balance: number;
  busy: boolean;
  onBuy: (id: string) => void;
  onPreview: (item: Cosmetic) => void;
}) {
  const t = useT();
  const isBanner = item.kind === 'BANNER';
  const isTheme = item.kind === 'THEME';
  const themeStops = useGamerGradient(item.id, item.theme);
  const press = useCallback(() => onBuy(item.id), [onBuy, item.id]);
  const preview = useCallback(() => onPreview(item), [onPreview, item]);

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
   *
   * Free no longer implies owned. A free item has to be claimed from this shelf like any
   * other purchase — the button below says "Claim" and the request is the ordinary buy at
   * a price of zero.
   */
  const free = item.price === 0;
  const cost = free ? t.market.shop.free : t.market.shop.coinsPrice(item.price);

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
  const costClass = free ? 'text-muted' : 'font-medium text-gold';

  /*
   * The artwork and the name open a closer look; the button beside them still buys.
   *
   * Two targets in one row rather than one, because they answer different questions — "what
   * is this" and "I'll take it" — and a single tap that did both would put a purchase one
   * mis-tap away from a glance. It is also why the pressable stops short of the control:
   * nesting one button inside another leaves a screen reader announcing a button within a
   * button, and the row's own label would swallow the price the buy control has to say.
   *
   * Owned items are not pressable at all. There is nothing left to decide about something
   * already bought, and it lives in the Inventory now — an affordance here would lead to a
   * sheet whose only offer is a button that cannot be pressed.
   */
  const canPreview = !item.owned;

  const artwork = (
    <>
      {/* A frame is shown against a plain disc so the ring reads as a ring rather
          than as a picture with a hole punched in it. A theme has no picture at all — it
          is drawn as a swatch of the two colours it would paint a card in. */}
      <View
        className={
          isTheme
            ? ''
            : isBanner
              ? 'h-16 w-24 overflow-hidden rounded-xl bg-raised'
              : 'h-16 w-16 items-center justify-center rounded-full bg-raised'
        }
      >
        {isTheme ? (
          <ThemeSwatch stops={themeStops} />
        ) : (
        <Image
          source={{ uri: item.image ?? undefined }}
          style={FILL}
          contentFit={isBanner ? 'cover' : 'contain'}
          autoplay
          transition={150}
          // The catalogue does not change between visits, and these are animated WebPs
          // — the most expensive thing to decode in the app. A disk cache means the
          // second visit to the shop costs nothing, and `recyclingKey` keeps the
          // previous item's art out of a reused cell.
          cachePolicy="memory-disk"
          recyclingKey={item.id}
        />
        )}
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
    </>
  );

  return (
    <Card>
      <View className="flex-row items-center gap-4">
        {canPreview ? (
          <Pressable
            onPress={preview}
            accessibilityRole="button"
            accessibilityLabel={t.market.shop.previewA11y(
              `${item.name}${item.animated ? t.market.shop.animatedSuffix : ''}`,
            )}
            className="min-w-0 flex-1 flex-row items-center gap-4 active:opacity-70"
          >
            {artwork}
          </Pressable>
        ) : (
          <View className="min-w-0 flex-1 flex-row items-center gap-4">{artwork}</View>
        )}

        <Pressable
          // Owned is as disabled as unaffordable, and for a better reason: there is simply
          // nothing left to sell. The tap does nothing either way, so neither state pretends
          // otherwise.
          disabled={busy || item.owned || !affordable}
          onPress={press}
          accessibilityRole="button"
          // "animated" is carried here and nowhere else on the row. Dropping the visible
          // tag was right — the preview is playing, so sighted people can see it — but
          // that is exactly the argument for keeping it in the label, because it is the
          // one difference between two frames that a screen reader could not otherwise
          // report.
          accessibilityLabel={(() => {
            const name = `${item.name}${item.animated ? t.market.shop.animatedSuffix : ''}`;
            if (item.owned) return t.market.shop.ownedA11y(name);
            if (free) return t.market.shop.claimA11y(name);
            return affordable
              ? t.market.shop.buyA11y(name, item.price)
              : t.market.shop.cantAffordA11y(name, item.price);
          })()}
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
            {/* "Claim", not "Buy", for the one free item. It is the same request and the
                same zero charge, but the word is the instruction: a free frame that says
                Buy reads as a price that failed to load. */}
            {item.owned
              ? t.market.shop.owned
              : free
                ? t.market.shop.claim
                : affordable
                  ? t.market.shop.buy
                  : t.market.shop.notEnoughCoins}
          </Text>
        </Pressable>
      </View>
    </Card>
  );
});


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
  const t = useT();
  const subscription = useQuery({
    queryKey: ['subscription'],
    queryFn: billingApi.subscription,
  });

  const isGold = subscription.data?.tier === 'GOLD';

  return (
    <Pressable
      onPress={() => router.push('/gold')}
      accessibilityRole="button"
      accessibilityLabel={
        isGold ? t.market.shop.goldCardMemberA11y : t.market.shop.goldCardGetA11y
      }
      className="mb-5 active:opacity-80"
    >
      <Card>
        <View className="flex-row items-center justify-between gap-4">
          <View className="flex-1 gap-1">
            <Text variant="overline" className="text-gold">
              GAMEBUDDY GOLD
            </Text>
            <Text variant="heading">
              {isGold ? t.market.shop.goldCardTitleMember : t.market.shop.goldCardTitle}
            </Text>
            <Text variant="caption">
              {isGold ? t.market.shop.goldCardBodyMember : t.market.shop.goldCardBody}
            </Text>
          </View>
          <View className="h-2 w-2 rotate-45 border-r-2 border-t-2 border-muted" />
        </View>
      </Card>
    </Pressable>
  );
}
