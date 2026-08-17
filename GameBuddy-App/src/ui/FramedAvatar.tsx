import { Image } from 'expo-image';
import { View } from 'react-native';
import { Avatar } from './Avatar';

/**
 * How much of a frame's width is the clear hole in the middle.
 *
 * <p>Fixed by the art, not chosen here: `cosmetics/generate.py` punches the hole at
 * `HOLE_R = FRAME_SIZE * 0.390`. Measuring the eleven exported files back confirms it —
 * the first non-transparent pixel out from the centre sits at 0.781 of the width on nine
 * of them and 0.789–0.797 on the two thinnest rings, so 0.78 clears every one of them.
 *
 * <p>Every frame shares that radius on purpose: a set where one hugs the avatar and the
 * next floats away reads as a mistake rather than a choice, and it is what lets a single
 * constant position all of them.
 *
 * <p>If the art changes, this changes with it. They are two halves of one measurement and
 * there is no way to derive this one at runtime.
 */
const HOLE_RATIO = 0.78;

type FramedAvatarProps = {
  /** The worn frame's URL, or null/undefined for none — which is the common case. */
  frame: string | null | undefined;
  source: string | null | undefined;
  name: string | null | undefined;
  colorSeed?: string;
  /** The avatar's diameter. The frame overhangs it; see below. */
  size?: number;
  className?: string;
};

/**
 * An avatar with the frame its owner is wearing, if any.
 *
 * <p>`size` is the avatar's diameter, matching {@link Avatar}, so this is a drop-in
 * replacement at every call site and a framed gamer is never rendered smaller than an
 * unframed one standing next to them in the same list.
 *
 * <p>That means the frame overhangs its container by about 14% on each side rather than
 * being boxed inside it. This is deliberate and it is how frames work everywhere they are
 * sold: the ring is meant to break out of the avatar's footprint, and shrinking the
 * picture to make room would punish someone for buying one. The overhang lands in padding
 * that lists already have — but a parent that clips its children will cut the ring, so
 * this must not be dropped inside an `overflow-hidden` box.
 *
 * <p>`expo-image` rather than React Native's `Image`: six of the eleven frames are
 * animated WebP, which RN's own component will not play on both platforms.
 */
export function FramedAvatar({
  frame,
  source,
  name,
  colorSeed,
  size = 56,
  className,
}: FramedAvatarProps) {
  return (
    <View
      className={className}
      style={{ width: size, height: size, alignItems: 'center', justifyContent: 'center' }}
    >
      <Avatar source={source} name={name} colorSeed={colorSeed} size={size} />
      <FrameOverlay frame={frame} size={size} />
    </View>
  );
}

/**
 * Just the ring, for callers that draw their own portrait.
 *
 * <p>The deck card is the reason this is separate: when a candidate has no photo it
 * renders an oversized monogram on a full-bleed tint rather than {@link Avatar}'s small
 * one, and that gamer's frame should still be on show. Sharing the overlay keeps the
 * geometry in one place — two copies of `HOLE_RATIO` would eventually disagree, and the
 * symptom would be a ring that sits slightly wrong on one screen only.
 *
 * <p>Expects to sit inside a centred container the same size as the portrait.
 */
export function FrameOverlay({
  frame,
  size,
}: {
  frame: string | null | undefined;
  size: number;
}) {
  if (!frame) return null;

  // How far the ring extends past the avatar on each side.
  const overhang = (size / HOLE_RATIO - size) / 2;

  return (
    <Image
      source={{ uri: frame }}
      // All four insets, and no width/height. The frame's box is then derived from the
      // parent's *actual* rendered size rather than from the `size` prop, which keeps it
      // concentric even where the two disagree.
      //
      // They did disagree: with `top`/`left` plus an explicit width, the deck card drew
      // the ring 9dp down and to the right of the avatar, while the profile — same
      // component, parent sized in points instead of by a Tailwind class — was centred.
      // Measuring the two screens is what found it; it is small enough to read as "the
      // frame doesn't quite fit" rather than as a bug with a cause.
      style={{
        position: 'absolute',
        top: -overhang,
        left: -overhang,
        right: -overhang,
        bottom: -overhang,
      }}
      contentFit="contain"
      // The ring sits over the avatar, so without this it would swallow taps meant for
      // the profile underneath it.
      pointerEvents="none"
      // Animated frames should be moving whenever they are on screen; a frame that needs
      // a tap to start is just a still image most of the time.
      autoplay
      // No fade. The avatar is already painted underneath, so a transition here reads as
      // the ring flickering rather than as the image arriving.
      transition={0}
      // Animated WebP, and the same handful of frames recur across every list in the app —
      // so this is the single most worthwhile disk cache here. `recyclingKey` matters for
      // the same reason it does on the avatar underneath: a recycled row must not keep
      // the previous person's ring.
      cachePolicy="memory-disk"
      recyclingKey={frame}
    />
  );
}
