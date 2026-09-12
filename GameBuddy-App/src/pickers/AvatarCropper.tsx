import { Image } from "expo-image";
import { ImageManipulator, SaveFormat } from "expo-image-manipulator";
import { useState } from "react";
import {
  ActivityIndicator,
  StyleSheet,
  useWindowDimensions,
  View,
} from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
} from "react-native-reanimated";
import { useT } from "../i18n/useT";
import { useThemeColors } from "../theme";
import { Button } from "../ui/Button";
import { overlay } from "../ui/elevation";
import { FrameOverlay } from "../ui/FramedAvatar";
import { Text } from "../ui/Text";

/** What the server stores, and what the crop is rendered down to. */
const OUTPUT = 512;

/**
 * Places a picked image inside the avatar, before it is uploaded.
 *
 * **The operating system's cropper could not do this job.** `expo-image-picker`'s
 * `allowsEditing` opens a square editor drawn by Android, which is fine as far as it goes
 * and stops one step short of the thing that matters: the avatar is a *circle*, worn inside
 * a frame the gamer may have paid for, and neither is visible while cropping. So people
 * placed a face in the middle of a square and found it sitting off-centre in a ring, with
 * no way to nudge it. It also only ever covered two of the three ways in — an image from
 * Files had no crop step at all and went to the server whole, to be centre-cropped by a
 * machine that has never seen a face.
 *
 * This shows the real circle, at the real proportions, with the real frame on top: what you
 * position is what everybody else sees.
 *
 * **The crop is computed here and the image is cut here**, so the upload is a 512px square
 * rather than a 12-megapixel original — which also means the server's own centre-crop
 * becomes a no-op rather than a second opinion.
 */
