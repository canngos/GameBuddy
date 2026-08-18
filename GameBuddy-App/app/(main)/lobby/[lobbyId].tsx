import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { memo, useCallback, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, View } from 'react-native';
import { lobbyApi } from '../../../src/api/lobby';
import type { LobbyDetail, LobbyMember, LobbyMessage } from '../../../src/api/types';
import { useUpper } from '../../../src/i18n/case';
import { useT } from '../../../src/i18n/useT';
import { startsExact } from '../../../src/lobby/startsAt';
import { ToneBadge } from '../../../src/lobby/ToneChip';
import { useLobbyChat } from '../../../src/lobby/useLobbyChat';
import { useSession } from '../../../src/session/store';
import { useThemeColors } from '../../../src/theme';
import {
  Avatar,
  BackHeader,
  Button,
  Card,
  ErrorNotice,
  Screen,
  Text,
  TextField,
} from '../../../src/ui';
import { cn } from '../../../src/ui/cn';

/**
 * One lobby: header, roster, the owner's pending inbox, and the chat.
 *
 * Everything lives in the chat list's `ListHeaderComponent` so the screen is a single
 * scrolling surface — a ScrollView with a FlatList inside it is the layout RN warns
 * about, and the chat is the part that grows.
 *
 * Live updates arrive through `useLobbyChat`: chat lines append straight into the cache,
 * roster and lifecycle changes invalidate the detail query. Sends go over HTTP.
 */
