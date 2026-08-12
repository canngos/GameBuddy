import { useQuery } from '@tanstack/react-query';
import { View } from 'react-native';
import { adminApi } from '../../src/api/admin';
import { GrowthChart } from '../../src/admin/GrowthChart';
import { Card, ErrorNotice, Screen, Text } from '../../src/ui';

/**
 * The overview: how the product is doing, on one screen.
 *
 * Every number comes from a single request. Eight separate calls would each land at a
 * slightly different moment, so "accounts" and "active today" would disagree about when
 * today was — a dashboard that contradicts itself is worse than one that refreshes a
 * little less often.
 */
export default function ConsoleScreen() {
  const analytics = useQuery({
    queryKey: ['admin', 'analytics'],
    queryFn: () => adminApi.analytics(),
    // The console is a screen somebody leaves open. A minute is short enough that the
    // numbers are current when they look back, long enough that it is not polling.
    staleTime: 60_000,
    refetchInterval: 60_000,
  });

  const data = analytics.data;

  return (
    <Screen scroll edges={['top']}>
      {/* Sign out used to live here, as a muted word in the corner. It is on the
          Settings tab now — reachable from every screen instead of one, and next to the
          other things an account can do. */}
      <View className="pt-2">
        <Text variant="overline">GameBuddy</Text>
        <Text variant="title">Console</Text>
      </View>

      {analytics.error && (
        <View className="mt-4">
          <ErrorNotice error={analytics.error} onRetry={() => void analytics.refetch()} />
        </View>
      )}

      {!data && analytics.isLoading && (
        <Text variant="body" className="mt-6 text-muted">
          Loading…
        </Text>
      )}

      {data && (
        <>
          {/* Waiting work first. It is the only part of this screen that is a to-do
              list, and burying it under vanity numbers is how a queue grows. */}
          {(data.openReports > 0 || data.avatarsPending > 0) && (
            <Card className="mt-4">
              <Text variant="overline">Needs you</Text>
              <View className="mt-3 flex-row">
                <Stat label="Open reports" value={data.openReports} emphasis />
                <Stat label="Avatars to review" value={data.avatarsPending} emphasis />
              </View>

              {/* The terms commit to answering a report within 24 hours. This is the
                  number that says whether that is true right now — on the first screen
                  of the console, because a commitment you have to go looking for is one
                  you find out about from a reviewer. */}
              {data.openReports > 0 && (
                <View
                  className={
                    data.oldestOpenReportHours >= 24
                      ? 'mt-4 rounded-input bg-danger/15 p-3'
                      : 'mt-4 rounded-input bg-canvas p-3'
                  }
                >
                  <Text
                    variant="caption"
                    className={data.oldestOpenReportHours >= 24 ? 'text-danger' : undefined}
                  >
                    {data.oldestOpenReportHours >= 24
                      ? `Oldest report has been open ${data.oldestOpenReportHours}h — past the 24-hour commitment.`
                      : `Oldest report has been open ${data.oldestOpenReportHours}h of the 24-hour commitment.`}
                  </Text>
                </View>
              )}
            </Card>
          )}

          <Card className="mt-4">
            <Text variant="overline">People</Text>
            <View className="mt-3 flex-row">
              <Stat label="Accounts" value={data.accounts} />
              <Stat label="Finished signup" value={data.registered} />
            </View>
            <View className="mt-4 flex-row">
              <Stat label="New today" value={data.newToday} />
              <Stat label="New this week" value={data.newWeek} />
            </View>
          </Card>

          <Card className="mt-4">
            <Text variant="overline">Growth · last 30 days</Text>
            <View className="mt-4">
              <GrowthChart points={data.growth} />
            </View>
          </Card>

          <Card className="mt-4">
            <Text variant="overline">Active</Text>
            <View className="mt-3 flex-row">
              <Stat label="Today" value={data.activeToday} />
              <Stat label="7 days" value={data.activeWeek} />
              <Stat label="30 days" value={data.activeMonth} />
            </View>
          </Card>

          <Card className="mt-4">
            <Text variant="overline">What the app produced</Text>
            <View className="mt-3 flex-row">
              <Stat label="Matches" value={data.mutualMatches} />
              <Stat label="Messages" value={data.messages} />
            </View>
          </Card>

          {/* Money, after the product numbers and before the account admin. The order is
              the argument: how many people the app served, then what that was worth. */}
          <Card className="mt-4">
            <Text variant="overline">Funnel · last 30 days</Text>

            {/* Each step and the step before it, side by side, so a drop-off is read
                across rather than worked out. Percentages are deliberately absent — see
                the Funnel type for why. */}
            <View className="mt-3 flex-row">
              <Stat label="Saw paywall" value={data.funnel.paywallViewers} />
              <Stat label="Tapped buy" value={data.funnel.checkoutStarters} />
            </View>
            <View className="mt-4 flex-row">
              <Stat label="Trials" value={data.funnel.trialsStarted} />
              <Stat label="Paid" value={data.funnel.paidStarted} />
              <Stat label="Renewals" value={data.funnel.renewals} />
            </View>

            <View className="mt-4">
              <Text variant="caption">
                {data.funnel.cohort30 > 0
                  ? `${data.funnel.cohort30Paid} of ${data.funnel.cohort30} accounts that reached 30 days had bought Gold by then.`
                  : 'Free-to-paid at 30 days needs accounts that are 30 days old. None yet.'}
              </Text>
            </View>
          </Card>

          <Card className="mt-4">
            <Text variant="overline">Coin economy · last 30 days</Text>
            <View className="mt-3 flex-row">
              <Stat label="Earned" value={data.funnel.coinsEarned} />
              <Stat label="Spent" value={data.funnel.coinsSpent} />
            </View>
            <Text variant="caption" className="mt-3">
              Faucets against sinks. Earning far ahead of spending means the sinks are too
              expensive or too dull to bother with; the reverse means coins are scarce
              enough to be worth buying.
            </Text>
          </Card>

          <Card className="mt-4">
            <Text variant="overline">Day-7 retention · like cap</Text>
            {data.retention.length === 0 ? (
              // Not an error and not zero: the question cannot be asked yet. Saying so
              // beats an empty card that reads as "retention is nothing".
              <Text variant="caption" className="mt-3">
                No cohort has reached seven days yet.
              </Text>
            ) : (
              <>
                <View className="mt-3 flex-row">
                  {data.retention.map((cohort) => (
                    <Stat
                      key={cohort.cohort}
                      label={`${cohort.cohort.toLowerCase()} · ${cohort.signups}`}
                      value={cohort.retained}
                    />
                  ))}
                </View>
                <Text variant="caption" className="mt-3">
                  Retained of signups, per arm. The arms are assigned at signup and never
                  move, so a difference here is the cap and not the crowd.
                </Text>
              </>
            )}
          </Card>

          <Card className="mt-4">
            <Text variant="overline">Accounts</Text>
            <View className="mt-3 flex-row">
              <Stat label="Subscribers" value={data.subscribers} />
              <Stat label="Banned" value={data.banned} />
              <Stat label="Deleted" value={data.deleted} />
            </View>
            <View className="mt-4">
              <Stat label="Under 18" value={data.minors} />
              <Text variant="caption" className="mt-1">
                Minors are matched only with other minors. This number is what the
                age-rating and monetisation position has to be argued against.
              </Text>
            </View>
          </Card>
        </>
      )}
    </Screen>
  );
}

type StatProps = {
  label: string;
  value: number;
  /** Draws it in the brand colour. For the counts that mean "do something". */
  emphasis?: boolean;
};

function Stat({ label, value, emphasis }: StatProps) {
  return (
    <View className="flex-1">
      <Text variant="title" className={emphasis && value > 0 ? 'text-brand' : undefined}>
        {value.toLocaleString()}
      </Text>
      <Text variant="caption">{label}</Text>
    </View>
  );
}
