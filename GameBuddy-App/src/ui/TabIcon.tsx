import { View, type ColorValue } from 'react-native';

export type TabIconName =
  | 'deck'
  | 'community'
  | 'messages'
  | 'market'
  | 'profile'
  // The moderator console. Still rectangles and circles, so still no icon library —
  // but this is the set the header comment means: anything past these wants SVG.
  | 'analytics'
  | 'reports'
  | 'avatars'
  | 'settings';

type TabIconProps = {
  name: TabIconName;
  /**
   * `ColorValue`, not `string`: react-navigation hands the tab's tint straight
   * through, and on both platforms that can be an opaque platform colour rather than
   * a literal. It only ever ends up in a `borderColor`, which accepts either.
   */
  color: ColorValue;
  size?: number;
};

/**
 * Tab bar icons, drawn from plain views.
 *
 * No icon font and no SVG library is bundled. Adding one for five glyphs would be a
 * dependency, an asset, and a licence to keep track of; these are simple enough shapes
 * that borders and border-radius express them, and they inherit the active colour
 * without a second asset for the selected state.
 *
 * If the icon set grows past this handful — or needs anything with a curve that is not
 * a circle — switch to `react-native-svg` rather than making these cleverer.
 */
export function TabIcon({ name, color, size = 24 }: TabIconProps) {
  switch (name) {
    case 'deck':
      // Two offset cards: the stack the deck actually is.
      return (
        <View style={{ width: size, height: size }} className="items-center justify-center">
          <View
            className="absolute rounded-[3px] border-2 opacity-45"
            style={{
              width: size * 0.6,
              height: size * 0.78,
              borderColor: color,
              transform: [{ rotate: '-14deg' }, { translateX: -size * 0.12 }],
            }}
          />
          <View
            className="absolute rounded-[3px] border-2"
            style={{
              width: size * 0.6,
              height: size * 0.78,
              borderColor: color,
              transform: [{ rotate: '8deg' }, { translateX: size * 0.1 }],
            }}
          />
        </View>
      );

    case 'analytics':
      // Three bars of increasing height: the growth graph, in miniature.
      return (
        <View
          style={{ width: size, height: size }}
          className="flex-row items-end justify-center"
        >
          {[0.4, 0.68, 1].map((scale) => (
            <View
              key={scale}
              className="rounded-[1.5px]"
              style={{
                width: size * 0.16,
                height: size * 0.72 * scale,
                backgroundColor: color,
                marginHorizontal: size * 0.045,
              }}
            />
          ))}
        </View>
      );

    case 'reports':
      // A pennant on a pole. A flag rather than the usual warning triangle because a
      // triangle here would have to be faked with border tricks.
      return (
        <View style={{ width: size, height: size }} className="items-center justify-center">
          <View
            className="absolute rounded-full"
            style={{
              width: size * 0.1,
              height: size * 0.8,
              backgroundColor: color,
              left: size * 0.24,
            }}
          />
          <View
            className="absolute rounded-[2px] border-2"
            style={{
              width: size * 0.44,
              height: size * 0.36,
              borderColor: color,
              left: size * 0.33,
              top: size * 0.12,
            }}
          />
        </View>
      );

    case 'avatars':
      // A picture frame with a head in it: the avatar queue.
      return (
        <View style={{ width: size, height: size }} className="items-center justify-center">
          <View
            className="absolute rounded-[4px] border-2"
            style={{ width: size * 0.82, height: size * 0.82, borderColor: color }}
          />
          <View
            className="absolute rounded-full border-2"
            style={{
              width: size * 0.26,
              height: size * 0.26,
              borderColor: color,
              top: size * 0.24,
            }}
          />
          <View
            className="absolute rounded-t-full border-2"
            style={{
              width: size * 0.46,
              height: size * 0.2,
              borderColor: color,
              bottom: size * 0.13,
            }}
          />
        </View>
      );

    case 'settings':
      // Sliders: three tracks with a knob on each. A gear needs teeth, which is where
      // borders and radii stop being enough and an SVG library would start earning its
      // place.
      return (
        <View style={{ width: size, height: size }} className="justify-center">
          {[0.28, 0.6, 0.42].map((knob, row) => (
            <View
              key={knob}
              className="w-full flex-row items-center"
              style={{ marginVertical: size * 0.06 }}
            >
              <View
                className="flex-1 rounded-full"
                style={{ height: 2, backgroundColor: color }}
              />
              <View
                className="absolute rounded-full border-2"
                style={{
                  width: size * 0.22,
                  height: size * 0.22,
                  borderColor: color,
                  backgroundColor: 'transparent',
                  left: size * knob,
                }}
                // The knob sits on the track rather than beside it, which is what makes
                // three identical rows read as one control.
                key={`knob-${row}`}
              />
            </View>
          ))}
        </View>
      );

    case 'community':
      // Two heads and shoulders, overlapping.
      return (
        <View style={{ width: size, height: size }} className="items-center justify-center">
          <View className="flex-row items-end" style={{ gap: -size * 0.12 }}>
            <Person color={color} size={size * 0.62} faded />
            <Person color={color} size={size * 0.74} />
          </View>
        </View>
      );

    case 'messages':
      // A rounded speech bubble with a tail.
      return (
        <View style={{ width: size, height: size }} className="items-center justify-center">
          <View
            className="rounded-[6px] border-2"
            style={{ width: size * 0.86, height: size * 0.68, borderColor: color }}
          />
          <View
            className="absolute border-2 border-r-0 border-t-0"
            style={{
              width: size * 0.2,
              height: size * 0.2,
              bottom: size * 0.1,
              left: size * 0.24,
              borderColor: color,
              transform: [{ rotate: '-45deg' }],
            }}
          />
        </View>
      );

    case 'market':
      // A basket: trapezoid body plus a handle.
      return (
        <View style={{ width: size, height: size }} className="items-center justify-center">
          <View
            className="rounded-b-[5px] rounded-t-[2px] border-2"
            style={{
              width: size * 0.8,
              height: size * 0.52,
              marginTop: size * 0.22,
              borderColor: color,
            }}
          />
          <View
            className="absolute rounded-t-full border-2 border-b-0"
            style={{
              width: size * 0.44,
              height: size * 0.26,
              top: size * 0.12,
              borderColor: color,
            }}
          />
        </View>
      );

    case 'profile':
      return (
        <View style={{ width: size, height: size }} className="items-center justify-center">
          <Person color={color} size={size * 0.86} />
        </View>
      );
  }
}

/** A head over shoulders, shared by the community and profile glyphs. */
function Person({
  color,
  size,
  faded = false,
}: {
  color: ColorValue;
  size: number;
  faded?: boolean;
}) {
  return (
    <View style={{ width: size, height: size, opacity: faded ? 0.45 : 1 }} className="items-center">
      <View
        className="rounded-full border-2"
        style={{ width: size * 0.42, height: size * 0.42, borderColor: color }}
      />
      <View
        className="rounded-t-full border-2 border-b-0"
        style={{
          width: size * 0.82,
          height: size * 0.4,
          marginTop: size * 0.08,
          borderColor: color,
        }}
      />
    </View>
  );
}
