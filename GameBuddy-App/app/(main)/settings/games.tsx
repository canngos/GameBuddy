import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { authApi } from '../../../src/api/auth';
import { catalogueApi, profileApi } from '../../../src/api/catalogue';
import { useT } from '../../../src/i18n/useT';
import { CataloguePicker } from '../../../src/pickers/CataloguePicker';
import { gameFilters, toGameItem } from '../../../src/pickers/gameFilters';
import { SelectionCount } from '../../../src/onboarding/SelectionCount';
import { EditScreen, EditTitle } from '../../../src/ui/EditScreen';
import { MIN_GAMES } from '../../../src/validation';

export default function EditGames() {
  const router = useRouter();
  const t = useT();
  const queryClient = useQueryClient();

  const me = useQuery({ queryKey: ['me'], queryFn: profileApi.me });
  const games = useQuery({ queryKey: ['games'], queryFn: catalogueApi.games });

  // Seeded from what is already on the profile, so this is an edit rather than a fresh
  // pick. Seeded once, in an effect, rather than through a `selected ?? profile` fallback
  // read: the fallback form forced the toggle handler to close over `current`, which gave
  // it a new identity every render and put all three hundred cards back on the render
  // path for one tap. The `isLoading` gate below covers the frame before the seed lands.
  const [current, setCurrent] = useState<string[]>([]);
  const seeded = useRef(false);
  useEffect(() => {
    if (me.data && !seeded.current) {
      seeded.current = true;
      setCurrent(me.data.games.map((g) => g.gameId));
    }
  }, [me.data]);

  const onToggle = useCallback(
    (id: string) =>
      setCurrent((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id])),
    [],
  );

  const items = useMemo(() => (games.data ?? []).map(toGameItem), [games.data]);
  const filters = useMemo(() => gameFilters(games.data ?? [], t), [games.data, t]);

  const save = useMutation({
    mutationFn: () => authApi.changeGames(current),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['me'] });
      // The backend now ranks this gamer from their live profile until the model is
      // retrained on it, so the cached feed describes the games they just replaced.
      void queryClient.invalidateQueries({ queryKey: ['recommendations'] });
      router.back();
    },
  });

  // Named once and used twice: EditScreen still takes them, and with `scroll={false}` the
  // title block is drawn by the picker's list header instead of by the screen.
  const title = t.settings.gamesScreen.title;
  const subtitle = t.settings.gamesScreen.subtitle(MIN_GAMES);

  return (
    <EditScreen
      title={title}
      subtitle={subtitle}
      onSave={() => save.mutate()}
      saving={save.isPending}
      canSave={current.length >= MIN_GAMES}
      error={save.error}
      // The picker is a FlatList and scrolls itself.
      scroll={false}
      footerNote={<SelectionCount picked={current.length} minimum={MIN_GAMES} />}
    >
      <CataloguePicker
        header={<EditTitle title={title} subtitle={subtitle} />}
        items={items}
        selected={current}
        onToggle={onToggle}
        isLoading={games.isPending || me.isPending}
        error={games.error}
        onRetry={() => games.refetch()}
        layout="grid"
        searchPlaceholder={t.onboarding.games.searchPlaceholder}
        filters={filters}
      />
    </EditScreen>
  );
}
