import { useQuery } from "@tanstack/react-query";
import { Image } from "expo-image";
import { useRouter } from "expo-router";
import { Crown, Settings, Shirt } from "lucide-react-native";
import { ActivityIndicator, Pressable, View } from "react-native";
import { subscriptionQuery } from "../../src/api/billing";
import { profileApi } from "../../src/api/catalogue";
import { socialApi } from "../../src/api/social";
import { useUpper } from "../../src/i18n/case";
import { useCountryName } from "../../src/i18n/countryNames";
import type { ShowcasedBadge } from '../../src/api/types';
import { useT } from "../../src/i18n/useT";
import { useThemeColors } from "../../src/theme";
import { LinkedAccounts } from "../../src/profile/LinkedAccounts";
import { ThemedCardFrame } from "../../src/ui/ThemedCardFrame";
import {
  Card,
  ErrorNotice,
  FramedAvatar,
  GamePills,
  Icon,
  type PillGame,
  ProfileBanner,
  Screen,
  Text,
  useProfileHeaderLayout,
} from "../../src/ui";

export default function Profile() {
  const router = useRouter();
  const colors = useThemeColors();
  const header = useProfileHeaderLayout();
  const t = useT();
  const upper = useUpper();
  const localize = useCountryName();

  const me = useQuery({ queryKey: ["me"], queryFn: profileApi.me });
  // Only reaches the banner strip when no banner is worn — a picture bought for that
  // strip beats a colour bought for the card.
  const friends = useQuery({
    queryKey: ["friends"],
    queryFn: socialApi.friends,
  });

  // Same key the Market, the deck and the paywall use, so react-query serves all of them
  // from one request rather than this adding a fourth call on a screen that already makes
  // three.
  const subscription = useQuery(subscriptionQuery());
  const isGold = subscription.data?.tier === "GOLD";

  return (
    <Screen scroll edges={["top"]}>
      <View className="flex-row items-center justify-between pb-6 pt-8">
        <View>
          <Text variant="overline">{upper(t.profile.header)}</Text>
          <Text variant="title">{t.profile.title}</Text>
        </View>
        <View className="flex-row items-center gap-2">
          {/* Inventory before Settings, because it is the one people come back to. A
              wardrobe is visited whenever something new is bought; settings are visited
              roughly once. */}
          <Pressable
            onPress={() => router.push("/inventory")}
            accessibilityRole="button"
            accessibilityLabel={t.profile.inventoryA11y}
            className="h-11 w-11 items-center justify-center rounded-full bg-raised active:opacity-70"
          >
            <Icon as={Shirt} size={20} tone="content" />
          </Pressable>

          <Pressable
            onPress={() => router.push("/settings")}
            accessibilityRole="button"
            accessibilityLabel={t.profile.settingsA11y}
            className="h-11 w-11 items-center justify-center rounded-full bg-raised active:opacity-70"
          >
            {/* A cog, not the three dots that used to be here. The dots said "there is a
                menu behind this", and there is not — it goes straight to Settings. */}
            <Icon as={Settings} size={20} tone="content" />
          </Pressable>
        </View>
      </View>

      {me.isPending && <ActivityIndicator color={colors.primary} />}
      {me.error && (
        <ErrorNotice error={me.error} onRetry={() => me.refetch()} />
      )}

      <View className="gap-6">
        {me.data && (
          /* The wrapper exists only so the theme's edge has somewhere to sit: it draws
             just *outside* the card, and a card's own background is painted before any of
             its children, so a frame mounted inside it would cover the card rather than
             ring it. See `ThemedCardFrame`. */
          <View>
            <ThemedCardFrame theme={me.data.theme} radius={24} />
            <Card className={header.cardGap}>
              <ProfileBanner source={me.data.banner} />

              {/* Pulled up over the banner's lower edge, which is the arrangement every
                  profile header uses: it ties the two together instead of stacking a
                  picture on top of an unrelated strip of art. */}
              <View
                className={`${
                  // The pills add a row to the bottom-aligned name column, which
                  // grows it upward — so the overlap gives way rather than the
                  // username climbing onto the banner.
                  me.data.linkedAccounts?.length
                    ? header.overlapWithExtraRow
                    : header.overlap
                } flex-row items-end gap-4`}
              >
                <FramedAvatar
                  frame={me.data.frame}
                  source={me.data.avatar}
                  name={me.data.username}
                  colorSeed={me.data.userId}
                  size={header.avatar}
                />
                <View className="flex-1 gap-0.5 pb-1">
                  {/* The name shrinks and truncates so the tag beside it is never pushed off
                      the row. A long username losing its tail is a smaller loss than a
                      membership badge that silently disappears for exactly the people who
                      have one. */}
                  <View className="flex-row items-center gap-2">
                    <Text variant="heading" numberOfLines={1} className="shrink">
                      {me.data.username}
                    </Text>
                    {isGold && <GoldTag />}
                  </View>
                  {/* Directly under the name and above the age line: it answers "where do I
                      reach this person", which belongs with who they are rather than in the
                      list of what they play. */}
                  <LinkedAccounts accounts={me.data.linkedAccounts} />
                  <Text variant="caption">
                    {[me.data.age, localize(me.data.country)]
                      .filter(Boolean)
                      .join(" · ")}
                  </Text>
                </View>
              </View>

              <View className="flex-row gap-3">
                {/* Two of the three go somewhere, and the count is the way in: a number
                    you can tap beats the same number printed above a list of the thing it
                    counts. Coins is the odd one out on purpose — spending them is the
                    Market's job, and sending people there from here would be a shop
                    doorway in the middle of a profile. */}
                <Stat
                  label={t.profile.friends}
                  value={friends.data?.length ?? 0}
                  onPress={() => router.push("/friends")}
                />
                <Stat label={t.profile.coins} value={me.data.coin ?? 0} />
                <Stat
                  label={t.profile.badges}
                  value={me.data.badgeCount ?? 0}
                  onPress={() => router.push("/badges")}
                />
              </View>

              <Showcase
                badges={me.data.badges ?? []}
                onPress={() => router.push("/badges")}
              />

              <View className="h-px bg-line" />

              {/* The same pills as the deck, cover art and all — `games` has carried
                  `gameIcon` all along and this used to render only the name. */}
              <GameSection title={t.profile.games} games={me.data.games} />
              {/* Directly under games, because it is the second half of the same question:
                  what you play, and what you play it on. */}
              <Tags title={t.profile.playsOn} items={me.data.platforms ?? []} />
              <Tags
                title={t.profile.keywords}
                items={me.data.keywords.map((k) => k.keywordName)}
              />
            </Card>
          </View>
        )}

        {/* Friend requests used to sit here. They are now at the top of the messages
            tab (`src/social/FriendRequestsSection.tsx`): a request is somebody asking
            for a conversation, and testers went looking for it among the conversations
            rather than on the screen that describes them. */}
      </View>
    </Screen>
  );
}

