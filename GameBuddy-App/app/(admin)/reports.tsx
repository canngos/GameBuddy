import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { memo, useCallback, useMemo } from 'react';
import { Alert, FlatList, View } from 'react-native';
import { adminApi } from '../../src/api/admin';
import type { Report } from '../../src/api/types';
import { Button, Card, ErrorNotice, Screen, Text } from '../../src/ui';

/**
 * The moderation queue.
 *
 * Two verdicts, and they are not symmetrical. **Remove** deletes the content and closes
 * every open report against it; **Keep** closes this report alone and leaves the content
 * up. Removing is the destructive one, so it asks first — a mis-tap on a phone should not
 * be able to delete somebody's post.
 *
 * Banning is deliberately not here. It is on the Accounts tab, one deliberate step away,
 * because "this post is unacceptable" and "this person should lose their account" are
 * different judgements and a single row of buttons invites conflating them. The report
 * shows how many open reports stand against the author, which is the number that should
 * drive that second decision.
 */
export default function ReportsScreen() {
  const queryClient = useQueryClient();

  const reports = useQuery({
    queryKey: ['admin', 'reports'],
    queryFn: () => adminApi.reports(),
    staleTime: 30_000,
  });

  const settle = useMutation({
    mutationFn: ({ reportId, remove }: { reportId: string; remove: boolean }) =>
      remove ? adminApi.actionReport(reportId) : adminApi.dismissReport(reportId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'reports'] });
      // The overview counts open reports, so it is wrong the moment one closes.
      void queryClient.invalidateQueries({ queryKey: ['admin', 'analytics'] });
    },
  });

  // Stable, so the extracted row below can memoise. It was an inline arrow, which is also
  // why the row could not be a component at all.
  const confirmRemove = useCallback(
    (report: Report) =>
      Alert.alert(
        'Remove this content?',
        'It is deleted for everyone, and every open report against it is closed. This cannot be undone.',
        [
          { text: 'Cancel', style: 'cancel' },
          {
            text: 'Remove',
            style: 'destructive',
            onPress: () => settle.mutate({ reportId: report.reportId, remove: true }),
          },
        ],
      ),
    [settle],
  );

  const open = reports.data?.reports ?? [];

  const keyExtractor = useCallback((report: Report) => report.reportId, []);
  const onKeep = useCallback(
    (reportId: string) => settle.mutate({ reportId, remove: false }),
    [settle],
  );
  const renderRow = useCallback(
    ({ item }: { item: Report }) => (
      <ReportRow report={item} busy={settle.isPending} onKeep={onKeep} onRemove={confirmRemove} />
    ),
    [settle.isPending, onKeep, confirmRemove],
  );

  return (
    <Screen edges={['top']}>
      <View className="pb-3 pt-2">
        <Text variant="overline">Moderation</Text>
        <Text variant="title">Reports</Text>
      </View>

      {reports.error && <ErrorNotice error={reports.error} onRetry={() => void reports.refetch()} />}
      {settle.error && <ErrorNotice error={settle.error} />}

      <FlatList
        data={open}
        keyExtractor={keyExtractor}
        showsVerticalScrollIndicator={false}
        contentContainerClassName="pb-8"
        onRefresh={() => void reports.refetch()}
        refreshing={reports.isFetching}
        ListEmptyComponent={
          reports.isLoading ? null : (
            <Text variant="body" className="mt-8 text-center text-muted">
              Nothing waiting. Reported posts, comments and profiles land here.
            </Text>
          )
        }
        renderItem={renderRow}
      />
    </Screen>
  );
}

/** "3h ago". Precision beyond this does not change a moderator's decision. */
function when(iso: string) {
  const minutes = Math.floor((Date.now() - new Date(iso).getTime()) / 60_000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

/**
 * One report in the queue.
 *
 * Extracted so it can be memoised at all — it was inline JSX inside `renderItem`, which
 * left no component to wrap. `when()` builds a `Date` and reads the clock, so it was doing
 * that for every row on every render of the screen; it is memoised on the timestamp here,
 * which is the only thing that can change the answer.
 */
const ReportRow = memo(function ReportRow({
  report,
  busy,
  onKeep,
  onRemove,
}: {
  report: Report;
  busy: boolean;
  onKeep: (reportId: string) => void;
  onRemove: (report: Report) => void;
}) {
  const keep = useCallback(() => onKeep(report.reportId), [onKeep, report.reportId]);
  const remove = useCallback(() => onRemove(report), [onRemove, report]);
  const age = useMemo(() => when(report.createdAt), [report.createdAt]);

  return (
    <Card className="mb-3">
      <View className="flex-row items-center justify-between">
        <Text variant="overline">{report.contentType}</Text>
        <Text variant="caption" className={report.overdue ? 'text-danger' : undefined}>
          {age}
        </Text>
      </View>

      {/* The terms promise a decision within 24 hours. A promise whose state is
          invisible is one that gets broken quietly, so the queue says so — and it
          says so on the row, not in a summary somewhere else. */}
      {report.overdue && (
        <View className="mt-2 self-start rounded-full bg-danger/15 px-3 py-1">
          <Text variant="caption" className="text-danger">
            Past the 24-hour commitment — {report.ageHours}h open
          </Text>
        </View>
      )}

      <Text variant="bodyStrong" className="mt-2">
        {report.authorUsername ?? 'Unknown author'}
      </Text>

      {/* The reason is the reporter's words, so it is quoted rather than
          presented as a finding. */}
      <Text variant="caption" className="mt-1">
        Reported for: {report.reason}
      </Text>

      {report.content ? (
        <View className="mt-3 rounded-input bg-canvas p-3">
          <Text variant="body">{report.content}</Text>
        </View>
      ) : (
        // A profile report has no text by design — the complaint is about the
        // account itself, its username or its picture. Saying "already gone" here
        // told the moderator the content had been removed, which was untrue for
        // every profile report and would have made them dismiss real ones.
        <Text variant="caption" className="mt-3 italic">
          {report.contentType === 'PROFILE'
            ? 'A report about the account itself — its username, picture or bio.'
            : 'The content is already gone — removed, or the account was deleted.'}
        </Text>
      )}

      {report.authorOpenReportCount != null && report.authorOpenReportCount > 1 && (
        <Text variant="caption" className="mt-3 text-primary">
          {report.authorOpenReportCount} open reports against this account
        </Text>
      )}

      <View className="mt-4 flex-row gap-3">
        <View className="flex-1">
          <Button label="Keep" variant="secondary" onPress={keep} disabled={busy} />
        </View>
        <View className="flex-1">
          <Button label="Remove" variant="danger" onPress={remove} disabled={busy} />
        </View>
      </View>
    </Card>
  );
});
