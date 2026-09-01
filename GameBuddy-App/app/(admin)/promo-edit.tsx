import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { View } from 'react-native';
import { adminApi } from '../../src/api/admin';
import { selectedPeople, usePromoDraft } from '../../src/admin/promoDraft';
import type {
  DirectoryUser,
  PromoCode,
  PromoCodeInput,
  PromoCodeKind,
} from '../../src/api/types';
import {
  Button,
  Card,
  Checkbox,
  ErrorNotice,
  LinkRow,
  RowGroup,
  Screen,
  SegmentRow,
  Segment,
  Text,
  TextField,
} from '../../src/ui';
import { EditScreen } from '../../src/ui/EditScreen';

/**
 * Making a code, and changing one.
 *
 * One screen for both, because the fields are the same fields and a second copy of this
 * form would drift from the first the week after it was written. What differs is small and
 * explicit: the code string can be chosen when creating and never afterwards — it may
 * already be sitting in somebody's inbox, and renaming it would turn a gift that was sent
 * into a code that does not exist.
 *
 * Validity is always "from now". Editing a code with three days left and leaving the field
 * at thirty gives it thirty days from today rather than adding thirty to what it had; the
 * caption says so, because the other reading is just as reasonable.
 */
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

/**
 * Waits for the code before drawing the form.
 *
 * The split exists so the form can take its starting values as props and set them up with
 * `useState` initialisers. The obvious alternative — one component that fetches and then
 * copies the row into eight pieces of state from an effect — is the shape that overwrites
 * something half-typed the moment a refetch lands, and React now warns about it by name.
 */
export default function PromoEditScreen() {
  const { id } = useLocalSearchParams<{ id?: string }>();
  const editing = typeof id === 'string' && id.length > 0;
  const resetDraft = usePromoDraft((state) => state.reset);

  const existing = useQuery({
    queryKey: ['admin', 'promo', id],
    queryFn: () => adminApi.promoCode(id as string),
    enabled: editing,
  });

  if (editing && !existing.data) {
    return (
      <Screen scroll edges={['top', 'bottom']}>
        <View className="pt-16">
          {existing.error ? (
            <ErrorNotice error={existing.error} onRetry={() => void existing.refetch()} />
          ) : (
            <Text variant="body" className="text-center text-muted">
              Loading…
            </Text>
          )}
        </View>
      </Screen>
    );
  }

  // `key` remounts the form when the target changes, which is what makes the initialisers
  // run again — an editor opened on one code and then on another must not keep the first
  // one's fields.
  return (
    <PromoForm
      key={existing.data?.id ?? 'new'}
      existing={existing.data ?? null}
      resetDraft={resetDraft}
    />
  );
}

