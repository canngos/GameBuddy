import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useMemo } from 'react';
import { View } from 'react-native';
import { catalogueApi } from '../../src/api/catalogue';
import { useT } from '../../src/i18n/useT';
import { useDraft } from '../../src/onboarding/draft';
import { SelectionCount } from '../../src/onboarding/SelectionCount';
import { StepHeader } from '../../src/onboarding/StepHeader';
import { CataloguePicker } from '../../src/pickers/CataloguePicker';
import { gameFilters, toGameItem } from '../../src/pickers/gameFilters';
import { Button, Screen } from '../../src/ui';
import { MIN_GAMES } from '../../src/validation';

export default function Games() {
  const router = useRouter();
  const t = useT();
  // Selectors rather than the whole store: `toggleGame` is then identity-stable, which is
  // what lets the picker's cards stay memoised and keeps a tap off the catalogue's render
  // path. It also stops an unrelated draft write from re-rendering this screen.
  const gameIds = useDraft((s) => s.gameIds);
  const toggleGame = useDraft((s) => s.toggleGame);

  const games = useQuery({ queryKey: ['games'], queryFn: catalogueApi.games });

  const items = useMemo(() => (games.data ?? []).map(toGameItem), [games.data]);
  const filters = useMemo(() => gameFilters(games.data ?? [], t), [games.data, t]);

  return (
    // Not `scroll`: the picker is a FlatList and owns the scrolling, so the step header
    // rides in its list header instead of in a ScrollView around it.
    <Screen
      footer={
        <View className="gap-2">
          <SelectionCount picked={gameIds.length} minimum={MIN_GAMES} />
          <Button
            label={t.common.continue}
            disabled={gameIds.length < MIN_GAMES}
            onPress={() => router.push('/platforms')}
          />
          <Button label={t.common.back} variant="ghost" onPress={() => router.back()} />
        </View>
      }
    >
      <CataloguePicker
        header={
          <StepHeader
            step={4}
            total={6}
            title={t.onboarding.games.title}
            subtitle={t.onboarding.games.subtitle(MIN_GAMES)}
          />
        }
        items={items}
        selected={gameIds}
        onToggle={toggleGame}
        isLoading={games.isPending}
        error={games.error}
        onRetry={() => games.refetch()}
        layout="grid"
        searchPlaceholder={t.onboarding.games.searchPlaceholder}
        filters={filters}
      />
    </Screen>
  );
}
