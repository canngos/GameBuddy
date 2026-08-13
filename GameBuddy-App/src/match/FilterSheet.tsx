import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { Image, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import { profileApi } from '../api/catalogue';
import { PLATFORMS } from '../profile/platforms';
import { Button } from '../ui/Button';
import { Text } from '../ui/Text';
import { NO_FILTERS, activeCount, isNarrowing, type FeedFilters } from './filters';

type FilterSheetProps = {
  visible: boolean;
  filters: FeedFilters;
  /** Whether this account may actually use them. False shows the controls locked. */
  unlocked: boolean;
  onApply: (filters: FeedFilters) => void;
  onDismiss: () => void;
};

/**
 * Narrows the deck by game, region and who is on right now.
 *
 * **The controls are shown to everyone, including accounts that cannot use them.** A
 * locked control that names what it would do sells the thing; a hidden one cannot. This
 * is the whole reason the sheet renders its rows in both states rather than swapping in a
 * generic upgrade screen — a free gamer should see "Only players in Finland" and want it.
 *
 * The lock is presentation only. Enforcement is server-side in `DefaultMatchService`,
 * which refuses a narrowed feed with 159 and cannot be talked out of it by a client. So
 * the worst a stale `unlocked` can do is offer a control that then gets refused, which
 * the deck handles — it never grants anything.
 *
 * Choices are staged locally and only handed up on Apply. Editing the live filters would
 * refetch the feed on every tap, and each fetch records an impression for everyone it
 * returns — three taps would poison the training data with three decks nobody looked at.
 *
 * See {@link LimitSheet} for why this is a positioned sibling rather than a `Modal`.
 */
export function FilterSheet({ visible, filters, unlocked, onApply, onDismiss }: FilterSheetProps) {
  const router = useRouter();
  const progress = useSharedValue(0);
  const [draft, setDraft] = useState<FeedFilters>(filters);

  // Re-open with what is actually applied, not with an abandoned edit from last time.
  useEffect(() => {
    if (visible) setDraft(filters);
  }, [visible, filters]);

  useEffect(() => {
    progress.value = visible
      ? withSpring(1, { damping: 20, stiffness: 200 })
      : withTiming(0, { duration: 120 });
  }, [visible, progress]);

  const backdropStyle = useAnimatedStyle(() => ({ opacity: progress.value }));
  const sheetStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: (1 - progress.value) * 560 }],
  }));

  const me = useQuery({ queryKey: ['me'], queryFn: profileApi.me, enabled: visible });
  const myGames = me.data?.games ?? [];
  const myCountry = me.data?.country ?? null;

  if (!visible) return null;

  const count = activeCount(draft);

  return (
    <View style={[StyleSheet.absoluteFill, { zIndex: 50 }]}>
      <Animated.View style={[StyleSheet.absoluteFill, backdropStyle]}>
        <View className="flex-1 justify-end bg-ink-900/70">
          <Pressable className="flex-1" onPress={onDismiss} accessibilityLabel="Close" />

          <Animated.View style={sheetStyle}>
            <View className="max-h-[80%] rounded-t-[28px] bg-surface pb-10 pt-3">
              <View className="mb-3 h-1 w-10 self-center rounded-full bg-line" />

              <View className="flex-row items-center justify-between gap-4 px-6 pb-3">
                <View className="flex-1">
                  <Text variant="title">Filters</Text>
                  <Text variant="caption">
                    {unlocked
                      ? 'Narrow the deck to the people you actually want to play with.'
                      : 'Part of Gold. Have a look at what it does.'}
                  </Text>
                </View>
                {!unlocked && <GoldPill />}
              </View>

              <ScrollView className="px-6" contentContainerClassName="gap-6 pb-4">
                <Section
                  title="Game"
                  hint={
                    myGames.length
                      ? 'Only people who play this one.'
                      : 'Add games to your profile to filter by them.'
                  }>
                  <View className="flex-row flex-wrap gap-2">
                    <GameChip
                      label="Any game"
                      selected={draft.gameId === null}
                      disabled={!unlocked}
                      onPress={() => setDraft((d) => ({ ...d, gameId: null }))}
                    />
                    {myGames.map((game) => (
                      <GameChip
                        key={game.gameId}
                        label={game.gameName}
                        icon={game.gameIcon}
                        selected={draft.gameId === game.gameId}
                        disabled={!unlocked}
                        onPress={() =>
                          setDraft((d) => ({
                            ...d,
                            // Tapping the selected game clears it, so "any game" is
                            // reachable without hunting for the chip that means nothing.
                            gameId: d.gameId === game.gameId ? null : game.gameId,
                          }))
                        }
                      />
                    ))}
                  </View>
                </Section>

                <Section
                  title="Region"
                  hint={
                    myCountry
                      ? 'Same timezone, same servers, same evening.'
                      : 'Set your country on your profile to filter by it.'
                  }>
                  <Toggle
                    label={myCountry ? `Only players in ${myCountry}` : 'Only players near me'}
                    value={draft.country !== null}
                    // Nothing to compare against without a country of our own, and
                    // sending an empty one would filter the deck down to nobody.
                    disabled={!unlocked || !myCountry}
                    onChange={(on) => setDraft((d) => ({ ...d, country: on ? myCountry : null }))}
                  />
                </Section>

                {/* One platform at a time, as chips rather than the multi-select used on
                    the profile: there the question is "what do you own", here it is "who
                    can I play with tonight", and that is one answer. Somebody on PC and
                    Switch shows up under either. */}
                <Section title="Platform" hint="Whoever you can actually get in a lobby with.">
                  <View className="flex-row flex-wrap gap-2">
                    <GameChip
                      label="Any platform"
                      selected={draft.platform === null}
                      disabled={!unlocked}
                      onPress={() => setDraft((d) => ({ ...d, platform: null }))}
                    />
                    {PLATFORMS.map((platform) => (
                      <GameChip
                        key={platform.id}
                        label={platform.label}
                        selected={draft.platform === platform.id}
                        disabled={!unlocked}
                        onPress={() =>
                          setDraft((d) => ({
                            ...d,
                            platform: d.platform === platform.id ? null : platform.id,
                          }))
                        }
                      />
                    ))}
                  </View>
                </Section>

                <Section title="Availability" hint="Active in the last 15 minutes.">
                  <Toggle
                    label="Online now"
                    value={draft.onlineNow}
                    disabled={!unlocked}
                    onChange={(on) => setDraft((d) => ({ ...d, onlineNow: on }))}
                  />
                </Section>
              </ScrollView>

              <View className="gap-2 border-t border-line px-6 pt-4">
                {unlocked ? (
                  <>
                    <Button
                      label={count > 0 ? `Show ${count === 1 ? '1 filter' : `${count} filters`}` : 'Show everyone'}
                      onPress={() => {
                        onApply(draft);
                        onDismiss();
                      }}
                    />
                    {isNarrowing(filters) && (
                      <Button
                        label="Clear filters"
                        variant="ghost"
                        onPress={() => {
                          onApply(NO_FILTERS);
                          onDismiss();
                        }}
                      />
                    )}
                  </>
                ) : (
                  <>
                    <Button
                      label="Unlock filters with Gold"
                      onPress={() => {
                        // Dismiss first — this sheet is a positioned sibling of the deck,
                        // so leaving it mounted would cover the paywall it opens.
                        onDismiss();
                        router.push('/gold');
                      }}
                    />
                    <Button label="Not now" variant="ghost" onPress={onDismiss} />
                  </>
                )}
              </View>
            </View>
          </Animated.View>
        </View>
      </Animated.View>
    </View>
  );
}

