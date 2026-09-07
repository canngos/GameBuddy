import Svg, { Circle, Path, Rect } from 'react-native-svg';

/**
 * The families a keyword can belong to.
 *
 * Twelve icons for forty-eight keywords, not forty-eight icons. Drawing one per keyword
 * would mean a dozen near-identical crosshairs and a maintenance job every time the
 * catalogue grows — and it would not help anybody read the list faster, which is the
 * whole point of putting icons there. Grouping means the eye learns twelve shapes and
 * can then skim: everything competitive looks competitive.
 */
export type KeywordFamily =
  | 'precision'
  | 'competitive'
  | 'trophy'
  | 'chill'
  | 'social'
  | 'solo'
  | 'story'
  | 'creative'
  | 'time'
  | 'explore'
  | 'speed'
  | 'fandom';

/**
 * Which family a keyword belongs to.
 *
 * Keyed by the exact `keyword_name` in the database, lower-cased. A keyword with no entry
 * falls back to {@link DEFAULT_FAMILY} rather than rendering nothing, so a keyword added
 * to the catalogue tomorrow gets a sensible icon today and can be classified later.
 */
const FAMILIES: Record<string, KeywordFamily> = {
  // Aiming at a number and hitting it.
  'aim training': 'precision',
  'combo practice': 'precision',
  'min-maxer': 'precision',
  theorycrafter: 'precision',
  tryhard: 'precision',

  // Playing to win against other people.
  competitive: 'competitive',
  'ranked grinder': 'competitive',
  clutch: 'competitive',
  esports: 'competitive',
  'tournament goer': 'competitive',
  shotcaller: 'competitive',

  // Playing to finish, own or complete something.
  'achievement hunter': 'trophy',
  completionist: 'trophy',
  collector: 'trophy',
  'loot goblin': 'trophy',
  'endgame focused': 'trophy',

  // Low stakes, low friction.
  chill: 'chill',
  casual: 'chill',
  'toxic-free': 'chill',
  'no mic': 'chill',

  // Other people are the point.
  social: 'social',
  'co-op': 'social',
  'couch co-op': 'social',
  'squad player': 'social',
  'guild leader': 'social',
  'voice chat': 'social',

  // Deliberately alone.
  'solo player': 'solo',

  // There for the story.
  'story-driven': 'story',
  'lore nerd': 'story',
  'no spoilers': 'story',
  roleplayer: 'story',

  // Making things, or making things work.
  builder: 'creative',
  decorator: 'creative',
  modder: 'creative',
  'emulator user': 'creative',

  // How long a session runs.
  'long sessions': 'time',
  'short sessions': 'time',
  grinder: 'time',

  // Going and looking.
  explorer: 'explore',
  raider: 'explore',
  'sim racer': 'explore',

  // Doing it fast, or doing it in front of people.
  speedrunner: 'speed',
  streamer: 'speed',
  chaotic: 'speed',

  // Loving a thing for its own sake.
  'anime fan': 'fandom',
  'waifu collector': 'fandom',
  nostalgic: 'fandom',
  'jumpscare enjoyer': 'fandom',
};

const DEFAULT_FAMILY: KeywordFamily = 'chill';

export function familyOf(keyword: string): KeywordFamily {
  return FAMILIES[keyword.trim().toLowerCase()] ?? DEFAULT_FAMILY;
}

type KeywordIconProps = {
  /** The keyword name; the family is looked up from it. */
  keyword: string;
  color: string;
  size?: number;
};

/**
 * A keyword's icon, drawn as a stroked path.
 *
 * SVG rather than composed Views, on the instruction {@link TabIcon} used to carry: that
 * file drew its glyphs from borders and border-radius and said outright that anything past
 * a handful, or anything needing a curve that is not a circle, should switch to
 * `react-native-svg` instead of getting cleverer. Twelve icons with arcs and diagonals was
 * past both lines. `TabIcon` has since taken its own advice and moved to Lucide.
 *
 * These stay hand-drawn, and deliberately: the twelve families are this product's own
 * vocabulary — there is no general-purpose icon set with a glyph for "co-op grinder". They
 * are drawn to Lucide's spec, which is what keeps them from looking like a second set.
 *
 * Single colour, passed in, so these tint with selection state and theme exactly as the
 * tab icons do — no second asset for the selected state, and nothing to re-export when
 * the palette changes.
 */
