import { useEffect } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import type { Candidate } from '../api/types';
import { avatarGradient, avatarUri, initialsOf } from '../avatars';
import { useCountryName } from '../i18n/countryNames';
import { useT } from '../i18n/useT';
import { Avatar } from '../ui/Avatar';
import { overlay } from '../ui/elevation';
import { FrameOverlay } from '../ui/FramedAvatar';
import { GradientView } from '../ui/Gradient';
import { Text } from '../ui/Text';
import { GameRow, KeywordRow, PlatformRow } from './CandidateCard';

type CandidateSheetProps = {
  /** The candidate whose profile is open, or null for closed. */
  candidate: Candidate | null;
  onDismiss: () => void;
};

/**
 * The whole of a candidate, behind one tap on their card.
 *
 * **A sheet rather than a screen.** There is already a gamer-profile *route* — the one a
 * chat header opens — and it is the wrong thing to reuse here twice over: it offers block,
 * report and remove-friend, which are answers to "who is this person I already know" and
 * not to "should I swipe right", and reaching it means leaving the deck mid-decision. A
 * sheet keeps the card underneath, dismisses back to exactly where it was, and matches
 * what `FilterSheet` and `LimitSheet` already do on this screen.
 *
 * **It fetches nothing.** The deck already holds the complete `Candidate` — the card was
 * summarising it, not truncating a partial payload — so this is the same object rendered
 * without caps. That is why opening it is instant and works offline exactly as far as the
 * deck itself does.
 *
 * The pills are imported from `CandidateCard` rather than rewritten, so the summary and the
 * full list cannot drift into looking like two different products.
 */
export function CandidateSheet({ candidate, onDismiss }: CandidateSheetProps) {
  const t = useT();
  const visible = !!candidate;
  const localize = useCountryName();
  const progress = useSharedValue(0);

  useEffect(() => {
    progress.value = visible
      ? withSpring(1, { damping: 20, stiffness: 200 })
      : withTiming(0, { duration: 120 });
  }, [visible, progress]);

  const backdropStyle = useAnimatedStyle(() => ({ opacity: progress.value }));
  const sheetStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: (1 - progress.value) * 560 }],
  }));

  if (!candidate) return null;

  const hasPhoto = !!avatarUri(candidate.avatar);

  return (
    <View style={[StyleSheet.absoluteFill, overlay(45)]}>
      <Animated.View style={[StyleSheet.absoluteFill, backdropStyle]}>
        <View className="flex-1 justify-end bg-ink-900/70">
          <Pressable className="flex-1" onPress={onDismiss} accessibilityLabel={t.common.close} />

          <Animated.View style={sheetStyle}>
            <View className="max-h-[86%] overflow-hidden rounded-t-[28px] bg-surface pb-10 pt-3">
              <View className="mb-3 h-1 w-10 self-center rounded-full bg-line" />

              {/* The same identity block the card wears, at half the height. Repeating it
                  is what makes the sheet read as *this* person's profile rather than as a
                  detached list of tags — and the gradient is their colour everywhere. */}
              <View className="overflow-hidden">
                <GradientView
                  colors={avatarGradient(candidate.userId)}
                  direction="diagonal"
                  className="absolute inset-0"
                  pointerEvents="none"
                />
                <GradientView
                  colors={['rgba(0,0,0,0)', 'rgba(0,0,0,0.55)']}
                  direction="vertical"
                  className="absolute inset-0"
                  pointerEvents="none"
                />

                <View className="flex-row items-center gap-4 px-6 py-5">
                  <View className="h-16 w-16 items-center justify-center">
                    {hasPhoto ? (
                      <Avatar
                        source={candidate.avatar}
                        name={candidate.gamerUsername}
                        size={64}
                      />
                    ) : (
                      <View className="h-16 w-16 items-center justify-center rounded-full bg-white/25">
                        <Text className="font-bold text-[22px] leading-[26px] text-white">
                          {initialsOf(candidate.gamerUsername)}
                        </Text>
                      </View>
                    )}
                    <FrameOverlay frame={candidate.frame} size={64} />
                  </View>

                  <View className="min-w-0 flex-1">
                    <Text variant="heading" numberOfLines={1} className="text-white">
                      {candidate.gamerUsername}
                    </Text>
                    <Text className="font-medium text-[13px] leading-[18px] text-white/85">
                      {[candidate.age, localize(candidate.country)].filter(Boolean).join(' · ')}
                    </Text>
                  </View>
                </View>
              </View>

              {/* Everything, uncapped. `hidden` defaults to 0, so no "+N" pill appears —
                  this is the surface that answers the one the card raises. */}
              <ScrollView
                className="px-6"
                contentContainerClassName="gap-6 py-5"
                showsVerticalScrollIndicator={false}
              >
                <GameRow games={candidate.favoriteGames ?? []} />
                {!!candidate.platforms?.length && (
                  <PlatformRow platforms={candidate.platforms} />
                )}
                <KeywordRow title={t.deck.card.style} items={candidate.selectedKeywords ?? []} />
              </ScrollView>

              <View className="border-t border-line px-6 pt-4">
                <Pressable
                  onPress={onDismiss}
                  accessibilityRole="button"
                  className="items-center rounded-card py-3 active:opacity-70"
                >
                  {/* No Match or Pass in here on purpose. The decision belongs to the deck,
                      where the buttons and the swipe already live — offering a third way to
                      accept, on a surface somebody opened to *read*, invites the mis-tap
                      this sheet exists to prevent. */}
                  <Text variant="label" className="text-primary">
                    Back to the deck
                  </Text>
                </Pressable>
              </View>
            </View>
          </Animated.View>
        </View>
      </Animated.View>
    </View>
  );
}
