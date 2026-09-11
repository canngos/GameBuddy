import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, ScrollView, View } from 'react-native';
import { adminApi } from '../../src/api/admin';
import type {
  CaseDetail,
  CaseMessageContext,
  CaseReportItem,
  ModerationAction,
  ReportReasonCode,
} from '../../src/api/types';
import { Button, Card, ErrorNotice, Screen, Text, TextField } from '../../src/ui';

/**
 * One case, with the evidence, and the ladder.
 *
 * This is the audited screen: fetching it decrypts the reported messages for someone who
 * was not in the conversation, and the server logs that read against the moderator. So it
 * is a screen a moderator opens deliberately, not a row in a list.
 *
 * The decision is one step from the ladder. A warning and a photo removal change little; a
 * suspension is time-limited and lifts itself; a ban is permanent and is the only step that
 * demands a written note, because a removal nobody can explain later is one nobody should be
 * making. Whatever the moderator picks, the target is told which rule and how to appeal, and
 * the reporters hear the outcome — the server does that, not this screen.
 */

const LADDER: { action: ModerationAction; label: string; danger?: boolean }[] = [
  { action: 'DISMISS', label: 'Dismiss — nothing wrong' },
  { action: 'WARN', label: 'Warn' },
  { action: 'REMOVE_PHOTO', label: 'Remove photo' },
  { action: 'SUSPEND_24H', label: 'Suspend 24 hours' },
  { action: 'SUSPEND_7D', label: 'Suspend 7 days' },
  { action: 'BAN', label: 'Ban permanently', danger: true },
];

const REASONS: ReportReasonCode[] = [
  'HARASSMENT',
  'SEXUAL',
  'SPAM_SCAM',
  'UNDERAGE',
  'IMPERSONATION',
  'OTHER',
];

export default function CaseScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();

  const detail = useQuery({
    queryKey: ['admin', 'case', id],
    queryFn: () => adminApi.case(id),
    enabled: !!id,
  });

  // The reason the moderator resolves under (defaults to the most-reported once loaded).
  const [reason, setReason] = useState<ReportReasonCode | null>(null);
  const [note, setNote] = useState('');
  const [removePhoto, setRemovePhoto] = useState(false);

  const resolve = useMutation({
    mutationFn: (action: ModerationAction) =>
      adminApi.resolveCase(id, {
        action,
        reasonCode: reason ?? undefined,
        note: note.trim() || undefined,
        removePhoto,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'cases'] });
      void queryClient.invalidateQueries({ queryKey: ['admin', 'analytics'] });
      router.back();
    },
  });

  const c = detail.data?.caseDetail;

  const decide = (action: ModerationAction) => {
    if (action === 'BAN' && note.trim().length === 0) {
      Alert.alert('A ban needs a note', 'Write down why before banning — it is the one decision with no lighter step.');
      return;
    }
    if (action === 'BAN') {
      Alert.alert('Ban this account?', 'This is permanent. They are told the rule and can appeal.', [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Ban', style: 'destructive', onPress: () => resolve.mutate(action) },
      ]);
      return;
    }
    resolve.mutate(action);
  };

  return (
    <Screen edges={['top']}>
      <View className="flex-row items-center justify-between pb-2 pt-2">
        <View>
          <Text variant="overline">Case</Text>
          <Text variant="title">{c?.targetUsername ?? 'Account'}</Text>
        </View>
        <Button label="Back" variant="secondary" onPress={() => router.back()} />
      </View>

      {detail.error && <ErrorNotice error={detail.error} onRetry={() => void detail.refetch()} />}
      {resolve.error && <ErrorNotice error={resolve.error} />}

      {c && (
        <ScrollView showsVerticalScrollIndicator={false} contentContainerClassName="pb-10">
          <CaseTarget detail={c} />

          <Text variant="overline" className="mb-2 mt-4">
            Reports ({c.reports.length})
          </Text>
          {c.reports.map((r) => (
            <ReportCard key={r.reportId} report={r} />
          ))}

          {c.messageContext && c.messageContext.length > 0 && (
            <>
              <Text variant="overline" className="mb-2 mt-4">
                Conversation
              </Text>
              <Card className="mb-3 gap-2">
                {c.messageContext.map((m) => (
                  <ContextLine key={m.messageId} message={m} />
                ))}
              </Card>
            </>
          )}

          {c.history.length > 0 && (
            <>
              <Text variant="overline" className="mb-2 mt-4">
                History
              </Text>
              {c.history.map((h, i) => (
                <Text key={i} variant="caption" className="mb-1 text-muted">
                  {h.action}
                  {h.reasonCode ? ` · ${h.reasonCode}` : ''}
                  {h.note ? ` · "${h.note}"` : ''}
                </Text>
              ))}
            </>
          )}

          <Text variant="overline" className="mb-2 mt-5">
            Decision
          </Text>
          <Card className="gap-3">
            <View>
              <Text variant="caption" className="mb-1 text-muted">
                Rule (defaults to the most reported)
              </Text>
              <View className="flex-row flex-wrap gap-2">
                {REASONS.map((code) => (
                  <Button
                    key={code}
                    label={code}
                    variant={reason === code ? 'primary' : 'ghost'}
                    onPress={() => setReason(code)}
                  />
                ))}
              </View>
            </View>

            <TextField
              label="Note (required to ban)"
              value={note}
              onChangeText={setNote}
              placeholder="What was decided and why"
              maxLength={500}
              multiline
            />

            <Button
              label={removePhoto ? 'Photo will be removed ✓' : 'Also remove the photo'}
              variant={removePhoto ? 'primary' : 'secondary'}
              onPress={() => setRemovePhoto((v) => !v)}
            />

            <View className="mt-1 gap-2">
              {LADDER.map((step) => (
                <Button
                  key={step.action}
                  label={step.label}
                  variant={step.danger ? 'danger' : 'secondary'}
                  disabled={resolve.isPending}
                  onPress={() => decide(step.action)}
                />
              ))}
            </View>
          </Card>
        </ScrollView>
      )}
    </Screen>
  );
}

