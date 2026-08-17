import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import type { LobbyMessage } from '../api/types';
import { useChatSocketApi, useChatSocketStatus } from '../chat/ChatSocketProvider';

/**
 * Feeds one lobby screen from the shared socket.
 *
 * The socket is receive-only, exactly like 1:1 chat: sends go over HTTP
 * (`lobbyApi.send`) and this hook makes their arrivals — everyone else's, and the
 * lifecycle — appear without polling.
 *
 * - `MESSAGE` frames append straight into the `['lobby-messages', id]` cache. No
 *   invalidation: the frame carries the line itself, and a refetch to learn something we
 *   are holding would be a round trip for nothing.
 * - `MEMBER` and `STATE` frames invalidate the lobby queries instead. They announce that
 *   something changed, not what — the roster and status come from the detail fetch, which
 *   also picks up anything else that changed with them.
 *
 * De-duplication matters on the MESSAGE path: with several lobby members' events fanned
 * out per-user, our own send comes back only via the HTTP response (the backend skips the
 * author), but a reconnect can replay recent frames. Appending by id keeps that harmless.
 */
export function useLobbyChat(lobbyId: string | undefined) {
  // Narrow hooks: a lobby cares about its own frames and the connection, never presence.
  const { onLobby } = useChatSocketApi();
  const { status } = useChatSocketStatus();
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!lobbyId) return;

    return onLobby((event) => {
      if (event.lobbyId !== lobbyId) return;

      if (event.type === 'MESSAGE' && event.message) {
        const line = event.message as LobbyMessage;
        queryClient.setQueryData<LobbyMessage[]>(['lobby-messages', lobbyId], (current) => {
          if (!current) return current;
          if (current.some((m) => m.id === line.id)) return current;
          return [...current, line];
        });
        return;
      }

      // MEMBER and STATE: refetch what changed. `my-lobbies` too — a kick or a cancel
      // changes what the tab's own list should show.
      void queryClient.invalidateQueries({ queryKey: ['lobby', lobbyId] });
      void queryClient.invalidateQueries({ queryKey: ['my-lobbies'] });
    });
  }, [lobbyId, onLobby, queryClient]);

  return { socketStatus: status };
}
