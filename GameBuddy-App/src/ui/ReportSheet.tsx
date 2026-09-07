import { Pressable, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useT } from '../i18n/useT';
import { Button } from './Button';
import { Card } from './Card';
import { Text } from './Text';

/**
 * The reasons offered for a report.
 *
 * A fixed list rather than a free-text box. The backend stores whatever string it is
 * given and a moderator has to read it, so a short vocabulary is both faster to tap and
 * sortable in the queue; anything genuinely unusual is what "Something else" is for.
 *
 * The same five cover a post, a comment and a person — "someone may be a minor" is the
 * one that matters most and it was always about the account rather than the text.
 */
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
  onPick: (reason: string) => void;
  onCancel: () => void;
};

/**
 * An overlay for choosing why something is being reported.
 *
 * Absolutely positioned inside the screen rather than a `Modal`, matching the deck's
 * overlays: Reanimated's entering animations do not run inside a `Modal` on Android, and
 * a sibling that covers the screen behaves the same without that trap.
 */
export function ReportSheet({ subject, onPick, onCancel }: ReportSheetProps) {
  const t = useT();

  const reasons = [
    t.ui.reasonHarassment,
    t.ui.reasonSexual,
    t.ui.reasonSpam,
    t.ui.reasonMinor,
    t.ui.reasonOther,
  ];
  // The overlay covers the whole screen, safe area included — it has to, or the dimming
  // stops short of the edges. So the sheet inside it has to keep itself clear of the
  // navigation bar, which was overlapping Cancel on a three-button Android layout.
  const insets = useSafeAreaInsets();

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
            <Text variant="caption">
              {t.ui.reportBlurb}
            </Text>
          </View>

          {reasons.map((reason) => (
            <Button
              key={reason}
              label={reason}
              variant="ghost"
              size="md"
              onPress={() => onPick(reason)}
            />
          ))}

          <Button label={t.common.cancel} variant="secondary" size="md" onPress={onCancel} />
        </Card>
      </View>
    </View>
  );
}
