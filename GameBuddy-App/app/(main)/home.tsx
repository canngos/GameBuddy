import { useQuery } from "@tanstack/react-query";
import { BlurTargetView, BlurView } from "expo-blur";
import { useRouter } from "expo-router";
import { Gamepad2, SlidersHorizontal } from "lucide-react-native";
import { useEffect, useRef, useState } from "react";
import { ActivityIndicator, Pressable, StyleSheet, View } from "react-native";
import { AdmirersBadge } from "./admirers";
import { billingApi } from "../../src/api/billing";
import { UpgradePromptSheet } from "../../src/billing/UpgradePromptSheet";
import { REWIND_COST_COINS } from "../../src/match/prices";
import type { Candidate } from "../../src/api/types";
import { CandidateCard } from "../../src/match/CandidateCard";
import { CandidateSheet } from "../../src/match/CandidateSheet";
import { DeckActions } from "../../src/match/DeckActions";
import { FilterSheet } from "../../src/match/FilterSheet";
import { LimitSheet } from "../../src/match/LimitSheet";
import { SwipeCard } from "../../src/match/SwipeCard";
import {
  NO_FILTERS,
  activeCount,
  type FeedFilters,
} from "../../src/match/filters";
import { useUpper } from "../../src/i18n/case";
import { useT } from "../../src/i18n/useT";
import { useCoachmarkTarget } from "../../src/hints/coachmark";
import { SwipeDemo } from "../../src/hints/SwipeDemo";
import { useHints } from "../../src/hints/store";
import { useHint } from "../../src/hints/useHint";
import { useCelebration } from "../../src/match/celebration";
import { useDeckLayout } from "../../src/match/deckLayout";
import { useDeck } from "../../src/match/useDeck";
import { useThemeColors } from "../../src/theme";
import { useTutorial } from "../../src/tutorial/store";
import {
  Button,
  EmptyState,
  ErrorNotice,
  Icon,
  Screen,
  Text,
} from "../../src/ui";

