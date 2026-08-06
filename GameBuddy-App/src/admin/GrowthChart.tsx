import { View } from 'react-native';
import type { GrowthPoint } from '../api/types';
import { Text } from '../ui';

type GrowthChartProps = {
  points: GrowthPoint[];
  /** Bar height at the busiest day. */
  height?: number;
};

/**
 * Daily signups, drawn with plain views.
 *
 * No charting library and no `react-native-svg`. A bar chart is a row of rectangles
 * whose heights are a ratio, which is about fifteen lines here against a dependency,
 * its native module, and a rebuild of the dev client to add it — and the moment this
 * needs a curve, an axis or a tooltip, that trade flips and it should be reconsidered
 * rather than made cleverer.
 *
 * Zero-signup days are drawn as a one-pixel stub rather than nothing at all. A missing
 * bar reads as missing data; a flat one reads as a quiet day, which is what it is.
 */
export function GrowthChart({ points, height = 120 }: GrowthChartProps) {
  const peak = Math.max(0, ...points.map((p) => p.signups));
  const total = points.reduce((sum, p) => sum + p.signups, 0);
  // The divisor only. Kept separate from `peak` so the label reports the real busiest
  // day: clamping both made a completely quiet window read "peak 1", which is a number
  // no day actually had.
  const scale = Math.max(1, peak);

  return (
    <View>
      <View className="flex-row items-end" style={{ height }}>
        {points.map((point) => (
          <View
            key={point.date}
            className="flex-1 justify-end"
            // A gap between bars rather than a margin on them: with thirty days on a
            // phone each bar is a few points wide, and a margin would eat most of it.
            style={{ paddingHorizontal: 1 }}
          >
            <View
              className="rounded-t-[2px] bg-brand"
              style={{
                height: Math.max(1, (point.signups / scale) * height),
                // Quiet days stay visible but read as background rather than as data.
                opacity: point.signups === 0 ? 0.25 : 1,
              }}
            />
          </View>
        ))}
      </View>

      <View className="mt-2 flex-row justify-between">
        <Text variant="caption">{label(points[0]?.date)}</Text>
        <Text variant="caption">
          {total} in {points.length} days · peak {peak}
        </Text>
        <Text variant="caption">{label(points[points.length - 1]?.date)}</Text>
      </View>
    </View>
  );
}

/** "6 Aug" — the year is never in doubt across a thirty-day window. */
function label(iso: string | undefined) {
  if (!iso) return '';
  const [, month, day] = iso.split('-');
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  return `${Number(day)} ${months[Number(month) - 1] ?? ''}`;
}
