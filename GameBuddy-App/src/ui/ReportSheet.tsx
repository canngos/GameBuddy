import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useT } from '../i18n/useT';
import { Button } from './Button';
import { Card } from './Card';
import { Text } from './Text';
import { TextField } from './TextField';

/**
 * A reason code, shared with the backend's `ContentReport.ReasonCode`.
 *
 * A fixed set rather than free text: it means the same thing in every language, the
 * moderation policy branches on it (an underage report is urgent whoever files it, a
 * sexual one about a picture pulls the picture), and a short vocabulary is faster to tap
 * than a box to fill. "Something else" is what genuinely unusual reports are for, and it
 * is the only one that asks for words.
 */
export type ReasonCode = 'HARASSMENT' | 'SEXUAL' | 'SPAM_SCAM' | 'UNDERAGE' | 'IMPERSONATION' | 'OTHER';

export type ReportPick = { reasonCode: ReasonCode; note?: string };

type ReportSheetProps = {
  /**
   * What is being reported, as a *whole title* rather than a noun spliced into one.
   *
   * It used to be the bare word — `what="profile"` rendered as `Report this {what}` —
   * which reads fine in English and falls apart everywhere else: German capitalises the
   * noun, French and Spanish need agreement, and Turkish puts the demonstrative and the
   * suffix somewhere a template cannot reach. Each language now writes the sentence.
   */
  subject: 'profile' | 'message';
  onPick: (pick: ReportPick) => void;
  onCancel: () => void;
};

/** The reasons, paired with the code the backend stores. Order is the order they show in. */
function reasonList(t: ReturnType<typeof useT>): { code: ReasonCode; label: string }[] {
  return [
    { code: 'HARASSMENT', label: t.ui.reasonHarassment },
    { code: 'SEXUAL', label: t.ui.reasonSexual },
    { code: 'SPAM_SCAM', label: t.ui.reasonSpam },
    { code: 'IMPERSONATION', label: t.ui.reasonImpersonation },
    { code: 'UNDERAGE', label: t.ui.reasonMinor },
    { code: 'OTHER', label: t.ui.reasonOther },
  ];
}

/**
 * An overlay for choosing why something is being reported.
 *
 * Absolutely positioned inside the screen rather than a `Modal`, matching the deck's
 * overlays: Reanimated's entering animations do not run inside a `Modal` on Android, and
 * a sibling that covers the screen behaves the same without that trap.
 *
 * Two steps, and only for one reason: every code but "Something else" is sent the moment
 * it is tapped, while OTHER asks for a line first — "other" with nothing written is not a
 * report anyone can act on, which is exactly what the backend refuses.
 */
export function ReportSheet({ subject, onPick, onCancel }: ReportSheetProps) {
  const t = useT();
  const [note, setNote] = useState('');
  const [needNote, setNeedNote] = useState(false);

  // The overlay covers the whole screen, safe area included — it has to, or the dimming
  // stops short of the edges. So the sheet inside it has to keep itself clear of the
  // navigation bar, which was overlapping Cancel on a three-button Android layout.
  const insets = useSafeAreaInsets();

  const pick = (code: ReasonCode) => {
    if (code === 'OTHER') {
      setNeedNote(true);
      return;
    }
    onPick({ reasonCode: code });
  };

  const submitNote = () => {
    const text = note.trim();
    if (!text) return;
    onPick({ reasonCode: 'OTHER', note: text });
  };

  return (
    <View className="absolute inset-0 justify-end bg-black/50">
      {/* Tapping the dimmed area dismisses, which is what everyone tries first. */}
      <Pressable
        className="flex-1"
        onPress={onCancel}
        accessibilityRole="button"
        accessibilityLabel={t.ui.reportCancelA11y}
      />
      <View className="p-4" style={{ paddingBottom: insets.bottom + 16 }}>
        <Card className="gap-2">
          <View className="gap-1 pb-1">
            <Text variant="bodyStrong">
              {subject === 'profile' ? t.ui.reportProfileTitle : t.ui.reportMessageTitle}
            </Text>
            <Text variant="caption">{needNote ? t.ui.reportNoteBlurb : t.ui.reportBlurb}</Text>
          </View>

          {needNote ? (
            <>
              <TextField
                value={note}
                onChangeText={setNote}
                placeholder={t.ui.reportNotePlaceholder}
                maxLength={300}
                multiline
                autoFocus
              />
              <Button
                label={t.ui.reportSend}
                variant="primary"
                size="md"
                disabled={note.trim().length === 0}
                onPress={submitNote}
              />
            </>
          ) : (
            reasonList(t).map(({ code, label }) => (
              <Button key={code} label={label} variant="ghost" size="md" onPress={() => pick(code)} />
            ))
          )}

          <Button label={t.common.cancel} variant="secondary" size="md" onPress={onCancel} />
        </Card>
      </View>
    </View>
  );
}