export default function Deck() {
  // Android's BlurView samples a specific view rather than the window; this is it.
  const upcomingBlurTarget = useRef<View>(null);
  const colors = useThemeColors();
  const router = useRouter();
  const t = useT();
  const upper = useUpper();
  const [filters, setFilters] = useState<FeedFilters>(NO_FILTERS);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const deck = useDeck(filters);
  const deckLayout = useDeckLayout();

  /*
   * Hand the match to the app-wide celebration.
   *
   * The overlay used to be rendered right here, which meant only the gamer who swiped last
   * ever saw it — the other side of the match got a push and nothing more. It now lives
   * above the tab navigator so a push can raise it too; see `src/match/celebration.ts`.
   *
   * `deck.matchedWith` stays, because it is also what freezes the swipe gesture while the
   * celebration is up. Clearing it is handed over as the dismiss callback so the deck
   * unfreezes when the overlay closes, without the overlay knowing what a deck is.
   */
  const celebrate = useCelebration((s) => s.celebrate);
  useEffect(() => {
    const matched = deck.matchedWith;
    if (!matched) return;
    celebrate(
      {
        userId: matched.userId,
        username: matched.gamerUsername,
        avatar: matched.avatar,
        frame: matched.frame,
      },
      deck.dismissMatch,
    );
  }, [deck.matchedWith, deck.dismissMatch, celebrate]);

  // Shared cache with the paywall, so buying Gold there unlocks the controls here without
  // a reload. Only ever advisory — see FilterSheet on why the server is the authority.
  const subscription = useQuery({
    queryKey: ["subscription"],
    queryFn: billingApi.subscription,
  });
  const unlocked = subscription.data?.canUseAdvancedFilters ?? false;

  /**
   * The candidate whose full profile is open, or null.
   *
   * Held here rather than inside the card because the sheet has to outlive the card's own
   * layout and draw over the deck's actions — and because closing it must leave the deck
   * exactly as it was, which is the whole reason this is a sheet and not a route.
   */
  const [profileOf, setProfileOf] = useState<Candidate | null>(null);

  // All three overlays freeze the gesture. Swiping the card behind a modal would decide
  // someone's fate invisibly — and the profile sheet is the easiest of the three to open by
  // accident, so it is also the one most likely to be dismissed with a stray drag.
  //
  // **Kept separate from `frozen` below, and that separation is load-bearing.** A first-use
  // hint also has to freeze the gesture, so `hintActive` belongs in `frozen` — but a hint
  // must never be part of its *own* eligibility. It was, once: `enabled` read `frozen`, so
  // claiming the slot set `hintActive`, which made `enabled` false, which released the slot,
  // which made `enabled` true again. React caught it as "Maximum update depth exceeded" and
  // the deck would not render at all. Hints therefore gate on `overlayUp`, which is the
  // three things that are genuinely somebody else's, and the gesture gates on `frozen`.
  const overlayUp = !!deck.block || !!deck.matchedWith || !!profileOf;

  const hintActive = useHints((s) => s.active !== null);
  // Unlike the tutorial a hint is an ordinary sibling rather than a Modal, so
  // gesture-handler would still find the card underneath the dim — freezing here is what
  // stops a tap meant for "Got it" from landing as a swipe.
  const frozen = overlayUp || hintActive;

  // The gesture demo, on the first real card. Waits for a card to actually be there:
  // a hand miming a swipe over a spinner teaches nothing and spends the one showing.
  const swipeHint = useHint(
    "deck.swipe",
    !!deck.current && !deck.isLoading && !deck.error && !overlayUp,
  );

  // Filters come second, and only once swiping is understood — the count on the button
  // means nothing to somebody who has not yet seen the deck run.
  const { attach: attachFilterHint } = useCoachmarkTarget(
    "deck.filter",
    !!deck.current && !deck.isLoading && !overlayUp && !filtersOpen,
  );

  // Last of the three, and only once there is a Super Like to spend: explaining a control
  // whose only action today is "go and buy one" is a shop pitch wearing a tip's clothes.
  const { attach: attachSuperLikeHint } = useCoachmarkTarget(
    "deck.superLike",
    !!deck.current && !overlayUp && (deck.allowance?.superLikes ?? 0) > 0,
  );

  // The day-3 prompt waits for a quiet moment. It is the one thing on this screen nobody
  // asked for, so it must not arrive on top of a match they just made or a limit that just
  // stopped them — both of those are answers to an action, and this would talk over them.
  //
  // The tutorial outranks it too, and that is not hypothetical: an account that skipped
  // through its first session without finishing the tutorial and came back on day three
  // gets both at once, and the tutorial is mounted in the layout above this screen — so it
  // wins the paint and the prompt is spent underneath it, seen by nobody. Deferring costs
  // one app open; not deferring costs the only showing there is.

  const tutorialStep = useTutorial((s) => s.step);
  const promptDue =
    (subscription.data?.upgradePromptDue ?? false) &&
    !frozen &&
    tutorialStep === null;

  return (
    // Top only: the tab bar owns the bottom inset now. It briefly did not, and the
    // Pass/Match labels sat underneath Android's navigation bar — invisible in a
    // browser, obvious on a device.
    <Screen edges={["top"]} padded={false}>
      <View className="flex-row items-center justify-between gap-4 px-6 pb-2 pt-2">
        {/* Both capped at one line.

            The heading is two words in English and four in some languages, and this row
            gives it whatever the three controls beside it do not want. On a narrow screen
            — a small phone, or any phone with the display size turned up — that was about
            a third of the width, so "Who's playing" wrapped onto four lines and the header
            alone took a quarter of the screen. The deck below is the screen; the header is
            a label for it, and a label that grows by wrapping is one that takes room from
            the thing it is labelling. */}
        <View className="flex-1">
          <Text variant="overline" numberOfLines={1}>
            {upper(t.deck.header.discover)}
          </Text>
          <Text
            variant="heading"
            numberOfLines={1}
            adjustsFontSizeToFit
            minimumFontScale={0.7}
          >
            {t.deck.header.title}
          </Text>
        </View>

        {/* Settings used to live here. It moved to Profile once that tab existed —
            one less thing in the header, and it stops colliding with the floating
            dev-menu button Expo Go draws in exactly this corner. */}
        {/* Two numbers, and they answer different questions: how many likes you have
            left, and how many people are waiting for you. The second is the reason to
            come back, so it sits closest to the thumb. */}
        <View className="flex-row items-center gap-4">
          {/* Wrapped so the coach mark has something to measure: `measureInWindow`
              needs a host view, and FilterButton's own Pressable is the thing being
              highlighted rather than a box we may attach a ref to. */}
          <View ref={attachFilterHint} collapsable={false}>
            <FilterButton
              count={activeCount(filters)}
              onPress={() => setFiltersOpen(true)}
            />
          </View>
          <AdmirersBadge />
          <Allowance deck={deck} />
        </View>
      </View>

      {/* The gutter around the card scales with everything else — see `useDeckLayout`. */}
      <View
        className="flex-1 px-6"
        style={{ paddingVertical: deckLayout.cardGutter }}
      >
        {deck.isLoading && (
          <View className="flex-1 items-center justify-center gap-3">
            <ActivityIndicator color={colors.primary} />
            <Text variant="caption">{t.deck.header.loading}</Text>
          </View>
        )}

        {deck.error && (
          <View className="flex-1 justify-center">
            <ErrorNotice
              error={deck.error}
              onRetry={() => void deck.refetchFeed()}
            />
          </View>
        )}

        {deck.filtersRefused && (
          <View className="flex-1 justify-center">
            <FiltersLocked
              onUpgrade={() => router.push("/gold")}
              onClear={() => setFilters(NO_FILTERS)}
            />
          </View>
        )}

        {!deck.isLoading &&
          !deck.error &&
          !deck.filtersRefused &&
          deck.exhausted && (
            <Exhausted
              onReload={deck.reload}
              filtered={activeCount(filters) > 0}
              onClearFilters={() => setFilters(NO_FILTERS)}
            />
          )}

        {deck.current && (
          <View className="flex-1">
            {/* The next card sits underneath, smaller and dimmed, so the deck reads as a
                stack with somewhere to go rather than a single card that vanishes.

                **Scaled, not inset.** It used to be positioned `top-4 bottom-2`, which made
                it a genuinely shorter card — 24dp shorter — and so it laid its contents out
                in less room than the card in front. On a small phone that was the difference
                between the Style tags fitting and being clipped off the bottom, so swiping
                revealed a card with no tags on it and putting it in front put them back. A
                transform changes what is drawn, never the box it was measured in, so the
                card behind now lays out exactly as it will when it is the card in front. */}
            {deck.upcoming && (
              <View
                className="absolute inset-0 overflow-hidden rounded-card"
                pointerEvents="none"
                style={{ transform: [{ scale: 0.95 }] }}
              >
                {/*
                  Two views, not one, because a `BlurView` blurs what is *behind* it rather
                  than what is inside it. Wrapping the card in one would have blurred the
                  screen behind the deck and left the card itself perfectly sharp — which
                  looks like the blur simply did not work.

                  On Android it also needs to be told what to sample: `blurTarget` takes a
                  ref to the `BlurTargetView` below, and without `blurMethod` the default is
                  `'none'`, which draws a flat translucent rectangle. Both of those fail
                  quietly — you get *a* view, just not a blurred one.
                */}
                <BlurTargetView
                  ref={upcomingBlurTarget}
                  style={StyleSheet.absoluteFill}
                >
                  <CandidateCard candidate={deck.upcoming} muted />
                </BlurTargetView>

                <BlurView
                  blurTarget={upcomingBlurTarget}
                  // `dimezisBlurView` rather than the SDK-31+ variant: that one falls back
                  // to no blur at all below Android 12, and the phone this was asked for is
                  // a P20 Lite on Android 9. The card behind does not move while the front
                  // one is dragged, so there is no per-frame resampling to pay for.
                  blurMethod="dimezisBlurView"
                  intensity={20}
                  tint="default"
                  style={StyleSheet.absoluteFill}
                  pointerEvents="none"
                />
              </View>
            )}

            <SwipeCard
              // Keyed by candidate so each card gets a fresh gesture and a zeroed
              // position; without this the next card inherits the last one's offset
              // and starts halfway off screen.
              key={deck.current.userId}
              candidate={deck.current}
              // The first real decision is proof the demo was not needed, so it puts
              // it away rather than leaving it up over the next card.
              onDecide={(decision) => {
                swipeHint.dismiss();
                deck.submit(decision);
              }}
              onOpenProfile={() => setProfileOf(deck.current)}
              frozen={frozen}
            />

            {/* After the card, so it draws over it, and inside the same box so the dim
                stops at the card's edges. It lets real swipes through. */}
            {swipeHint.due && <SwipeDemo onDismiss={swipeHint.dismiss} />}
          </View>
        )}
      </View>

      {/* Nothing is reported inline here any more. A refused swipe and a refused rewind
          could both be on screen at once, stacked under the card, shifting the controls
          down and staying until something else replaced them. Every deck failure now
          arrives as the same sheet the limits use — see `LimitSheet`. */}

      {deck.current && (
        <DeckActions
          onDecide={deck.submit}
          disabled={frozen}
          onRewind={() => deck.rewind()}
          canRewind={deck.canRewind}
          // Gold gets rewinds as an entitlement; everyone else pays, and sees the price.
          rewindCost={unlocked ? 0 : REWIND_COST_COINS}
          superLikes={deck.allowance?.superLikes ?? 0}
          superLikeRef={attachSuperLikeHint}
        />
      )}

      <FilterSheet
        visible={filtersOpen}
        filters={filters}
        unlocked={unlocked}
        onApply={setFilters}
        onDismiss={() => setFiltersOpen(false)}
      />

      {/* Below the limit sheet and the match overlay in z-order, because this one is opened
          on purpose and those two arrive on their own. */}
      <CandidateSheet
        candidate={profileOf}
        onDismiss={() => setProfileOf(null)}
      />

      <LimitSheet
        block={deck.block}
        allowance={deck.allowance}
        onDismiss={deck.dismissBlock}
      />

      {/* Last, so it renders above the other two — but it also waits for them. A match
          overlay or a limit sheet is a response to something the gamer just did, and
          landing a pitch on top of either would talk over it. */}
      <UpgradePromptSheet due={promptDue} />
    </Screen>
  );
}

