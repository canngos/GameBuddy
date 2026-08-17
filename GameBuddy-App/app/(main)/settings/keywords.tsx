import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { authApi } from '../../../src/api/auth';
import { catalogueApi, profileApi } from '../../../src/api/catalogue';
import { CataloguePicker } from '../../../src/pickers/CataloguePicker';
import { SelectionCount } from '../../../src/onboarding/SelectionCount';
import { EditScreen, EditTitle } from '../../../src/ui/EditScreen';
import { MIN_KEYWORDS } from '../../../src/validation';

export default function EditKeywords() {
  const router = useRouter();
  const queryClient = useQueryClient();

  const me = useQuery({ queryKey: ['me'], queryFn: profileApi.me });
  const keywords = useQuery({ queryKey: ['keywords'], queryFn: catalogueApi.keywords });

  // Seeded once rather than read through a `selected ?? profile` fallback — see the note
  // on the games screen: the fallback form is what made the toggle handler unstable.
  const [current, setCurrent] = useState<string[]>([]);
  const seeded = useRef(false);
  useEffect(() => {
    if (me.data && !seeded.current) {
      seeded.current = true;
      setCurrent(me.data.keywords.map((k) => k.id));
    }
  }, [me.data]);

  const onToggle = useCallback(
    (id: string) =>
      setCurrent((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id])),
    [],
  );

  const items = useMemo(
    () =>
      (keywords.data ?? []).map((k) => ({
        id: k.id,
        label: k.keywordName,
        detail: k.description,
      })),
    [keywords.data],
  );

  const save = useMutation({
    mutationFn: () => authApi.changeKeywords(current),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['me'] });
      // Same as games: the ranking changes behind this, so the feed is stale.
      void queryClient.invalidateQueries({ queryKey: ['recommendations'] });
      router.back();
    },
  });

  const title = 'How you play';
  const subtitle = `At least ${MIN_KEYWORDS}. These are the habits and moods we match on.`;

  return (
    <EditScreen
      title={title}
      subtitle={subtitle}
      onSave={() => save.mutate()}
      saving={save.isPending}
      canSave={current.length >= MIN_KEYWORDS}
      error={save.error}
      // The picker is a FlatList and scrolls itself.
      scroll={false}
      footerNote={<SelectionCount picked={current.length} minimum={MIN_KEYWORDS} />}
    >
      <CataloguePicker
        header={<EditTitle title={title} subtitle={subtitle} />}
        items={items}
        selected={current}
        onToggle={onToggle}
        isLoading={keywords.isPending || me.isPending}
        error={keywords.error}
        onRetry={() => keywords.refetch()}
      />
    </EditScreen>
  );
}
