import { useEffect, useState } from 'react';
import { StyleSheet, View, type LayoutChangeEvent } from 'react-native';
import Animated, {
  Easing,
  cancelAnimation,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import { useCardTheme } from '../theme';
import { AnimatedGradient, GradientView } from './Gradient';

type ThemedCardFrameProps = {
  /** The worn theme's slug, or null/undefined for none — the common case. */
  theme: string | null | undefined;
  /** The corner radius of the card this rings, so the frame stays concentric with it. */
  radius: number;
  /** How far the edge extends past the card on each side. */
  width?: number;
};

/**
 * The edge a card theme draws around a card.
 *
 * <p><strong>A frame, not a fill.</strong> The first version of this feature painted the
 * card's background in the theme's colours, which made every theme read as a banner —
 * another picture behind the content rather than something worn. A theme is now what its
 * name says: a border around the outside of the card, in the same spirit as the avatar
 * frames, which are also rings around something rather than backgrounds behind it.
 *
 * <p><strong>It overhangs rather than insets, and that is what makes it work.</strong> It
 * paints a gradient in a box `width` larger than the card on every side; the card's own
 * opaque background then covers the middle, and only the ring outside it shows. That is
 * the one way to get a *gradient* border in React Native, whose `borderColor` takes a
 * single colour.
 *
 * <p><strong>Mount it as the sibling immediately before the card</strong>, inside a
 * wrapper that sizes to the card and does not clip:
 *
 * <pre>{@code
 * <View>
 *   <ThemedCardFrame theme={slug} radius={24} />
 *   <Card>…</Card>
 * </View>
 * }</pre>
 *
 * It must not be a *child* of the card, however tempting that reads. A view's own
 * background is painted before any of its children, so a child can never sit behind it —
 * no `zIndex` changes that, because `zIndex` orders siblings against each other and not
 * against the parent they are inside. Mounted as a child of an opaque card the gradient
 * covers the card whole, which is exactly the banner-looking fill this component exists to
 * stop being.
 *
 * <p>The same caveat as {@link FramedAvatar} therefore applies, for the same reason: this
 * draws outside its own bounds, so <strong>the wrapper must not clip its children</strong>
 * or the edge is cut off. Being absolute it takes no layout space, so adding a theme never
 * moves anything.
 *
 * <p><strong>Animated themes rotate the gradient</strong> rather than running a highlight
 * around the perimeter. A travelling highlight means tracking four sides and two corners in
 * one continuous parameter; rotating the fill under a fixed window reads the same and
 * cannot desynchronise at a corner. It honours `useReducedMotion`, because a border that
 * never stops moving is exactly what that setting exists for.
 */
export function ThemedCardFrame({ theme, radius, width = 3 }: ThemedCardFrameProps) {
  const card = useCardTheme(theme);
  const reduceMotion = useReducedMotion();
  const spin = useSharedValue(0);
  /*
   * Measured, because a rotating fill has to be a square with the box's *diagonal* for a
   * side to cover the corners at every angle, and that length cannot be written as a
   * percentage of a box whose sides differ. The obvious `150%` square is really a 1.5×
   * scaled rectangle: turn a tall one on its side and it is suddenly far too short, which
   * shows up as bare wedges sweeping across the card.
   */
  const [box, setBox] = useState({ w: 0, h: 0 });
  const onLayout = (e: LayoutChangeEvent) => {
    const { width: w, height: h } = e.nativeEvent.layout;
    setBox((prev) => (prev.w === w && prev.h === h ? prev : { w, h }));
  };
  const side = Math.ceil(Math.sqrt(box.w * box.w + box.h * box.h));

  const moving = !!card?.animated && !reduceMotion;

  useEffect(() => {
    if (!moving) {
      cancelAnimation(spin);
      spin.value = 0;
      return;
    }
    spin.value = withRepeat(withTiming(1, { duration: 6000, easing: Easing.linear }), -1, false);
    return () => cancelAnimation(spin);
  }, [moving, spin]);

  const spinStyle = useAnimatedStyle(() => ({
    transform: [{ rotate: `${spin.value * 360}deg` }],
  }));

  // No theme, or one this build has no colours for: no edge, and nothing rendered at all,
  // so an unthemed card is exactly the card it was before this existed.
  if (!card) return null;

  return (
    <View
      pointerEvents="none"
      // Always measured, not only while animating: `onLayout` fires when a layout is
      // computed, not when a handler appears, so attaching it the moment `moving` flips
      // true — which is what equipping an animated theme over a static one does, without
      // remounting this — would leave the square sized zero and draw no edge at all.
      onLayout={onLayout}
      style={{
        position: 'absolute',
        top: -width,
        left: -width,
        right: -width,
        bottom: -width,
        borderRadius: radius + width,
        overflow: 'hidden',
      }}
    >
      {moving ? (
        // Centred on the box, so every angle still reaches all four corners.
        side > 0 && (
          <Animated.View
            style={[
              {
                position: 'absolute',
                width: side,
                height: side,
                left: (box.w - side) / 2,
                top: (box.h - side) / 2,
              },
              spinStyle,
            ]}
          >
            {/* The raw animated LinearGradient takes start/end rather than a direction —
                see `Gradient.tsx`, where only the plain `GradientView` wraps them. */}
            <AnimatedGradient
              colors={card.stops}
              start={DIAGONAL.start}
              end={DIAGONAL.end}
              style={StyleSheet.absoluteFill}
            />
          </Animated.View>
        )
      ) : (
        <GradientView colors={card.stops} direction="diagonal" style={StyleSheet.absoluteFill} />
      )}
    </View>
  );
}

/** The same pair `GradientView`'s `diagonal` uses; the animated node needs them directly. */
const DIAGONAL = { start: { x: 0, y: 0 }, end: { x: 1, y: 1 } } as const;
