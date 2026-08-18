import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, type TextInput, View } from 'react-native';
import { billingApi } from '../../../src/api/billing';
import { catalogueApi } from '../../../src/api/catalogue';
import { ApiError, Code } from '../../../src/api/envelope';
import { lobbyApi } from '../../../src/api/lobby';
import type { Game, LobbyTone } from '../../../src/api/types';
import { useUpper } from '../../../src/i18n/case';
import { useT } from '../../../src/i18n/useT';
import { localZone, startsFull } from '../../../src/lobby/startsAt';
import { TONES, ToneChip } from '../../../src/lobby/ToneChip';
import { useThemeColors } from '../../../src/theme';
import {
  Avatar,
  BackHeader,
  Button,
  Card,
  ErrorNotice,
  Screen,
  Text,
  TextField,
} from '../../../src/ui';
import { cn } from '../../../src/ui/cn';

/**
 * "When?" as one-tap choices, plus a typed exact time for everything they do not cover.
 *
 * Three shortcuts only — the ones that are relative to this moment and so cannot be
 * wrong. Fixed-hour presets ("tonight 20:00") were dropped: they guess when a person
 * plays, they are wrong for anybody whose evening is not ours, and "Pick a time" now
 * answers that question properly.
 *
 * Typed fields rather than `@react-native-community/datetimepicker`, for the reasons
 * `BirthDateField` gives: it is a native dependency rendered differently on each
 * platform, and a spinner is a slow way to say 20:00.
 */
// Keys, not labels: the words come from the dictionary at render time, because a
// module-level label array is evaluated before anybody has chosen a language.
const WHEN_PRESETS: { key: 'presetNow' | 'presetIn1h' | 'presetIn3h'; startsAt: () => Date }[] = [
  { key: 'presetNow', startsAt: () => new Date() },
  { key: 'presetIn1h', startsAt: () => hoursFromNow(1) },
  { key: 'presetIn3h', startsAt: () => hoursFromNow(3) },
];

/** The index that means "typed below" rather than a preset. */
const CUSTOM_WHEN = WHEN_PRESETS.length;

/** What the backend accepts; refusing here saves a round trip and says something useful. */
const HORIZON_DAYS = 14;

function hoursFromNow(hours: number): Date {
  return new Date(Date.now() + hours * 3_600_000);
}

/**
 * Four numbers to an instant, in the entering gamer's own zone.
 *
 * **No year field.** Nothing may be planned more than two weeks out, so the year is
 * never ambiguous — it is this one, unless the date has already passed, which on 28
 * December means somebody typing 2 January means next year. Asking for a year to
 * resolve a case arithmetic can resolve is a field nobody should have to fill.
 *
 * Returns null for anything that is not a real moment: 31 February, hour 26, or a
 * half-filled row.
 */
function resolveTypedStart(day: string, month: string, hour: string, minute: string): Date | null {
  if (!day || !month || !hour || !minute) return null;

  const d = Number(day);
  const mo = Number(month);
  const h = Number(hour);
  const mi = Number(minute);
  if (mo < 1 || mo > 12 || d < 1 || d > 31 || h > 23 || mi > 59) return null;

  const now = new Date();
  for (const year of [now.getFullYear(), now.getFullYear() + 1]) {
    const candidate = new Date(year, mo - 1, d, h, mi, 0, 0);
    // Rejects dates the calendar rolled over — new Date(2026, 1, 31) is 3 March.
    if (candidate.getMonth() !== mo - 1 || candidate.getDate() !== d) continue;
    // The same fifteen minutes of slack the backend allows, so "20:00" typed at 20:02
    // is the evening being planned rather than an error.
    if (candidate.getTime() > now.getTime() - 15 * 60_000) return candidate;
  }
  return null;
}

const SEATS = [2, 3, 4, 5];

const digits = (value: string, max: number) => value.replace(/\D/g, '').slice(0, max);

/**
 * Opening a lobby — the Gold perk.
 *
 * The gate is the server's: SUBSCRIPTION_REQUIRED (159) routes to the paywall. The
 * entitlement check up top only *labels* the screen for free-tier users before they fill
 * a form the server would refuse; it never decides anything by itself.
 */
