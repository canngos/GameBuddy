import { View } from 'react-native';
import { Text } from '../ui';

/**
 * The Season Pass slot, before there is a Season Pass.
 *
 * <p><b>A teaser, not a loading state.</b> The distinction is the whole point of this
 * component. "Season Pass — is loading" reads as a network problem, and users report
 * network problems as bugs; "Coming soon" reads as a plan. So there is no spinner, no
 * shimmer and no skeleton — those are the vocabulary of something that is about to
 * arrive in the next second, and this is not.
 *
 * Not tappable either, and disabled to assistive technology rather than merely inert. A
 * card that looks pressable and does nothing is a worse promise than one that plainly
 * says it is not ready.
 *
 * Whether it appears at all is the server's decision — see `seasonPassTeaser` on
 * `GET /billing/subscription`. Seasons are an art commitment before they are an
 * engineering one, six cosmetics every six weeks forever, and a promise that may have to
 * be withdrawn should not need a store release to withdraw.
 */
export function SeasonPassTeaser() {
  return (
    <View
      accessible
      accessibilityRole="summary"
      accessibilityState={{ disabled: true }}
      accessibilityLabel="Season Pass. Coming soon — new rewards every season."
      importantForAccessibility="yes"
      className="mb-5 overflow-hidden rounded-card border border-line bg-raised p-5"
    >
      <View className="flex-row items-center justify-between gap-4">
        <View className="flex-1 gap-1">
          <Text variant="overline" className="text-muted">
            SEASON PASS
          </Text>
          <Text variant="heading">Coming soon</Text>
          <Text variant="caption">New rewards every season.</Text>
        </View>

        {/* A quiet mark rather than an icon that suggests a destination. Muted on
            purpose: this card should read as furniture with a future, not as the
            brightest thing on the screen. */}
        <View className="h-12 w-12 items-center justify-center rounded-full bg-surface">
          <Text className="text-[20px] leading-[24px]">🗓️</Text>
        </View>
      </View>
    </View>
  );
}