export default function LobbyScreen() {
  const { lobbyId } = useLocalSearchParams<{ lobbyId: string }>();
  const router = useRouter();
  const colors = useThemeColors();
  const t = useT();
  const upper = useUpper();
  const queryClient = useQueryClient();
  const myId = useSession((s) => s.userId);
  const listRef = useRef<FlatList<LobbyMessage>>(null);

  const detail = useQuery({
    queryKey: ['lobby', lobbyId],
    queryFn: () => lobbyApi.get(lobbyId!),
    enabled: !!lobbyId,
  });

  const lobby = detail.data?.lobby;
  const isOwner = lobby?.myStatus === 'OWNER';
  const inTeam = lobby?.myStatus === 'OWNER' || lobby?.myStatus === 'ACCEPTED';
  const chatOpen = lobby?.status === 'OPEN' || lobby?.status === 'LOCKED';

  const messages = useQuery({
    queryKey: ['lobby-messages', lobbyId],
    queryFn: () => lobbyApi.messages(lobbyId!),
    // The backend answers LOBBY_NOT_MEMBER to anyone else; not asking beats asking to
    // be refused. ENDED/CANCELLED stay readable, so this keys on membership alone.
    enabled: !!lobbyId && inTeam,
  });

  useLobbyChat(inTeam ? lobbyId : undefined);

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['lobby', lobbyId] });
    void queryClient.invalidateQueries({ queryKey: ['my-lobbies'] });
    void queryClient.invalidateQueries({ queryKey: ['lobbies'] });
  };

  const join = useMutation({ mutationFn: () => lobbyApi.join(lobbyId!), onSuccess: invalidate });
  const leave = useMutation({ mutationFn: () => lobbyApi.leave(lobbyId!), onSuccess: invalidate });
  const lock = useMutation({ mutationFn: () => lobbyApi.lock(lobbyId!), onSuccess: invalidate });
  const unlock = useMutation({ mutationFn: () => lobbyApi.unlock(lobbyId!), onSuccess: invalidate });
  const end = useMutation({ mutationFn: () => lobbyApi.end(lobbyId!), onSuccess: invalidate });
  const cancel = useMutation({ mutationFn: () => lobbyApi.cancel(lobbyId!), onSuccess: invalidate });

  const answer = useMutation({
    mutationFn: ({ userId, accept }: { userId: string; accept: boolean }) =>
      accept ? lobbyApi.accept(lobbyId!, userId) : lobbyApi.reject(lobbyId!, userId),
    onSuccess: invalidate,
  });

  const kick = useMutation({
    mutationFn: (userId: string) => lobbyApi.kick(lobbyId!, userId),
    onSuccess: invalidate,
  });

  const send = useMutation({
    mutationFn: (text: string) => lobbyApi.send(lobbyId!, text),
    onSuccess: (line) => {
      // The screened text, as everyone else will see it. Appended by id so the socket
      // replaying it on a reconnect cannot double it.
      queryClient.setQueryData<LobbyMessage[]>(['lobby-messages', lobbyId], (current) => {
        if (!current) return [line];
        if (current.some((m) => m.id === line.id)) return current;
        return [...current, line];
      });
    },
  });

  /**
   * Takes the text rather than reading it from this component's state.
   *
   * The half-typed message lives in {@link Composer} now. It used to be state up here, and
   * the roster and the lobby header are in the list's `ListHeaderComponent` — so every
   * character typed rebuilt the whole header, every member row and every pending request.
   */
  const submit = useCallback(
    (text: string) => {
      if (!text || send.isPending) return;
      send.mutate(text);
    },
    [send],
  );

  const keyExtractor = useCallback((m: LobbyMessage) => m.id, []);
  const scrollToEnd = useCallback(
    () => listRef.current?.scrollToEnd({ animated: false }),
    [],
  );
  const renderLine = useCallback(
    ({ item }: { item: LobbyMessage }) => (
      <ChatLine line={item} mine={item.senderId === myId} />
    ),
    [myId],
  );

  if (detail.isPending) {
    return (
      <Screen edges={['top']}>
        <BackHeader title={t.lobby.detail.fallbackTitle} />
        <View className="flex-1 items-center justify-center">
          <ActivityIndicator color={colors.primary} />
        </View>
      </Screen>
    );
  }

  if (detail.error || !detail.data || !lobby) {
    return (
      <Screen edges={['top']}>
        <BackHeader title={t.lobby.detail.fallbackTitle} />
        <View className="pt-4">
          <ErrorNotice
            error={detail.error ?? new Error(t.lobby.detail.gone)}
            onRetry={() => detail.refetch()}
          />
        </View>
      </Screen>
    );
  }

  const firstError =
    join.error ?? leave.error ?? lock.error ?? unlock.error ?? end.error ?? cancel.error ??
    answer.error ?? kick.error ?? null;

  return (
    <Screen edges={['top']} padded={false}>
      <View className="px-6">
        <BackHeader title={lobby.title} subtitle={lobby.gameName ?? undefined} />
      </View>

      <FlatList
        ref={listRef}
        data={inTeam ? (messages.data ?? []) : []}
        keyExtractor={keyExtractor}
        contentContainerClassName="gap-2 px-6 pb-4"
        showsVerticalScrollIndicator={false}
        onContentSizeChange={scrollToEnd}
        renderItem={renderLine}
        ListHeaderComponent={
          <View className="gap-4 pb-4 pt-2">
            <LobbyHeader detail={detail.data} />

            {firstError && <ErrorNotice error={firstError} />}

            <ActionRow
              detail={detail.data}
              pending={{
                join: join.isPending,
                leave: leave.isPending,
                lock: lock.isPending,
                unlock: unlock.isPending,
                end: end.isPending,
                cancel: cancel.isPending,
              }}
              on={{
                join: () => join.mutate(),
                leave: () => {
                  leave.mutate();
                  // Leaving is also how a pending request is withdrawn; either way this
                  // screen is no longer somewhere the viewer belongs.
                  if (lobby.myStatus === 'ACCEPTED') router.back();
                },
                lock: () => lock.mutate(),
                unlock: () => unlock.mutate(),
                end: () => end.mutate(),
                cancel: () => cancel.mutate(),
              }}
            />

            {isOwner && detail.data.pendingRequests.length > 0 && lobby.status === 'OPEN' && (
              <View className="gap-2">
                <Text variant="overline">{upper(t.lobby.detail.wantsToJoin)}</Text>
                {detail.data.pendingRequests.map((request) => (
                  <MemberRow key={request.userId} member={request}>
                    <Button
                      label={t.lobby.detail.accept}
                      size="md"
                      loading={answer.isPending}
                      onPress={() => answer.mutate({ userId: request.userId, accept: true })}
                    />
                    <Button
                      label={t.lobby.detail.pass}
                      variant="ghost"
                      size="md"
                      loading={answer.isPending}
                      onPress={() => answer.mutate({ userId: request.userId, accept: false })}
                    />
                  </MemberRow>
                ))}
              </View>
            )}

            <View className="gap-2">
              <Text variant="overline">
                {upper(t.lobby.detail.team(lobby.playerCount, lobby.maxPlayers))}
              </Text>
              {detail.data.members.map((member) => (
                <MemberRow key={member.userId} member={member}>
                  {isOwner && member.status !== 'OWNER' && chatOpen && (
                    <Button
                      label={t.common.remove}
                      variant="ghost"
                      size="md"
                      loading={kick.isPending}
                      onPress={() => kick.mutate(member.userId)}
                    />
                  )}
                </MemberRow>
              ))}
            </View>

            {inTeam && (
              <Text variant="overline">
                {upper(chatOpen ? t.lobby.detail.chat : t.lobby.detail.chatReadOnly)}
              </Text>
            )}
            {inTeam && messages.error && (
              <ErrorNotice error={messages.error} onRetry={() => messages.refetch()} />
            )}
          </View>
        }
        ListEmptyComponent={
          inTeam && !messages.isPending && !messages.error ? (
            <View className="items-center gap-1 py-6">
              <Text variant="bodyStrong">{t.lobby.detail.emptyChatTitle}</Text>
              <Text variant="caption" className="text-center">
                {t.lobby.detail.emptyChatBlurb}
              </Text>
            </View>
          ) : null
        }
      />

      {!!send.error && (
        <View className="px-6 pb-2">
          <ErrorNotice error={send.error} />
        </View>
      )}

      {inTeam && chatOpen && <Composer sending={send.isPending} onSend={submit} />}
    </Screen>
  );
}