/** Today's remaining likes, shown before the wall rather than at it. */
function Allowance({ deck }: { deck: ReturnType<typeof useDeck> }) {
  const t = useT();
  const allowance = deck.allowance;
  if (!allowance || allowance.unlimited) return null;

  return (
    <View className="items-end">
      <Text className="font-semibold text-[15px] leading-[20px] text-accent">
        {allowance.remainingAccepts}
      </Text>
      <Text variant="caption">{t.deck.header.likesLeft}</Text>
    </View>
  );
}

/**
 * Opens the filters, and says whether any are on.
 *
 * The count matters more than it looks: a filtered deck runs out far sooner than an
 * unfiltered one, and without a visible badge an empty deck reads as "nobody uses this
 * app" rather than "you asked for Valorant players in Finland who are online".
 */
function FilterButton({
  count,
  onPress,
}: {
  count: number;
  onPress: () => void;
}) {
  const t = useT();
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={
        count > 0
          ? t.deck.header.filtersOnA11y(count)
          : t.deck.header.filtersA11y
      }
      hitSlop={8}
      className={[
        "h-9 flex-row items-center gap-1.5 rounded-full border px-3",
        count > 0 ? "border-primary bg-primary/10" : "border-line bg-raised",
      ].join(" ")}
    >
      {/* Was the literal character ⚙︎, which renders at whatever weight the platform
          font feels like and cannot take the active colour. */}
      <Icon
        as={SlidersHorizontal}
        size={15}
        tone={count > 0 ? "primary" : "muted"}
      />
      <Text
        className={[
          "font-semibold text-[13px] leading-[17px]",
          count > 0 ? "text-primary" : "text-muted",
        ].join(" ")}
      >
        {count > 0 ? String(count) : t.deck.header.filter}
      </Text>
    </Pressable>
  );
}

