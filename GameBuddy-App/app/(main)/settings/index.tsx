import { useMutation, useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { openAdPrivacyOptions, useAdConsent } from '../../../src/ads/consent';
import { authApi } from '../../../src/api/auth';
import { profileApi } from '../../../src/api/catalogue';
import { LanguagePicker } from '../../../src/i18n/LanguagePicker';
import { Flag } from '../../../src/i18n/Flag';
import { LANG_NAMES } from '../../../src/i18n/languages';
import { useLangStore } from '../../../src/i18n/store';
import { useUpper } from '../../../src/i18n/case';
import { useT } from '../../../src/i18n/useT';
import { openPrivacy, openTerms } from '../../../src/legal';
import { useTutorial } from '../../../src/tutorial/store';
import { useSession } from '../../../src/session/store';
import { useScheme } from '../../../src/theme';
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
  const [pickingLanguage, setPickingLanguage] = useState(false);
  const lang = useLangStore((s) => s.lang);
  const t = useT();
  const upper = useUpper();
  const adPrivacyRequired = useAdConsent((s) => s.privacyOptionsRequired);

  // Built from the catalogue rather than imported as a module constant: a `const` array
  // of labels is evaluated once at import time, long before anybody has chosen a
  // language, so it would stay in whatever language it was written in.
  const themeOptions = [
    { value: 'system' as const, label: t.settings.themeSystem, hint: t.settings.themeSystemHint },
    { value: 'light' as const, label: t.settings.themeLight, hint: t.settings.themeLightHint },
    { value: 'dark' as const, label: t.settings.themeDark, hint: t.settings.themeDarkHint },
  ];
  const router = useRouter();
  const preference = useScheme((s) => s.preference);
  const setPreference = useScheme((s) => s.setPreference);
  const signOut = useSession((s) => s.signOut);

  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const startTutorial = useTutorial((s) => s.start);
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
        <Text variant="title">{t.settings.title}</Text>
      </View>

      <View className="gap-8">
        <View className="gap-3">
          <Text variant="overline">{upper(t.settings.sectionProfile)}</Text>

          <RowGroup>
            <LinkRow
              label={t.settings.avatar}
              hint={t.settings.avatarHint}
              href="/settings/avatar"
              position="first"
            />
            <LinkRow
              label={t.settings.birthDate}
              hint={t.settings.birthDateHint}
              href="/settings/age"
              position="middle"
            />
            <LinkRow
              label={t.settings.games}
              hint={t.settings.selected(me.data?.games.length ?? 0)}
              href="/settings/games"
              position="middle"
            />
            <LinkRow
              label={t.settings.platforms}
              // Optional-chained through `platforms` as well as `me.data`. The type says
              // the field is always there and the API agrees — but a profile cached before
              // this shipped has no such key, and reading `.length` off it is a render
              // error on the settings screen for anyone upgrading.
              hint={
                me.data?.platforms?.length
                  ? me.data.platforms.join(', ')
                  : t.settings.platformsUnset
              }
              href="/settings/platforms"
              position="middle"
            />
            <LinkRow
              label={t.settings.keywords}
              hint={t.settings.selected(me.data?.keywords.length ?? 0)}
              href="/settings/keywords"
              position="last"
            />
          </RowGroup>
        </View>

        <View className="gap-3">
          <Text variant="overline">{upper(t.settings.sectionAppearance)}</Text>

          {/* One group, hairlines between rows, rounded only at the ends. */}
          <RowGroup>
            {themeOptions.map((option, index) => (
              <SelectRow
                key={option.value}
                label={option.label}
                hint={option.hint}
                selected={preference === option.value}
                onPress={() => setPreference(option.value)}
                position={
                  index === 0 ? 'first' : index === themeOptions.length - 1 ? 'last' : 'middle'
                }
              />
            ))}
          </RowGroup>

          <Text variant="caption" className="px-1">
            {t.settings.themeNote}
          </Text>

          {/* Language sits under Appearance rather than in a section of its own: both
              are "how the app presents itself on this device", and both are remembered
              here rather than on the account. */}
          <RowGroup>
            <Pressable
              onPress={() => setPickingLanguage(true)}
              accessibilityRole="button"
              accessibilityLabel={t.language.current(LANG_NAMES[lang])}
              className="flex-row items-center gap-3 px-4 py-3.5 active:opacity-70"
            >
              <Flag lang={lang} size={28} />
              <View className="flex-1">
                <Text variant="body">{t.language.label}</Text>
              </View>
              <Text variant="caption">{LANG_NAMES[lang]}</Text>
            </Pressable>
          </RowGroup>
        </View>

        <LanguagePicker
          visible={pickingLanguage}
          onClose={() => setPickingLanguage(false)}
        />

        <View className="gap-3">
          <Text variant="overline">{upper(t.settings.sectionNotifications)}</Text>

          <RowGroup>
            <LinkRow
              label={t.settings.notificationsRow}
              hint={t.settings.notificationsHint}
              href="/settings/notifications"
              position="single"
            />
          </RowGroup>
        </View>

        <View className="gap-3">
          <Text variant="overline">{upper(t.settings.sectionAbout)}</Text>

          {/* Both stores want these reachable from inside the app, not only from the
              listing page — somebody who already installed it will never see that page
              again. */}
          <RowGroup>
            <LinkRow
              label={t.settings.tutorial}
              hint={t.settings.tutorialHint}
              onPress={() => {
                // Back to the deck first: the overlay navigates from wherever it starts,
                // and starting it from inside Settings would leave the settings stack
                // open underneath the whole tour.
                router.replace('/home');
                startTutorial();
              }}
              position="single"
            />
          </RowGroup>

          {/* The advert row is here only where Google says it has to be: UMP reports
              `privacyOptionsRequirementStatus` as required in the EEA, the UK and some US
              states, and nowhere else. Showing it everywhere would offer most of the world
              a form that opens onto nothing. It also moves Privacy off the bottom of the
              group, hence the computed position rather than a hardcoded "last". */}
          <RowGroup>
            <LinkRow
              label={t.settings.terms}
              hint={t.settings.termsHint}
              onPress={() => void openTerms()}
              position="first"
            />
            <LinkRow
              label={t.settings.privacy}
              hint={t.settings.privacyHint}
              onPress={() => void openPrivacy()}
              position={adPrivacyRequired ? 'middle' : 'last'}
            />
            {adPrivacyRequired && (
              <LinkRow
                label={t.settings.adPrivacy}
                hint={t.settings.adPrivacyHint}
                onPress={() => void openAdPrivacyOptions()}
                position="last"
              />
            )}
          </RowGroup>

          <Text variant="caption" className="px-1">
            Reports are reviewed within 24 hours. There is no tolerance for objectionable
            content or abusive users.
          </Text>

          {/* IGDB's terms require attribution wherever their data is used, and the game
              covers and descriptions in the catalogue come from them. */}
          <Text variant="caption" className="px-1">
            Game covers and descriptions from IGDB.com.
          </Text>
        </View>

        <View className="gap-3">
          <Text variant="overline">{upper(t.settings.sectionAccount)}</Text>

          <RowGroup>
            <LinkRow
              label={t.settings.password}
              hint={t.settings.passwordHint}
              href="/settings/password"
              position="first"
            />
            <LinkRow
              label={t.settings.blocked}
              hint={t.settings.blockedHint}
              href="/settings/blocked"
              position="last"
            />
          </RowGroup>

          <Button label={t.settings.signOut} variant="secondary" onPress={() => void signOut()} />

          {/* Deleting from inside the app is a Play Store requirement for anything
              that lets people create an account, not a nicety. */}
          {!confirmingDelete ? (
            <Button
              label={t.settings.deleteAccount}
              variant="danger"
              onPress={() => setConfirmingDelete(true)}
            />
          ) : (
            <Card className="gap-4 border border-danger/40">
              <View className="gap-1">
                <Text variant="bodyStrong" className="text-danger">
                  {t.settings.deleteTitle}
                </Text>
                <Text variant="caption">
                  {t.settings.deleteBody}
                </Text>
              </View>

              <TextField
                label={t.settings.password}
                value={password}
                onChangeText={setPassword}
                secure
                textContentType="password"
                autoComplete="current-password"
              />

              {deleteAccount.error && <ErrorNotice error={deleteAccount.error} />}

              <Button
                label={t.settings.deleteAccount}
                variant="danger"
                loading={deleteAccount.isPending}
                disabled={password.length === 0}
                onPress={() => deleteAccount.mutate()}
              />
              <Button
                label={t.settings.keepAccount}
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
        <Button label={t.common.back} variant="ghost" onPress={() => router.back()} />
      </View>
    </Screen>
  );
}
