import { Gamepad2, Wrench } from 'lucide-react-native';
import { ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { GradientView } from '../ui/Gradient';
import { Icon } from '../ui/Icon';
import { Text } from '../ui/Text';

/** No links or actions: every entry point stays here while maintenance is enabled. */
export function MaintenanceScreen() {
  return (
    <SafeAreaView className="flex-1 bg-canvas" edges={['top', 'bottom', 'left', 'right']}>
      <ScrollView
        contentContainerClassName="flex-grow items-center justify-center px-6 py-10"
        showsVerticalScrollIndicator={false}
      >
        <View className="w-full max-w-md items-center">
          <View className="mb-10 flex-row items-center gap-2">
            <Icon as={Gamepad2} tone="primary" size={28} />
            <Text variant="title">GameBuddy</Text>
          </View>

          <View
            className="mb-8 h-24 w-24 items-center justify-center overflow-hidden rounded-3xl"
            accessibilityElementsHidden
            importantForAccessibility="no-hide-descendants"
          >
            <GradientView name="primary" className="absolute inset-0" />
            <Icon as={Wrench} tone="inverse" size={40} />
          </View>

          <View className="mb-5 rounded-full border border-line bg-surface px-4 py-2">
            <Text variant="overline" className="text-primary">UNDER MAINTENANCE</Text>
          </View>

          <Text variant="hero" className="mb-5 text-center" accessibilityRole="header">
            A better GameBuddy is on the way.
          </Text>
          <Text variant="body" className="text-center text-muted">
            We&apos;re carrying out maintenance to bring you a better experience.
          </Text>

          <View className="mt-8 w-full rounded-3xl border border-line bg-surface p-6">
            <Text variant="bodyStrong" className="text-center">
              We&apos;ll let you know when we&apos;re back!
            </Text>
            <Text variant="caption" className="mt-2 text-center">
              Thanks for your patience. We can&apos;t wait to play together again.
            </Text>
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