export function AvatarCropper({
  uri,
  frame,
  busy = false,
  onCancel,
  onDone,
}: {
  uri: string;
  /** The worn frame, drawn over the preview. Null is the common case. */
  frame?: string | null;
  /** True while the caller is uploading the result. */
  busy?: boolean;
  onCancel: () => void;
  onDone: (croppedUri: string) => void;
}) {
  const t = useT();
  const colors = useThemeColors();
  const { width } = useWindowDimensions();

  /**
   * The circle the gamer is composing into.
   *
   * Large enough to judge a face by, and small enough to leave room for the title and the
   * two buttons on a 360dp phone — which 300 did not, and the layout collapsed around it.
   */
  const circle = Math.min(width - 96, 260);

  const [source, setSource] = useState<{
    width: number;
    height: number;
  } | null>(null);
  const [cropping, setCropping] = useState(false);
  const [failed, setFailed] = useState(false);

  /*
   * The size comes from the image that is on screen, not from a second read of the file.
   *
   * `Image.loadAsync` looked like the tidy way to ask, and it is a trap here: it decodes the
   * file into its own ref, and the picked URI is a one-shot `content://` handle from the
   * Android photo picker. Having been consumed once, the `<Image>` below rendered a black
   * square at the wrong aspect ratio — a crop of nothing, positioned confidently.
   *
   * `onLoad` reports the intrinsic size of the decode that is already happening, so there is
   * only ever one reader of the URI.
   */

  /*
   * `contentFit="cover"` does the fitting; this only needs to know by how much.
   *
   * An earlier version sized the image by hand and let `cover` fit inside that, which is two
   * fitting rules stacked on one another — and they disagreed, so the picture landed as a
   * short black band across the middle of the circle. Letting the image fill the circle and
   * recording the single scale factor keeps one rule, and it is the same factor the crop
   * needs to convert back to source pixels.
   */
  const coverFactor = source
    ? Math.max(circle / source.width, circle / source.height)
    : 1;

  const coveredWidth = source ? source.width * coverFactor : circle;
  const coveredHeight = source ? source.height * coverFactor : circle;

  const translateX = useSharedValue(0);
  const translateY = useSharedValue(0);
  const scale = useSharedValue(1);
  const savedX = useSharedValue(0);
  const savedY = useSharedValue(0);
  const savedScale = useSharedValue(1);

  /**
   * How far the image may be dragged before the circle would show through.
   *
   * Clamped rather than rubber-banded on release: this is a crop, and a position that
   * cannot be saved is not worth letting somebody reach in the first place.
   */
  function clamp(value: number, limit: number) {
    "worklet";
    return Math.min(limit, Math.max(-limit, value));
  }

  const pan = Gesture.Pan()
    .onStart(() => {
      savedX.value = translateX.value;
      savedY.value = translateY.value;
    })
    .onChange((event) => {
      const limitX = Math.max(0, (coveredWidth * scale.value - circle) / 2);
      const limitY = Math.max(0, (coveredHeight * scale.value - circle) / 2);
      translateX.value = clamp(savedX.value + event.translationX, limitX);
      translateY.value = clamp(savedY.value + event.translationY, limitY);
    });

  const pinch = Gesture.Pinch()
    .onStart(() => {
      savedScale.value = scale.value;
    })
    .onChange((event) => {
      // Never below 1: the image is fitted to cover at 1, so anything smaller would open a
      // gap at the edge of the circle. Four times is past any useful crop of a phone photo.
      scale.value = Math.min(4, Math.max(1, savedScale.value * event.scale));
    })
    .onEnd(() => {
      const limitX = Math.max(0, (coveredWidth * scale.value - circle) / 2);
      const limitY = Math.max(0, (coveredHeight * scale.value - circle) / 2);
      // Zooming out can leave the image parked outside its own new bounds.
      translateX.value = withSpring(clamp(translateX.value, limitX), {
        damping: 20,
      });
      translateY.value = withSpring(clamp(translateY.value, limitY), {
        damping: 20,
      });
    });

  const gesture = Gesture.Simultaneous(pan, pinch);

  const imageStyle = useAnimatedStyle(() => ({
    transform: [
      { translateX: translateX.value },
      { translateY: translateY.value },
      { scale: scale.value },
    ],
  }));

  async function crop() {
    if (!source) return;
    setCropping(true);
    try {
      /*
       * Screen units back to the image, as *fractions* rather than pixels.
       *
       * **The size `onLoad` reports is not the size the file has.** Android decodes a large
       * picture downsampled to save memory, so a 900x1800 photo arrives here as 683x1365 —
       * and `ImageManipulator` then crops in the file's own full-size pixels. Computing the
       * rectangle in the preview's pixels and handing it to the manipulator meant a 683px
       * square cut out of a 900px-wide image: every crop came back zoomed in by the
       * downsample factor, off-centre, and different for each image depending on how
       * aggressively Android decided to shrink it. That is the "not the same as what I
       * cropped" bug, and it survived the earlier fix because both preview and result
       * looked plausible on their own.
       *
       * Fractions have no such problem. Where the circle sits is a property of the picture,
       * not of whatever resolution somebody decoded it at, so it is computed once here and
       * multiplied back up by the real dimensions below.
       */
      const drawnScale = coverFactor * scale.value;
      const size = circle / drawnScale;

      const fraction = size / source.width;
      const originFractionX =
        (source.width / 2 - translateX.value / drawnScale - size / 2) /
        source.width;
      const originFractionY =
        (source.height / 2 - translateY.value / drawnScale - size / 2) /
        source.height;

      /*
       * The file's real dimensions, asked of the one component that will do the cutting.
       *
       * Rendering with no operations queued decodes the original and hands back a reference
       * carrying its true width and height. Deliberately not `Image.loadAsync` and not the
       * picker's metadata: this is the only number guaranteed to be in the same coordinate
       * space as the `crop` below, which is the whole point.
       */
      const original = await ImageManipulator.manipulate(uri).renderAsync();

      // Rounded and clamped inside the source: a rect even a pixel outside it is rejected
      // by the native side, and floating point at these magnitudes gets there on its own.
      const side = Math.max(
        1,
        Math.round(
          Math.min(fraction * original.width, original.width, original.height),
        ),
      );
      const rect = {
        originX: Math.max(
          0,
          Math.min(
            Math.round(originFractionX * original.width),
            original.width - side,
          ),
        ),
        originY: Math.max(
          0,
          Math.min(
            Math.round(originFractionY * original.height),
            original.height - side,
          ),
        ),
        width: side,
        height: side,
      };
      // The dimensions are all this decode was for; the full-resolution bitmap behind it
      // must not wait for GC - on a 100+ MP photo it sits next to the preview's own copy.
      original.release();

      const context = ImageManipulator.manipulate(uri);
      context.crop(rect).resize({ width: OUTPUT, height: OUTPUT });
      const rendered = await context.renderAsync();
      const saved = await rendered.saveAsync({
        format: SaveFormat.JPEG,
        compress: 0.9,
      });
      rendered.release();
      onDone(saved.uri);
    } catch {
      setFailed(true);
    } finally {
      setCropping(false);
    }
  }

  return (
    <View style={[StyleSheet.absoluteFill, overlay(60)]}>
      <View className="flex-1 items-center justify-center gap-6 bg-ink-900/95 px-6">
        <View className="gap-1">
          <Text variant="title" className="text-center text-white">
            {t.settings.avatarScreen.cropTitle}
          </Text>
          <Text variant="caption" className="text-center text-white/70">
            {t.settings.avatarScreen.cropHint}
          </Text>
        </View>

        {/* One fixed-size box with both layers absolutely positioned inside it. The frame
            used to be a sibling pulled back over the circle with a negative margin, which
            worked out to be a 260dp hole in the middle of a flex column — the title and the
            buttons were displaced off the screen entirely. A box that owns its own space
            cannot do that. */}
        <View style={{ width: circle, height: circle }}>
          <GestureDetector gesture={gesture}>
            <View
              style={{
                width: circle,
                height: circle,
                borderRadius: circle / 2,
              }}
              className="items-center justify-center overflow-hidden bg-raised"
            >
              {/*
                Sized to the *whole* covered image, not to the circle.

                This box used to be `circle` square with the image set to `cover` inside it,
                which cropped the photo to a square before anybody had touched it. Panning
                then slid that already-square picture around and uncovered the grey behind
                it, rather than revealing more of the photo — while `crop()` went on
                computing its rectangle from the full covered image. So the preview and the
                result disagreed, and the taller the original the further apart they got: a
                4:3 portrait lost a quarter of its height to a crop nobody asked for, and
                dragging the face into the circle cut a region several hundred pixels away
                from the one on screen.

                At these dimensions the box *is* the covered image, so nothing is cropped
                early, panning moves real picture, and the circle above does the clipping —
                which is what makes what you position the thing that gets cut.
              */}
              <Animated.View
                style={[
                  { width: coveredWidth, height: coveredHeight },
                  imageStyle,
                ]}
              >
                <Image
                  source={{ uri }}
                  style={StyleSheet.absoluteFill}
                  // The box already carries the source's aspect ratio, so this scales
                  // without choosing anything to discard.
                  contentFit="cover"
                  onLoad={(event) =>
                    setSource({
                      width: event.source.width,
                      height: event.source.height,
                    })
                  }
                  onError={() => setFailed(true)}
                />
              </Animated.View>

              {!source && (
                <View
                  style={StyleSheet.absoluteFill}
                  className="items-center justify-center"
                >
                  <ActivityIndicator color={colors.primary} />
                </View>
              )}
            </View>
          </GestureDetector>

          <View style={StyleSheet.absoluteFill} pointerEvents="none">
            <FrameOverlay frame={frame} size={circle} />
          </View>
        </View>

        {failed && (
          <Text variant="caption" className="text-center text-danger">
            {t.settings.avatarScreen.cropFailed}
          </Text>
        )}

        <View className="w-full gap-2">
          <Button
            label={t.settings.avatarScreen.cropConfirm}
            loading={cropping || busy}
            disabled={!source}
            onPress={() => void crop()}
          />
          <Button label={t.common.cancel} variant="ghost" onPress={onCancel} />
        </View>
      </View>
    </View>
  );
}
