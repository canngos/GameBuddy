import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Check,
  Clapperboard,
  Coins,
  Crown,
  Target,
  type LucideIcon,
} from 'lucide-react-native';
import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { REWARDED_AD_COINS, adsAvailable, showRewardedAd } from '../ads/rewarded';
import { earnApi } from '../api/coins';
import type { Earn, Quest } from '../api/types';
import { useSession } from '../session/store';
import { Icon, Text, feedback, messageOf, showToast } from '../ui';
import { StreakStrip } from './StreakStrip';

const EARN_KEY = ['earn'];

/**
 * Ways to earn coins, above the ways to buy them.
 *
 * The order is the argument. Lifetime income used to be 975 coins from thirteen one-time
 * badge rewards against a 9,400-coin catalogue, which made every item past the first few a
 * coin-pack advertisement rather than something to work towards. A shop whose earning
 * section is hidden below the till is telling you which one it would rather you used.
 */
export function EarnCoins({ onBalanceChange }: { onBalanceChange?: (coins: number) => void }) {
  const queryClient = useQueryClient();

  const earn = useQuery({
    queryKey: EARN_KEY,
    queryFn: earnApi.state,
    // The daily unlocks on a wall clock, so a cached "not yet" goes stale by itself.
    staleTime: 60 * 1000,
  });

  /**
   * Every claim answers with the whole screen, so the response is written straight into
   * the cache. Refetching instead would leave a window where the coins had been paid but
   * the button still said "claim" — the shelf disagreeing with itself, which on a reward
   * is an invitation to tap again.
   */
  const applyEarn = (next: Earn) => {
    // Read before the cache is written, so this is genuinely the figure that was on screen
    // a moment ago rather than the one that is about to be.
    const before = earn.data?.coinBalance;

    queryClient.setQueryData(EARN_KEY, next);
    // The Market header and the profile both show a balance that has just moved.
    void queryClient.invalidateQueries({ queryKey: ['cosmetics'] });
    void queryClient.invalidateQueries({ queryKey: ['me'] });
    onBalanceChange?.(next.coinBalance);

    /*
     * The gained amount comes from the difference, not from the row that was tapped.
     *
     * The rows already show a reward figure, but it is a projection — the daily in
     * particular pays on the server's streak table, and the strip's own comment is explicit
     * that the client must not promise a day number. Announcing what actually arrived keeps
     * that honest, and it is the only figure that is certainly right.
     */
    const gained = before === undefined ? 0 : next.coinBalance - before;
    if (gained <= 0) return;

    feedback.reward();
    showToast({
      id: `earned:${next.coinBalance}`,
      title: `+${gained} coins`,
      body: 'Spend them on frames, banners or likes.',
      icon: Coins,
      tone: 'gold',
    });
  };

  const daily = useMutation({ mutationFn: earnApi.claimDaily, onSuccess: applyEarn });
  const quest = useMutation({ mutationFn: earnApi.claimQuest, onSuccess: applyEarn });
  const stipend = useMutation({ mutationFn: earnApi.claimStipend, onSuccess: applyEarn });

  // Watching an advert is not a mutation of ours: nothing is claimed and no endpoint is
  // called. AdMob's servers pay the coins through the server-side callback, so all this
  // tracks is whether an ad is on screen, and afterwards it refetches to pick up whatever
  // the server decided.
  const userId = useSession((s) => s.userId);
  const [watching, setWatching] = useState(false);

  async function watchAd() {
    if (!userId) return;
    setWatching(true);
    const before = earn.data?.coinBalance;

    try {
      const outcome = await showRewardedAd(userId);
      if (outcome === 'earned') {
        // Deliberately a refetch rather than adding the coins locally. The grant happens
        // out of band on Google's callback, so the client genuinely does not know the new
        // balance — and guessing it would show coins that might never arrive.
        await queryClient.invalidateQueries({ queryKey: EARN_KEY });
        void queryClient.invalidateQueries({ queryKey: ['cosmetics'] });
        void queryClient.invalidateQueries({ queryKey: ['me'] });

        /*
         * Announced from what the refetch actually returned, for the same reason the fetch
         * exists at all: Google's server-side callback is what pays, and it can be late or
         * can decline. Congratulating somebody on coins that never landed is worse than
         * saying nothing, so this stays silent unless the balance really moved.
         */
        const after = queryClient.getQueryData<Earn>(EARN_KEY)?.coinBalance;
        if (before !== undefined && after !== undefined && after > before) {
          feedback.reward();
          showToast({
            id: `earned:${after}`,
            title: `+${after - before} coins`,
            body: 'Thanks for watching.',
            icon: Coins,
            tone: 'gold',
          });
        }
      }
    } finally {
      setWatching(false);
    }
  }

  const busy = daily.isPending || quest.isPending || stipend.isPending || watching;
  const failure = daily.error ?? quest.error ?? stipend.error;
  const state = earn.data;

  if (!state) return null;

  return (
    <View className="gap-3 pb-6">
      <View className="gap-1">
        <Text variant="overline">EARN</Text>
        <Text variant="caption">Coins come back every day. Nothing here costs money.</Text>
      </View>

      {failure && (
        <View className="rounded-card border border-danger/40 bg-danger/10 p-3">
          <Text variant="caption" className="text-danger">
            {messageOf(failure)}
          </Text>
        </View>
      )}

      {/* The streak is the headline, because it is the one that compounds and the one
          worth coming back for — so it is no longer a row in this list at all. It is the
          seven-day strip above, which is the shape the backend's reward table has always
          had. See `src/market/StreakStrip.tsx`.

          The title still never projects a day number. "Day 4" would be a promise, and a
          broken streak pays day one however long the old run was. The strip shows banked
          days and the server's own figure for the next claim, so it stays honest. */}
      <StreakStrip
        earn={state}
        busy={busy}
        onClaim={() => daily.mutate()}
        readyIn={relative(state.dailyReadyAt)}
      />

      {/* Only where the build can actually show one. An older development build has no
          AdMob native module in it, and offering a button that reports "unavailable" is
          worse than not offering it. */}
      {adsAvailable() && (
        <ClaimRow
          icon={Clapperboard}
          title="Watch a short video"
          detail={
            state.adsLeftToday > 0
              ? `${state.adsLeftToday} left today`
              : 'That is today’s videos — back tomorrow'
          }
          reward={REWARDED_AD_COINS}
          ready={state.adsLeftToday > 0}
          busy={busy}
          onPress={() => void watchAd()}
        />
      )}

      {state.quests.map((q) => (
        <QuestRow key={q.code} quest={q} busy={busy} onPress={() => quest.mutate(q.code)} />
      ))}

      {/* Only shown to members. Advertising a reward that cannot be taken is a worse
          paywall than not mentioning it: it reads as broken rather than as an offer. */}
      {(state.stipendAvailable || state.stipendReadyAt) && (
        <ClaimRow
          icon={Crown}
          title="Gold monthly bonus"
          detail={state.stipendAvailable ? 'Yours this month' : `Back ${relative(state.stipendReadyAt)}`}
          reward={state.stipendAmount}
          ready={state.stipendAvailable}
          busy={busy}
          onPress={() => stipend.mutate()}
        />
      )}
    </View>
  );
}

