import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useState } from 'react';
import { ApiError, Code } from '../api/envelope';
import { matchApi } from '../api/match';
import type { Candidate } from '../api/types';

export type Decision = 'accept' | 'decline';

/** Why the deck is refusing to take another decision. */
export type Block =
  | { kind: 'accept-limit'; message: string }
  | { kind: 'swipe-limit'; message: string }
  | { kind: 'subscription'; message: string };

/**
 * The swipe deck's state machine.
 *
 * The queue is held locally rather than re-read from the server after each swipe:
 * `/match/get/recommendations` is the slowest call in the app (it goes out to the
 * Python model) and it also *records an impression for every candidate it returns*.
 * Refetching after each decision would both stall the deck and pollute the training
 * data with impressions nobody actually saw.
 */
export function useDeck() {
  const queryClient = useQueryClient();

  /** How far through the fetched page we are. */
  const [cursor, setCursor] = useState(0);
  const [block, setBlock] = useState<Block | null>(null);
  /** Set when an accept turned out to be mutual; drives the celebration. */
  const [matchedWith, setMatchedWith] = useState<Candidate | null>(null);

  const feed = useQuery({
    queryKey: ['recommendations'],
    queryFn: matchApi.recommendations,
    // The page is a snapshot tied to the impressions already recorded for it. Silently
    // replacing it mid-session would skip candidates the gamer never saw.
    staleTime: Infinity,
    gcTime: 30 * 60 * 1000,
  });

  const allowance = useQuery({
    queryKey: ['allowance'],
    queryFn: matchApi.allowance,
    staleTime: 60 * 1000,
  });

  const candidates = feed.data ?? [];
  const current = candidates[cursor] ?? null;
  const upcoming = candidates[cursor + 1] ?? null;
  const exhausted = !feed.isPending && cursor >= candidates.length;

  const decide = useMutation({
    mutationFn: async ({ decision, candidate }: { decision: Decision; candidate: Candidate }) => {
      if (decision === 'decline') {
        await matchApi.decline(candidate.userId);
        return { matched: false, candidate };
      }
      const result = await matchApi.accept(candidate.userId);
      return { matched: result.matched, candidate };
    },

    onSuccess: ({ matched, candidate }) => {
      if (matched) setMatchedWith(candidate);
      // Accepting spends budget and may open a conversation; both displays are now stale.
      void queryClient.invalidateQueries({ queryKey: ['allowance'] });
      if (matched) void queryClient.invalidateQueries({ queryKey: ['matches'] });
    },

    onError: (error, variables) => {
      // The card was advanced optimistically. A refusal has to put it back, or the
      // gamer silently loses the person they were looking at — and on a rate limit
      // that is the *worst* card to lose, because it is the one they wanted.
      setCursor((c) => Math.max(0, c - 1));

      if (error instanceof ApiError) {
        if (error.is(Code.ACCEPT_LIMIT_REACHED)) {
          setBlock({ kind: 'accept-limit', message: error.message });
          return;
        }
        if (error.is(Code.SWIPE_LIMIT_REACHED)) {
          setBlock({ kind: 'swipe-limit', message: error.message });
          return;
        }
        if (error.is(Code.SUBSCRIPTION_REQUIRED)) {
          setBlock({ kind: 'subscription', message: error.message });
          return;
        }
      }
      // Anything else — network, 500, rate limiting — is surfaced by `decide.error`
      // on the screen. The card is already back, so it can simply be swiped again.
    },
  });

  /**
   * Records a decision and advances immediately.
   *
   * Optimistic on purpose: waiting for the round trip before the next card appears
   * makes a deck feel broken, and the swipe animation has already committed visually.
   */
  const submit = useCallback(
    (decision: Decision) => {
      const candidate = candidates[cursor];
      if (!candidate) return;
      setCursor((c) => c + 1);
      decide.mutate({ decision, candidate });
    },
    [candidates, cursor, decide],
  );

  /** Asks for a fresh page. This is the only path that records new impressions. */
  const reload = useCallback(async () => {
    setCursor(0);
    setBlock(null);
    await queryClient.invalidateQueries({ queryKey: ['recommendations'] });
  }, [queryClient]);

  return {
    current,
    upcoming,
    exhausted,
    remaining: Math.max(0, candidates.length - cursor),
    isLoading: feed.isPending,
    error: feed.error,
    /** Only failures that were not turned into a `block`. */
    decisionError: block ? null : decide.error,
    allowance: allowance.data ?? null,
    block,
    dismissBlock: () => setBlock(null),
    matchedWith,
    dismissMatch: () => setMatchedWith(null),
    submit,
    reload,
    refetchFeed: feed.refetch,
  };
}
