import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Check,
  Clapperboard,
  Coins,
  Crown,
  Target,
  type LucideIcon,
} from 'lucide-react-native';
import { useEffect, useRef, useState } from 'react';
import { Pressable, View } from 'react-native';
import { REWARDED_AD_COINS, adsAvailable, showRewardedAd } from '../ads/rewarded';
import { earnApi } from '../api/coins';
import type { Earn, Mission } from '../api/types';
import { useUpper } from '../i18n/case';
import type { Dictionary } from '../i18n/dictionaries/en';
import { useT } from '../i18n/useT';
import { useSession } from '../session/store';
import { useThemeColors } from '../theme';
import { Burst, Icon, Text, feedback, messageOf, showToast } from '../ui';
import { StreakStrip } from './StreakStrip';

const EARN_KEY = ['earn'];

/**
 * How long to keep asking whether an advert's coins have landed.
 *
 * Six tries, roughly five seconds in total. The callback is a server-to-server request that
 * normally beats the second attempt; the tail is for a slow one. Stopping at five seconds
 * rather than waiting indefinitely keeps the button from being held hostage to Google's
 * latency — the coins still arrive, and the next refetch shows them.
 */
const CREDIT_POLL_DELAYS_MS = [300, 600, 900, 1200, 1000, 1000];

/**
 * Re-reads the earn state until the balance moves, or the attempts run out.
 *
 * Returns the last balance seen either way, so the caller can tell "paid" from "not yet"
 * and say something honest about which it was.
 */
