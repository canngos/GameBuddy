import { useMutation, useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';
import { authApi } from '../../../src/api/auth';
import { profileApi } from '../../../src/api/catalogue';
import { useSession } from '../../../src/session/store';
import { THEME_OPTIONS, useScheme } from '../../../src/theme';
import {
  Button,
  Card,
  ErrorNotice,
  LinkRow,
  RowGroup,
  Screen,
  SelectRow,
  Text,
  TextField,
} from '../../../src/ui';

/**
 * Everything that changes something, in one place.
 *
 * Profile editing lives here rather than on the Profile tab. A profile is something you
 * look at — yours and, later, other people's — and a column of "change this" rows down
 * the middle of it crowds out the part worth looking at. Settings is where people go
 * when they want to change something, so that is where the rows are.
 */
export default function Settings() {
  const router = useRouter();
  const preference = useScheme((s) => s.preference);
  const setPreference = useScheme((s) => s.setPreference);
  const signOut = useSession((s) => s.signOut);

  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [password, setPassword] = useState('');

  // Only for the row hints ("3 selected"). Already cached by the Profile tab, so this
  // is a read from the cache rather than a second request in the common case.
  const me = useQuery({ queryKey: ['me'], queryFn: profileApi.me });

  const deleteAccount = useMutation({
    mutationFn: () => authApi.deleteAccount(password),
    // The account is gone; there is nothing left to be signed in to.
    onSuccess: () => void signOut(),
  });

  return (
    <Screen scroll edges={['top', 'bottom']}>
      <View className="gap-1 pb-8 pt-8">
        <Text variant="title">Settings</Text>
      </View>

      <View className="gap-8">
        <View className="gap-3">
          <Text variant="overline">PROFILE</Text>

          <RowGroup>
            <LinkRow
              label="Avatar"
              hint="Change your picture"
              href="/settings/avatar"
              position="first"
            />
            <LinkRow
              label="Age"
              hint="Decides who you are shown"
              href="/settings/age"
              position="middle"
            />
            <LinkRow
              label="Games"
              hint={`${me.data?.games.length ?? 0} selected`}
              href="/settings/games"
              position="middle"
            />
            <LinkRow
              label="Keywords"
              hint={`${me.data?.keywords.length ?? 0} selected`}
              href="/settings/keywords"
              position="last"
            />
          </RowGroup>
        </View>

        <View className="gap-3">
          <Text variant="overline">APPEARANCE</Text>

          {/* One group, hairlines between rows, rounded only at the ends. */}
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

          <Text variant="caption" className="px-1">
            The theme applies immediately and is remembered on this device.
          </Text>
        </View>

        <View className="gap-3">
          <Text variant="overline">NOTIFICATIONS</Text>

          <RowGroup>
            <LinkRow
              label="What we send you"
              hint="Messages, matches, communities, reminders"
              href="/settings/notifications"
              position="single"
            />
          </RowGroup>
        </View>

        <View className="gap-3">
          <Text variant="overline">ACCOUNT</Text>

          <RowGroup>
            <LinkRow
              label="Password"
              hint="Signs you out everywhere"
              href="/settings/password"
              position="first"
            />
            <LinkRow
              label="Blocked"
              hint="Who you have blocked"
              href="/settings/blocked"
              position="last"
            />
          </RowGroup>

          <Button label="Sign out" variant="secondary" onPress={() => void signOut()} />

          {/* Deleting from inside the app is a Play Store requirement for anything
              that lets people create an account, not a nicety. */}
          {!confirmingDelete ? (
            <Button
              label="Delete my account"
              variant="danger"
              onPress={() => setConfirmingDelete(true)}
            />
          ) : (
            <Card className="gap-4 border border-danger/40">
              <View className="gap-1">
                <Text variant="bodyStrong" className="text-danger">
                  This cannot be undone
                </Text>
                <Text variant="caption">
                  Your profile, matches and messages are removed. Enter your password to confirm
                  — a stolen phone should not be enough to do this.
                </Text>
              </View>

              <TextField
                label="Password"
                value={password}
                onChangeText={setPassword}
                secure
                textContentType="password"
                autoComplete="current-password"
              />

              {deleteAccount.error && <ErrorNotice error={deleteAccount.error} />}

              <Button
                label="Delete my account"
                variant="danger"
                loading={deleteAccount.isPending}
                disabled={password.length === 0}
                onPress={() => deleteAccount.mutate()}
              />
              <Button
                label="Keep my account"
                variant="ghost"
                onPress={() => {
                  setConfirmingDelete(false);
                  setPassword('');
                }}
              />
            </Card>
          )}
        </View>
      </View>

      <View className="mt-auto pt-10">
        <Button label="Back" variant="ghost" onPress={() => router.back()} />
      </View>
    </Screen>
  );
}
