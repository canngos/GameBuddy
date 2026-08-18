import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { authApi } from '../../../src/api/auth';
import { profileApi } from '../../../src/api/catalogue';
import { useT } from '../../../src/i18n/useT';
import { SelectionCount } from '../../../src/onboarding/SelectionCount';
import { PlatformPicker } from '../../../src/profile/PlatformPicker';
import { MIN_PLATFORMS, PLATFORMS, type PlatformId } from '../../../src/profile/platforms';
import { EditScreen } from '../../../src/ui/EditScreen';

/**
 * Changing what you play on.
 *
 * The profile carries labels ("PlayStation") because that is what every screen renders,
 * but the picker and the server both work in enum names ("PLAYSTATION") — so the current
 * selection is mapped back through the label on load. One translation, in one place.
 */
export default function EditPlatforms() {
  const router = useRouter();
  const t = useT();
  const queryClient = useQueryClient();

  const me = useQuery({ queryKey: ['me'], queryFn: profileApi.me });

  const [selected, setSelected] = useState<PlatformId[] | undefined>(undefined);
  const saved = (me.data?.platforms ?? [])
    .map((label) => PLATFORMS.find((p) => p.label === label)?.id)
    .filter((id): id is PlatformId => !!id);
  const current = selected ?? saved;

  const save = useMutation({
    mutationFn: () => authApi.changePlatforms(current),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['me'] });
      // Unlike games and keywords, the ranking is untouched: platform is a filter the
      // feed applies, not a feature the model scores on. Only a deck that was narrowed by
      // platform could be stale, and that is the gamer's own filter, not this change.
      router.back();
    },
  });

  return (
    <EditScreen
      title={t.settings.platformsScreen.title}
      subtitle={t.settings.platformsScreen.subtitle}
      onSave={() => save.mutate()}
      saving={save.isPending}
      canSave={current.length >= MIN_PLATFORMS}
      error={save.error}
    >
      <PlatformPicker
        selected={current}
        onToggle={(id) =>
          setSelected(current.includes(id) ? current.filter((x) => x !== id) : [...current, id])
        }
      />

      <SelectionCount picked={current.length} minimum={MIN_PLATFORMS} />
    </EditScreen>
  );
}