export function KeywordIcon({ keyword, color, size = 22 }: KeywordIconProps) {
  const family = familyOf(keyword);

  // 24x24 viewBox, 2px strokes, round caps: Lucide's spec exactly, which is what lets
  // these sit beside a Lucide glyph without either looking wrong. Do not change it.
  const stroke = { stroke: color, strokeWidth: 2, strokeLinecap: 'round' as const, fill: 'none' };

  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      {family === 'precision' && (
        <>
          <Circle cx={12} cy={12} r={8} {...stroke} />
          <Circle cx={12} cy={12} r={3} {...stroke} />
          <Path d="M12 1v3M12 20v3M1 12h3M20 12h3" {...stroke} />
        </>
      )}

      {family === 'competitive' && (
        // A pennant on a pole: a match, a bracket, a thing you win.
        <>
          <Path d="M6 22V3" {...stroke} />
          <Path d="M6 4h12l-3 4 3 4H6" {...stroke} strokeLinejoin="round" />
        </>
      )}

      {family === 'trophy' && (
        <>
          <Path d="M8 4h8v5a4 4 0 0 1-8 0V4Z" {...stroke} strokeLinejoin="round" />
          <Path d="M8 6H5v2a3 3 0 0 0 3 3M16 6h3v2a3 3 0 0 1-3 3" {...stroke} />
          <Path d="M12 13v4M9 21h6M10 17h4l1 4H9l1-4Z" {...stroke} strokeLinejoin="round" />
        </>
      )}

      {family === 'chill' && (
        // A crescent. Quiet, unhurried, nobody shouting.
        <Path d="M20 14A9 9 0 1 1 10 4a7 7 0 0 0 10 10Z" {...stroke} strokeLinejoin="round" />
      )}

      {family === 'social' && (
        <>
          <Circle cx={9} cy={8} r={3.2} {...stroke} />
          <Path d="M3 20a6 6 0 0 1 12 0" {...stroke} />
          <Circle cx={17} cy={9} r={2.4} {...stroke} />
          <Path d="M15.5 15a5 5 0 0 1 5.5 5" {...stroke} />
        </>
      )}

      {family === 'solo' && (
        <>
          <Circle cx={12} cy={8} r={3.6} {...stroke} />
          <Path d="M5 21a7 7 0 0 1 14 0" {...stroke} />
        </>
      )}

      {family === 'story' && (
        // An open book.
        <>
          <Path d="M12 6.5C10.5 5 8.5 4.5 4 4.5v13c4.5 0 6.5.5 8 2" {...stroke} strokeLinejoin="round" />
          <Path d="M12 6.5C13.5 5 15.5 4.5 20 4.5v13c-4.5 0-6.5.5-8 2" {...stroke} strokeLinejoin="round" />
        </>
      )}

      {family === 'creative' && (
        // A brick and a tool: building and modifying.
        <>
          <Rect x={3} y={13} width={18} height={7} rx={1.5} {...stroke} />
          <Path d="M8 13v7M14 13v7" {...stroke} />
          <Path d="M8 9l4-5 4 5" {...stroke} strokeLinejoin="round" />
        </>
      )}

      {family === 'time' && (
        <>
          <Circle cx={12} cy={12} r={8.5} {...stroke} />
          <Path d="M12 7v5.5l3.5 2" {...stroke} strokeLinejoin="round" />
        </>
      )}

      {family === 'explore' && (
        // A compass needle.
        <>
          <Circle cx={12} cy={12} r={8.5} {...stroke} />
          <Path d="M15.5 8.5l-2 5-5 2 2-5 5-2Z" {...stroke} strokeLinejoin="round" />
        </>
      )}

      {family === 'speed' && (
        // A bolt.
        <Path d="M13 2L4 14h6l-1 8 9-12h-6l1-8Z" {...stroke} strokeLinejoin="round" />
      )}

      {family === 'fandom' && (
        // A four-point sparkle.
        <>
          <Path d="M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2 2-6Z" {...stroke} strokeLinejoin="round" />
          <Path d="M18.5 4.5l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7.7-2Z" {...stroke} strokeLinejoin="round" />
        </>
      )}
    </Svg>
  );
}
