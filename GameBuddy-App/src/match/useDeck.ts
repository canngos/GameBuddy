import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError, Code } from '../api/envelope';
import { matchApi } from '../api/match';
import type { Candidate } from '../api/types';
import { NO_FILTERS, type FeedFilters } from './filters';

/**
 * `super` is an accept that spends a Super Like.
 *
 * The same swipe to the server — `/match/accept` with one flag set — because it is the
 * same answer, told louder: the other gamer is notified straight away rather than finding
 * out if and when they swipe back.
 */
export type Decision = 'accept' | 'decline' | 'super';

/** Why the deck is refusing to take another decision. */
export type Block =
  | { kind: 'accept-limit'; message: string }
  | { kind: 'swipe-limit'; message: string }
  | { kind: 'subscription'; message: string }
  /** No Super Likes left. The way out is the Market, not waiting. */
  | { kind: 'no-super-likes'; message: string }
  /** A rewind was refused for want of coins. The Market is the way out. */
  | { kind: 'no-coins'; message: string }
  /** Nothing has been swiped yet, or the swipe became a match. Nothing to offer. */
  | { kind: 'nothing-to-rewind'; message: string }
  /** Anything else the deck could not do. Said once, in the same place as the rest. */
  | { kind: 'deck-error'; message: string };

/**
 * The swipe deck's state machine.
 *
 * The queue is held locally rather than re-read from the server after each swipe:
 * `/match/get/recommendations` is the slowest call in the app (it goes out to the
 * Python model) and it also *records an impression for every candidate it returns*.
 * Refetching after each decision would both stall the deck and pollute the training
 * data with impressions nobody actually saw.
 */