/**
 * The deck when the server refused a narrowed feed.
 *
 * Two ways out, and both have to be here. Upgrading is the one being sold, but somebody
 * whose Gold has simply lapsed did not choose this state and must be able to get their
 * ordinary deck back without paying — otherwise an expiry silently bricks the main screen
 * of the app.
 */
function FiltersLocked({
  onUpgrade,
  onClear,
}: {
  onUpgrade: () => void;
  onClear: () => void;
}) {
  const t = useT();
  return (
    <EmptyState
      icon={SlidersHorizontal}
      title={t.deck.locked.title}
      blurb={t.deck.locked.blurb}
      // An offer rather than the end of a list, so it gets the lit ring.
      accent
    >
      <Button label={t.deck.locked.getGold} onPress={onUpgrade} />
      <Button
        label={t.deck.locked.showEveryone}
        variant="ghost"
        onPress={onClear}
      />
    </EmptyState>
  );
}

function Exhausted({
  onReload,
  filtered,
  onClearFilters,
}: {
  onReload: () => void;
  filtered: boolean;
  onClearFilters: () => void;
}) {
  const t = useT();
  return (
    <View className="flex-1 justify-center">
      <EmptyState
        icon={Gamepad2}
        title={
          filtered ? t.deck.exhausted.filteredTitle : t.deck.exhausted.title
        }
        blurb={
          filtered ? t.deck.exhausted.filteredBlurb : t.deck.exhausted.blurb
        }
      >
        {/* Offered before "look again", because refetching the same narrow filters is the
            one thing that will not produce anybody new. */}
        {filtered && (
          <Button
            label={t.deck.filters.clearFilters}
            onPress={onClearFilters}
          />
        )}
        <Button
          label={t.deck.exhausted.lookAgain}
          variant="secondary"
          onPress={onReload}
        />
      </EmptyState>
    </View>
  );
}
