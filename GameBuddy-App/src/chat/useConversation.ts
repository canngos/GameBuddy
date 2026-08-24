import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useFocusEffect } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { chatApi } from '../api/chat';
import type { Conversation, InboxEntry } from '../api/types';
import { useSession } from '../session/store';
import { useActiveConversation } from './activeConversation';
import { useChatSocketApi, useChatSocketStatus } from './ChatSocketProvider';
import { useChatPresence } from './presenceStore';

/** Smallest gap between two "I am typing" frames for the same conversation. */
const TYPING_THROTTLE_MS = 3000;

/**
 * One conversation: its history, its live socket, and sending.
 *
 * **Sending goes over HTTP; the socket only receives.** The backend writes a message to
 * the database before delivering it, so a recipient who is offline loses nothing — they
 * read it next time they open the conversation. The socket's only job is making that
 * instant for whoever happens to be connected. Sending over it as well would have tied
 * "can I say anything at all" to "is my socket up", which is the wrong dependency: a
 * dropped socket should slow chat down, not break it.
 *
 * History and live messages are kept apart and merged for display rather than appended
 * into one list, because the two overlap — a refetch returns messages the socket already
 * delivered, and concatenating would show them twice.
 */
export function useConversation(friendId: string) {
  const queryClient = useQueryClient();
  const myId = useSession((s) => s.userId);
  const { onMessage, sendTyping } = useChatSocketApi();
  const { status } = useChatSocketStatus();
  /*
   * This friend's slice only. Selecting `presence[friendId]` from the store instead of
   * consuming the whole pushed map is what stops a stranger going online elsewhere in
   * the app from re-rendering the one screen holding an unbounded message list.
   */
  const pushedPresence = useChatPresence((s) => s.presence[friendId]);
  const friendIsTyping = useChatPresence((s) => s.typing[friendId] === true);

  /** Messages that arrived over the socket since this screen opened. */
  const [live, setLive] = useState<Conversation[]>([]);

  const history = useQuery({
    queryKey: ['conversation', friendId],
    queryFn: () => chatApi.conversation(friendId),
    /*
     * Never served stale. A conversation is the one screen where a five minute old copy
     * is simply wrong — and the cached copy also meant re-opening a chat sent no request,
     * so the server-side read watermark (moved by loading the history) never advanced and
     * the unread badge stayed up. `markRead` below covers the watermark on its own now;
     * this covers the messages.
     */
    staleTime: 0,
    /*
     * The safety net for when the socket is not connected: without live delivery this
     * is the only way a reply appears, and it stops chat being dead in the water.
     *
     * **Only when the socket is down, though.** This was an unconditional ten seconds,
     * which meant that for the entire time a chat was open the app refetched the *whole
     * unpaginated history* six times a minute — parsing all of it, rebuilding the merged
     * list, re-rendering the thread — while the STOMP socket sat right there delivering
     * the very same messages live, into `setLive` below. It was the largest pure waste in
     * the app: all of that work, on a screen where nothing had changed, over mobile data.
     *
     * Thirty seconds rather than ten when it does run: this is a fallback for a socket
     * that is already trying to reconnect every four seconds, not a primary transport.
     */
    refetchInterval: status === 'connected' ? false : 30_000,
  });

  /**
   * Tells the server this thread has been read, and clears its badge here immediately.
   *
   * The optimistic edit is what makes leaving a chat feel right: the messages screen keeps
   * the inbox cached, so without zeroing the row the badge is still showing the old number
   * when the list comes back, for as long as it takes the refetch to land.
   */
  const markRead = useCallback(() => {
    void chatApi.markRead(friendId).catch(() => {
      // A failed mark-read is not worth surfacing — the next open re-sends it, and the
      // history load moves the watermark anyway. Swallowing it keeps an offline blip from
      // putting an error on a screen whose messages loaded fine.
    });
    queryClient.setQueryData<InboxEntry[]>(['inbox'], (current) =>
      current?.map((row) => (row.userId === friendId ? { ...row, unreadCount: 0 } : row)),
    );
  }, [friendId, queryClient]);

  /**
   * While this conversation is on screen it is read as it arrives, and it owns the toast.
   *
   * Focus rather than mount: the chat stays mounted underneath a profile pushed on top of
   * it, and the tab navigator freezes rather than unmounts a blurred tab. Registering on
   * mount would suppress notifications for a chat nobody is looking at any more.
   */
  useFocusEffect(
    useCallback(() => {
      useActiveConversation.getState().open(friendId);
      markRead();
      return () => useActiveConversation.getState().close(friendId);
    }, [friendId, markRead]),
  );

  useEffect(
    () =>
      // The socket belongs to the session, not to this screen — see ChatSocketProvider.
      // All this registers is an interest in messages while the conversation is mounted.
      onMessage((notification) => {
        // One subscription serves every conversation, so a message for someone else can
        // land here. Take only the ones from the person on screen; the rest just mean
        // the inbox moved.
        if (notification.senderId !== friendId) {
          void queryClient.invalidateQueries({ queryKey: ['inbox'] });
          return;
        }
        setLive((current) => [
          ...current,
          {
            id: notification.id,
            sender: notification.senderId,
            receiver: myId ?? '',
            message: notification.message,
            date: new Date().toISOString(),
          },
        ]);
        // Read the moment it is drawn. The watermark only ever moved when the history
        // loaded, and a thread receiving live never reloads — so a conversation somebody
        // sat and read in real time was still counted unread when they left it.
        markRead();
      }),
    [onMessage, friendId, myId, queryClient, markRead],
  );

  /**
   * Whatever arrived for the *previous* conversation is not part of this one.
   *
   * Without this, opening a second chat kept the live messages from the first and merged
   * them into a conversation they do not belong to — they are only absent today because
   * the screen used to be remounted along with its socket.
   */
  useEffect(() => setLive([]), [friendId]);

  const send = useMutation({
    mutationFn: (text: string) => chatApi.send(friendId, text.trim()),
    onSuccess: () => {
      // Refetch rather than guess: the server assigns the id and the timestamp, and the
      // history is the record. Appending a local copy would need reconciling later.
      void history.refetch();
      void queryClient.invalidateQueries({ queryKey: ['inbox'] });
    },
  });

  const messages = useMemo(() => {
    const seen = new Set((history.data ?? []).map((m) => m.id));
    return [...(history.data ?? []), ...live.filter((m) => !seen.has(m.id))];
  }, [history.data, live]);

  /**
   * The other person's state, asked for on open and again on every reconnect.
   *
   * A push only reports a *transition*. Somebody who has been online for an hour generates
   * no event, so without a fetch the header would say nothing until they happened to close
   * the app — and, worse, anything that changed while we were disconnected was announced to
   * a socket that was not listening. Re-asking on reconnect is what closes that window.
   *
   * It also has to survive a failed request. This was `retry: false`, and one cancelled
   * fetch — which a flaky network produces regularly — left the header permanently blank,
   * because nothing ever asked again. Now a reconnect re-asks, and a couple of retries
   * cover a failure that happens while the socket stays up.
   */
  const fetchedPresence = useQuery({
    queryKey: ['presence', friendId],
    queryFn: () => chatApi.presence(friendId),
    // Presence is state, not a fact: a cached answer from five minutes ago is worse than
    // no answer, so it is never served stale.
    staleTime: 0,
    gcTime: 0,
    retry: 2,
  });

  const refetchPresence = fetchedPresence.refetch;
  useEffect(() => {
    if (status === 'connected') void refetchPresence();
  }, [status, refetchPresence]);

  /**
   * The pushed value wins — but only until the socket drops.
   *
   * While disconnected the last push is a memory, not a fact, so on reconnect the fetch
   * above is authoritative again. Comparing timestamps would be better still; this is the
   * cheap version of the same idea, and the header refuses to claim anything at all when
   * our own connection is down (see StatusLabel).
   */
  const friendPresence = pushedPresence ?? fetchedPresence.data ?? null;

  /**
   * Reports that we are typing, at most once every few seconds.
   *
   * Throttled here rather than at the call site because the natural call site is
   * `onChangeText`, which fires per keystroke — a frame per character would be a burst of
   * socket traffic and a database check on the server for each one. The indicator lasts
   * several seconds anyway, so a keystroke that sends nothing changes nothing.
   */
  const lastTypingSent = useRef(0);
  const notifyTyping = useCallback(() => {
    const now = Date.now();
    if (now - lastTypingSent.current < TYPING_THROTTLE_MS) return;
    lastTypingSent.current = now;
    sendTyping(friendId);
  }, [sendTyping, friendId]);

  return {
    messages,
    myId,
    /** Live-delivery state only. Sending does not depend on it. */
    status,
    /** Null until the first answer arrives, which is not the same as "offline". */
    presence: friendPresence,
    isTyping: friendIsTyping,
    notifyTyping,
    isLoading: history.isPending,
    error: history.error,
    sending: send.isPending,
    sendError: send.error,
    send: (text: string) => send.mutate(text),
    resetSendError: () => send.reset(),
    refetch: history.refetch,
  };
}
