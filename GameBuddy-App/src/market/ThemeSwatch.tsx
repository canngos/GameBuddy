import { View } from 'react-native';
import { GradientView } from '../ui';

/**
 * A theme on a shelf row: a little card wearing its edge.
 *
 * <p>A theme has no artwork, so the shop has to draw one. The obvious thing — a rectangle
 * filled with the two colours — is what shipped first, and it was wrong for the same
 * reason the feature itself was wrong: a filled rectangle reads as a banner. What is being
 * sold is a *border*, so the swatch is a card-shaped chip with the gradient around its
 * outside and the card's own surface colour inside, which is the product in miniature.
 *
 * <p>Built by stacking rather than by {@link ThemedCardFrame}: this is a fixed, tiny box
 * with nothing to overhang into, so an inset is simpler and cannot be clipped by the row.
 */
export function ThemeSwatch({
  stops,
  size = 64,
  edge = 4,
}: {
  stops: readonly [string, string];
  size?: number;
  edge?: number;
}) {
  return (
    <View
      className="overflow-hidden rounded-xl"
      style={{ height: size, width: size * 1.5 }}
    >
      <GradientView colors={stops} direction="diagonal" className="absolute inset-0" />
      <View
        className="bg-surface"
        style={{
          position: 'absolute',
          top: edge,
          left: edge,
          right: edge,
          bottom: edge,
          borderRadius: 12 - edge,
        }}
      />
    </View>
  );
}
