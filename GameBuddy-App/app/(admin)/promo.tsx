import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { memo, useCallback, useState } from 'react';
import { FlatList, Pressable, View } from 'react-native';
import { adminApi } from '../../src/api/admin';
import type { PromoCode } from '../../src/api/types';
import {
  Button,
  Card,
  ConfirmDialog,
  ErrorNotice,
  Screen,
  Text,
  type ConfirmRequest,
} from '../../src/ui';

/**
 * What is being given away, and to whom.
 *
 * Two ways to stop a code, next to each other and deliberately unequal. Disabling is one
 * tap with no confirmation because it is reversible and keeps every row; deleting asks,
 * because it removes the code, its recipient list and the record of who used it. The
 * asymmetry is the same one the Accounts tab uses for banning and restoring: the direction
 * you cannot undo is the direction that stops to ask.
 *
 * Neither takes anything back. Coins are in the ledger and Gold is on the account by the
 * time either button exists, which is why the confirmation says so rather than leaving an
 * administrator to wonder.
 */
export default function PromoCodesScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [confirm, setConfirm] = useState<ConfirmRequest | null>(null);

  const codes = useQuery({
    queryKey: ['admin', 'promo'],
    queryFn: () => adminApi.promoCodes(),
    staleTime: 30_000,
  });

  const refresh = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ['admin', 'promo'] });
  }, [queryClient]);

  const toggle = useMutation({
    mutationFn: ({ id, disabled }: { id: string; disabled: boolean }) =>
      disabled ? adminApi.enablePromoCode(id) : adminApi.disablePromoCode(id),
    onSuccess: refresh,
  });

  const remove = useMutation({
    mutationFn: (id: string) => adminApi.deletePromoCode(id),
    onSuccess: () => {
      setConfirm(null);
      refresh();
    },
  });

  const { mutate: toggleMutate } = toggle;
  const onToggle = useCallback(
    (code: PromoCode) => toggleMutate({ id: code.id, disabled: code.disabledAt !== null }),
    [toggleMutate],
  );

  const { mutate: removeMutate } = remove;
  const onDelete = useCallback(
    (code: PromoCode) =>
      setConfirm({
        title: `Delete ${code.code}?`,
        body:
          code.redemptionCount > 0
            ? `It disappears for everyone, along with the record of the ${code.redemptionCount} ` +
              'people who used it. What they were given stays on their accounts.'
            : 'It disappears for everyone. Nobody has used it yet.',
        confirmLabel: 'Delete',
        destructive: true,
        onConfirm: () => removeMutate(code.id),
      }),
    [removeMutate],
  );

  const onOpen = useCallback(
    (code: PromoCode) => router.push(`/promo-edit?id=${code.id}`),
    [router],
  );

  const rows = codes.data?.codes ?? [];

  const keyExtractor = useCallback((code: PromoCode) => code.id, []);
  const renderRow = useCallback(
    ({ item }: { item: PromoCode }) => (
      <PromoRow
        code={item}
        busy={toggle.isPending || remove.isPending}
        onOpen={onOpen}
        onToggle={onToggle}
        onDelete={onDelete}
      />
    ),
    [toggle.isPending, remove.isPending, onOpen, onToggle, onDelete],
  );

  return (
    <Screen edges={['top']}>
      <View className="pb-3 pt-2">
        <Text variant="overline">Billing</Text>
        <Text variant="title">Promotion codes</Text>
      </View>

      <View className="pb-4">
        <Button label="New code" onPress={() => router.push('/promo-edit')} />
      </View>

      {codes.error && <ErrorNotice error={codes.error} onRetry={() => void codes.refetch()} />}
      {toggle.error && <ErrorNotice error={toggle.error} />}
      {remove.error && <ErrorNotice error={remove.error} />}

      <FlatList
        data={rows}
        keyExtractor={keyExtractor}
        showsVerticalScrollIndicator={false}
        contentContainerClassName="pb-8"
        onRefresh={() => void codes.refetch()}
        refreshing={codes.isFetching}
        ListEmptyComponent={
          codes.isLoading ? null : (
            <Text variant="body" className="mt-8 text-center text-muted">
              No codes yet. Tap New code to make one.
            </Text>
          )
        }
        renderItem={renderRow}
      />

      {confirm && (
        <ConfirmDialog
          request={confirm}
          busy={remove.isPending}
          onCancel={() => setConfirm(null)}
        />
      )}
    </Screen>
  );
}

