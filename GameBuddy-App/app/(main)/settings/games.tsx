import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useMemo, useState } from 'react';
import { authApi } from '../../../src/api/auth';
import { catalogueApi, profileApi } from '../../../src/api/catalogue';
import { CataloguePicker } from '../../../src/pickers/CataloguePicker';
import { SelectionCount } from '../../../src/onboarding/SelectionCount';
import { EditScreen } from '../../../src/ui/EditScreen';
import { MIN_GAMES } from '../../../src/validation';

export default function EditGames() {
  const router = useRouter();
  const queryClient = useQueryClient();

  const me = useQuery({ queryKey: ['me'], queryFn: profileApi.me });
  const games = useQuery({ queryKey: ['games'], queryFn: catalogueApi.games });

  // Seeded from what is already on the profile, so this is an edit rather than a
  // fresh pick. `undefined` until the profile arrives, so the seed is not lost to a
  // first render with no data.
  const [selected, setSelected] = useState<string[] | undefined>(undefined);
  const current = selected ?? me.data?.games.map((g) => g.gameId) ?? [];

  const items = useMemo(
    () =>
      (games.data ?? []).map((game) => ({
        id: game.gameId,
        label: game.gameName,
        keywords: game.category ?? undefined,
      })),
    [games.data],
  );

  const save = useMutation({
    mutationFn: () => authApi.changeGames(current),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['me'] });
      // Changing games republishes a ProfileChangedEvent and re-clusters this gamer in
      // the recommender, so the cached feed describes the old profile.
      void queryClient.invalidateQueries({ queryKey: ['recommendations'] });
      router.back();
    },
  });

  return (
    <EditScreen
      title="Games you play"
      subtitle={`At least ${MIN_GAMES}. Changing these changes who you are shown.`}
      onSave={() => save.mutate()}
      saving={save.isPending}
      canSave={current.length >= MIN_GAMES}
      error={save.error}
    >
      <CataloguePicker
        items={items}
        selected={current}
        onToggle={(id) =>
          setSelected(
            current.includes(id) ? current.filter((x) => x !== id) : [...current, id],
          )
        }
        isLoading={games.isPending || me.isPending}
        error={games.error}
        onRetry={() => games.refetch()}
        searchPlaceholder="Search games"
      />

      <SelectionCount picked={current.length} minimum={MIN_GAMES} />
    </EditScreen>
  );
}
