import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, View } from 'react-native';
import { badgesApi } from '../../src/api/badges';
import type { Badge, BadgeBoard, UserInfo } from '../../src/api/types';
import { useThemeColors } from '../../src/theme';
import { BackHeader, Button, Card, ErrorNotice, Screen, Text, messageOf } from '../../src/ui';

const BOARD_KEY = ['badges'];

/**
 * Badges: what there is to do, and what has been done.
 *
 * A grid of every mission rather than a list of the ones already earned. The locked
 * ones are the point — a screen that only shows what somebody has finished tells a new
 * gamer nothing about what the app wants them to try, which is the whole reason to have
 * missions rather than statistics.
 *
 * Tapping one opens it. That is where claiming and showcasing live, because both are
 * decisions about a single badge and neither belongs on a tile the size of a thumbnail.
 */
export default function Badges() {
  const colors = useThemeColors();
  const queryClient = useQueryClient();
  const [openCode, setOpenCode] = useState<string | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  const board = useQuery({ queryKey: BOARD_KEY, queryFn: badgesApi.board });

  /**
   * Reading the board can *earn* badges, so the profile behind it may now be wrong.
   *
   * The server awards on read: it re-evaluates every mission when this endpoint is hit,
   * so simply opening this screen is what turns "3 matches" into First Contact. Claiming
   * already invalidated the profile, but the far commoner path does not involve claiming
   * anything — you open the board, see the new badge, and go back to a Profile still
   * showing yesterday's count. It stayed wrong for the full five-minute stale window.
   *
   * Compared rather than invalidated unconditionally: the count is usually unchanged, and
   * a refetch on every visit to this screen would be a request that answers with what the
   * cache already holds.
   */
  const earned = board.data?.earned;
  useEffect(() => {
    if (earned === undefined) return;
    const cached = queryClient.getQueryData<UserInfo>(['me']);
    if (cached && cached.badgeCount !== earned) {
      void queryClient.invalidateQueries({ queryKey: ['me'] });
    }
  }, [earned, queryClient]);

  /**
   * Claiming and showcasing both answer with the whole board, so the response goes
   * straight into the cache instead of triggering a refetch — the same reasoning as the
   * market. A refetch would leave a window where the badge read as claimed but the coin
   * balance had not moved.
   *
   * The profile is invalidated rather than written: what changed there is the badge
   * count, the showcase and the balance, and it is not on screen to flicker.
   */
  const applyBoard = (next: BadgeBoard) => {
    setFailure(null);
    queryClient.setQueryData(BOARD_KEY, next);
    void queryClient.invalidateQueries({ queryKey: ['me'] });
  };

  const onError = (error: unknown) => setFailure(messageOf(error));

  const collect = useMutation({
    mutationFn: badgesApi.collect,
    onSuccess: applyBoard,
    onError,
  });
  const showcase = useMutation({
    mutationFn: badgesApi.showcase,
    onSuccess: applyBoard,
    onError,
  });

  const busy = collect.isPending || showcase.isPending;
  const badges = board.data?.badges ?? [];
  const shown = badges.filter((badge) => badge.showcased).map((badge) => badge.code);
  const slots = board.data?.showcaseSlots ?? 3;
  const open = badges.find((badge) => badge.code === openCode) ?? null;

  /**
   * Opening a badge clears the last complaint.
   *
   * The screen stays mounted behind the tab bar, so without this "your showcase is
   * full" sits at the top of the grid until the next successful action — long after it
   * has been dealt with, and above a screen where nothing is wrong.
   */
  const openBadge = (code: string) => {
    setFailure(null);
    setOpenCode(code);
  };

  /**
   * Toggles one badge in the showcase and sends the whole selection.
   *
   * The full-showcase case is caught here rather than silently dropping the oldest.
   * Deciding for somebody which of their three badges to take down is exactly the kind
   * of helpfulness that reads as a bug.
   */
  const toggleShowcase = (badge: Badge) => {
    if (badge.showcased) {
      showcase.mutate(shown.filter((code) => code !== badge.code));
      return;
    }
    if (shown.length >= slots) {
      setFailure(`You can show ${slots} badges. Take one off first.`);
      return;
    }
    showcase.mutate([...shown, badge.code]);
  };

  return (
    <Screen scroll edges={['top']}>
      <BackHeader
        title="Badges"
        subtitle="Finish missions, claim coins, show off three"
        right={
          board.data ? (
            <Text variant="label" className="text-muted">
              {board.data.earned}/{board.data.total}
            </Text>
          ) : undefined
        }
      />

      {board.isPending && <ActivityIndicator color={colors.primary} />}
      {board.error && <ErrorNotice error={board.error} onRetry={() => board.refetch()} />}

      {failure && (
        <Card className="mb-3">
          <Text variant="body" className="text-danger">
            {failure}
          </Text>
        </Card>
      )}

      {/* A three-column grid, laid out by width rather than by `gap` so the last row of
          a partial set still lines up with the ones above it. */}
      <View className="flex-row flex-wrap justify-between pb-6">
        {badges.map((badge) => (
          <Tile key={badge.code} badge={badge} onPress={() => openBadge(badge.code)} />
        ))}
        {/* Two spacers, so a row holding one or two tiles keeps them left-aligned
            instead of spreading them across the width. */}
        <View className="w-[31%]" />
        <View className="w-[31%]" />
      </View>

      <Detail
        badge={open}
        busy={busy}
        onClose={() => setOpenCode(null)}
        onCollect={() => open && collect.mutate(open.code)}
        onToggleShowcase={() => open && toggleShowcase(open)}
      />
    </Screen>
  );
}