async function pollForCredit(
  queryClient: ReturnType<typeof useQueryClient>,
  before: number | undefined,
): Promise<number | undefined> {
  let latest = before;

  for (const delay of CREDIT_POLL_DELAYS_MS) {
    await new Promise((resolve) => setTimeout(resolve, delay));
    // Refetch rather than invalidate: this must be the network answer, not whatever is
    // already cached, and it has to be awaited to be worth checking.
    await queryClient.refetchQueries({ queryKey: EARN_KEY });
    latest = queryClient.getQueryData<Earn>(EARN_KEY)?.coinBalance;
    if (before !== undefined && latest !== undefined && latest > before) return latest;
  }

  return latest;
}

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
  const t = useT();
  const upper = useUpper();
  const colors = useThemeColors();

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
      title: t.market.earn.earnedTitle(gained),
      body: t.market.earn.earnedBody,
      icon: Coins,
      tone: 'gold',
    });
  };

  const daily = useMutation({ mutationFn: earnApi.claimDaily, onSuccess: applyEarn });
  const mission = useMutation({ mutationFn: earnApi.claimMission, onSuccess: applyEarn });
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
        /*
         * Wait for the coins rather than asking once and giving up.
         *
         * The grant arrives out of band — Google calls `/ads/reward` from their servers, and
         * that call races the refetch below. A single immediate fetch usually loses that
         * race, which looks exactly like the reward being broken: the advert finishes, the
         * balance is unchanged, nothing is said. So this re-asks for a few seconds, and
         * stops the moment the balance moves.
         */
        const after = await pollForCredit(queryClient, before);
        void queryClient.invalidateQueries({ queryKey: ['cosmetics'] });
        void queryClient.invalidateQueries({ queryKey: ['me'] });

        if (before !== undefined && after !== undefined && after > before) {
          feedback.reward();
          showToast({
            id: `earned:${after}`,
            title: t.market.earn.earnedTitle(after - before),
            body: t.market.earn.watchedBody,
            icon: Coins,
            tone: 'gold',
          });
        } else {
          /*
           * Watched, but nothing had landed by the time we stopped asking.
           *
           * Silence was the old answer and it is the wrong one: to the gamer this is
           * indistinguishable from being cheated, and it is what testers reported. The
           * callback may still be in flight, or — if this build shipped without a live ad
           * unit — it was never sent at all. Either way, say that it is coming rather than
           * pretending nothing happened.
           */
          showToast({
            id: 'ads:pending',
            title: t.market.earn.rewardPendingTitle,
            body: t.market.earn.rewardPendingBody,
            icon: Clapperboard,
            tone: 'muted',
          });
        }
      } else if (outcome === 'consentRequired') {
        // Says why rather than doing nothing. Refusing consent is a legitimate choice and
        // the button stays where it is, so without this the row would just look broken —
        // and the way back is a screen away, which nobody would guess at.
        showToast({
          id: 'ads:consent',
          title: t.market.earn.consentTitle,
          body: t.market.earn.consentBody,
          icon: Clapperboard,
          tone: 'muted',
        });
      } else if (outcome === 'error' || outcome === 'unavailable') {
        // The advert never opened. Previously these two fell off the end of this chain and
        // the row simply stopped spinning, which is the report that brought us here: the
        // button did nothing and said nothing. It is not the gamer's fault and there is
        // nothing for them to fix, so the copy asks them to try again rather than
        // explaining an ad unit to them.
        showToast({
          id: 'ads:failed',
          title: t.market.earn.adFailedTitle,
          body: t.market.earn.adFailedBody,
          icon: Clapperboard,
          tone: 'muted',
        });
      }
    } finally {
      setWatching(false);
    }
  }

  /*
   * One flag disabled every row on the screen.
   *
   * Fine when the three missions were fixed and finishing two at once was a coincidence.
   * Not fine now: a set is dealt together and tends to complete together, so the common
   * case is three ready rows and a gamer tapping them in a row. Each row now only blocks
   * itself, and `busy` is kept for the ones that genuinely share the screen's state.
   */
  const claimingMission = mission.isPending ? mission.variables : undefined;
  const busy = daily.isPending || stipend.isPending || watching;
  const failure = daily.error ?? mission.error ?? stipend.error;
  const state = earn.data;

  /*
   * Finishing the third of three deals the next three in the same response, so the screen
   * changes under the gamer with nothing to mark it. The set number is what actually moved,
   * so that is what this watches — a burst on a claim would fire three times a set.
   */
  const [celebrate, setCelebrate] = useState(0);
  const lastSet = useRef<number | undefined>(undefined);
  useEffect(() => {
    const set = earn.data?.missionSet;
    if (set === undefined) return;
    if (lastSet.current !== undefined && set > lastSet.current) {
      setCelebrate((n) => n + 1);
    }
    lastSet.current = set;
  }, [earn.data?.missionSet]);

  if (!state) return null;

  return (
    <View className="gap-3 pb-6">
      <View className="gap-1">
        <Text variant="overline">{upper(t.market.earn.header)}</Text>
        <Text variant="caption">{t.market.earn.blurb}</Text>
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
        readyIn={relative(state.dailyReadyAt, t)}
      />

      {/* Only where the build can actually show one. An older development build has no
          AdMob native module in it, and offering a button that reports "unavailable" is
          worse than not offering it. */}
      {adsAvailable() && (
        <ClaimRow
          icon={Clapperboard}
          title={t.market.earn.watchVideo}
          detail={
            state.adsLeftToday > 0
              ? t.market.earn.videosLeft(state.adsLeftToday)
              : t.market.earn.videosDone
          }
          reward={(state?.adCoins ?? REWARDED_AD_COINS)}
          ready={state.adsLeftToday > 0}
          busy={busy}
          onPress={() => void watchAd()}
        />
      )}

      {/* The header exists because the campaign has an end, and a gamer who cannot see
          that is on a treadmill rather than a journey. It also has to say when the end has
          been passed: after the last set the pool repeats at the opening rate, and a screen
          that quietly started paying less would be the worse of the two options. */}
      <View className="flex-row items-baseline justify-between pt-1">
        <Text variant="overline">
          {state.missionVeteran
            ? upper(t.market.earn.missionSetVeteran(state.missionSet - state.missionSetsTotal))
            : upper(t.market.earn.missionSetOf(state.missionSet, state.missionSetsTotal))}
        </Text>
        <Text variant="caption" className="text-muted">
          {t.market.earn.missionBand[state.missionBand]}
        </Text>
      </View>

      <View>
        {state.missions.map((m) => (
          <MissionRow
            key={`${state.missionSet}:${m.slot}`}
            mission={m}
            busy={busy || claimingMission === m.code}
            onPress={() => mission.mutate(m.code)}
          />
        ))}

        {/* Over the three, not on one of them: what is being marked is the set turning
            over, and it is the whole block that was replaced. `pointerEvents` off so the
            particles never eat a tap on the new rows underneath.

            Mounted only once a set has actually turned over, because mounting counts as a
            play — left mounted it would fire every time somebody opened the Market. */}
        {celebrate > 0 && (
          <View className="absolute inset-0 items-center justify-center" pointerEvents="none">
            <Burst play={celebrate} color={colors.gold} radius={130} />
          </View>
        )}
      </View>

      {/* Only shown to members. Advertising a reward that cannot be taken is a worse
          paywall than not mentioning it: it reads as broken rather than as an offer. */}
      {(state.stipendAvailable || state.stipendReadyAt) && (
        <ClaimRow
          icon={Crown}
          title={t.market.earn.stipendTitle}
          detail={
            state.stipendAvailable
              ? t.market.earn.stipendReady
              : t.market.earn.stipendBack(relative(state.stipendReadyAt, t))
          }
          reward={state.stipendAmount}
          ready={state.stipendAvailable}
          busy={busy}
          onPress={() => stipend.mutate()}
        />
      )}
    </View>
  );
}

