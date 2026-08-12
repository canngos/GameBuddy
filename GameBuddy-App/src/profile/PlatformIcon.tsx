import Svg, { Circle, Path, Rect } from 'react-native-svg';
import type { PlatformId } from './platforms';

type PlatformIconProps = {
  platform: PlatformId;
  color: string;
  size?: number;
};

/**
 * The five platforms, drawn rather than branded.
 *
 * **No trademarked logos, and that is a sourcing decision before it is a legal one.**
 * The PlayStation wordmark is available under CC0 in Simple Icons; the Xbox and Nintendo
 * marks are not — both companies have had theirs removed from that set on request. So the
 * honest options were a row where one platform carries an official mark and four carry
 * improvisations, or five drawn by the same hand. A set that is consistent reads better
 * than a set that is authentic in one place out of five, and PC has no official mark at
 * all, which the inconsistent version could never solve.
 *
 * Each glyph is the *hardware*, not the brand: a monitor, a twin-stick pad, a pad with a
 * D-pad, a handheld with detachable side rails, a phone. Nobody needs a logo to know which
 * row is the Switch when it is the only one shaped like a Switch.
 *
 * 24x24 viewBox, 2px strokes, round caps — the proportions {@link KeywordIcon} and
 * {@link TabIcon} already use, so all three sets look drawn by the same hand. Monochrome,
 * so one asset serves both the selected and unselected states by taking the colour it is
 * given.
 */
export function PlatformIcon({ platform, color, size = 24 }: PlatformIconProps) {
  const stroke = {
    stroke: color,
    strokeWidth: 2,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    fill: 'none',
  };

  return (
    <Svg width={size} height={size} viewBox="0 0 24 24">
      {platform === 'PC' && (
        // A monitor on a stand. The only honest answer for a platform with no mark of
        // its own, and the one shape nobody mistakes for a console.
        <>
          <Rect x={2.5} y={4} width={19} height={12.5} rx={2} {...stroke} />
          <Path d="M9 20h6M12 16.5V20" {...stroke} />
        </>
      )}

      {/* The two pads are the hard pair: at 26px a gamepad is a gamepad, and the first
          attempt drew both with the same body and lost the distinction entirely. They are
          told apart the way the actual hardware is — symmetric sticks against offset ones
          — with everything finer than that removed, because a D-pad cross at this size is
          three smudged pixels. */}
      {platform === 'PLAYSTATION' && (
        <>
          <Path
            d="M8 7.5h8a4 4 0 0 1 3.9 3.1l1 4.4A2.5 2.5 0 0 1 18.5 18a2.5 2.5 0 0 1-2-1l-1.2-1.6H8.7L7.5 17a2.5 2.5 0 0 1-2 1 2.5 2.5 0 0 1-2.4-3l1-4.4A4 4 0 0 1 8 7.5Z"
            {...stroke}
          />
          {/* Level with each other, and low. */}
          <Circle cx={9.2} cy={12.4} r={1.5} {...stroke} strokeWidth={1.7} />
          <Circle cx={14.8} cy={12.4} r={1.5} {...stroke} strokeWidth={1.7} />
        </>
      )}

      {platform === 'XBOX' && (
        <>
          <Path
            d="M8 7.5h8a4 4 0 0 1 3.9 3.1l1 4.4A2.5 2.5 0 0 1 18.5 18a2.5 2.5 0 0 1-2-1l-1.2-1.6H8.7L7.5 17a2.5 2.5 0 0 1-2 1 2.5 2.5 0 0 1-2.4-3l1-4.4A4 4 0 0 1 8 7.5Z"
            {...stroke}
          />
          {/* Offset on the diagonal: left high, right low. The one difference between
              these two controllers that survives being shrunk. */}
          <Circle cx={8.7} cy={11.1} r={1.5} {...stroke} strokeWidth={1.7} />
          <Circle cx={15.3} cy={13.6} r={1.5} {...stroke} strokeWidth={1.7} />
        </>
      )}

      {platform === 'SWITCH' && (
        // A wide screen with a narrow rail either side, and a gap between them.
        //
        // Two attempts to get here. Equal widths read as three bars; touching the rails to
        // the screen read as a barrel. The gap is what carries the idea — three separate
        // pieces means detachable, and detachable is the only thing this machine's
        // silhouette has to say.
        <>
          <Rect x={9} y={4.5} width={6} height={15} rx={0.7} {...stroke} strokeWidth={1.8} />
          <Rect x={3.6} y={5.6} width={3.9} height={12.8} rx={1.9} {...stroke} strokeWidth={1.8} />
          <Rect x={16.5} y={5.6} width={3.9} height={12.8} rx={1.9} {...stroke} strokeWidth={1.8} />
        </>
      )}

      {platform === 'MOBILE' && (
        <>
          <Rect x={6.5} y={2.5} width={11} height={19} rx={2.5} {...stroke} />
          <Path d="M10.5 5.5h3" {...stroke} strokeWidth={1.6} />
          <Circle cx={12} cy={18} r={0.9} fill={color} stroke="none" />
        </>
      )}
    </Svg>
  );
}
