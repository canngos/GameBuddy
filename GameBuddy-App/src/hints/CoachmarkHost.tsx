import { useWindowDimensions, Pressable, StyleSheet, View } from 'react-native';
import Animated, { FadeIn } from 'react-native-reanimated';
import Svg, { Defs, Mask, Rect } from 'react-native-svg';
import { useT } from '../i18n/useT';
import { Button, Text } from '../ui';
import { useHints } from './store';
import { useCoachmark } from './coachmark';

/**
 * The spotlight: everything dimmed except the one control being explained.
 *
 * Mounted once in `app/(main)/_layout.tsx`, after `ToastHost` and before
 * `MatchCelebration` — a hint may cover the tabs, and a match must cover a hint.
 *
 * **An absolutely positioned sibling, not a `Modal`.** The tutorial is a `Modal` because it
 * has to sit above the tab bar *and* survive the deck's gesture handler, and that is the one
 * case worth the trade. Here the second half is handled differently: the deck freezes itself
 * while a hint is up (`hintActive` feeds `frozen` in `home.tsx`), so a swipe cannot happen
 * behind the dim. Which leaves only z-order, and being last in the layout is enough for that.
 *
 * **The hole is drawn, not layered.** One SVG rectangle covers the screen and an SVG mask
 * punches the control out of it, so there is exactly one dimmed surface rather than four
 * strips around a gap that never quite meet. `pointerEvents="none"` on the SVG so the
 * backdrop underneath still takes the tap.
 */

/** Breathing room around the highlighted control. */
const PAD = 8;
/** Matches the app's `rounded-card`, so the hole looks like it belongs to the control. */
const RADIUS = 12;
/** Gap between the hole and the callout. */
const GAP = 12;
/** The callout never touches the screen edge. */
const MARGIN = 16;

export function CoachmarkHost() {
  const t = useT();
  const { width, height } = useWindowDimensions();
  const key = useCoachmark((s) => s.key);
  const rect = useCoachmark((s) => s.rect);
  const dismiss = useHints((s) => s.dismiss);

  if (!key || !rect) return null;

  const copy = COPY_KEYS[key];
  if (!copy) return null;

  const hole = {
    x: rect.x - PAD,
    y: rect.y - PAD,
    width: rect.width + PAD * 2,
    height: rect.height + PAD * 2,
  };

  // Below the control when it sits in the top half, above it otherwise — so the callout is
  // always on the roomier side and never lands under the thing it is describing.
  const below = hole.y + hole.height < height / 2;
  const close = () => dismiss(key);

  return (
    <View style={StyleSheet.absoluteFill}>
      {/* Tapping anywhere puts it away. A spotlight that only answers a small button is one
          people feel trapped by, and there is nothing here to get wrong by tapping. */}
      <Pressable style={StyleSheet.absoluteFill} onPress={close} accessibilityRole="button">
        <Svg width={width} height={height} pointerEvents="none">
          <Defs>
            {/* White shows the dim, black hides it. */}
            <Mask id="spotlight">
              <Rect x={0} y={0} width={width} height={height} fill="white" />
              <Rect
                x={hole.x}
                y={hole.y}
                width={hole.width}
                height={hole.height}
                rx={RADIUS}
                ry={RADIUS}
                fill="black"
              />
            </Mask>
          </Defs>
          <Rect
            x={0}
            y={0}
            width={width}
            height={height}
            fill="black"
            opacity={0.72}
            mask="url(#spotlight)"
          />
        </Svg>
      </Pressable>

      {/* `style` only, never `className`: NativeWind drops classes on an Animated.View in
          silence — UI_NOTE §4.3. Every visual class is on the plain View inside. */}
      <Animated.View
        entering={FadeIn.duration(180)}
        pointerEvents="box-none"
        style={{
          position: 'absolute',
          left: MARGIN,
          right: MARGIN,
          ...(below
            ? { top: hole.y + hole.height + GAP }
            : { bottom: height - hole.y + GAP }),
        }}
      >
        <View className="gap-2 rounded-card bg-surface p-4">
          <Text variant="heading">{copy.title(t)}</Text>
          <Text variant="body" className="text-muted">
            {copy.body(t)}
          </Text>
          <View className="pt-1">
            <Button label={t.hints.gotIt} onPress={close} />
          </View>
        </View>
      </Animated.View>
    </View>
  );
}

/**
 * Which dictionary entry each hint reads.
 *
 * Resolved at render through the passed dictionary rather than captured at module load: a
 * module-level array of sentences is evaluated before a language has been chosen, which is
 * the bug that took every other label array out of this codebase.
 *
 * `deck.swipe` is absent on purpose — it is not a spotlight. Three captions animating with a
 * hand is the only honest way to show a gesture, and that lives in `SwipeDemo`.
 */
type Copy = {
  title: (t: ReturnType<typeof useT>) => string;
  body: (t: ReturnType<typeof useT>) => string;
};

const COPY_KEYS: Partial<Record<string, Copy>> = {
  'deck.filter': { title: (t) => t.hints.filter.title, body: (t) => t.hints.filter.body },
  'deck.superLike': { title: (t) => t.hints.superLike.title, body: (t) => t.hints.superLike.body },
  'lobby.create': { title: (t) => t.hints.lobbyCreate.title, body: (t) => t.hints.lobbyCreate.body },
  'chat.composer': { title: (t) => t.hints.chatComposer.title, body: (t) => t.hints.chatComposer.body },
};
