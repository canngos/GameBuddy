import { TriangleAlert } from 'lucide-react-native';
import { View } from 'react-native';
import { ApiError } from '../api/envelope';
import { useErrorText } from '../i18n/useErrorText';
import { useT } from '../i18n/useT';
import { Button } from './Button';
import { Icon } from './Icon';
import { Text } from './Text';

type ErrorNoticeProps = {
  error: unknown;
  onRetry?: () => void;
};

/**
 * A form-level failure banner.
 *
 * The message is resolved by `useErrorText`, which translates the backend's numeric
 * code into the reader's language and falls back to the server's own English sentence
 * for codes it does not know — see that file for why the translation happens here
 * rather than behind an `Accept-Language` header. Anything that is not an ApiError is
 * something we did not anticipate and gets a generic line instead of a stack trace.
 */
export function ErrorNotice({ error, onRetry }: ErrorNoticeProps) {
  const errorText = useErrorText();
  const t = useT();

  if (!error) return null;

  const message = errorText(error);

  // Offering "Try again" on a rejected password would be misleading — retrying the
  // identical request cannot help. Only transient failures get the button.
  const retryable = onRetry && (!(error instanceof ApiError) || error.isTransient);

  return (
    <View
      className="gap-3 rounded-card border border-danger/40 bg-danger/10 p-4"
      accessibilityRole="alert"
    >
      {/* The glyph is decorative: `accessibilityRole="alert"` above already tells a screen
          reader what this is, and a second announcement of "warning" before the message
          would just be noise. */}
      <View className="flex-row gap-3">
        {/* Nudged down by a wrapper rather than a prop: an 18px glyph aligned to the top of
            22px leading floats above the line it belongs to. `Icon` takes no className by
            design — it forwards a fixed prop set to Lucide, and giving it a style passthrough
            would reopen every question the wrapper exists to close. */}
        <View className="mt-0.5">
          <Icon as={TriangleAlert} size={18} tone="danger" />
        </View>
        <Text variant="bodyStrong" className="flex-1 text-danger">
          {message}
        </Text>
      </View>
      {retryable && (
        <Button label={t.common.retry} variant="secondary" size="md" onPress={onRetry} />
      )}
    </View>
  );
}

export function messageOf(error: unknown): string | null {
  if (!error) return null;
  return error instanceof ApiError ? error.message : 'Something went wrong.';
}
