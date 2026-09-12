import { useCallback, useEffect, useState } from "react";
import { StyleSheet, View } from "react-native";
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import { useT } from "../i18n/useT";
import { brand } from "../theme";
import type { MatchedGamer } from "./celebration";
import { FistBump } from "./FistBump";
import { Avatar } from "../ui/Avatar";
import { Burst } from "../ui/Burst";
import { Button } from "../ui/Button";
import { overlay } from "../ui/elevation";
import { GradientView } from "../ui/Gradient";
import { glow } from "../ui/glow";
import { celebrate } from "../ui/feedback";
import { Text } from "../ui/Text";
import { useScreenScale } from "../ui/useScreenScale";

type MatchOverlayProps = {
  /** `MatchedGamer`, not `Candidate` — this is raised from a push as well as from the deck. */
  candidate: MatchedGamer | null;
  onDismiss: () => void;
  onMessage: (candidate: MatchedGamer) => void;
};

/**
 * The mutual-match celebration.
 *
 * Driven by the `matched` flag on the accept response, not by the message text — see
 * `AcceptResponseBody` on the backend. Matching is the entire point of the product, so
 * this moment must not depend on English copy staying the same.
 *
 * Two deliberate choices, both learned the hard way:
 *
 * **No `Modal`.** This is an overlay inside our own screen, not something that needs to
 * escape the layout, and React Native's `Modal` brought stacking and sizing problems on
 * web for nothing in return. An absolutely positioned sibling behaves identically on
 * both platforms.
 *
 * **`className` never goes on an `Animated.View`.** NativeWind only routes `className`
 * into a style for components it has registered, and Reanimated's animated components
 * are created lazily, so registering them does not reliably catch the instance JSX
 * uses. The class is then dropped in silence — which does not look like a styling bug,
 * it looks like a layout bug: a full-screen backdrop renders transparent and zero-high.
 * So animated wrappers carry `style` only, and every visual class sits on a plain
 * `View` inside them.
 */
export function MatchOverlay({
  candidate,
  onDismiss,
  onMessage,
}: MatchOverlayProps) {
  /*
   * The celebration is the tallest thing in the app and it cannot scroll — it is a moment,
   * not a page — so on a small phone it has to fit by being smaller.
   *
   * `FistBump` draws into a box three times its `size` wide and 1.4x tall, so it is the
   * expensive one: 64 costs 192x90, and the avatar, hero headline and two buttons all
   * have to fit beneath it. The fist gives up the most, the portrait next, and the gaps
   * absorb the rest.
   */
  const { pick } = useScreenScale();
  const fist = pick(44, 54, 64);
  const portrait = pick(88, 104, 120);
  const gap = pick("gap-4", "gap-5", "gap-6");

  const t = useT();
  const visible = !!candidate;
  const progress = useSharedValue(0);

  /**
   * Whether the two fists have met yet.
   *
   * The burst and the buzz both wait for this. They used to fire on mount, which put the
   * light and the thump a third of a second *before* the thing they were reacting to.
   */
  const [bumped, setBumped] = useState(false);

  const onImpact = useCallback(() => {
    setBumped(true);
    // The one `celebrate` in the deck. A match is the rarest good thing that happens here
    // and the only moment that earns a heavier buzz than a swipe — landed on the contact.
    celebrate();
  }, []);

  useEffect(() => {
    progress.value = visible
      ? withSpring(1, { damping: 14, stiffness: 160 })
      : withTiming(0, { duration: 120 });
    // Re-armed when the overlay is dismissed, so a second match plays the whole thing again.
    if (!visible) setBumped(false);
  }, [visible, progress]);

  const backdropStyle = useAnimatedStyle(() => ({ opacity: progress.value }));
  const contentStyle = useAnimatedStyle(() => ({
    opacity: progress.value,
    // Overshoot slightly and settle, which reads as a pop rather than a fade.
    transform: [{ scale: 0.85 + progress.value * 0.15 }],
  }));

  if (!candidate) return null;

  return (
    <View style={[StyleSheet.absoluteFill, overlay(50)]}>
      <Animated.View style={[StyleSheet.absoluteFill, backdropStyle]}>
        <View className="flex-1 items-center justify-center bg-ink-900/90 px-8">
          {/* A wash of the accent ramp behind the celebration, at low alpha so it lifts the
              backdrop without competing with the avatar. Outside the scaling wrapper: it is
              the room, not the thing popping into it. */}
          <GradientView
            name="accent"
            direction="diagonal"
            className="absolute inset-0 opacity-25"
            pointerEvents="none"
          />

          {/*
            The burst. Absolutely positioned and behind everything, `pointerEvents="none"`
            so it never eats the tap on the buttons underneath it.

            Not inside the scaling wrapper below: that animates from 0.85 to 1, and a burst
            that grew with it would read as one object rather than as light coming off the
            avatar.

            `play` is pinned to 0 because this component only exists while a match is on
            screen — `MatchOverlay` returns null otherwise, so mounting *is* the trigger and
            there is never a second one to fire. Looping confetti turns a moment into
            wallpaper.

            `brand.DEFAULT` rather than the accent token, for the same reason the headline
            below is pinned: this backdrop is `ink-900/90` in both themes, so following a
            token that flips with the theme would repaint the particles for a surface that
            did not change.
          */}
          {bumped && <Burst play={0} color={brand.DEFAULT} radius={200} />}

          <Animated.View style={contentStyle}>
            <View className={`w-full items-center ${gap}`}>
              {/* Above the headline, because it is the sentence's verb: the bump happens,
                  then you read what it meant and see who it was with. */}
              <FistBump size={fist} onImpact={onImpact} />

              <View className="items-center gap-1">
                {/* `hero` — the largest type in the system, used here and essentially
                    nowhere else. This is the screen people screenshot. */}
                {/* `text-brand`, not `text-accent`. The accent token flips with the theme,
                    but this backdrop does not — it is `ink-900/90` in both. The light-mode
                    accent (#D42540) measures 3.86:1 here against the dark accent's 6.08:1,
                    so following the theme makes the headline *worse* in light mode for no
                    reason. Pinned to the pink that was designed for a dark surface, the
                    same call `SwipeCard` makes for its stamps. */}
                <Text variant="hero" className="text-center text-brand">
                  {t.deck.match.title}
                </Text>
                <Text className="text-center text-white/80">
                  {t.deck.match.body(candidate.username)}
                </Text>
              </View>

              {/* Lit rather than flat: the glow is on a wrapper because `Avatar` clips its
                  own contents, and on Android `overflow: hidden` clips the node's own
                  shadow. Same split as `Button`. */}
              <View
                className="rounded-full"
                style={glow("strong", brand.DEFAULT)}
              >
                <Avatar
                  source={candidate.avatar}
                  name={candidate.username}
                  colorSeed={candidate.userId}
                  size={portrait}
                />
              </View>

              <View className="w-full gap-3 pt-2">
                <Button
                  label={t.deck.match.message}
                  onPress={() => onMessage(candidate)}
                />
                {/* Secondary, not ghost: ghost's label is `text-muted`, which is dark
                    grey in light mode and would vanish against this overlay. */}
                <Button
                  label={t.deck.match.keep}
                  variant="secondary"
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