/**
 * A date nobody has to guess at.
 *
 * `toLocaleDateString()` with no arguments follows the device, which rendered 1 October as
 * "10/1/2026" — indistinguishable from 10 January to half the world, on the one screen
 * where the reader is deciding how long a giveaway runs. The email about the same code
 * spells the month out, and so does this.
 *
 * `en-GB` rather than the viewer's locale because the console is English-only by design.
 */
function day(iso: string) {
  return new Date(iso).toLocaleDateString('en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

/** What the code is worth, in the words the email uses. */
function reward(code: PromoCode) {
  if (code.kind === 'COIN') return `${code.coinAmount ?? 0} coins`;
  const days = code.goldDays ?? 0;
  return `${days} ${days === 1 ? 'day' : 'days'} of Gold`;
}

/** How much of it is left, and until when. */
function usage(code: PromoCode) {
  const used = code.maxRedemptions
    ? `Used ${code.redemptionCount} of ${code.maxRedemptions}`
    : `Used ${code.redemptionCount} times`;
  return `${used} · Expires ${day(code.expiresAt)}`;
}

/** Who it went to. Zero recipients is not "nobody" — it is a code anyone may type. */
function audience(code: PromoCode) {
  if (code.assigneeCount === 0) return 'Public code — anyone who knows it can use it';
  const people = `${code.assigneeCount} ${code.assigneeCount === 1 ? 'person' : 'people'}`;
  return `For ${people} · ${code.emailedCount} emailed`;
}

const STATUS_STYLE: Record<PromoCode['status'], string> = {
  ACTIVE: 'text-primary',
  EXPIRED: 'text-muted',
  EXHAUSTED: 'text-muted',
  DISABLED: 'text-danger',
};

/**
 * One code.
 *
 * Memoised and extracted rather than inline in `renderItem`, which is the rule the
 * Accounts and Avatars tabs both learned: JSX written inside `renderItem` is a new
 * component type on every render, so nothing can be memoised and every row rebuilds
 * whenever anything on the screen changes.
 */
const PromoRow = memo(function PromoRow({
  code,
  busy,
  onOpen,
  onToggle,
  onDelete,
}: {
  code: PromoCode;
  busy: boolean;
  onOpen: (code: PromoCode) => void;
  onToggle: (code: PromoCode) => void;
  onDelete: (code: PromoCode) => void;
}) {
  const open = useCallback(() => onOpen(code), [onOpen, code]);
  const toggle = useCallback(() => onToggle(code), [onToggle, code]);
  const remove = useCallback(() => onDelete(code), [onDelete, code]);
  const disabled = code.disabledAt !== null;

  return (
    <Card className="mb-3">
      <Pressable onPress={open} accessibilityRole="button" accessibilityLabel={`Edit ${code.code}`}>
        <View className="flex-row items-center justify-between">
          {/* Selectable because reading a code back out — into a newsletter, into a
              message — is most of what this screen is for. */}
          <Text variant="bodyStrong" selectable>
            {code.code}
          </Text>
          <Text variant="caption" className={STATUS_STYLE[code.status]}>
            {code.status.toLowerCase()}
          </Text>
        </View>

        <Text variant="body" className="mt-1">
          {reward(code)}
        </Text>
        <Text variant="caption" className="mt-1">
          {usage(code)}
        </Text>
        <Text variant="caption" className="mt-1">
          {audience(code)}
        </Text>
        {code.note && (
          <Text variant="caption" className="mt-1 text-muted">
            {code.note}
          </Text>
        )}
      </Pressable>

      <View className="mt-4 flex-row gap-3">
        <View className="flex-1">
          <Button
            label={disabled ? 'Enable' : 'Disable'}
            variant="secondary"
            onPress={toggle}
            disabled={busy}
          />
        </View>
        <View className="flex-1">
          <Button label="Delete" variant="danger" onPress={remove} disabled={busy} />
        </View>
      </View>
    </Card>
  );
});