/**
 * The membership badge that sits beside your own name.
 *
 * Small on purpose. It is a status marker, not an advert — the person reading it has already
 * paid, so it only has to be recognisable at a glance, and a tag competing with the username
 * for the top of the screen would read as the app still selling to somebody who has bought.
 *
 * The shape is the saving badge from the paywall's plan rows (`app/(main)/gold.tsx`): a
 * gold-tinted pill with 11px semibold gold text. Reused rather than reinvented so the app
 * has one "small gold badge" instead of two that nearly match.
 *
 * A filled crown rather than an outlined one: at 12px an outline closes up into a blob, and
 * the crown is the mark the whole Gold screen is built around.
 */
function GoldTag() {
  const colors = useThemeColors();
  const t = useT();

  return (
    <View
      className="shrink-0 flex-row items-center gap-1 rounded-full bg-gold/20 px-2 py-0.5"
      accessibilityRole="text"
      accessibilityLabel={t.profile.goldMemberA11y}
    >
      <Icon
        as={Crown}
        size={12}
        tone="gold"
        fill={colors.gold}
        strokeWidth={2}
      />
      <Text className="font-semibold text-[11px] leading-[15px] text-gold">
        Gold
      </Text>
    </View>
  );
}

function Stat({
  label,
  value,
  onPress,
}: {
  label: string;
  value: number;
  onPress?: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={!onPress}
      accessibilityRole={onPress ? "button" : undefined}
      // Both branches carry the same class keys; only the values move. A class present
      // on one and absent on the other stops NativeWind painting the subtree — see
      // `src/ui/hairline.ts`.
      className={
        onPress
          ? "flex-1 items-center gap-0.5 rounded-card bg-raised py-3 active:opacity-70"
          : "flex-1 items-center gap-0.5 rounded-card bg-raised py-3 active:opacity-100"
      }
    >
      <Text className="font-bold text-[20px] leading-[26px] text-primary">
        {value}
      </Text>
      <Text variant="caption">{label}</Text>
    </Pressable>
  );
}