/**
 * One badge in the grid.
 *
 * A locked badge is the same picture, dimmed — not a separate greyed-out asset. Half
 * the icons to download, and it means a badge cannot look like a different thing before
 * and after you earn it, which is what makes the wall worth staring at.
 */
function Tile({ badge, onPress }: { badge: Badge; onPress: () => void }) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${badge.title}. ${badge.earned ? 'Earned' : `${badge.progress} of ${badge.target}`}`}
      className="mb-5 w-[31%] items-center active:opacity-70"
    >
      <View className={badge.earned ? 'opacity-100' : 'opacity-35'}>
        <Image
          source={{ uri: badge.icon }}
          style={{ width: 82, height: 82 }}
          contentFit="contain"
          transition={150}
        />
      </View>

      <Text variant="caption" numberOfLines={2} className="pt-1 text-center text-content">
        {badge.title}
      </Text>

      {/* One line under every tile, and it says something different in each state:
          how far along, or that there are coins waiting. A tile that says nothing when
          a reward is unclaimed is how rewards go unclaimed. */}
      {badge.earned ? (
        <Text
          variant="caption"
          className={badge.collected ? 'text-center text-muted' : 'text-center text-gold'}
        >
          {badge.collected ? 'Earned' : `+${badge.reward}`}
        </Text>
      ) : (
        <Text variant="caption" className="text-center text-muted">
          {badge.progress}/{badge.target}
        </Text>
      )}

      {badge.showcased && <View className="mt-1 h-1 w-6 rounded-full bg-primary" />}
    </Pressable>
  );
}

/**
 * The sheet for one badge.
 *
 * A modal rather than a route: it is a detail *about* the grid behind it, and pushing a
 * screen for something with at most two buttons on it costs a transition each way for
 * no more information.
 */
function Detail({
  badge,
  busy,
  onClose,
  onCollect,
  onToggleShowcase,
}: {
  badge: Badge | null;
  busy: boolean;
  onClose: () => void;
  onCollect: () => void;
  onToggleShowcase: () => void;
}) {
  return (
    <Modal
      visible={!!badge}
      transparent
      animationType="fade"
      // Android's back button. Without this the sheet cannot be dismissed with it, and
      // the gesture people use to close a sheet is the one that leaves the app instead.
      onRequestClose={onClose}
    >
      {badge && (
        <Pressable className="flex-1 justify-end bg-black/60" onPress={onClose}>
          {/* Swallows taps so pressing the sheet itself does not close it. */}
          <Pressable
            className="items-center gap-3 rounded-t-[28px] bg-surface px-6 pb-10 pt-7"
            onPress={() => {}}
          >
            <View className={badge.earned ? 'opacity-100' : 'opacity-35'}>
              <Image
                source={{ uri: badge.icon }}
                style={{ width: 132, height: 132 }}
                contentFit="contain"
                transition={150}
              />
            </View>

            <Text variant="heading">{badge.title}</Text>
            <Text variant="body" className="text-center text-muted">
              {badge.description}
            </Text>

            {badge.earned ? (
              <Text variant="label" className="text-primary">
                Earned
              </Text>
            ) : (
              <Progress value={badge.progress} target={badge.target} />
            )}

            <View className="w-full gap-2 pt-2">
              {badge.earned && !badge.collected && (
                <Button
                  label={`Claim ${badge.reward} coins`}
                  onPress={onCollect}
                  loading={busy}
                />
              )}
              {badge.earned && (
                <Button
                  label={badge.showcased ? 'Remove from profile' : 'Show on profile'}
                  variant="secondary"
                  onPress={onToggleShowcase}
                  disabled={busy}
                />
              )}
              <Button label="Close" variant="ghost" onPress={onClose} />
            </View>
          </Pressable>
        </Pressable>
      )}
    </Modal>
  );
}

function Progress({ value, target }: { value: number; target: number }) {
  // The server caps progress at the target, so this cannot exceed 100 — but clamping
  // here as well costs nothing and keeps a bad response from drawing outside its track.
  const percent = Math.min(100, Math.round((value / Math.max(1, target)) * 100));

  return (
    <View className="w-full items-center gap-1.5 pt-1">
      <View className="h-2 w-full overflow-hidden rounded-full bg-raised">
        <View className="h-full rounded-full bg-primary" style={{ width: `${percent}%` }} />
      </View>
      <Text variant="caption">
        {value} of {target}
      </Text>
    </View>
  );
}
