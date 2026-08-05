import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { chatApi } from '../api/chat';
import type { Conversation } from '../api/types';
import { useSession } from '../session/store';
import { createChatSocket, type SocketStatus } from './socket';

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
  const token = useSession((s) => s.token);
  const myId = useSession((s) => s.userId);

  const [status, setStatus] = useState<SocketStatus>('idle');
  /** Messages that arrived over the socket since this screen opened. */
  const [live, setLive] = useState<Conversation[]>([]);

  const history = useQuery({
    queryKey: ['conversation', friendId],
    queryFn: () => chatApi.conversation(friendId),
    // The safety net for when the socket is not connected: without live delivery this
    // is the only way a reply appears. Cheap, and it stops chat being dead in the water.
    refetchInterval: 10_000,
  });

  useEffect(() => {
    if (!token) return;

    const socket = createChatSocket(token, {
      onStatus: setStatus,
      onMessage: (notification) => {
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
      },
    });

    socket.connect();
    return () => socket.disconnect();
  }, [token, friendId, myId, queryClient]);

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

  return {
    messages,
    myId,
    /** Live-delivery state only. Sending does not depend on it. */
    status,
    isLoading: history.isPending,
    error: history.error,
    sending: send.isPending,
    sendError: send.error,
    send: (text: string) => send.mutate(text),
    resetSendError: () => send.reset(),
    refetch: history.refetch,
  };
}