/**
 * The compose box, and the only thing that knows what is half-typed.
 *
 * Owning `draft` down here is the whole point: the roster, the lobby header and the
 * pending-request list all live in the message list's `ListHeaderComponent`, so while this
 * state was held by the screen above, typing one character re-rendered every one of them.
 *
 * Memoised, and both its props are stable — `sending` is a boolean and `onSend` is a
 * `useCallback` — so a new message arriving over the socket does not reset what somebody
 * is in the middle of writing.
 */
const Composer = memo(function Composer({
  sending,
  onSend,
}: {
  sending: boolean;
  onSend: (text: string) => void;
}) {
  const t = useT();
  const [draft, setDraft] = useState('');
  const empty = draft.trim().length === 0;

  const submit = useCallback(() => {
    const text = draft.trim();
    if (!text || sending) return;
    setDraft('');
    onSend(text);
  }, [draft, sending, onSend]);

  return (
    <View className="flex-row items-end gap-2 border-t border-line p-3">
      <View className="flex-1">
        <TextField
          value={draft}
          onChangeText={setDraft}
          placeholder={t.lobby.detail.composerPlaceholder}
          multiline
          maxLength={1000}
          onSubmitEditing={submit}
          returnKeyType="send"
        />
      </View>
      <Pressable
        onPress={submit}
        disabled={empty || sending}
        accessibilityRole="button"
        accessibilityLabel={t.common.send}
        className={cn(
          'h-touch w-touch items-center justify-center rounded-full bg-primary active:opacity-80',
          (empty || sending) && 'opacity-40',
        )}
      >
        <View className="h-3 w-3 rotate-45 border-r-2 border-t-2 border-white" />
      </Pressable>
    </View>
  );
});

function LobbyHeader({ detail }: { detail: LobbyDetail }) {
  const { lobby } = detail;
  const t = useT();
  const upper = useUpper();
  return (
    <Card className="gap-3">
      <View className="flex-row items-center gap-3">
        <Avatar
          source={lobby.gameIcon}
          name={lobby.gameName ?? '?'}
          colorSeed={lobby.gameId}
          size={44}
        />
        <View className="flex-1 gap-0.5">
          <Text variant="bodyStrong">{lobby.gameName ?? t.lobby.card.unknownGame}</Text>
          <Text variant="caption">
            {t.lobby.detail.playsBy(
              startsExact(lobby.startsAt, t),
              lobby.ownerUsername ?? t.lobby.card.unknownOwner,
            )}
          </Text>
        </View>
        <ToneBadge tone={lobby.tone} />
      </View>

      {!!lobby.description && <Text variant="body">{lobby.description}</Text>}
      {!!lobby.requirements && (
        <View className="gap-0.5">
          <Text variant="overline">{upper(t.lobby.detail.requirements)}</Text>
          <Text variant="body">{lobby.requirements}</Text>
        </View>
      )}

      {lobby.status !== 'OPEN' && (
        <View className="self-start rounded-full bg-raised px-2.5 py-0.5">
          <Text variant="label">
            {lobby.status === 'LOCKED' && t.lobby.detail.statusLocked}
            {lobby.status === 'ENDED' && t.lobby.detail.statusEnded}
            {lobby.status === 'CANCELLED' && t.lobby.detail.statusCancelled}
            {lobby.status === 'ARCHIVED' && t.lobby.detail.statusArchived}
          </Text>
        </View>
      )}
    </Card>
  );
}