/**
 * The three badges this gamer chose to display.
 *
 * Always rendered, even with nothing on show — the empty state is an invitation to go
 * and earn one, and a row that appears out of nowhere the first time you showcase
 * something makes the card jump.
 */
function Showcase({
  badges,
  onPress,
}: {
  // The shared type rather than a structural copy of three of its fields: the copy
  // silently stopped matching the moment the wire type grew an `animated` flag.
  badges: ShowcasedBadge[];
  onPress: () => void;
}) {
  const t = useT();
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={t.profile.badgesA11y}
      className="flex-row items-center gap-3 active:opacity-70"
    >
      {badges.length === 0 ? (
        <Text variant="caption">{t.profile.noShowcase}</Text>
      ) : (
        badges.map((badge) => (
          // Wide enough for two lines of the longest title in the set. At one line
          // every name but the shortest ended in an ellipsis, which is a worse way to
          // spend the space than simply wrapping.
          <View
            key={badge.code}
            className="items-center gap-1"
            style={{ width: 80 }}
          >
            <Image
              source={{ uri: badge.icon }}
              style={{ width: 48, height: 48 }}
              contentFit="contain"
              transition={150}
              // The two hardest badges have animated artwork. Without this the
              // showcase — the one surface where a badge is shown to somebody
              // else — would freeze them on their first frame.
              autoplay={badge.animated}
              cachePolicy="memory-disk"
              recyclingKey={badge.code}
            />
            <Text variant="caption" numberOfLines={2} className="text-center">
              {badge.title}
            </Text>
          </View>
        ))
      )}
    </Pressable>
  );
}

/**
 * Games, drawn exactly as the deck draws them.
 *
 * Its own wrapper rather than a flag on `Tags`, because a game is not a tag: it has art, and
 * `GamePills` is what knows how to place it. The heading matches the tag sections beside it
 * so the card still reads as one list of facts about a person.
 */
function GameSection({ title, games }: { title: string; games: PillGame[] }) {
  return (
    <View className="gap-2">
      <Text variant="label" className="text-muted">
        {title}
      </Text>
      {games.length === 0 ? (
        <Text variant="body" className="text-muted">
          —
        </Text>
      ) : (
        <GamePills games={games} />
      )}
    </View>
  );
}

function Tags({
  title,
  items,
  accent = false,
}: {
  title: string;
  items: string[];
  accent?: boolean;
}) {
  return (
    <View className="gap-2">
      <Text variant="label" className="text-muted">
        {title}
      </Text>
      {items.length === 0 ? (
        <Text variant="body" className="text-muted">
          —
        </Text>
      ) : (
        <View className="flex-row flex-wrap gap-2">
          {items.map((item) => (
            <View
              key={item}
              className={
                accent
                  ? "rounded-full border border-primary/30 bg-primary/10 px-3 py-1.5"
                  : "rounded-full bg-raised px-3 py-1.5"
              }
            >
              <Text
                variant="caption"
                className={accent ? "text-primary" : "text-content"}
              >
                {item}
              </Text>
            </View>
          ))}
        </View>
      )}
    </View>
  );
}
