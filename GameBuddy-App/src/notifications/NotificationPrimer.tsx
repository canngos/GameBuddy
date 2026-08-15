import { useState } from 'react';
import { View } from 'react-native';
import { Button, Card, Text } from '../ui';
import { markPrimed, requestSystemPermission } from './permission';

/**
 * Asks for notifications in our own words, before the operating system asks in its.
 *
 * <p>The system prompt can be answered once and the answer is effectively permanent —
 * on Android 13+ a second dismissal is final, and nothing inside the app can reopen it.
 * Spending that single question on somebody who has just arrived and does not yet know
 * what the app would tell them is how an app ends up with notifications off forever.
 *
 * <p>So this screen goes first. It costs one tap for somebody who was going to say yes
 * anyway, and for everybody else it converts a permanent "no" into "not now" — a state
 * they can change from Settings whenever the app has earned it.
 *
 * <p>The reasons are specific. "Stay updated!" is what an app says when it wants
 * permission for its own sake; naming the three things that will actually interrupt them
 * lets somebody make a real decision, and it is what the notifications then deliver.
 */
export function NotificationPrimer({ onDone }: { onDone: () => void }) {
  const [asking, setAsking] = useState(false);

  const allow = async () => {
    setAsking(true);
    try {
      // Whatever the system prompt returns, we are done here. A refusal is an answer,
      // not a reason to ask again.
      await requestSystemPermission();
    } finally {
      setAsking(false);
      onDone();
    }
  };

  const notNow = async () => {
    // Recorded, so this screen does not reappear on the next launch. The system prompt
    // is left unspent — they never saw it — so turning notifications on from Settings
    // later still works normally.
    await markPrimed();
    onDone();
  };

  return (
    <View className="flex-1 justify-center gap-6 px-6">
      <View className="gap-2">
        <Text variant="overline">NOTIFICATIONS</Text>
        <Text variant="title">Don't miss the good part</Text>
        <Text variant="body" className="text-muted">
          GameBuddy is other people. Most of what happens here happens while the app is
          closed.
        </Text>
      </View>

      <Card className="gap-4">
        <Reason title="When you match" body="Both of you said yes — that is when a conversation can start." />
        <Reason title="When someone messages you" body="So a reply does not wait until you next happen to open the app." />
        <Reason
          title="When somebody wants into your lobby"
          body="And when the owner of one lets you in."
        />
      </Card>

      <View className="gap-2">
        <Button label="Turn on notifications" loading={asking} onPress={allow} />
        {/* Not styled as a lesser choice. A "no" that has been made deliberately hard to
            find is the pattern this whole screen exists to avoid. */}
        <Button label="Not now" variant="ghost" disabled={asking} onPress={notNow} />
      </View>

      <Text variant="caption" className="text-center">
        You can change this any time in Settings, and choose which kinds you want.
      </Text>
    </View>
  );
}

function Reason({ title, body }: { title: string; body: string }) {
  return (
    <View className="flex-row gap-3">
      <View className="mt-1.5 h-2 w-2 rounded-full bg-primary" />
      <View className="flex-1 gap-0.5">
        <Text variant="bodyStrong">{title}</Text>
        <Text variant="caption">{body}</Text>
      </View>
    </View>
  );
}
