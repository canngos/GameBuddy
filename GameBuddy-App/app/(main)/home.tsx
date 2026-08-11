import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Pressable, View } from 'react-native';
import { AdmirersBadge } from './admirers';
import { billingApi } from '../../src/api/billing';
import { BoostButton, REWIND_COST_COINS } from '../../src/match/BoostButton';
import { CandidateCard } from '../../src/match/CandidateCard';
import { DeckActions } from '../../src/match/DeckActions';
import { FilterSheet } from '../../src/match/FilterSheet';
import { LimitSheet } from '../../src/match/LimitSheet';
import { MatchOverlay } from '../../src/match/MatchOverlay';
import { SwipeCard } from '../../src/match/SwipeCard';
import { NO_FILTERS, activeCount, type FeedFilters } from '../../src/match/filters';
import { useDeck } from '../../src/match/useDeck';
import { useThemeColors } from '../../src/theme';
import { Button, ErrorNotice, Screen, Text } from '../../src/ui';

export default function Deck() {
  const colors = useThemeColors();
  const router = useRouter();
  const [filters, setFilters] = useState<FeedFilters>(NO_FILTERS);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const deck = useDeck(filters);

  // Shared cache with the paywall, so buying Gold there unlocks the controls here without
  // a reload. Only ever advisory — see FilterSheet on why the server is the authority.
  const subscription = useQuery({ queryKey: ['subscription'], queryFn: billingApi.subscription });
  const unlocked = subscription.data?.canUseAdvancedFilters ?? false;

  // Both overlays freeze the gesture. Swiping the card behind a modal would decide
  // someone's fate invisibly.
  const frozen = !!deck.block || !!deck.matchedWith;

  return (
    // Top only: the tab bar owns the bottom inset now. It briefly did not, and the
    // Pass/Match labels sat underneath Android's navigation bar — invisible in a
    // browser, obvious on a device.
    <Screen edges={['top']} padded={false}>
      <View className="flex-row items-center justify-between gap-4 px-6 pb-2 pt-2">
        <View className="flex-1">
          <Text variant="overline">DISCOVER</Text>
          <Text variant="heading">Who's playing</Text>
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
            <ActivityIndicator color={colors.brand} />
            <Text variant="caption">Finding people who play what you play…</Text>
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

      <LimitSheet
        block={deck.block}
        allowance={deck.allowance}
        onDismiss={deck.dismissBlock}
      />

      <MatchOverlay
        candidate={deck.matchedWith}
        onDismiss={deck.dismissMatch}
        onMessage={() => {
          // Chat is not built yet. Dismissing at least leaves the deck usable rather
          // than navigating to a route that does not exist.
          deck.dismissMatch();
        }}
      />
    </Screen>
  );
}

/** Today's remaining likes, shown before the wall rather than at it. */
function Allowance({ deck }: { deck: ReturnType<typeof useDeck> }) {
  const allowance = deck.allowance;
  if (!allowance || allowance.unlimited) return null;

  return (
    <View className="items-end">
      <Text className="font-semibold text-[15px] leading-[20px] text-brand">
        {allowance.remainingAccepts}
      </Text>
      <Text variant="caption">likes left</Text>
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
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={count > 0 ? `Filters, ${count} on` : 'Filters'}
      hitSlop={8}
      className={[
        'h-9 flex-row items-center gap-1.5 rounded-full border px-3',
        count > 0 ? 'border-brand bg-brand/10' : 'border-line bg-raised',
      ].join(' ')}>
      <Text className={count > 0 ? 'text-[14px] leading-[18px] text-brand' : 'text-[14px] leading-[18px] text-muted'}>
        ⚙︎
      </Text>
      <Text
        className={[
          'font-semibold text-[13px] leading-[17px]',
          count > 0 ? 'text-brand' : 'text-muted',
        ].join(' ')}>
        {count > 0 ? String(count) : 'Filter'}
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
  return (
    <View className="items-center gap-4 px-4">
      <View className="h-16 w-16 items-center justify-center rounded-full bg-brand/15">
        <Text className="text-[28px] leading-[34px]">🔍</Text>
      </View>
      <Text variant="heading" className="text-center">
        Filters are part of Gold
      </Text>
      <Text variant="body" className="text-center text-muted">
        Narrow the deck to one game, your region, or people who are online right now.
      </Text>
      <View className="w-full gap-2">
        <Button label="Get Gold" onPress={onUpgrade} />
        <Button label="Show everyone instead" variant="ghost" onPress={onClear} />
      </View>
    </View>
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
  return (
    <View className="flex-1 items-center justify-center gap-4 px-4">
      <View className="h-16 w-16 items-center justify-center rounded-full bg-raised">
        <Text className="text-[28px] leading-[34px]">🎮</Text>
      </View>
      <Text variant="heading" className="text-center">
        {filtered ? "That's everyone matching your filters" : "That's everyone for now"}
      </Text>
      <Text variant="body" className="text-center text-muted">
        {filtered
          ? 'Widening them brings more people back into the deck.'
          : 'New players join all the time, and people you passed on come back around after a while.'}
      </Text>
      {/* Offered before "look again", because refetching the same narrow filters is the
          one thing that will not produce anybody new. */}
      {filtered && <Button label="Clear filters" onPress={onClearFilters} />}
      <Button label="Look again" variant="secondary" onPress={onReload} />
    </View>
  );
}
