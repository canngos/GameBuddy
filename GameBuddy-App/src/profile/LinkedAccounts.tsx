import { View } from 'react-native';
import type { LinkedAccount } from '../api/types';
import { useT } from '../i18n/useT';
import { Text } from '../ui';
import { PROVIDER_LABELS, ProviderMark } from './ProviderMark';

/**
 * Where to find this person outside GameBuddy, as a row of pills.
 *
 * **No heading.** It sits directly under the username, above the age and country line, on
 * both profile screens and on the deck card — and at that size a "Find them on" label above
 * two short pills was more chrome than content. A Discord logo next to a name needs no
 * introduction, which is exactly why the mark is drawn rather than the service named.
 *
 * Draws nothing when the list is empty, which is most people. The server has already removed
 * anything this viewer may not see, so "empty" also covers "linked, but not for you" — and
 * the two are deliberately indistinguishable from outside.
 */
export function LinkedAccounts({
  accounts,
  /**
   * For the deck card, whose ground is a per-user gradient rather than a surface token.
   * Nothing there can be read from the palette, so the pills borrow the same translucent
   * white the card's other overlays use.
   */
  onCard = false,
}: {
  accounts: LinkedAccount[] | undefined;
  onCard?: boolean;
}) {
  const t = useT();

  if (!accounts?.length) {
    return null;
  }

  return (
    <View className={`flex-row flex-wrap gap-2 ${onCard ? 'justify-center' : ''}`}>
      {accounts.map((account) => {
        const name = PROVIDER_LABELS[account.provider];
        const shown = account.handle ?? t.profile.linkedVerified(name);

        return (
          <View
            key={account.provider}
            className={`flex-row items-center gap-1.5 rounded-full px-2.5 py-1 ${
              onCard ? 'bg-white/20' : 'bg-raised'
            }`}
            // Read as one thing. Without this a screen reader announces the logo and the
            // handle as two unrelated items, and the logo is the half that says which
            // service the name belongs to.
            accessible
            accessibilityLabel={account.handle ? `${name}: ${account.handle}` : shown}
          >
            <ProviderMark provider={account.provider} size={12} color={onCard ? '#FFFFFF' : undefined} />
            <Text
              variant="caption"
              numberOfLines={1}
              className={onCard ? 'text-white' : account.handle ? 'text-content' : 'text-muted'}
            >
              {/* No handle means it did not survive screening. The pill still stands: what
                  was verified is the account, and that is still true — but a masked name
                  would be a name this person is not called. */}
              {shown}
            </Text>
          </View>
        );
      })}
    </View>
  );
}