export function useDeck(filters: FeedFilters = NO_FILTERS) {
  const queryClient = useQueryClient();

  /** How far through the fetched page we are. */
  const [cursor, setCursor] = useState(0);
  const [block, setBlock] = useState<Block | null>(null);
  /** Set when an accept turned out to be mutual; drives the celebration. */
  const [matchedWith, setMatchedWith] = useState<Candidate | null>(null);

  const feed = useQuery({
    // Filters are part of the key, so each combination is its own cached page rather than
    // one page that changes meaning. Going back to a filter you had on returns the deck
    // you left, and — because a fetch is what records impressions — does not re-record
    // everyone in it.
    queryKey: ['recommendations', filters],
    queryFn: () => matchApi.recommendations(filters),
    // The page is a snapshot tied to the impressions already recorded for it. Silently
    // replacing it mid-session would skip candidates the gamer never saw.
    staleTime: Infinity,
    gcTime: 30 * 60 * 1000,
  });

  // The cursor is an index into a specific queue. Changing filters swaps the queue for a
  // different one, so keeping the old index would open the new deck partway through and
  // silently skip the first however-many people in it.
  useEffect(() => {
    setCursor(0);
  }, [filters.gameId, filters.country, filters.onlineNow, filters.platform]);

  const allowance = useQuery({
    queryKey: ['allowance'],
    queryFn: matchApi.allowance,
    staleTime: 60 * 1000,
  });

  // Memoised so it is a stable input to the callbacks below; the `?? []` fallback minted
  // a fresh array every render while the feed was still loading.
  const candidates = useMemo(() => feed.data ?? [], [feed.data]);
  const current = candidates[cursor] ?? null;
  const upcoming = candidates[cursor + 1] ?? null;
  // A failed fetch also leaves the queue empty, and "that's everyone for now" is a lie
  // about a request that never returned anybody in the first place.
  const exhausted = !feed.isPending && !feed.error && cursor >= candidates.length;

  const decide = useMutation({
    mutationFn: async ({ decision, candidate }: { decision: Decision; candidate: Candidate }) => {
      if (decision === 'decline') {
        await matchApi.decline(candidate.userId);
        return { matched: false, candidate };
      }
      const result = await matchApi.accept(candidate.userId, decision === 'super');
      return { matched: result.matched, candidate, decision };
    },

    onSuccess: ({ matched, candidate, decision }) => {
      if (matched) setMatchedWith(candidate);
      // Accepting spends budget and may open a conversation; both displays are now stale.
      void queryClient.invalidateQueries({ queryKey: ['allowance'] });
      if (matched) void queryClient.invalidateQueries({ queryKey: ['matches'] });
      // A Super Like is spent from the same balance the Market sells and the inventory
      // counts, so both of those are now one behind.
      if (decision === 'super') {
        void queryClient.invalidateQueries({ queryKey: ['cosmetics'] });
        void queryClient.invalidateQueries({ queryKey: ['me'] });
      }
    },

    onError: (error, variables) => {
      // The card was advanced optimistically. A refusal has to put back *the card this
      // refusal belongs to*: with two swipes in flight, a blind step back resurrects the
      // wrong candidate and silently consumes the one that actually failed. Falls back
      // to one step when the queue was replaced under the request — and on a rate limit
      // that is the *worst* card to lose, because it is the one they wanted.
      setCursor((c) => {
        const index = candidates.findIndex(
          (candidate) => candidate.userId === variables.candidate.userId,
        );
        return index >= 0 ? Math.min(c, index) : Math.max(0, c - 1);
      });

      if (error instanceof ApiError) {
        // The backend spends a Super Like from the consumable balance and refuses with
        // COIN_NOT_ENOUGH when there is none — the same code a purchase gets. It means
        // something different here, so it is caught before it reaches the generic error
        // strip: "not enough coins" would send somebody looking for coins they have.
        if (variables.decision === 'super' && error.is(Code.COIN_NOT_ENOUGH)) {
          setBlock({ kind: 'no-super-likes', message: error.message });
          return;
        }
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
      /*
       * Everything else lands in the same sheet rather than a banner on the deck.
       *
       * These used to render as an `ErrorNotice` stacked under the card, and two at once
       * — a refused rewind and a refused swipe — pushed the controls around and stayed
       * there. A deck is a full-screen, one-thing-at-a-time surface; an error on it is a
       * modal answer to something you just did, not a status line.
       */
      setBlock({
        kind: 'deck-error',
        message: error instanceof ApiError ? error.message : '',
      });
    },
  });

  /**
   * Records a decision and advances immediately.
   *
   * Optimistic on purpose: waiting for the round trip before the next card appears
   * makes a deck feel broken, and the swipe animation has already committed visually.
   */
  const { mutate: decideMutate } = decide;
  const submit = useCallback(
    (decision: Decision) => {
      const candidate = candidates[cursor];
      if (!candidate) return;
      setCursor((c) => c + 1);
      decideMutate({ decision, candidate });
    },
    // `mutate` rather than the mutation object, which is a fresh literal every render -
    // with [decide] here, every render of the deck rebuilt SwipeCard's Pan/Tap gesture
    // chain. Now it rebuilds only when a swipe actually moves the cursor.
    [candidates, cursor, decideMutate],
  );

  /**
   * Takes back the last swipe and puts that gamer back on top.
   *
   * The returned candidate is spliced into the cached page rather than triggering a
   * refetch, for the same reason the queue is held locally at all: a refetch records a
   * fresh impression for everyone it returns, so undoing one swipe would tell the model
   * about fifty views that never happened.
   *
   * `cursor` does not move. The candidate is inserted *at* the cursor, so the deck is
   * showing them the moment this resolves.
   */
  const rewind = useMutation({
    mutationFn: matchApi.rewind,

    /*
     * Refusals here are ordinary and expected — no swipe yet, not enough coins, or the
     * swipe turned into a match — so each gets the sheet with the one action that helps.
     * Coins are buyable; the other two are simply facts, and the sheet just says so.
     */
    onError: (error) => {
      if (error instanceof ApiError) {
        if (error.is(Code.COIN_NOT_ENOUGH)) {
          setBlock({ kind: 'no-coins', message: error.message });
          return;
        }
        if (error.is(Code.NOTHING_TO_REWIND) || error.is(Code.REWIND_MATCHED)) {
          setBlock({ kind: 'nothing-to-rewind', message: error.message });
          return;
        }
      }
      setBlock({ kind: 'deck-error', message: error instanceof ApiError ? error.message : '' });
    },
    onSuccess: (result) => {
      queryClient.setQueryData<Candidate[]>(['recommendations', filters], (page) => {
        const current = page ?? [];
        // Guard against a double-tap racing the response: the server refuses the second
        // one with 171, but the first could still land twice through a retry.
        if (current.some((c) => c.userId === result.gamer.userId)) return current;
        return [...current.slice(0, cursor), result.gamer, ...current.slice(cursor)];
      });

      // The swipe was un-made, so the budget went back up, and coins may have been spent.
      void queryClient.invalidateQueries({ queryKey: ['allowance'] });
      void queryClient.invalidateQueries({ queryKey: ['cosmetics'] });
      void queryClient.invalidateQueries({ queryKey: ['me'] });
    },
  });

  const dismissBlock = useCallback(() => setBlock(null), []);
  const dismissMatch = useCallback(() => setMatchedWith(null), []);

  /** Asks for a fresh page. This is the only path that records new impressions. */
  const reload = useCallback(async () => {
    setCursor(0);
    setBlock(null);
    await queryClient.invalidateQueries({ queryKey: ['recommendations'] });
  }, [queryClient]);

  // A filtered feed from a free account is refused outright, and it can happen without
  // anybody touching the controls: Gold expiring mid-session turns a working deck into a
  // 159. Separated from `error` because the answer is a paywall, not a Retry button —
  // retrying the same refused request forever is the one thing that cannot help.
  const filtersRefused =
    feed.error instanceof ApiError && feed.error.is(Code.SUBSCRIPTION_REQUIRED);

  return {
    current,
    upcoming,
    exhausted,
    remaining: Math.max(0, candidates.length - cursor),
    isLoading: feed.isPending,
    error: filtersRefused ? null : feed.error,
    filtersRefused,
    allowance: allowance.data ?? null,
    block,
    dismissBlock,
    matchedWith,
    // Stable, because `app/(main)/home.tsx` lists this in a `useEffect` dependency array.
    // As a fresh arrow it changed identity every render, so while a match was on screen
    // the effect re-ran on every one of them. `celebrate` dedupes by user id so nothing
    // visible went wrong, which is exactly why it went unnoticed.
    dismissMatch,
    submit,
    reload,
    refetchFeed: feed.refetch,

    /** Only offered once something has actually been swiped this session. */
    canRewind: cursor > 0 && !rewind.isPending,
    rewind: rewind.mutate,
    rewinding: rewind.isPending,
  };
}
