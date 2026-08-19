import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Image } from "expo-image";
import { memo, useCallback, useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  View,
} from "react-native";
import { badgesApi } from "../../src/api/badges";
import type { Badge, BadgeBoard, UserInfo } from "../../src/api/types";
import { useT } from "../../src/i18n/useT";
import { useThemeColors } from "../../src/theme";
import {
  BackHeader,
  Button,
  Card,
  ErrorNotice,
  Screen,
  Text,
  messageOf,
  useScreenScale,
} from "../../src/ui";

const BOARD_KEY = ["badges"];

const grid = StyleSheet.create({
  // `flex-1` with a third-width cap: three columns that divide the row exactly, and a
  // partial last row that stays left-aligned instead of stretching.
  tile: { flex: 1, maxWidth: "33.33%" },
  column: { gap: 8 },
  icon: { width: 82, height: 82 },
});
const TILE = grid.tile;
const COLUMN = grid.column;
const ICON = grid.icon;

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
  const t = useT();
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
    const cached = queryClient.getQueryData<UserInfo>(["me"]);
    if (cached && cached.badgeCount !== earned) {
      void queryClient.invalidateQueries({ queryKey: ["me"] });
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
    void queryClient.invalidateQueries({ queryKey: ["me"] });
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
  const slots = board.data?.showcaseSlots ?? 3;

  // Derived from the whole catalogue, so both were a scan of it on every render — and
  // this screen stays mounted behind the tab bar.
  const shown = useMemo(
    () => badges.filter((badge) => badge.showcased).map((badge) => badge.code),
    [badges],
  );
  const open = useMemo(
    () => badges.find((badge) => badge.code === openCode) ?? null,
    [badges, openCode],
  );

  /**
   * Opening a badge clears the last complaint.
   *
   * The screen stays mounted behind the tab bar, so without this "your showcase is
   * full" sits at the top of the grid until the next successful action — long after it
   * has been dealt with, and above a screen where nothing is wrong.
   */
  const openBadge = useCallback((code: string) => {
    setFailure(null);
    setOpenCode(code);
  }, []);

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
      setFailure(t.market.badges.showcaseFull(slots));
      return;
    }
    showcase.mutate([...shown, badge.code]);
  };

  const keyExtractor = useCallback((badge: Badge) => badge.code, []);

  const renderTile = useCallback(
    ({ item }: { item: Badge }) => <Tile badge={item} onPress={openBadge} />,
    [openBadge],
  );

  return (
    <Screen edges={["top"]}>
      <BackHeader
        title={t.market.badges.title}
        subtitle={t.market.badges.subtitle}
        right={
          board.data ? (
            <Text variant="label" className="text-muted">
              {board.data.earned}/{board.data.total}
            </Text>
          ) : undefined
        }
      />

      {/* Three columns. The two spacer Views this used to need are gone: `numColumns`
          lays a partial last row out from the left on its own, which is what they were
          simulating. */}
      <FlatList
        className="flex-1"
        data={badges}
        numColumns={3}
        columnWrapperStyle={COLUMN}
        keyExtractor={keyExtractor}
        renderItem={renderTile}
        contentContainerClassName="pb-6"
        showsVerticalScrollIndicator={false}
        initialNumToRender={12}
        maxToRenderPerBatch={9}
        windowSize={7}
        ListHeaderComponent={
          <View className="gap-2">
            {board.isPending && <ActivityIndicator color={colors.primary} />}
            {!!board.error && (
              <ErrorNotice
                error={board.error}
                onRetry={() => board.refetch()}
              />
            )}
            {!!failure && (
              <Card className="mb-3">
                <Text variant="body" className="text-danger">
                  {failure}
                </Text>
              </Card>
            )}
          </View>
        }
      />

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
const Tile = memo(function Tile({
  badge,
  onPress,
}: {
  badge: Badge;
  onPress: (code: string) => void;
}) {
  const t = useT();
  const press = useCallback(() => onPress(badge.code), [onPress, badge.code]);

  return (
    <Pressable
      onPress={press}
      accessibilityRole="button"
      accessibilityLabel={t.market.badges.tileA11y(
        badge.title,
        badge.earned
          ? t.market.badges.earned
          : t.market.badges.progressOf(badge.progress, badge.target),
      )}
      style={TILE}
      className="mb-5 items-center active:opacity-70"
    >
      <View className={badge.earned ? "opacity-100" : "opacity-35"}>
        <Image
          source={{ uri: badge.icon }}
          style={ICON}
          contentFit="contain"
          transition={150}
          // A disk cache and a recycling key: the catalogue is fixed, so the second visit
          // to this screen should paint from disk, and a recycled cell must not show the
          // previous badge's art while the new one decodes.
          cachePolicy="memory-disk"
          recyclingKey={badge.code}
        />
      </View>

      <Text
        variant="caption"
        numberOfLines={2}
        className="pt-1 text-center text-content"
      >
        {badge.title}
      </Text>

      {/* One line under every tile, and it says something different in each state:
          how far along, or that there are coins waiting. A tile that says nothing when
          a reward is unclaimed is how rewards go unclaimed. */}
      {badge.earned ? (
        <Text
          variant="caption"
          className={
            badge.collected ? "text-center text-muted" : "text-center text-gold"
          }
        >
          {badge.collected ? t.market.badges.earned : `+${badge.reward}`}
        </Text>
      ) : (
        <Text variant="caption" className="text-center text-muted">
          {badge.progress}/{badge.target}
        </Text>
      )}

      {badge.showcased && (
        <View className="mt-1 h-1 w-6 rounded-full bg-primary" />
      )}
    </Pressable>
  );
});

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
  // The art is the sheet's largest single element, so it is the first thing to give.
  const badgeArt = useScreenScale().pick(96, 116, 132);
  const t = useT();
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
            /*
             * Capped and scrollable, which it was not.
             *
             * A 132dp icon, a heading, a description that runs to three or four lines and up
             * to three stacked buttons is around 480dp of sheet. On a 640dp phone that is
             * most of the screen, and on a small one with large text the buttons went off
             * the bottom with no way to reach them — the modal had no cap and nothing
             * scrolled, so the only way out was the Android back button.
             *
             * Same treatment as `CandidateSheet`: the sheet is capped, the description
             * scrolls, and the buttons are pinned below so the way out is always on screen.
             */
            className="max-h-[86%] overflow-hidden rounded-t-[28px] bg-surface"
            onPress={() => {}}
          >
            <ScrollView
              contentContainerClassName="items-center gap-3 px-6 pt-7"
              showsVerticalScrollIndicator={false}
            >
              <View className={badge.earned ? "opacity-100" : "opacity-35"}>
                <Image
                  source={{ uri: badge.icon }}
                  style={{ width: badgeArt, height: badgeArt }}
                  contentFit="contain"
                  transition={150}
                  cachePolicy="memory-disk"
                />
              </View>

              <Text variant="heading">{badge.title}</Text>
              <Text variant="body" className="text-center text-muted">
                {badge.description}
              </Text>

              {badge.earned ? (
                <Text variant="label" className="text-primary">
                  {t.market.badges.earned}
                </Text>
              ) : (
                <Progress value={badge.progress} target={badge.target} />
              )}
            </ScrollView>

            <View className="w-full gap-2 px-6 pb-10 pt-3">
              {badge.earned && !badge.collected && (
                <Button
                  label={t.market.badges.claimCoins(badge.reward)}
                  onPress={onCollect}
                  loading={busy}
                />
              )}
              {badge.earned && (
                <Button
                  label={
                    badge.showcased
                      ? t.market.badges.removeFromProfile
                      : t.market.badges.showOnProfile
                  }
                  variant="secondary"
                  onPress={onToggleShowcase}
                  disabled={busy}
                />
              )}
              <Button
                label={t.common.close}
                variant="ghost"
                onPress={onClose}
              />
            </View>
          </Pressable>
        </Pressable>
      )}
    </Modal>
  );
}

function Progress({ value, target }: { value: number; target: number }) {
  const t = useT();
  // The server caps progress at the target, so this cannot exceed 100 — but clamping
  // here as well costs nothing and keeps a bad response from drawing outside its track.
  const percent = Math.min(
    100,
    Math.round((value / Math.max(1, target)) * 100),
  );

  return (
    <View className="w-full items-center gap-1.5 pt-1">
      <View className="h-2 w-full overflow-hidden rounded-full bg-raised">
        <View
          className="h-full rounded-full bg-primary"
          style={{ width: `${percent}%` }}
        />
      </View>
      <Text variant="caption">{t.market.badges.progressOf(value, target)}</Text>
    </View>
  );
}
