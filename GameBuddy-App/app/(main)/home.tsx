import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { Gamepad2, SlidersHorizontal } from 'lucide-react-native';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, View } from 'react-native';
import { AdmirersBadge } from './admirers';
import { billingApi } from '../../src/api/billing';
import { UpgradePromptSheet } from '../../src/billing/UpgradePromptSheet';
import { BoostButton, REWIND_COST_COINS } from '../../src/match/BoostButton';
import type { Candidate } from '../../src/api/types';
import { CandidateCard } from '../../src/match/CandidateCard';
import { CandidateSheet } from '../../src/match/CandidateSheet';
import { DeckActions } from '../../src/match/DeckActions';
import { FilterSheet } from '../../src/match/FilterSheet';
import { LimitSheet } from '../../src/match/LimitSheet';
import { SwipeCard } from '../../src/match/SwipeCard';
import { NO_FILTERS, activeCount, type FeedFilters } from '../../src/match/filters';
import { useUpper } from '../../src/i18n/case';
import { useT } from '../../src/i18n/useT';
import { useCelebration } from '../../src/match/celebration';
import { useDeck } from '../../src/match/useDeck';
import { useThemeColors } from '../../src/theme';
import { useTutorial } from '../../src/tutorial/store';
import { Button, EmptyState, ErrorNotice, Icon, Screen, Text } from '../../src/ui';

export default function Deck() {
  const colors = useThemeColors();
  const router = useRouter();
  const t = useT();
  const upper = useUpper();
  const [filters, setFilters] = useState<FeedFilters>(NO_FILTERS);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const deck = useDeck(filters);

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
  const subscription = useQuery({ queryKey: ['subscription'], queryFn: billingApi.subscription });
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
  const frozen = !!deck.block || !!deck.matchedWith || !!profileOf;

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
    (subscription.data?.upgradePromptDue ?? false) && !frozen && tutorialStep === null;

  return (
    // Top only: the tab bar owns the bottom inset now. It briefly did not, and the
    // Pass/Match labels sat underneath Android's navigation bar — invisible in a
    // browser, obvious on a device.
    <Screen edges={['top']} padded={false}>
      <View className="flex-row items-center justify-between gap-4 px-6 pb-2 pt-2">
        <View className="flex-1">
          <Text variant="overline">{upper(t.deck.header.discover)}</Text>
          <Text variant="heading">{t.deck.header.title}</Text>
        </View>

        {/* Settings used to live here. It moved to Profile once that tab existed —
            one less thing in the header, and it stops colliding with the floating
            dev-menu button Expo Go draws in exactly this corner. */}
        {/* Two numbers, and they answer different questions: how many likes you have
            left, and how many people are waiting for you. The second is the reason to
            come back, so it sits closest to the thumb. */}
        <View className="flex-row items-center gap-4">
          <FilterButton count={activeCount(filters)} onPress={() => setFiltersOpen(true)} />
          <AdmirersBadge />
          <Allowance deck={deck} />
        </View>
      </View>

      <View className="flex-1 px-6 py-3">
        {deck.isLoading && (
          <View className="flex-1 items-center justify-center gap-3">
            <ActivityIndicator color={colors.primary} />
            <Text variant="caption">{t.deck.header.loading}</Text>
          </View>
        )}

        {deck.error && (
          <View className="flex-1 justify-center">
            <ErrorNotice error={deck.error} onRetry={() => void deck.refetchFeed()} />
          </View>
        )}

        {deck.filtersRefused && (
          <View className="flex-1 justify-center">
            <FiltersLocked
              onUpgrade={() => router.push('/gold')}
              onClear={() => setFilters(NO_FILTERS)}
            />
          </View>
        )}

        {!deck.isLoading && !deck.error && !deck.filtersRefused && deck.exhausted && (
          <Exhausted
            onReload={deck.reload}
            filtered={activeCount(filters) > 0}
            onClearFilters={() => setFilters(NO_FILTERS)}
          />
        )}

        {deck.current && (
          <View className="flex-1">
            {/* The next card sits underneath, inset and dimmed, so the deck reads as a
                stack with somewhere to go rather than a single card that vanishes. */}
            {deck.upcoming && (
              <View className="absolute inset-x-3 bottom-2 top-4" pointerEvents="none">
                <CandidateCard candidate={deck.upcoming} muted />
              </View>
            )}

            <SwipeCard
              // Keyed by candidate so each card gets a fresh gesture and a zeroed
              // position; without this the next card inherits the last one's offset
              // and starts halfway off screen.
              key={deck.current.userId}
              candidate={deck.current}
              onDecide={deck.submit}
              onOpenProfile={() => setProfileOf(deck.current)}
              frozen={frozen}
            />
          </View>
        )}
      </View>

      {deck.decisionError && (
        <View className="px-6 pb-2">
          <ErrorNotice error={deck.decisionError} />
        </View>
      )}

      {deck.rewindError && (
        <View className="px-6 pb-2">
          <ErrorNotice error={deck.rewindError} onRetry={deck.clearRewindError} />
        </View>
      )}

      {deck.current && (
        <DeckActions
          onDecide={deck.submit}
          disabled={frozen}
          onRewind={() => deck.rewind()}
          canRewind={deck.canRewind}
          // Gold gets rewinds as an entitlement; everyone else pays, and sees the price.
          rewindCost={unlocked ? 0 : REWIND_COST_COINS}
          boost={<BoostButton />}
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
      <CandidateSheet candidate={profileOf} onDismiss={() => setProfileOf(null)} />

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
function FilterButton({ count, onPress }: { count: number; onPress: () => void }) {
  const t = useT();
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={
        count > 0 ? t.deck.header.filtersOnA11y(count) : t.deck.header.filtersA11y
      }
      hitSlop={8}
      className={[
        'h-9 flex-row items-center gap-1.5 rounded-full border px-3',
        count > 0 ? 'border-primary bg-primary/10' : 'border-line bg-raised',
      ].join(' ')}>
      {/* Was the literal character ⚙︎, which renders at whatever weight the platform
          font feels like and cannot take the active colour. */}
      <Icon as={SlidersHorizontal} size={15} tone={count > 0 ? 'primary' : 'muted'} />
      <Text
        className={[
          'font-semibold text-[13px] leading-[17px]',
          count > 0 ? 'text-primary' : 'text-muted',
        ].join(' ')}>
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
function FiltersLocked({ onUpgrade, onClear }: { onUpgrade: () => void; onClear: () => void }) {
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
      <Button label={t.deck.locked.showEveryone} variant="ghost" onPress={onClear} />
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
        title={filtered ? t.deck.exhausted.filteredTitle : t.deck.exhausted.title}
        blurb={filtered ? t.deck.exhausted.filteredBlurb : t.deck.exhausted.blurb}
      >
        {/* Offered before "look again", because refetching the same narrow filters is the
            one thing that will not produce anybody new. */}
        {filtered && <Button label={t.deck.filters.clearFilters} onPress={onClearFilters} />}
        <Button label={t.deck.exhausted.lookAgain} variant="secondary" onPress={onReload} />
      </EmptyState>
    </View>
  );
}