function Section({
  title,
  hint,
  children,
}: {
  title: string;
  hint: string;
  children: React.ReactNode;
}) {
  return (
    <View className="gap-3">
      <View className="gap-0.5">
        <Text variant="overline">{title.toUpperCase()}</Text>
        <Text variant="caption">{hint}</Text>
      </View>
      {children}
    </View>
  );
}

function GoldPill() {
  return (
    <View className="rounded-full bg-gold/15 px-3 py-1">
      <Text className="font-semibold text-[12px] leading-[16px] text-gold">GOLD</Text>
    </View>
  );
}

function GameChip({
  label,
  icon,
  selected,
  disabled,
  onPress,
}: {
  label: string;
  icon?: string;
  selected: boolean;
  disabled: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityState={{ selected, disabled }}
      // `max-w-full` caps the chip at the row's width. Without it a long game name — "Atelier
      // Ryza 3: Alchemist of the End & the Secret Key" is the one that exposed this — makes a
      // chip wider than the sheet, and `flex-wrap` overflows it off the right edge rather
      // than wrapping, because a single item cannot be wrapped against itself.
      className={[
        'max-w-full flex-row items-center gap-2 rounded-full border px-3 py-2',
        selected ? 'border-primary bg-primary/10' : 'border-line bg-raised',
        disabled ? 'opacity-50' : '',
      ].join(' ')}>
      {icon ? <Image source={{ uri: icon }} className="h-5 w-5 rounded" /> : null}
      <Text
        numberOfLines={1}
        className={[
          'shrink font-medium text-[14px] leading-[18px]',
          // `text-content`, not `text-ink`. There is no bare `ink` colour — the palette
          // defines `ink-500/700/900` and nothing else — so `text-ink` compiled to nothing,
          // NativeWind dropped it, and the label fell through to the platform's default
          // black. On a light chip that passes for deliberate; on `raised` in dark mode it is
          // black on near-black, which is how this shipped unreadable in one theme only.
          selected ? 'text-primary' : 'text-content',
        ].join(' ')}>
        {label}
      </Text>
    </Pressable>
  );
}

/**
 * A row that reads as a switch.
 *
 * Not React Native's `Switch`: it renders with the platform's own accent colour and
 * ignores the app's, so a row of them would be the only iOS-green thing on the screen.
 */
function Toggle({
  label,
  value,
  disabled,
  onChange,
}: {
  label: string;
  value: boolean;
  disabled: boolean;
  onChange: (value: boolean) => void;
}) {
  return (
    <Pressable
      onPress={() => onChange(!value)}
      disabled={disabled}
      accessibilityRole="switch"
      accessibilityState={{ checked: value, disabled }}
      className={[
        'flex-row items-center justify-between gap-4 rounded-card border p-4',
        value ? 'border-primary bg-primary/10' : 'border-line bg-raised',
        disabled ? 'opacity-50' : '',
      ].join(' ')}>
      {/* `text-content` for the same reason as the chip above — `text-ink` was not a real
          class and left this label rendering in the platform's default black. */}
      <Text className="flex-1 font-medium text-[15px] leading-[20px] text-content">{label}</Text>
      <View
        className={[
          'h-6 w-10 justify-center rounded-full px-0.5',
          value ? 'bg-primary' : 'bg-line',
        ].join(' ')}>
        <View className={['h-5 w-5 rounded-full bg-surface', value ? 'self-end' : ''].join(' ')} />
      </View>
    </Pressable>
  );
}