type ActionHandlers = Record<'join' | 'leave' | 'lock' | 'unlock' | 'end' | 'cancel', () => void>;
type ActionPending = Record<keyof ActionHandlers, boolean>;

/**
 * What the viewer can do to this lobby, by role and state.
 *
 * The owner's row follows the lifecycle exactly: Lock and Cancel while OPEN; Unlock and
 * End while LOCKED. **Cancel is absent while LOCKED on purpose** — a formed team is one
 * deliberate unlock away from being called off, never one stray tap.
 */
function ActionRow({
  detail,
  pending,
  on,
}: {
  detail: LobbyDetail;
  pending: ActionPending;
  on: ActionHandlers;
}) {
  const { lobby } = detail;
  const status = lobby.status;
  const t = useT();

  if (lobby.myStatus === 'OWNER') {
    if (status === 'OPEN') {
      return (
        <View className="flex-row gap-2">
          <View className="flex-1">
            <Button label={t.lobby.detail.lockTeam} loading={pending.lock} onPress={on.lock} />
          </View>
          <View className="flex-1">
            <Button
              label={t.lobby.detail.cancelLobby}
              variant="danger"
              loading={pending.cancel}
              onPress={on.cancel}
            />
          </View>
        </View>
      );
    }
    if (status === 'LOCKED') {
      return (
        <View className="flex-row gap-2">
          <View className="flex-1">
            <Button
              label={t.lobby.detail.unlock}
              variant="secondary"
              loading={pending.unlock}
              onPress={on.unlock}
            />
          </View>
          <View className="flex-1">
            <Button label={t.lobby.detail.endLobby} loading={pending.end} onPress={on.end} />
          </View>
        </View>
      );
    }
    return null;
  }

  if (lobby.myStatus === 'ACCEPTED') {
    if (status === 'OPEN' || status === 'LOCKED') {
      return (
        <Button
          label={t.lobby.detail.leaveLobby}
          variant="ghost"
          loading={pending.leave}
          onPress={on.leave}
        />
      );
    }
    return null;
  }

  if (lobby.myStatus === 'PENDING') {
    return (
      <View className="gap-2">
        <Text variant="caption">{t.lobby.detail.requestSent}</Text>
        <Button
          label={t.lobby.detail.withdraw}
          variant="ghost"
          loading={pending.leave}
          onPress={on.leave}
        />
      </View>
    );
  }

  if (lobby.myStatus === 'REJECTED') {
    return <Text variant="caption">{t.lobby.detail.rejected}</Text>;
  }

  // A stranger, or someone who left/was removed — both may ask (again).
  if (status === 'OPEN' && lobby.playerCount < lobby.maxPlayers) {
    return <Button label={t.lobby.detail.askToJoin} loading={pending.join} onPress={on.join} />;
  }
  if (status === 'OPEN') {
    return <Text variant="caption">{t.lobby.detail.full}</Text>;
  }
  return null;
}

function MemberRow({ member, children }: { member: LobbyMember; children?: React.ReactNode }) {
  const t = useT();
  return (
    <Card className="flex-row items-center gap-3">
      <Avatar source={member.avatar} name={member.username ?? '?'} colorSeed={member.userId} size={36} />
      <View className="flex-1">
        <Text variant="bodyStrong" numberOfLines={1}>
          {member.username ?? t.lobby.detail.unknownGamer}
        </Text>
        {member.status === 'OWNER' && <Text variant="caption">{t.lobby.detail.owner}</Text>}
      </View>
      {children}
    </Card>
  );
}

const ChatLine = memo(function ChatLine({
  line,
  mine,
}: {
  line: LobbyMessage;
  mine: boolean;
}) {
  const t = useT();
  return (
    <View className={cn('max-w-[85%] gap-0.5', mine ? 'self-end' : 'self-start')}>
      {!mine && <Text variant="caption">{line.senderUsername ?? t.lobby.detail.unknownGamer}</Text>}
      <View className={cn('rounded-2xl px-3 py-2', mine ? 'bg-primary' : 'bg-raised')}>
        <Text variant="body" className={mine ? 'text-white' : 'text-content'}>
          {line.message}
        </Text>
      </View>
    </View>
  );
});