function QuestRow({
  quest,
  busy,
  onPress,
}: {
  quest: Quest;
  busy: boolean;
  onPress: () => void;
}) {
  const done = quest.progress >= quest.target;

  return (
    <ClaimRow
      icon={quest.claimed ? Check : Target}
      title={quest.title}
      detail={quest.claimed ? 'Done this week' : `${quest.progress} of ${quest.target}`}
      reward={quest.reward}
      ready={done && !quest.claimed}
      busy={busy}
      dimmed={quest.claimed}
      progress={quest.claimed ? 1 : quest.progress / quest.target}
      onPress={onPress}
    />
  );
}

function ClaimRow({
  icon,
  title,
  detail,
  reward,
  ready,
  busy,
  dimmed = false,
  progress,
  onPress,
}: {
  icon: LucideIcon;
  title: string;
  detail: string;
  reward: number;
  ready: boolean;
  busy: boolean;
  dimmed?: boolean;
  progress?: number;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={!ready || busy}
      accessibilityRole="button"
      accessibilityLabel={ready ? `Claim ${reward} coins: ${title}` : `${title}, ${detail}`}
      accessibilityState={{ disabled: !ready || busy }}
      className={[
        'flex-row items-center gap-3 rounded-card border p-4',
        ready ? 'border-gold/40 bg-gold/5 active:opacity-70' : 'border-line bg-raised',
        dimmed ? 'opacity-50' : '',
      ].join(' ')}
    >
      {/* Gold when the reward is there to take, muted when it is not — the same signal the
          row's border and the "+N" already carry, so the glyph is not the only thing
          saying it. */}
      <View className="h-10 w-10 items-center justify-center rounded-full bg-surface">
        <Icon as={icon} size={18} tone={ready ? 'gold' : 'muted'} />
      </View>

      <View className="flex-1 gap-1">
        <Text variant="bodyStrong">{title}</Text>
        <Text variant="caption">{detail}</Text>

        {progress !== undefined && progress < 1 && (
          <View className="mt-1 h-1 overflow-hidden rounded-full bg-line">
            <View
              className="h-1 rounded-full bg-gold"
              style={{ width: `${Math.max(0, Math.min(1, progress)) * 100}%` }}
            />
          </View>
        )}
      </View>

      <View className="items-end">
        <Text className={['font-bold text-[15px] leading-[20px]', ready ? 'text-gold' : 'text-muted'].join(' ')}>
          +{reward}
        </Text>
        {ready && (
          <Text variant="caption" className="text-gold">
            Claim
          </Text>
        )}
      </View>
    </Pressable>
  );
}

/** "in 3h", "in 12m", or "soon" — enough to know whether to wait. */
function relative(iso: string | null): string {
  if (!iso) return 'soon';
  const seconds = Math.max(0, Math.floor((new Date(iso).getTime() - Date.now()) / 1000));
  if (seconds < 60) return 'in a moment';
  if (seconds < 3600) return `in ${Math.floor(seconds / 60)}m`;
  if (seconds < 86400) return `in ${Math.floor(seconds / 3600)}h`;
  return `in ${Math.floor(seconds / 86400)}d`;
}
