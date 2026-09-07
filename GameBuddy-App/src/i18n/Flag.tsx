import { View } from 'react-native';
import Svg, { Circle, Path, Polygon, Rect } from 'react-native-svg';
import { useThemeColors } from '../theme';
import type { Lang } from './languages';

/**
 * A country flag in a circular frame, drawn rather than typed.
 *
 * **Not emoji.** `🇫🇮` looks like a flag on iOS and like the letters "FI" on Android —
 * Android has never shipped the regional-indicator glyphs, and no amount of font
 * fallback conjures them. A language picker built from emoji would therefore be a
 * column of two-letter codes on the platform this app ships to first.
 *
 * **Not image assets either.** Seven PNGs at three densities is twenty-one files to
 * carry for artwork that is a handful of rectangles; `react-native-svg` is already a
 * dependency (Lucide draws through it), so these cost nothing to bundle and stay sharp
 * at any size.
 *
 * The circle is a `borderRadius` clip on the wrapper rather than an SVG mask: the flags
 * are then plain unclipped rectangles, which is far easier to read and to correct.
 * Each is cropped to its middle, which is why the proportions here are not the official
 * ones — a Finnish cross drawn to spec sits off-centre and looks broken once round.
 */
type FlagProps = {
  lang: Lang;
  size?: number;
};

export function Flag({ lang, size = 32 }: FlagProps) {
  const colors = useThemeColors();
  const Artwork = FLAGS[lang];

  return (
    <View
      style={{
        width: size,
        height: size,
        borderRadius: size / 2,
        overflow: 'hidden',
        // A hairline ring, so a white-heavy flag (Finland) still reads as a disc
        // against the card behind it rather than dissolving into it.
        borderWidth: 1,
        borderColor: colors.line,
      }}
    >
      <Svg width={size} height={size} viewBox="0 0 60 60">
        <Artwork />
      </Svg>
    </View>
  );
}

/** A Nordic cross, which is three of the seven. Only the colours differ. */
function NordicCross({ field, cross }: { field: string; cross: string }) {
  return (
    <>
      <Rect x="0" y="0" width="60" height="60" fill={field} />
      <Rect x="0" y="24" width="60" height="12" fill={cross} />
      <Rect x="17" y="0" width="12" height="60" fill={cross} />
    </>
  );
}

/** Horizontal bands, top to bottom. */
function Bands({ colours }: { colours: string[] }) {
  const height = 60 / colours.length;
  return (
    <>
      {colours.map((colour, index) => (
        <Rect key={colour + index} x="0" y={index * height} width="60" height={height} fill={colour} />
      ))}
    </>
  );
}

/** Vertical bands, left to right. */
function Stripes({ colours }: { colours: string[] }) {
  const width = 60 / colours.length;
  return (
    <>
      {colours.map((colour, index) => (
        <Rect key={colour + index} x={index * width} y="0" width={width} height="60" fill={colour} />
      ))}
    </>
  );
}

/** A five-pointed star, as an SVG polygon. */
function star(cx: number, cy: number, outer: number, inner: number): string {
  const points: string[] = [];
  for (let i = 0; i < 10; i++) {
    const radius = i % 2 === 0 ? outer : inner;
    // Starts at the top and walks clockwise, so the star sits point-up.
    const angle = (Math.PI / 5) * i - Math.PI / 2;
    points.push(`${cx + radius * Math.cos(angle)},${cy + radius * Math.sin(angle)}`);
  }
  return points.join(' ');
}

const FLAGS: Record<Lang, () => React.JSX.Element> = {
  /**
   * The Union Flag, for English.
   *
   * Cropped to the centre and simplified: the counterchange — the way the red saltire
   * sits offset inside the white one, so the diagonals pinwheel — is what makes this
   * flag recognisable, and it survives at 32px. The exact widths do not.
   */
  en: () => (
    <>
      <Rect x="0" y="0" width="60" height="60" fill="#012169" />
      {/* White saltire, then red inside it, offset to give the pinwheel. */}
      <Path d="M0,0 L60,60 M60,0 L0,60" stroke="#FFFFFF" strokeWidth="14" />
      <Path d="M0,0 L60,60" stroke="#C8102E" strokeWidth="6" transform="translate(3,-3)" />
      <Path d="M60,0 L0,60" stroke="#C8102E" strokeWidth="6" transform="translate(3,3)" />
      {/* The upright cross sits over the diagonals. */}
      <Rect x="0" y="22" width="60" height="16" fill="#FFFFFF" />
      <Rect x="22" y="0" width="16" height="60" fill="#FFFFFF" />
      <Rect x="0" y="25" width="60" height="10" fill="#C8102E" />
      <Rect x="25" y="0" width="10" height="60" fill="#C8102E" />
    </>
  ),

  fi: () => <NordicCross field="#FFFFFF" cross="#003580" />,
  sv: () => <NordicCross field="#006AA7" cross="#FECC00" />,
  de: () => <Bands colours={['#000000', '#DD0000', '#FFCE00']} />,
  fr: () => <Stripes colours={['#002395', '#FFFFFF', '#ED2939']} />,

  /**
   * Spain, without the coat of arms. At this size the arms are an illegible smudge, and
   * the red-yellow-red band is what identifies the flag anyway. The yellow is double
   * height, which is the proportion that matters.
   */
  es: () => (
    <>
      <Rect x="0" y="0" width="60" height="60" fill="#AA151B" />
      <Rect x="0" y="15" width="60" height="30" fill="#F1BF00" />
    </>
  ),

  /**
   * Turkey. The crescent is one white disc with a red one biting into it — the same
   * trick the flag itself uses — and the star sits in the crescent's opening.
   */
  tr: () => (
    <>
      <Rect x="0" y="0" width="60" height="60" fill="#E30A17" />
      <Circle cx="26" cy="30" r="13" fill="#FFFFFF" />
      <Circle cx="30.5" cy="30" r="10.4" fill="#E30A17" />
      <Polygon points={star(43, 30, 7, 2.9)} fill="#FFFFFF" />
    </>
  ),
};
