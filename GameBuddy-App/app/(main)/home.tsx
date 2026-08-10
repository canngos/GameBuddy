import { ActivityIndicator, View } from 'react-native';
import { AdmirersBadge } from './admirers';
import { CandidateCard } from '../../src/match/CandidateCard';
import { DeckActions } from '../../src/match/DeckActions';
import { LimitSheet } from '../../src/match/LimitSheet';
import { MatchOverlay } from '../../src/match/MatchOverlay';
import { SwipeCard } from '../../src/match/SwipeCard';
import { useDeck } from '../../src/match/useDeck';
import { useThemeColors } from '../../src/theme';
import { Button, ErrorNotice, Screen, Text } from '../../src/ui';

export default function Deck() {
  const colors = useThemeColors();
  const deck = useDeck();

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

        {!deck.isLoading && !deck.error && deck.exhausted && <Exhausted onReload={deck.reload} />}

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

      {deck.current && <DeckActions onDecide={deck.submit} disabled={frozen} />}

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

function Exhausted({ onReload }: { onReload: () => void }) {
  return (
    <View className="flex-1 items-center justify-center gap-4 px-4">
      <View className="h-16 w-16 items-center justify-center rounded-full bg-raised">
        <Text className="text-[28px] leading-[34px]">🎮</Text>
      </View>
      <Text variant="heading" className="text-center">
        That's everyone for now
      </Text>
      <Text variant="body" className="text-center text-muted">
        New players join all the time, and people you passed on come back around after a
        while.
      </Text>
      <Button label="Look again" variant="secondary" onPress={onReload} />
    </View>
  );
}