export default function CreateLobby() {
  const router = useRouter();
  const colors = useThemeColors();
  const t = useT();
  const upper = useUpper();
  const queryClient = useQueryClient();

  const subscription = useQuery({ queryKey: ['subscription'], queryFn: billingApi.subscription });
  const games = useQuery({ queryKey: ['games'], queryFn: catalogueApi.games });

  const [game, setGame] = useState<Game | null>(null);
  const [gameSearch, setGameSearch] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [requirements, setRequirements] = useState('');
  const [tone, setTone] = useState<LobbyTone>('CHILL');
  const [maxPlayers, setMaxPlayers] = useState(5);
  const [when, setWhen] = useState(0);
  const [typedDay, setTypedDay] = useState('');
  const [typedMonth, setTypedMonth] = useState('');
  const [typedHour, setTypedHour] = useState('');
  const [typedMinute, setTypedMinute] = useState('');

  const monthRef = useRef<TextInput>(null);
  const hourRef = useRef<TextInput>(null);
  const minuteRef = useRef<TextInput>(null);

  const typedStart = useMemo(
    () => resolveTypedStart(typedDay, typedMonth, typedHour, typedMinute),
    [typedDay, typedMonth, typedHour, typedMinute],
  );
  const typedTooFar =
    typedStart !== null && typedStart.getTime() > Date.now() + HORIZON_DAYS * 86_400_000;
  const startsAt = when === CUSTOM_WHEN ? typedStart : WHEN_PRESETS[when].startsAt();
  const zone = localZone();

  const matchingGames = useMemo(() => {
    const needle = gameSearch.trim().toLowerCase();
    if (!needle) return [];
    return (games.data ?? [])
      .filter((g) => g.gameName.toLowerCase().includes(needle))
      .slice(0, 6);
  }, [games.data, gameSearch]);

  const create = useMutation({
    mutationFn: () =>
      lobbyApi.create({
        gameId: game!.gameId,
        title: title.trim(),
        description: description.trim() || undefined,
        requirements: requirements.trim() || undefined,
        tone,
        maxPlayers,
        // An absolute instant. Whatever zone it was typed in, everybody else reads it
        // in theirs — see `src/lobby/startsAt.ts`.
        startsAt: (startsAt ?? new Date()).toISOString(),
      }),
    onSuccess: (detail) => {
      void queryClient.invalidateQueries({ queryKey: ['my-lobbies'] });
      void queryClient.invalidateQueries({ queryKey: ['lobbies'] });
      router.replace({
        pathname: '/lobby/[lobbyId]',
        params: { lobbyId: detail.lobby.id },
      } as never);
    },
    onError: (error) => {
      // The gate. Same pattern as the deck's filters: the server said Gold, the paywall
      // is where that is answered.
      if (error instanceof ApiError && error.is(Code.SUBSCRIPTION_REQUIRED)) {
        router.push('/gold' as never);
      }
    },
  });

  const ready = game !== null && title.trim().length > 0 && startsAt !== null && !typedTooFar;
  const notGold = subscription.data ? !subscription.data.canCreateLobby : false;

  return (
    <Screen edges={['top']}>
      <BackHeader title={t.lobby.create.title} />

      <ScrollView
        contentContainerClassName="gap-5 pb-8 pt-4"
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        {notGold && (
          <Card className="gap-2">
            <Text variant="bodyStrong">{t.lobby.create.goldTitle}</Text>
            <Text variant="caption">{t.lobby.create.goldBlurb}</Text>
            <Button
              label={t.lobby.create.getGold}
              size="md"
              onPress={() => router.push('/gold' as never)}
            />
          </Card>
        )}

        <View className="gap-2">
          <Text variant="overline">{upper(t.lobby.create.game)}</Text>
          {game ? (
            <Card className="flex-row items-center gap-3">
              <Avatar source={game.gameIcon} name={game.gameName} colorSeed={game.gameId} size={40} />
              <Text variant="bodyStrong" className="flex-1" numberOfLines={1}>
                {game.gameName}
              </Text>
              <Button
                label={t.lobby.create.change}
                variant="ghost"
                size="md"
                onPress={() => setGame(null)}
              />
            </Card>
          ) : (
            <View className="gap-2">
              <TextField
                value={gameSearch}
                onChangeText={setGameSearch}
                placeholder={t.lobby.create.searchGame}
                autoCorrect={false}
                autoCapitalize="none"
              />
              {games.isPending && gameSearch.trim().length > 0 && (
                <ActivityIndicator color={colors.primary} />
              )}
              {matchingGames.map((g) => (
                <Pressable
                  key={g.gameId}
                  onPress={() => {
                    setGame(g);
                    setGameSearch('');
                  }}
                  accessibilityRole="button"
                  className="flex-row items-center gap-3 rounded-2xl bg-raised p-3 active:opacity-70"
                >
                  <Avatar source={g.gameIcon} name={g.gameName} colorSeed={g.gameId} size={32} />
                  <Text variant="body" numberOfLines={1}>
                    {g.gameName}
                  </Text>
                </Pressable>
              ))}
            </View>
          )}
        </View>

        <View className="gap-2">
          <Text variant="overline">{upper(t.tabs.lobby)}</Text>
          <TextField
            value={title}
            onChangeText={setTitle}
            placeholder={t.lobby.create.titlePlaceholder}
            maxLength={80}
          />
          <TextField
            value={description}
            onChangeText={setDescription}
            placeholder={t.lobby.create.descriptionPlaceholder}
            multiline
            maxLength={500}
          />
          <TextField
            value={requirements}
            onChangeText={setRequirements}
            placeholder={t.lobby.create.requirementsPlaceholder}
            multiline
            maxLength={300}
          />
          <Text variant="caption">{t.lobby.create.approveHint}</Text>
        </View>

        <View className="gap-2">
          <Text variant="overline">{upper(t.lobby.create.tone)}</Text>
          <View className="flex-row flex-wrap gap-2">
            {TONES.map((value) => (
              <ToneChip
                key={value}
                tone={value}
                active={tone === value}
                onPress={() => setTone(value)}
              />
            ))}
          </View>
        </View>

        <View className="gap-2">
          <Text variant="overline">{upper(t.lobby.create.players)}</Text>
          <View className="flex-row gap-2">
            {SEATS.map((seats) => (
              <Pressable
                key={seats}
                onPress={() => setMaxPlayers(seats)}
                accessibilityRole="button"
                accessibilityState={{ selected: maxPlayers === seats }}
                className={cn(
                  'h-11 w-11 items-center justify-center rounded-full',
                  maxPlayers === seats ? 'bg-primary' : 'bg-raised',
                )}
              >
                <Text
                  variant="bodyStrong"
                  className={maxPlayers === seats ? 'text-white' : 'text-content'}
                >
                  {seats}
                </Text>
              </Pressable>
            ))}
          </View>
        </View>

        <View className="gap-2">
          <Text variant="overline">{upper(t.lobby.create.when)}</Text>
          <View className="flex-row flex-wrap gap-2">
            {WHEN_PRESETS.map((option, index) => (
              <Pressable
                key={option.key}
                onPress={() => setWhen(index)}
                accessibilityRole="button"
                accessibilityState={{ selected: when === index }}
                className={cn(
                  'rounded-full px-3.5 py-2',
                  when === index ? 'bg-primary' : 'bg-raised',
                )}
              >
                <Text variant="label" className={when === index ? 'text-white' : 'text-content'}>
                  {t.lobby.create[option.key]}
                </Text>
              </Pressable>
            ))}
            <Pressable
              onPress={() => setWhen(CUSTOM_WHEN)}
              accessibilityRole="button"
              accessibilityState={{ selected: when === CUSTOM_WHEN }}
              className={cn(
                'rounded-full px-3.5 py-2',
                when === CUSTOM_WHEN ? 'bg-primary' : 'bg-raised',
              )}
            >
              <Text
                variant="label"
                className={when === CUSTOM_WHEN ? 'text-white' : 'text-content'}
              >
                {t.lobby.create.pickATime}
              </Text>
            </Pressable>
          </View>

          {when === CUSTOM_WHEN && (
            <View className="gap-2 pt-1">
              {/* Day/month then hour/minute, focus advancing on its own — the same shape
                  as onboarding's date of birth, so the app asks for a date one way. */}
              <View className="flex-row gap-3">
                <View className="flex-1">
                  <TextField
                    label={t.onboarding.birthDate.day}
                    value={typedDay}
                    onChangeText={(text) => {
                      const next = digits(text, 2);
                      setTypedDay(next);
                      if (next.length === 2) monthRef.current?.focus();
                    }}
                    keyboardType="number-pad"
                    maxLength={2}
                    placeholder="24"
                  />
                </View>
                <View className="flex-1">
                  <TextField
                    ref={monthRef}
                    label={t.onboarding.birthDate.month}
                    value={typedMonth}
                    onChangeText={(text) => {
                      const next = digits(text, 2);
                      setTypedMonth(next);
                      if (next.length === 2) hourRef.current?.focus();
                    }}
                    keyboardType="number-pad"
                    maxLength={2}
                    placeholder="08"
                  />
                </View>
                <View className="flex-1">
                  <TextField
                    ref={hourRef}
                    label={t.lobby.create.hour}
                    value={typedHour}
                    onChangeText={(text) => {
                      const next = digits(text, 2);
                      setTypedHour(next);
                      if (next.length === 2) minuteRef.current?.focus();
                    }}
                    keyboardType="number-pad"
                    maxLength={2}
                    placeholder="20"
                  />
                </View>
                <View className="flex-1">
                  <TextField
                    ref={minuteRef}
                    label={t.lobby.create.minute}
                    value={typedMinute}
                    onChangeText={(text) => setTypedMinute(digits(text, 2))}
                    keyboardType="number-pad"
                    maxLength={2}
                    placeholder="00"
                  />
                </View>
              </View>

              {typedTooFar ? (
                <Text variant="caption" className="text-danger">
                  {t.lobby.create.tooFar}
                </Text>
              ) : typedStart ? (
                <Text variant="caption">{t.lobby.create.playsAt(startsFull(typedStart, t))}</Text>
              ) : (
                <Text variant="caption">{t.lobby.create.use24h}</Text>
              )}
            </View>
          )}

          <Text variant="caption">
            {zone ? t.lobby.create.zoneHint(zone) : t.lobby.create.zoneHintNoZone}
          </Text>
        </View>

        {!!create.error &&
          !(create.error instanceof ApiError && create.error.is(Code.SUBSCRIPTION_REQUIRED)) && (
            <ErrorNotice error={create.error} />
          )}

        <Button
          label={t.lobby.create.submit}
          loading={create.isPending}
          disabled={!ready}
          onPress={() => create.mutate()}
        />
      </ScrollView>
    </Screen>
  );
}
