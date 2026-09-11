import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useLocalSearchParams, useRouter } from "expo-router";
import { memo, useCallback, useEffect, useMemo, useState } from "react";
import { ActivityIndicator, FlatList, Pressable, View } from "react-native";
import { LOBBY_BOOST_COST_COINS, lobbyApi } from "../../../src/api/lobby";
import type {
  LobbyDetail,
  LobbyMember,
  LobbyMessage,
} from "../../../src/api/types";
import { useUpper } from "../../../src/i18n/case";
import { useT } from "../../../src/i18n/useT";
import { startsExact } from "../../../src/lobby/startsAt";
import { ToneBadge } from "../../../src/lobby/ToneChip";
import { LIVE_QUERY } from "../../../src/lobby/live";
import { useLobbyChat } from "../../../src/lobby/useLobbyChat";
import { useSession } from "../../../src/session/store";
import { useThemeColors } from "../../../src/theme";
import {
  Avatar,
  BackHeader,
  Button,
  Card,
  ErrorNotice,
  Screen,
  Text,
  TextField,
} from "../../../src/ui";
import { cn } from "../../../src/ui/cn";

/**
 * One lobby: header, roster, the owner's pending inbox, and the chat.
 *
 * Everything lives in the chat list's roster block (`LobbyAbove`) so the screen is a single
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
  const queryClient = useQueryClient();
  const myId = useSession((s) => s.userId);
  // A member's profile, where reporting and blocking live. The terms promise a report is
  // reachable from a lobby; this is how — the same profile screen the deck and chat open.
  const onOpenProfile = useCallback(
    (userId: string) => router.push({ pathname: '/messages/gamer/[userId]', params: { userId } }),
    [router],
  );

  const detail = useQuery({
    queryKey: ["lobby", lobbyId],
    queryFn: () => lobbyApi.get(lobbyId!),
    enabled: !!lobbyId,
    ...LIVE_QUERY,
  });

  const lobby = detail.data?.lobby;
  const isOwner = lobby?.myStatus === "OWNER";
  const inTeam = lobby?.myStatus === "OWNER" || lobby?.myStatus === "ACCEPTED";
  const chatOpen = lobby?.status === "OPEN" || lobby?.status === "LOCKED";

  const messages = useQuery({
    queryKey: ["lobby-messages", lobbyId],
    queryFn: () => lobbyApi.messages(lobbyId!),
    // The backend answers LOBBY_NOT_MEMBER to anyone else; not asking beats asking to
    // be refused. ENDED/CANCELLED stay readable, so this keys on membership alone.
    enabled: !!lobbyId && inTeam,
    ...LIVE_QUERY,
  });

  /*
   * Subscribed whether or not we are on the team yet, which is the whole point.
   *
   * This used to be `inTeam ? lobbyId : undefined`, so somebody whose request was still
   * pending registered no handler at all — and being accepted is precisely the event they
   * are waiting for. The backend does send it to them (`accept` saves the ACCEPTED row
   * before it fans out, so the new member is in the team by then); there was simply
   * nothing listening, and the screen went on offering "withdraw request" until the app
   * was restarted.
   *
   * Nothing leaks by subscribing early: the handler drops frames for other lobbies, and
   * the backend only sends MESSAGE frames to members, so a pending requester receives
   * lifecycle events and nothing else.
   */
  useLobbyChat(lobbyId);

  /*
   * Reading is what clears the badge, but only on the server.
   *
   * `GET /lobby/{id}/messages` moves this member's `lastReadAt` watermark, and the count
   * the tab draws comes from a different query — `my-lobbies`. Nothing connected the two,
   * so the purple bubble kept whatever number it was last told, and with the lobby list
   * cached it survived leaving the screen and coming back. Refetching the list once the
   * messages have landed is what makes "I have read these" visible where it is shown.
   */
  useEffect(() => {
    if (!messages.isSuccess) return;
    void queryClient.invalidateQueries({ queryKey: ["my-lobbies"] });
  }, [messages.isSuccess, messages.dataUpdatedAt, queryClient]);

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ["lobby", lobbyId] });
    void queryClient.invalidateQueries({ queryKey: ["my-lobbies"] });
    void queryClient.invalidateQueries({ queryKey: ["lobbies"] });
  };

  const join = useMutation({
    mutationFn: () => lobbyApi.join(lobbyId!),
    onSuccess: invalidate,
  });
  const leave = useMutation({
    mutationFn: () => lobbyApi.leave(lobbyId!),
    onSuccess: invalidate,
  });
  const lock = useMutation({
    mutationFn: () => lobbyApi.lock(lobbyId!),
    onSuccess: invalidate,
  });
  const unlock = useMutation({
    mutationFn: () => lobbyApi.unlock(lobbyId!),
    onSuccess: invalidate,
  });
  const end = useMutation({
    mutationFn: () => lobbyApi.end(lobbyId!),
    onSuccess: invalidate,
  });
  const cancel = useMutation({
    mutationFn: () => lobbyApi.cancel(lobbyId!),
    onSuccess: invalidate,
  });

  const boost = useMutation({
    mutationFn: () => lobbyApi.boost(lobbyId!),
    onSuccess: (fresh) => {
      // The response is the whole refreshed lobby, so the frame and the button both change
      // without a second request. The coin balance moved as well, which the Market header
      // and the profile both show.
      queryClient.setQueryData(["lobby", lobbyId], fresh);
      void queryClient.invalidateQueries({ queryKey: ["my-lobbies"] });
      void queryClient.invalidateQueries({ queryKey: ["lobbies"] });
      void queryClient.invalidateQueries({ queryKey: ["me"] });
      void queryClient.invalidateQueries({ queryKey: ["cosmetics"] });
    },
  });

  const answer = useMutation({
    mutationFn: ({ userId, accept }: { userId: string; accept: boolean }) =>
      accept
        ? lobbyApi.accept(lobbyId!, userId)
        : lobbyApi.reject(lobbyId!, userId),
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
      queryClient.setQueryData<LobbyMessage[]>(
        ["lobby-messages", lobbyId],
        (current) => {
          if (!current) return [line];
          if (current.some((m) => m.id === line.id)) return current;
          return [...current, line];
        },
      );
    },
  });

  /**
   * Takes the text rather than reading it from this component's state.
   *
   * The half-typed message lives in {@link Composer} now. It used to be state up here, and
   * the roster and the lobby header are in the list's roster block (`LobbyAbove`) — so every
   * character typed rebuilt the whole header, every member row and every pending request.
   */
  const { mutate: sendMutate, isPending: sendPending } = send;
  const submit = useCallback(
    (text: string) => {
      if (!text || sendPending) return;
      sendMutate(text);
    },
    // `mutate` is the stable part of the mutation object (which is a fresh literal every
    // render); isPending has to stay a dependency to keep the guard honest, so this
    // changes twice per send rather than on every socket frame.
    [sendMutate, sendPending],
  );

  const keyExtractor = useCallback((m: LobbyMessage) => m.id, []);
  // The list below is inverted, so it wants newest first - same shape as the DM thread.
  const newestFirst = useMemo(
    () => (messages.data ? [...messages.data].reverse() : []),
    [messages.data],
  );

  /*
   * Stable handlers for the roster block above the chat. `LobbyAbove` is memoised so a
   * socket frame that changes nothing (and every render this screen does while somebody
   * types) can bail out of reconciling the header, the action row and every member row -
   * which is only possible if none of these change identity per render. `mutate` is the
   * stable part of each mutation object.
   */
  const { mutate: joinMutate } = join;
  const { mutate: leaveMutate } = leave;
  const { mutate: lockMutate } = lock;
  const { mutate: unlockMutate } = unlock;
  const { mutate: endMutate } = end;
  const { mutate: cancelMutate } = cancel;
  const { mutate: boostMutate } = boost;
  const { mutate: answerMutate } = answer;
  const { mutate: kickMutate } = kick;
  const { refetch: refetchMessages } = messages;
  const myStatus = lobby?.myStatus;
  const onJoin = useCallback(() => joinMutate(), [joinMutate]);
  const onLeave = useCallback(() => {
    leaveMutate();
    // Leaving is also how a pending request is withdrawn; either way this screen is no
    // longer somewhere the viewer belongs.
    if (myStatus === "ACCEPTED") router.back();
  }, [leaveMutate, myStatus, router]);
  const onLock = useCallback(() => lockMutate(), [lockMutate]);
  const onUnlock = useCallback(() => unlockMutate(), [unlockMutate]);
  const onEnd = useCallback(() => endMutate(), [endMutate]);
  const onCancel = useCallback(() => cancelMutate(), [cancelMutate]);
  const onBoost = useCallback(() => boostMutate(), [boostMutate]);
  const onAnswer = useCallback(
    (userId: string, accept: boolean) => answerMutate({ userId, accept }),
    [answerMutate],
  );
  const onKick = useCallback((userId: string) => kickMutate(userId), [kickMutate]);
  const onRetryMessages = useCallback(() => {
    void refetchMessages();
  }, [refetchMessages]);

  const renderLine = useCallback(
    ({ item }: { item: LobbyMessage }) => (
      <ChatLine line={item} mine={item.senderId === myId} />
    ),
    [myId],
  );

  if (detail.isPending) {
    return (
      <Screen edges={["top"]}>
        <BackHeader title={t.lobby.detail.fallbackTitle} />
        <View className="flex-1 items-center justify-center">
          <ActivityIndicator color={colors.primary} />
        </View>
      </Screen>
    );
  }

  if (detail.error || !detail.data || !lobby) {
    return (
      <Screen edges={["top"]}>
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
    join.error ??
    leave.error ??
    lock.error ??
    unlock.error ??
    end.error ??
    cancel.error ??
    boost.error ??
    answer.error ??
    kick.error ??
    null;

  return (
    <Screen edges={["top"]} padded={false}>
      <View className="px-6">
        <BackHeader
          title={lobby.title}
          subtitle={lobby.gameName ?? undefined}
        />
      </View>

      <FlatList
        // Newest first, drawn bottom-up. The DM thread documents the whole story: the
        // old shape (oldest first + scrollToEnd on onContentSizeChange) opened long
        // histories part-way up and then chased a moving target as row heights arrived.
        // Inverting pins index 0 - the latest message - against the composer for free.
        // The roster block moves to ListFooterComponent, which an inverted list draws
        // at the visual top.
        inverted
        data={inTeam ? newestFirst : []}
        keyExtractor={keyExtractor}
        // Code-side paddingTop is the visual bottom once inverted - the gap against the
        // composer.
        contentContainerClassName="gap-2 px-6 pt-4"
        showsVerticalScrollIndicator={false}
        initialNumToRender={12}
        maxToRenderPerBatch={12}
        windowSize={7}
        renderItem={renderLine}
        ListFooterComponent={
          <LobbyAbove
            detail={detail.data}
            firstError={firstError}
            messagesError={inTeam ? (messages.error ?? null) : null}
            isOwner={isOwner}
            chatOpen={chatOpen}
            inTeam={inTeam}
            joinPending={join.isPending}
            leavePending={leave.isPending}
            lockPending={lock.isPending}
            unlockPending={unlock.isPending}
            endPending={end.isPending}
            cancelPending={cancel.isPending}
            boostPending={boost.isPending}
            answerPending={answer.isPending}
            kickPending={kick.isPending}
            onJoin={onJoin}
            onLeave={onLeave}
            onLock={onLock}
            onUnlock={onUnlock}
            onEnd={onEnd}
            onCancel={onCancel}
            onBoost={onBoost}
            onAnswer={onAnswer}
            onKick={onKick}
            onOpenProfile={onOpenProfile}
            myId={myId}
            onRetryMessages={onRetryMessages}
          />
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

      {inTeam && chatOpen && (
        <Composer sending={send.isPending} onSend={submit} />
      )}
    </Screen>
  );
}

/**
 * The compose box, and the only thing that knows what is half-typed.
 *
 * Owning `draft` down here is the whole point: the roster, the lobby header and the
 * pending-request list all live in the message list's roster block (`LobbyAbove`), so while this
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
  const [draft, setDraft] = useState("");
  const empty = draft.trim().length === 0;

  const submit = useCallback(() => {
    const text = draft.trim();
    if (!text || sending) return;
    setDraft("");
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
          "h-touch w-touch items-center justify-center rounded-full bg-primary active:opacity-80",
          (empty || sending) && "opacity-40",
        )}
      >
        <View className="h-3 w-3 rotate-45 border-r-2 border-t-2 border-white" />
      </Pressable>
    </View>
  );
});

/**
 * Everything drawn above the chat: the lobby card, the action row, pending requests and
 * the roster. It used to be a ~105-line element built inline in `ListHeaderComponent`,
 * reconstructed on every render of the screen - which arrives on every socket lobby
 * frame and every LIVE_QUERY refetch. Memoised with only stable or slow-moving props, so
 * a render that changes none of them reconciles nothing up here.
 *
 * Lives in `ListFooterComponent` because the chat list is inverted, and an inverted list
 * draws its footer at the visual top.
 */
const LobbyAbove = memo(function LobbyAbove({
  detail,
  firstError,
  messagesError,
  isOwner,
  chatOpen,
  inTeam,
  joinPending,
  leavePending,
  lockPending,
  unlockPending,
  endPending,
  cancelPending,
  boostPending,
  answerPending,
  kickPending,
  onJoin,
  onLeave,
  onLock,
  onUnlock,
  onEnd,
  onCancel,
  onBoost,
  onAnswer,
  onKick,
  onOpenProfile,
  myId,
  onRetryMessages,
}: {
  detail: LobbyDetail;
  firstError: Error | null;
  messagesError: Error | null;
  isOwner: boolean;
  chatOpen: boolean;
  inTeam: boolean;
  joinPending: boolean;
  leavePending: boolean;
  lockPending: boolean;
  unlockPending: boolean;
  endPending: boolean;
  cancelPending: boolean;
  boostPending: boolean;
  answerPending: boolean;
  kickPending: boolean;
  onJoin: () => void;
  onLeave: () => void;
  onLock: () => void;
  onUnlock: () => void;
  onEnd: () => void;
  onCancel: () => void;
  onBoost: () => void;
  onAnswer: (userId: string, accept: boolean) => void;
  onKick: (userId: string) => void;
  onOpenProfile: (userId: string) => void;
  myId: string | null;
  onRetryMessages: () => void;
}) {
  const t = useT();
  const upper = useUpper();
  const { lobby } = detail;

  return (
    <View className="gap-4 pb-4 pt-2">
      <LobbyHeader detail={detail} />

      {firstError && <ErrorNotice error={firstError} />}

      <ActionRow
        detail={detail}
        pending={{
          join: joinPending,
          leave: leavePending,
          lock: lockPending,
          unlock: unlockPending,
          end: endPending,
          cancel: cancelPending,
          boost: boostPending,
        }}
        on={{
          join: onJoin,
          leave: onLeave,
          lock: onLock,
          unlock: onUnlock,
          end: onEnd,
          cancel: onCancel,
          boost: onBoost,
        }}
      />

      {isOwner && detail.pendingRequests.length > 0 && lobby.status === "OPEN" && (
        <View className="gap-2">
          <Text variant="overline">{upper(t.lobby.detail.wantsToJoin)}</Text>
          {detail.pendingRequests.map((request) => (
            <MemberRow key={request.userId} member={request} onOpenProfile={onOpenProfile} myId={myId}>
              <Button
                label={t.lobby.detail.accept}
                size="md"
                loading={answerPending}
                onPress={() => onAnswer(request.userId, true)}
              />
              <Button
                label={t.lobby.detail.pass}
                variant="ghost"
                size="md"
                loading={answerPending}
                onPress={() => onAnswer(request.userId, false)}
              />
            </MemberRow>
          ))}
        </View>
      )}

      <View className="gap-2">
        <Text variant="overline">
          {upper(t.lobby.detail.team(lobby.playerCount, lobby.maxPlayers))}
        </Text>
        {detail.members.map((member) => (
          <MemberRow key={member.userId} member={member} onOpenProfile={onOpenProfile} myId={myId}>
            {isOwner && member.status !== "OWNER" && chatOpen && (
              <Button
                label={t.common.remove}
                variant="ghost"
                size="md"
                loading={kickPending}
                onPress={() => onKick(member.userId)}
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
      {inTeam && messagesError && (
        <ErrorNotice error={messagesError} onRetry={onRetryMessages} />
      )}
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
          name={lobby.gameName ?? "?"}
          colorSeed={lobby.gameId}
          size={44}
        />
        <View className="flex-1 gap-0.5">
          <Text variant="bodyStrong">
            {lobby.gameName ?? t.lobby.card.unknownGame}
          </Text>
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

      {lobby.status !== "OPEN" && (
        <View className="self-start rounded-full bg-raised px-2.5 py-0.5">
          <Text variant="label">
            {lobby.status === "LOCKED" && t.lobby.detail.statusLocked}
            {lobby.status === "ENDED" && t.lobby.detail.statusEnded}
            {lobby.status === "CANCELLED" && t.lobby.detail.statusCancelled}
            {lobby.status === "ARCHIVED" && t.lobby.detail.statusArchived}
          </Text>
        </View>
      )}
    </Card>
  );
}

type ActionHandlers = Record<
  "join" | "leave" | "lock" | "unlock" | "end" | "cancel" | "boost",
  () => void
>;
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

  if (lobby.myStatus === "OWNER") {
    if (status === "OPEN") {
      return (
        <View className="gap-2">
          <View className="flex-row gap-2">
            <View className="flex-1">
              <Button
                label={t.lobby.detail.lockTeam}
                loading={pending.lock}
                onPress={on.lock}
              />
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

          {/* Below the two lifecycle buttons, and only while there is something to promote.
              An already-boosted lobby says so instead of offering a second one, which the
              server refuses anyway. */}
          {/* Gold, not accent: accent is the like/match colour, and next to the red Cancel
              button it read as something having gone wrong rather than as a thing bought. */}
          {lobby.boosted ? (
            <Text variant="caption" className="text-gold">
              {t.lobby.boost.activeCaption}
            </Text>
          ) : (
            <View className="gap-1">
              <Button
                label={t.lobby.boost.action(LOBBY_BOOST_COST_COINS)}
                variant="secondary"
                loading={pending.boost}
                onPress={on.boost}
              />
              <Text variant="caption">{t.lobby.boost.caption}</Text>
            </View>
          )}
        </View>
      );
    }
    if (status === "LOCKED") {
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
            <Button
              label={t.lobby.detail.endLobby}
              loading={pending.end}
              onPress={on.end}
            />
          </View>
        </View>
      );
    }
    return null;
  }

  if (lobby.myStatus === "ACCEPTED") {
    if (status === "OPEN" || status === "LOCKED") {
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

  if (lobby.myStatus === "PENDING") {
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

  if (lobby.myStatus === "REJECTED") {
    return <Text variant="caption">{t.lobby.detail.rejected}</Text>;
  }

  // A stranger, or someone who left/was removed — both may ask (again).
  if (status === "OPEN" && lobby.playerCount < lobby.maxPlayers) {
    return (
      <Button
        label={t.lobby.detail.askToJoin}
        loading={pending.join}
        onPress={on.join}
      />
    );
  }
  if (status === "OPEN") {
    return <Text variant="caption">{t.lobby.detail.full}</Text>;
  }
  return null;
}

function MemberRow({
  member,
  onOpenProfile,
  myId,
  children,
}: {
  member: LobbyMember;
  onOpenProfile?: (userId: string) => void;
  myId?: string | null;
  children?: React.ReactNode;
}) {
  const t = useT();
  // Your own row is not a link to yourself; everyone else's opens their profile, which is
  // where reporting and blocking live.
  const canOpen = !!onOpenProfile && member.userId !== myId;
  return (
    <Card className="flex-row items-center gap-3">
      <Pressable
        className="flex-1 flex-row items-center gap-3"
        disabled={!canOpen}
        onPress={canOpen ? () => onOpenProfile?.(member.userId) : undefined}
        accessibilityRole={canOpen ? "button" : undefined}
      >
        <Avatar
          source={member.avatar}
          name={member.username ?? "?"}
          colorSeed={member.userId}
          size={36}
        />
        <View className="flex-1">
          <Text variant="bodyStrong" numberOfLines={1}>
            {member.username ?? t.lobby.detail.unknownGamer}
          </Text>
          {member.status === "OWNER" && (
            <Text variant="caption">{t.lobby.detail.owner}</Text>
          )}
        </View>
      </Pressable>
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
    <View
      className={cn("max-w-[85%] gap-0.5", mine ? "self-end" : "self-start")}
    >
      {!mine && (
        <Text variant="caption">
          {line.senderUsername ?? t.lobby.detail.unknownGamer}
        </Text>
      )}
      <View
        className={cn(
          "rounded-2xl px-3 py-2",
          mine ? "bg-primary" : "bg-raised",
        )}
      >
        <Text variant="body" className={mine ? "text-white" : "text-content"}>
          {line.message}
        </Text>
      </View>
    </View>
  );
});
