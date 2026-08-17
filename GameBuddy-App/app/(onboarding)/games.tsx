import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useMemo } from 'react';
import { View } from 'react-native';
import { catalogueApi } from '../../src/api/catalogue';
import { useDraft } from '../../src/onboarding/draft';
import { SelectionCount } from '../../src/onboarding/SelectionCount';
import { StepHeader } from '../../src/onboarding/StepHeader';
import { CataloguePicker } from '../../src/pickers/CataloguePicker';
import { gameFilters, toGameItem } from '../../src/pickers/gameFilters';
import { Button, Screen } from '../../src/ui';
import { MIN_GAMES } from '../../src/validation';

export default function Games() {
  const router = useRouter();
  // Selectors rather than the whole store: `toggleGame` is then identity-stable, which is
  // what lets the picker's cards stay memoised and keeps a tap off the catalogue's render
  // path. It also stops an unrelated draft write from re-rendering this screen.
  const gameIds = useDraft((s) => s.gameIds);
  const toggleGame = useDraft((s) => s.toggleGame);

  const games = useQuery({ queryKey: ['games'], queryFn: catalogueApi.games });

  const items = useMemo(() => (games.data ?? []).map(toGameItem), [games.data]);
  const filters = useMemo(() => gameFilters(games.data ?? []), [games.data]);

  return (
    // Not `scroll`: the picker is a FlatList and owns the scrolling, so the step header
    // rides in its list header instead of in a ScrollView around it.
    <Screen
      footer={
        <View className="gap-2">
          <SelectionCount picked={gameIds.length} minimum={MIN_GAMES} />
          <Button
            label="Continue"
            disabled={gameIds.length < MIN_GAMES}
            onPress={() => router.push('/platforms')}
          />
          <Button label="Back" variant="ghost" onPress={() => router.back()} />
        </View>
      }
    >
      <CataloguePicker
        header={
          <StepHeader
            step={4}
            total={6}
            title="What do you play?"
            subtitle={`Pick at least ${MIN_GAMES}. This is most of what the recommendations are built from.`}
          />
        }
        items={items}
        selected={gameIds}
        onToggle={toggleGame}
        isLoading={games.isPending}
        error={games.error}
        onRetry={() => games.refetch()}
        layout="grid"
        searchPlaceholder="Search games"
        filters={filters}
      />
    </Screen>
  );
}