function CaseTarget({ detail }: { detail: CaseDetail }) {
  return (
    <Card className="gap-1">
      <Text variant="bodyStrong">{detail.targetUsername ?? 'Unknown account'}</Text>
      <Text variant="caption" className="text-muted">
        {detail.summary.distinctReporters} reporters · weight {detail.summary.weightedScore.toFixed(1)}
        {detail.summary.autoHidden ? ' · hidden pending review' : ''}
      </Text>
      {detail.targetAvatarStatus && (
        <Text variant="caption" className="text-muted">
          Avatar: {detail.targetAvatarStatus}
        </Text>
      )}
      {detail.targetSuspended && (
        <Text variant="caption" className="text-danger">
          Currently suspended{detail.targetSuspendedUntil ? ` until ${detail.targetSuspendedUntil}` : ''}
        </Text>
      )}
    </Card>
  );
}

function ReportCard({ report }: { report: CaseReportItem }) {
  return (
    <Card className="mb-2">
      <View className="flex-row items-center justify-between">
        <Text variant="overline">{report.reasonCode ?? report.contentType}</Text>
        <Text variant="caption" className="text-muted">
          weight {report.reporterWeight}
        </Text>
      </View>
      {report.note ? (
        <Text variant="body" className="mt-1">
          {report.note}
        </Text>
      ) : (
        <Text variant="caption" className="mt-1 italic text-muted">
          No note
        </Text>
      )}
    </Card>
  );
}

function ContextLine({ message }: { message: CaseMessageContext }) {
  return (
    <View className={message.reported ? 'rounded-input bg-danger/10 p-2' : undefined}>
      <Text variant="caption" className="text-muted">
        {message.senderUsername ?? message.senderId}
        {message.reported ? ' · reported' : ''}
      </Text>
      <Text variant="body">{message.message}</Text>
    </View>
  );
}
