import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useMemo } from 'react';
import { View } from 'react-native';
import { catalogueApi } from '../../src/api/catalogue';
import { useDraft } from '../../src/onboarding/draft';
import { SelectionCount } from '../../src/onboarding/SelectionCount';
import { StepHeader } from '../../src/onboarding/StepHeader';
import { CataloguePicker } from '../../src/pickers/CataloguePicker';
import { Button, Screen } from '../../src/ui';
import { MIN_GAMES } from '../../src/validation';

export default function Games() {
  const router = useRouter();
  const { gameIds, toggleGame } = useDraft();

  const games = useQuery({ queryKey: ['games'], queryFn: catalogueApi.games });

  const items = useMemo(
    () =>
      (games.data ?? []).map((game) => ({
        id: game.gameId,
        label: game.gameName,
        keywords: game.category ?? undefined,
      })),
    [games.data],
  );

  return (
    <Screen scroll>
      <StepHeader
        step={3}
        total={4}
        title="What do you play?"
        subtitle={`Pick at least ${MIN_GAMES}. This is most of what the recommendations are built from.`}
      />

      <CataloguePicker
        items={items}
        selected={gameIds}
        onToggle={toggleGame}
        isLoading={games.isPending}
        error={games.error}
        onRetry={() => games.refetch()}
        searchPlaceholder="Search games"
      />

      <View className="mt-auto gap-2 pt-10">
        <SelectionCount picked={gameIds.length} minimum={MIN_GAMES} />
        <Button
          label="Continue"
          disabled={gameIds.length < MIN_GAMES}
          onPress={() => router.push('/keywords')}
        />
        <Button label="Back" variant="ghost" onPress={() => router.back()} />
      </View>
    </Screen>
  );
}
