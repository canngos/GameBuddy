import { Pressable, View } from 'react-native';
import { useT } from '../i18n/useT';
import { Button } from './Button';
import { Card } from './Card';
import { Text } from './Text';

export type ConfirmRequest = {
  title: string;
  body: string;
  /** The word on the button that goes through with it. */
  confirmLabel: string;
  /** Draws the confirm button in danger red. On for anything that removes or blocks. */
  destructive?: boolean;
  onConfirm: () => void;
};

/**
 * "Are you sure?", in the app's own chrome.
 *
 * **Replaces `Alert.alert` for in-app confirmations.** The native dialog is drawn by
 * Android, not by us: square corners, its own type scale, its own teal system accent, and
 * buttons that shout in capitals. Next to this app's rounded cards and neon palette it read
 * as something from another decade — and worse, as something that was not part of the app at
 * all, which is not what you want on the screen where somebody is deciding whether to block
 * a person.
 *
 * Centred rather than a bottom sheet, and that is the distinction from `ActionSheet`: a
 * sheet offers a list of things you might do, and this asks one question that has to be
 * answered before anything else happens. Centring it is what makes it read as a stop.
 *
 * Absolutely positioned rather than a `Modal`, like every other overlay here — Reanimated's
 * entering animations do not run inside a `Modal` on Android. Mount it as the last child of
 * a `Screen`, never inside a ScrollView.
 */
export function ConfirmDialog({
  request,
  busy = false,
  onCancel,
}: {
  request: ConfirmRequest;
  /** Keeps the dialog up with its button spinning while the action is in flight. */
  busy?: boolean;
  onCancel: () => void;
}) {
  const t = useT();

  return (
    <View className="absolute inset-0 items-center justify-center bg-black/50 px-6">
      {/* Tapping outside cancels, which is what everyone tries first. Disabled while the
          action runs, so a stray tap cannot dismiss a dialog mid-request. */}
      <Pressable
        className="absolute inset-0"
        onPress={busy ? undefined : onCancel}
        disabled={busy}
        accessibilityRole="button"
        accessibilityLabel={t.common.cancel}
      />

      <Card className="w-full gap-3">
        <Text variant="heading">{request.title}</Text>
        <Text variant="body" className="text-muted">
          {request.body}
        </Text>

        {/* Confirm on the right, where the primary action sits everywhere else in the app.
            Both full-width halves rather than text links: this is the app's button
            language, and a destructive choice deserves a real target rather than a word. */}
        <View className="flex-row gap-2 pt-1">
          <View className="flex-1">
            <Button
              label={t.common.cancel}
              variant="secondary"
              size="md"
              disabled={busy}
              onPress={onCancel}
            />
          </View>
          <View className="flex-1">
            <Button
              label={request.confirmLabel}
              variant={request.destructive ? 'danger' : 'primary'}
              size="md"
              loading={busy}
              onPress={request.onConfirm}
            />
          </View>
        </View>
      </Card>
    </View>
  );
}
