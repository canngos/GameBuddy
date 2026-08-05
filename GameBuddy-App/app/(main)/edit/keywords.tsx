import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useMemo, useState } from 'react';
import { authApi } from '../../../src/api/auth';
import { catalogueApi, profileApi } from '../../../src/api/catalogue';
import { CataloguePicker } from '../../../src/pickers/CataloguePicker';
import { SelectionCount } from '../../../src/onboarding/SelectionCount';
import { EditScreen } from '../../../src/ui/EditScreen';
import { MIN_KEYWORDS } from '../../../src/validation';

export default function EditKeywords() {
  const router = useRouter();
  const queryClient = useQueryClient();

  const me = useQuery({ queryKey: ['me'], queryFn: profileApi.me });
  const keywords = useQuery({ queryKey: ['keywords'], queryFn: catalogueApi.keywords });

  const [selected, setSelected] = useState<string[] | undefined>(undefined);
  const current = selected ?? me.data?.keywords.map((k) => k.id) ?? [];

  const items = useMemo(
    () => (keywords.data ?? []).map((k) => ({ id: k.id, label: k.keywordName })),
    [keywords.data],
  );

  const save = useMutation({
    mutationFn: () => authApi.changeKeywords(current),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['me'] });
      // Same as games: this re-clusters the gamer, so the feed is stale.
      void queryClient.invalidateQueries({ queryKey: ['recommendations'] });
      router.back();
    },
  });

  return (
    <EditScreen
      title="How you play"
      subtitle={`At least ${MIN_KEYWORDS}. These are the habits and moods we match on.`}
      onSave={() => save.mutate()}
      saving={save.isPending}
      canSave={current.length >= MIN_KEYWORDS}
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
        isLoading={keywords.isPending || me.isPending}
        error={keywords.error}
        onRetry={() => keywords.refetch()}
      />

      <SelectionCount picked={current.length} minimum={MIN_KEYWORDS} />
    </EditScreen>
  );
}
