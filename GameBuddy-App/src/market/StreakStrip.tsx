import { Check, Flame } from 'lucide-react-native';
import { View } from 'react-native';
import type { Earn } from '../api/types';
import { useUpper } from '../i18n/case';
import { useT } from '../i18n/useT';
import { useThemeColors } from '../theme';
import { Button } from '../ui/Button';
import { cn } from '../ui/cn';
import { GradientView } from '../ui/Gradient';
import { glow } from '../ui/glow';
import { Icon } from '../ui/Icon';
import { Text } from '../ui/Text';

/**
 * What each consecutive day pays, capped at the last entry.
 *
 * **This mirrors `CoinFaucet.DAILY_BY_STREAK` on the backend, and the duplication is
 * deliberate and bounded.** The server is still the only thing that decides what a claim
 * actually pays — `Earn.dailyReward` is its figure for the next claim, and that is the
 * number on the button. This copy exists purely to draw the days *ahead*, which no
 * endpoint reports.
 *
 * If the two ever disagree the visible effect is a wrong number on a future day, not a
 * wrong payment: the claim still credits whatever the server says. That is the same trade
 * `src/profile/platforms.ts` makes for its labels, for the same reason — a strip that
 * cannot render until a request returns is a strip that flashes empty.
 */
const DAILY_BY_STREAK = [5, 10, 15, 20, 25, 25, 25];

/** How many days the cycle shows. The reward is flat past this, so there is nothing to add. */
const CYCLE = DAILY_BY_STREAK.length;

type StreakStripProps = {
  earn: Earn;
  busy: boolean;
  onClaim: () => void;
  /** "in 3h" — the caller already formats this for its other rows. */
  readyIn: string;
};

/**
 * The daily streak, as the seven-day cycle it has always been.
 *
 * This used to be one row in a list of five identical ones — a title, "1-day streak so
 * far", and a `+10 Claim` on the right — which said nothing about the only property that
 * makes a streak worth keeping: that tomorrow pays more than today, up to a point, and
 * that missing a day sends you back to five coins.
 *
 * The seven cells are not a metaphor invented for the design. `CoinFaucet` on the backend
 * has held a literal seven-entry table since the faucet was written; the client simply
 * never showed it. Rendering it is the difference between "claim your coins" and "you are
 * three days into a week that pays 125".
 *
 * The streak is the one thing on this screen that compounds, so it gets the space and the
 * gradient and everything else earns its place around it.
 */
export function StreakStrip({ earn, busy, onClaim, readyIn }: StreakStripProps) {
  const colors = useThemeColors();
  const t = useT();
  const upper = useUpper();

  /*
   * Which cell is "today".
   *
   * `earn.streak` is days *already banked*. When a claim is available the live cell is the
   * next one; when it is not, the last banked day is the live one and the rest are still
   * ahead. Getting this wrong by one is what would make the strip disagree with the button
   * beside it, which is the one thing it must never do.
   */
  const banked = Math.min(earn.streak, CYCLE);
  const liveIndex = earn.dailyAvailable ? Math.min(banked, CYCLE - 1) : banked - 1;
  const weekTotal = DAILY_BY_STREAK.reduce((sum, n) => sum + n, 0);

  return (
    <View className="overflow-hidden rounded-card" style={glow(earn.dailyAvailable ? 'soft' : 'none', colors.gold)}>
      <View className="overflow-hidden rounded-card border border-line bg-surface">
        {/* A wash of the gold ramp, at low alpha. Only when there is something to take —
            a lit panel over a spent faucet is the app celebrating nothing. */}
        <GradientView
          name="gold"
          direction="diagonal"
          className={cn('absolute inset-0', earn.dailyAvailable ? 'opacity-10' : 'opacity-0')}
          pointerEvents="none"
        />

        <View className="gap-4 p-5">
          <View className="flex-row items-center gap-2">
            <Icon
              as={Flame}
              size={18}
              tone="gold"
              // Filled once a run is going. An outline flame for a 6-day streak reads as
              // "not yet", which is the opposite of what six days deserves.
              fill={earn.streak > 0 ? colors.gold : 'none'}
            />
            <Text variant="overline" className="text-gold">
              {upper(t.market.earn.streakHeader)}
            </Text>
            <View className="flex-1" />
            <Text variant="caption">
              {earn.streak > 0 ? t.market.earn.day(earn.streak) : t.market.earn.notStarted}
            </Text>
          </View>

          <View className="flex-row justify-between gap-1">
            {DAILY_BY_STREAK.map((reward, index) => (
              <DayCell
                key={index}
                day={index + 1}
                reward={reward}
                claimed={index < banked}
                live={index === liveIndex && earn.dailyAvailable}
              />
            ))}
          </View>

          {earn.dailyAvailable ? (
            <Button
              label={t.market.earn.claimCoins(earn.dailyReward)}
              loading={busy}
              onPress={onClaim}
            />
          ) : (
            // Not a disabled button. A greyed-out control invites tapping to find out why;
            // a sentence answers the question without being tapped.
            <Text variant="caption" className="text-center">
              {earn.streak > 0
                ? t.market.earn.nextDay(earn.streak + 1, readyIn, weekTotal)
                : t.market.earn.back(readyIn)}
            </Text>
          )}
        </View>
      </View>
    </View>
  );
}

/**
 * One day.
 *
 * Three states, and every one of them keeps the same class keys with only the values
 * moving — a class that appears and disappears stops NativeWind painting the subtree, and
 * seven cells changing state on every claim is the worst possible place to learn that.
 * See `src/ui/hairline.ts`.
 */
function DayCell({
  day,
  reward,
  claimed,
  live,
}: {
  day: number;
  reward: number;
  claimed: boolean;
  live: boolean;
}) {
  const colors = useThemeColors();

  return (
    <View className="flex-1 items-center gap-1">
      <View
        className={cn(
          'h-9 w-full items-center justify-center rounded-xl border',
          live
            ? 'border-gold bg-gold/20'
            : claimed
              ? 'border-transparent bg-gold/10'
              : 'border-transparent bg-raised',
        )}
        style={glow(live ? 'soft' : 'none', colors.gold)}
      >
        {claimed ? (
          <Icon as={Check} size={14} tone="gold" strokeWidth={3} />
        ) : (
          <Text
            variant="numeral"
            className={cn('text-[13px] leading-[17px]', live ? 'text-gold' : 'text-muted')}
          >
            {reward}
          </Text>
        )}
      </View>
      <Text
        variant="caption"
        className={cn('text-[10px] leading-[13px]', live ? 'text-gold' : 'text-muted')}
      >
        {day}
      </Text>
    </View>
  );
}
