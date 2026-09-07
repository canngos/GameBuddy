import { useRouter } from "expo-router";
import { useEffect } from "react";
import { trackFunnel } from "../api/funnel";
import { Pressable, ScrollView, StyleSheet, View } from "react-native";
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import type { SwipeAllowance } from "../api/types";
import type { Dictionary } from "../i18n/dictionaries/en";
import { useT } from "../i18n/useT";
import { Button } from "../ui/Button";
import { Text } from "../ui/Text";
import type { Block } from "./useDeck";

type LimitSheetProps = {
  block: Block | null;
  allowance: SwipeAllowance | null;
  onDismiss: () => void;
};

/** Resolved per render rather than a module constant, so the copy follows the language. */
function copyFor(
  kind: Block["kind"],
  t: Dictionary,
): { title: string; body: string } {
  switch (kind) {
    case "accept-limit":
      return { title: t.deck.limit.likesTitle, body: t.deck.limit.likesBody };
    case "swipe-limit":
      return { title: t.deck.limit.swipesTitle, body: t.deck.limit.swipesBody };
    case "subscription":
      return { title: t.deck.limit.goldTitle, body: t.deck.limit.goldBody };
    case "no-super-likes":
      return {
        title: t.deck.limit.superLikesTitle,
        body: t.deck.limit.superLikesBody,
      };
    case "no-coins":
      return {
        title: t.deck.limit.noCoinsTitle,
        body: t.deck.limit.noCoinsBody,
      };
    case "nothing-to-rewind":
      return {
        title: t.deck.limit.nothingToRewindTitle,
        body: t.deck.limit.nothingToRewindBody,
      };
    case "deck-error":
      return {
        title: t.deck.limit.somethingWrongTitle,
        body: t.deck.limit.somethingWrongBody,
      };
  }
}

/**
 * Where the primary button goes, and what it says.
 *
 * Every other block on this sheet is a limit Gold lifts, so the offer is Gold. Running out
 * of Super Likes is not one of those: they are bought with coins in the Market and a
 * subscription does not come with any, so offering Gold here would be selling the wrong
 * thing to somebody who already knows what they want.
 */
function actionFor(
  kind: Block["kind"],
  t: Dictionary,
): { label: string; href: "/gold" | "/market" } | null {
  // Null where there is nothing to offer. "You have not swiped yet" and "something went
  // wrong" are statements of fact, and putting a shop button under either would be selling
  // to somebody who was told no by their own app.
  if (kind === "nothing-to-rewind" || kind === "deck-error") return null;
  if (kind === "no-super-likes")
    return { label: t.deck.limit.getSuperLikes, href: "/market" };
  if (kind === "no-coins")
    return { label: t.deck.limit.getCoins, href: "/market" };
  return { label: t.deck.limit.getGold, href: "/gold" };
}

/**
 * Shown when the backend refuses a decision because of a limit.
 *
 * The card that triggered it has already been put back by `useDeck`, so dismissing
 * this returns the gamer to exactly where they were rather than one person poorer.
 *
 * The upgrade button goes to the paywall, not to a store sheet: no IAP library is
 * installed yet, so `/gold` is where a purchase currently gets made (with a development
 * receipt). When the library lands, only the paywall changes.
 *
 * See {@link MatchOverlay} for why this is a positioned sibling rather than a `Modal`,
 * and why no `className` appears on an animated component here.
 */
export function LimitSheet({ block, allowance, onDismiss }: LimitSheetProps) {
  const router = useRouter();
  const t = useT();
  const visible = !!block;
  const progress = useSharedValue(0);

  // The moment a limit actually bites. Recorded here rather than at the paywall because
  // this is where demand appears; whether it converts is the next event's job.
  //
  // Not for an empty Super Like balance: that sheet offers the Market rather than Gold, so
  // counting it would inflate the paywall funnel with people who were never shown one.
  const paywall =
    visible &&
    (block?.kind === "accept-limit" ||
      block?.kind === "swipe-limit" ||
      block?.kind === "subscription");
  useEffect(() => {
    if (paywall) trackFunnel("PAYWALL_TRIGGERED");
  }, [paywall]);

  useEffect(() => {
    progress.value = visible
      ? withSpring(1, { damping: 20, stiffness: 200 })
      : withTiming(0, { duration: 120 });
  }, [visible, progress]);

  const backdropStyle = useAnimatedStyle(() => ({ opacity: progress.value }));
  const sheetStyle = useAnimatedStyle(() => ({
    // Slides up from below its own height.
    transform: [{ translateY: (1 - progress.value) * 380 }],
  }));

  if (!block) return null;
  const copy = copyFor(block.kind, t);
  const action = actionFor(block.kind, t);

  return (
    <View style={[StyleSheet.absoluteFill, { zIndex: 50 }]}>
      <Animated.View style={[StyleSheet.absoluteFill, backdropStyle]}>
        <View className="flex-1 justify-end bg-ink-900/70">
          {/* Tapping the dimmed area closes, which is the gesture people try first. */}
          <Pressable
            className="flex-1"
            onPress={onDismiss}
            accessibilityLabel={t.common.close}
          />

          <Animated.View style={sheetStyle}>
            {/* Capped, because `block.message` is the server's text and this file does not
                get to decide how long it is. Everything above the buttons scrolls; the way
                out stays pinned on screen. */}
            <View className="max-h-[86%] overflow-hidden rounded-t-[28px] bg-surface">
              <View
                className="h-1 w-10 self-center rounded-full bg-line"
                style={{ marginTop: 12 }}
              />

              <ScrollView
                contentContainerClassName="gap-4 px-6 pt-4"
                showsVerticalScrollIndicator={false}
              >
                <View className="gap-2">
                  <Text variant="title">{copy.title}</Text>
                  <Text variant="body" className="text-muted">
                    {copy.body}
                  </Text>
                </View>

                {/* The server's own message, kept because it is the authority on what was
                  refused and why — the copy above is framing, not the reason. */}
                {!!block.message && (
                  <View className="rounded-card bg-raised p-4">
                    <Text variant="caption">{block.message}</Text>
                  </View>
                )}

                {allowance && !allowance.unlimited && (
                  <View className="flex-row gap-3">
                    <Tally
                      label={t.deck.limit.swipesLeft}
                      value={allowance.remainingSwipes}
                    />
                    <Tally
                      label={t.deck.limit.likesLeft}
                      value={allowance.remainingAccepts}
                    />
                  </View>
                )}
              </ScrollView>

              {/* The upgrade is the primary action and dismissing is secondary, because
                  this sheet only appears at the moment the limit is the thing in the way
                  — which is the one moment removing it is worth paying for. It used to
                  offer only "Keep looking", which acknowledged the wall and then left the
                  gamer standing at it. */}
              <View className="gap-2 px-6 pb-10 pt-2">
                {action && (
                  <Button
                    label={action.label}
                    onPress={() => {
                      // Dismiss first: the sheet is a positioned sibling of the deck rather
                      // than a Modal, so leaving it mounted would put it over the paywall.
                      onDismiss();
                      router.push(action.href);
                    }}
                  />
                )}
                <Button
                  label={t.deck.limit.keepLooking}
                  variant="ghost"
                  onPress={onDismiss}
                />
              </View>
            </View>
          </Animated.View>
        </View>
      </Animated.View>
    </View>
  );
}

function Tally({ label, value }: { label: string; value: number }) {
  return (
    <View className="flex-1 items-center gap-0.5 rounded-card bg-raised py-3">
      <Text className="font-bold text-[22px] leading-[28px] text-accent">
        {value}
      </Text>
      <Text variant="caption">{label}</Text>
    </View>
  );
}
