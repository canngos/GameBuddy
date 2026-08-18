import { useState } from 'react';
import { View } from 'react-native';
import { useUpper } from '../i18n/case';
import { useT } from '../i18n/useT';
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
  const t = useT();
  const upper = useUpper();
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
        <Text variant="overline">{upper(t.notifications.header)}</Text>
        <Text variant="title">{t.notifications.primerTitle}</Text>
        <Text variant="body" className="text-muted">
          {t.notifications.primerBody}
        </Text>
      </View>

      <Card className="gap-4">
        <Reason title={t.notifications.reasonMatchTitle} body={t.notifications.reasonMatchBody} />
        <Reason
          title={t.notifications.reasonMessageTitle}
          body={t.notifications.reasonMessageBody}
        />
        <Reason title={t.notifications.reasonLobbyTitle} body={t.notifications.reasonLobbyBody} />
      </Card>

      <View className="gap-2">
        <Button label={t.notifications.turnOn} loading={asking} onPress={allow} />
        {/* Not styled as a lesser choice. A "no" that has been made deliberately hard to
            find is the pattern this whole screen exists to avoid. */}
        <Button label={t.notifications.notNow} variant="ghost" disabled={asking} onPress={notNow} />
      </View>

      <Text variant="caption" className="text-center">
        {t.notifications.changeLater}
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