/**
 * One of the three missions on screen.
 *
 * Keyed on set and slot rather than on the code, so replacing a finished set remounts the
 * rows instead of animating a progress bar from the old mission's numbers to the new one's.
 */
function MissionRow({
  mission,
  busy,
  onPress,
}: {
  mission: Mission;
  busy: boolean;
  onPress: () => void;
}) {
  const t = useT();
  const done = mission.progress >= mission.target;

  // The backend sends the title in English — it is a string on a Java enum, one per
  // mission, with no notion of a locale — so a gamer reading the app in Turkish met English
  // rows among localised ones. Translated by `code`, which is on the payload and is the
  // stable name of the mission rather than prose that can be reworded.
  //
  // Falls back to what the server sent when this build has never heard of the code. A
  // mission added after it shipped then reads in English, which is worse than the
  // dictionary and much better than an empty row.
  const title =
    (t.market.earn.missionTitles as Record<string, string | undefined>)[mission.code] ??
    mission.title;

  return (
    <ClaimRow
      icon={mission.claimed ? Check : Target}
      title={title}
      detail={
        mission.claimed
          ? t.market.earn.missionDone
          : t.market.earn.missionProgress(mission.progress, mission.target)
      }
      reward={mission.reward}
      ready={done && !mission.claimed}
      busy={busy}
      dimmed={mission.claimed}
      progress={mission.claimed ? 1 : mission.progress / mission.target}
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
  const t = useT();
  return (
    <Pressable
      onPress={onPress}
      disabled={!ready || busy}
      accessibilityRole="button"
      accessibilityLabel={
        ready ? t.market.earn.claimA11y(reward, title) : t.market.earn.rowA11y(title, detail)
      }
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
            {t.market.earn.claim}
          </Text>
        )}
      </View>
    </Pressable>
  );
}

/**
 * "in 3h", "in 12m", or "soon" — enough to know whether to wait.
 *
 * **Rounded up, not down.** Flooring is the obvious choice and it is wrong for a countdown:
 * the instant somebody claims a daily worth 24 hours, the remainder is 23h 59m and change,
 * and `Math.floor` renders that as "in 23h". The number can therefore never once equal the
 * cooldown it is counting down, and a tester reasonably reported the reward window as an
 * hour shorter than it is. Ceiling says "in 24h" for that first minute and is honest
 * everywhere else too: a countdown answers "how long until I can", and being told an hour
 * that has not fully elapsed is the answer that does not send somebody back early.
 */
function relative(iso: string | null, t: Dictionary): string {
  if (!iso) return t.market.earn.soon;
  const seconds = Math.max(0, Math.floor((new Date(iso).getTime() - Date.now()) / 1000));
  if (seconds < 60) return t.market.earn.inAMoment;
  if (seconds < 3600) return t.market.earn.inMinutes(Math.round(seconds / 60));

  // The unit is chosen from the *rounded* hours, not from the raw seconds, and that is the
  // whole subtlety. Picking the unit first and rounding second means a 24-hour cooldown
  // lands either side of the day boundary depending on a fraction of a second of clock
  // skew: a hair under renders "24h", a hair over renders "2d", because ceiling 1.0001
  // days is 2. Rounding first makes both cases say the same thing.
  const hours = Math.round(seconds / 3600);
  if (hours <= 24) return t.market.earn.inHours(hours);
  return t.market.earn.inDays(Math.round(seconds / 86400));
}