function PromoForm({
  existing: loaded,
  resetDraft,
}: {
  existing: PromoCode | null;
  resetDraft: (people: DirectoryUser[], locked?: string[]) => void;
}) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const editing = loaded !== null;

  const [kind, setKind] = useState<PromoCodeKind>(loaded?.kind ?? 'COIN');
  const [amount, setAmount] = useState(loaded?.coinAmount ? String(loaded.coinAmount) : '500');
  const [goldDays, setGoldDays] = useState(loaded?.goldDays ? String(loaded.goldDays) : '30');
  const [code, setCode] = useState(loaded?.code ?? '');
  const [validDays, setValidDays] = useState('30');
  const [maxRedemptions, setMaxRedemptions] = useState(
    loaded?.maxRedemptions ? String(loaded.maxRedemptions) : '',
  );
  const [note, setNote] = useState(loaded?.note ?? '');
  const [sendEmail, setSendEmail] = useState(false);
  const [disabled, setDisabled] = useState(loaded?.disabledAt != null);
  const [created, setCreated] = useState<PromoCode | null>(null);

  const selected = usePromoDraft((state) => state.selected);
  const clearDraft = usePromoDraft((state) => state.clear);
  const recipients = useMemo(() => selectedPeople(selected), [selected]);
  const id = loaded?.id;

  // Seeds the recipient list from whatever the code already has — a store rather than
  // React state, so this is not the setState-in-effect the split above is about. It runs
  // once per mount, and the `key` on this component is what makes "once per code" true.
  useEffect(() => {
    const people = (loaded?.assignees ?? []).map((person) => ({
      userId: person.userId,
      username: person.username,
      email: person.email,
      avatar: null,
      createdDate: null,
      lastActiveAt: null,
      gold: false,
    }));
    resetDraft(
      people,
      (loaded?.assignees ?? []).filter((person) => person.redeemed).map((person) => person.userId),
    );
  }, [loaded, resetDraft]);

  const body: PromoCodeInput = {
    kind,
    coinAmount: kind === 'COIN' ? Number(amount) : null,
    goldDays: kind === 'GOLD' ? Number(goldDays) : null,
    ...(editing ? {} : { code: code.trim() || undefined }),
    validDays: Number(validDays),
    maxRedemptions: maxRedemptions.trim() ? Number(maxRedemptions) : null,
    assigneeIds: recipients.map((person) => person.userId),
    sendEmail: sendEmail && recipients.length > 0,
    disabled,
    note: note.trim() || null,
  };

  const save = useMutation({
    mutationFn: () =>
      editing ? adminApi.updatePromoCode(id as string, body) : adminApi.createPromoCode(body),
    onSuccess: (saved) => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'promo'] });
      setCreated(saved);
    },
  });

  const positive = (value: string) => /^\d+$/.test(value.trim()) && Number(value) > 0;
  const canSave =
    positive(validDays) &&
    (kind === 'COIN' ? positive(amount) : positive(goldDays)) &&
    (maxRedemptions.trim() === '' || positive(maxRedemptions)) &&
    (code.trim() === '' || /^[A-Za-z0-9-]{4,32}$/.test(code.trim()));

  /**
   * The done state, shown instead of the form.
   *
   * The code is the point of it: after creating one, the next thing anybody does is copy
   * it somewhere, and sending them back to a list to find the row again is a step for no
   * reason. It also carries the only honest place to report a failed send — the code
   * exists either way, and an administrator who is not told has no reason to pass it on
   * another way.
   */
  if (created) {
    const emailed = created.emailed;
    return (
      <Screen scroll edges={['top', 'bottom']}>
        <View className="gap-2 pb-6 pt-8">
          <Text variant="title">{editing ? 'Code saved' : 'Code created'}</Text>
        </View>

        <Card className="gap-2">
          <Text variant="overline">The code</Text>
          <Text variant="title" selectable>
            {created.code}
          </Text>
          <Text variant="caption">
            {created.kind === 'COIN'
              ? `${created.coinAmount} coins · expires ${day(created.expiresAt)}`
              : `${created.goldDays} days of Gold · expires ${day(created.expiresAt)}`}
          </Text>
        </Card>

        {emailed && (emailed.sent > 0 || emailed.failed > 0) && (
          <Card className="mt-4 gap-1">
            <Text variant="bodyStrong">
              {emailed.sent === 0
                ? 'No emails could be sent'
                : `Emailed to ${emailed.sent} ${emailed.sent === 1 ? 'person' : 'people'}`}
            </Text>
            {emailed.failed > 0 && (
              <Text variant="caption" className="text-danger">
                {emailed.failed} could not be delivered. The code still works — pass it on
                another way.
              </Text>
            )}
          </Card>
        )}

        <View className="mt-auto gap-3 pt-10">
          {!editing && (
            <Button
              label="Create another"
              variant="secondary"
              onPress={() => {
                setCreated(null);
                setCode('');
                setSendEmail(false);
                clearDraft();
                resetDraft([]);
              }}
            />
          )}
          <Button label="Done" onPress={() => router.back()} />
        </View>
      </Screen>
    );
  }

  return (
    <EditScreen
      title={editing ? `Edit ${code || 'code'}` : 'New promotion code'}
      subtitle={
        editing
          ? 'Everything but the code itself can change.'
          : 'Coins or Gold, for one person or for anybody who has the code.'
      }
      saveLabel={editing ? 'Save' : 'Create code'}
      onSave={() => {
        if (!canSave) return;
        save.mutate();
      }}
      saving={save.isPending}
      canSave={canSave}
      error={save.error}
    >
      <View className="gap-6">
        <View className="gap-2">
          <Text variant="overline">What it gives</Text>
          <SegmentRow>
            <Segment label="Coins" active={kind === 'COIN'} onPress={() => setKind('COIN')} />
            <Segment label="Gold" active={kind === 'GOLD'} onPress={() => setKind('GOLD')} />
          </SegmentRow>
        </View>

        {kind === 'COIN' ? (
          <TextField
            label="Coins"
            value={amount}
            onChangeText={setAmount}
            keyboardType="number-pad"
          />
        ) : (
          <TextField
            label="Days of Gold"
            value={goldDays}
            onChangeText={setGoldDays}
            keyboardType="number-pad"
            hint="Added to any membership the account already has."
          />
        )}

        <TextField
          label="Code"
          value={code}
          onChangeText={setCode}
          autoCapitalize="characters"
          editable={!editing}
          hint={
            editing
              ? 'A code cannot be renamed — it may already be in somebody’s inbox.'
              : 'Leave empty to generate one.'
          }
        />

        <TextField
          label="Valid for (days)"
          value={validDays}
          onChangeText={setValidDays}
          keyboardType="number-pad"
          hint="Counted from now, including when you edit."
        />

        <TextField
          label="Max redemptions"
          value={maxRedemptions}
          onChangeText={setMaxRedemptions}
          keyboardType="number-pad"
          hint="Leave empty for unlimited. Each account can use a code once either way."
        />

        <View className="gap-2">
          <Text variant="overline">Who it is for</Text>
          <RowGroup>
            <LinkRow
              label="Choose people"
              hint={
                recipients.length === 0
                  ? 'Nobody yet — the code will be public'
                  : `${recipients.length} selected`
              }
              onPress={() => router.push('/promo-users')}
              position="single"
            />
          </RowGroup>

          {recipients.length > 0 && (
            <Card className="gap-1">
              {recipients.slice(0, 6).map((person) => (
                <Text key={person.userId} variant="caption">
                  {person.username ?? person.userId}
                  {person.email ? ` · ${person.email}` : ''}
                </Text>
              ))}
              {recipients.length > 6 && (
                <Text variant="caption" className="text-muted">
                  and {recipients.length - 6} more
                </Text>
              )}
            </Card>
          )}

          {recipients.length > 0 && (
            <Checkbox
              checked={sendEmail}
              onChange={setSendEmail}
              accessibilityLabel="Email the code to anyone who has not been sent it"
            >
              <Text variant="body">Email the code to anyone who has not been sent it</Text>
            </Checkbox>
          )}
        </View>

        {editing && (
          <Checkbox
            checked={disabled}
            onChange={setDisabled}
            accessibilityLabel="Disabled, nobody can redeem it"
          >
            <Text variant="body">Disabled — nobody can redeem it</Text>
          </Checkbox>
        )}

        <TextField
          label="Note"
          value={note}
          onChangeText={setNote}
          hint="Only you see this."
        />
      </View>
    </EditScreen>
  );
}
