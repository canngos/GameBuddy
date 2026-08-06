import { View } from 'react-native';
import { useSession } from '../../src/session/store';
import { THEME_OPTIONS, useScheme } from '../../src/theme';
import { Button, LinkRow, RowGroup, Screen, SelectRow, Text } from '../../src/ui';

/**
 * The console's own settings.
 *
 * A tab rather than a link in a header. Sign out was a muted word in the corner of the
 * Overview screen, which is the sort of thing that is obvious to whoever wrote it and
 * invisible to everyone else — and it was on one tab of four, so from the other three
 * there was no way out of the app at all.
 *
 * Deliberately short. The moderator has no profile to edit, no avatar, no games and no
 * notification preferences worth setting, so this is the three things that genuinely
 * apply: how it looks, the password, and the way out.
 */
export default function ConsoleSettings() {
  const signOut = useSession((s) => s.signOut);
  const preference = useScheme((s) => s.preference);
  const setPreference = useScheme((s) => s.setPreference);

  return (
    <Screen scroll edges={['top']}>
      <View className="pb-3 pt-2">
        <Text variant="overline">Console</Text>
        <Text variant="title">Settings</Text>
      </View>

      <Text variant="overline" className="mb-2 mt-4">
        Appearance
      </Text>
      <RowGroup>
        {THEME_OPTIONS.map((option, index) => (
          <SelectRow
            key={option.value}
            label={option.label}
            hint={option.hint}
            selected={preference === option.value}
            onPress={() => setPreference(option.value)}
            position={
              index === 0 ? 'first' : index === THEME_OPTIONS.length - 1 ? 'last' : 'middle'
            }
          />
        ))}
      </RowGroup>

      <Text variant="overline" className="mb-2 mt-8">
        Account
      </Text>
      <RowGroup>
        <LinkRow label="Password" hint="Signs you out everywhere" href="/password" />
      </RowGroup>

      <Text variant="caption" className="mt-3">
        This is the only way to change the moderator password. Setting a different value in
        the environment does nothing once the account exists — see ModeratorBootstrap.
      </Text>

      <View className="mt-8">
        <Button label="Sign out" variant="danger" onPress={() => void signOut()} />
      </View>
    </Screen>
  );
}
