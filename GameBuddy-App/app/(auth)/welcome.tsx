import { useRouter } from 'expo-router';
import { Gamepad2, MessagesSquare, ShieldCheck } from 'lucide-react-native';
import { View } from 'react-native';
import { Button, Icon, Screen, Text } from '../../src/ui';

/**
 * Three claims, each tied to something the product actually does — the recommender,
 * the age bands, the match-before-chat rule. Generic marketing copy here would be
 * writing a cheque the app has to cash on the next screen.
 */
const PITCH = [
  { icon: Gamepad2, title: 'Matched on taste', body: 'Ranked by what you play, not who is nearby.' },
  { icon: ShieldCheck, title: 'Age-separated', body: 'Under-18 and over-18 are never shown to each other.' },
  { icon: MessagesSquare, title: 'Chat after a match', body: 'Both of you have to say yes first.' },
];

export default function Welcome() {
  const router = useRouter();

  return (
    <Screen scroll>
      <View className="flex-1 justify-center gap-10 py-12">
        <View className="gap-3">
          {/* Two weights on one line: the brand reads as a mark rather than a heading.
              This keeps `brand` — the wordmark is the one use of the pink that is identity
              rather than a role, so it survives the demotion described in
              `src/theme/gradients.ts`. Everything else on this screen moved to `primary`. */}
          <Text variant="display" className="text-content">
            Find your
          </Text>
          <View className="flex-row items-baseline gap-2">
            <Text variant="display" className="text-brand">
              GameBuddy
            </Text>
            <View className="h-3 w-3 rounded-full bg-brand" />
          </View>
          <Text variant="body" className="mt-2 text-muted">
            People who play what you play, at the hours you actually play.
          </Text>
        </View>

        <View className="gap-3">
          {PITCH.map((item) => (
            <View
              key={item.title}
              className="flex-row items-center gap-4 rounded-card bg-raised p-4"
            >
              <View className="h-11 w-11 items-center justify-center rounded-full bg-primary/10">
                <Icon as={item.icon} size={20} tone="primary" />
              </View>
              <View className="flex-1 gap-0.5">
                <Text variant="bodyStrong">{item.title}</Text>
                <Text variant="caption">{item.body}</Text>
              </View>
            </View>
          ))}
        </View>
      </View>

      <View className="gap-3">
        <Button label="Create an account" onPress={() => router.push('/register')} />
        <Button
          label="I already have one"
          variant="secondary"
          onPress={() => router.push('/login')}
        />
      </View>
    </Screen>
  );
}
