import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Redirect, useRouter } from 'expo-router';
import { useMemo } from 'react';
import { View } from 'react-native';
import { authApi } from '../../src/api/auth';
import { catalogueApi } from '../../src/api/catalogue';
import { useT } from '../../src/i18n/useT';
import { useDraft } from '../../src/onboarding/draft';
import { SelectionCount } from '../../src/onboarding/SelectionCount';
import { StepHeader } from '../../src/onboarding/StepHeader';
import { CataloguePicker } from '../../src/pickers/CataloguePicker';
import { MIN_PLATFORMS } from '../../src/profile/platforms';
import { useSession } from '../../src/session/store';
import { Button, ErrorNotice, Screen } from '../../src/ui';
import { MIN_GAMES, MIN_KEYWORDS, parseBirthDate, toIsoDate } from '../../src/validation';

export default function Keywords() {
  const router = useRouter();
  const t = useT();
  const queryClient = useQueryClient();
  const draft = useDraft();
  const detailsCompleted = useSession((s) => s.detailsCompleted);

  const keywords = useQuery({ queryKey: ['keywords'], queryFn: catalogueApi.keywords });

  const items = useMemo(
    () =>
      (keywords.data ?? []).map((k) => ({
        id: k.id,
        label: k.keywordName,
        detail: k.description,
      })),
    [keywords.data],
  );

  const submit = useMutation({
    mutationFn: () =>
      authApi.setDetails({
        // The server derives the age from this and refuses anything under 18. Sending a
        // date rather than a number is the point: an age is an assertion, a date is a fact
        // the server can check.
        birthDate: toIsoDate(parseBirthDate(draft.birthDay, draft.birthMonth, draft.birthYear)!),
        country: draft.country,
        avatar: draft.avatarId!,
        // null means the question was never answered; the backend wants a string, and
        // an empty one is what "prefer not to say" already sends.
        gender: draft.gender ?? '',
        favoriteGames: draft.gameIds,
        platforms: draft.platformIds,
        keywords: draft.keywordIds,
      }),
    onSuccess: () => {
      detailsCompleted();
      draft.reset();
      // The profile the app is about to read is the one just written; anything cached
      // from before onboarding describes an empty account.
      void queryClient.invalidateQueries({ queryKey: ['me'] });
      router.replace('/home');
    },
  });

  // Reachable directly after a fast refresh, when the in-memory draft is empty. The
  // earlier answers are gone and cannot be recovered, so restart the run rather than
  // submit a half-filled profile the server will reject.
  const draftIntact =
    !!parseBirthDate(draft.birthDay, draft.birthMonth, draft.birthYear) &&
    !!draft.country &&
    !!draft.avatarId &&
    draft.gameIds.length >= MIN_GAMES &&
    draft.platformIds.length >= MIN_PLATFORMS;
  if (!draftIntact && !submit.isSuccess) return <Redirect href="/profile" />;

  return (
    // Not `scroll`: the picker is a FlatList and owns the scrolling.
    <Screen
      footer={
        <View className="gap-2">
          {/* Next to the button that failed, rather than below a forty-eight-row list
              where it needed a scroll to reach. */}
          {!!submit.error && <ErrorNotice error={submit.error} />}
          <SelectionCount picked={draft.keywordIds.length} minimum={MIN_KEYWORDS} />
          <Button
            label={t.common.finish}
            disabled={draft.keywordIds.length < MIN_KEYWORDS}
            loading={submit.isPending}
            onPress={() => submit.mutate()}
          />
          <Button label={t.common.back} variant="ghost" onPress={() => router.back()} />
        </View>
      }
    >
      <CataloguePicker
        header={
          <StepHeader
            step={6}
            total={6}
            title={t.onboarding.keywords.title}
            subtitle={t.onboarding.keywords.subtitle(MIN_KEYWORDS)}
          />
        }
        items={items}
        selected={draft.keywordIds}
        // Stable across renders even though `draft` is not: it is the same store action
        // every time, which is what keeps the rows memoised.
        onToggle={draft.toggleKeyword}
        isLoading={keywords.isPending}
        error={keywords.error}
        onRetry={() => keywords.refetch()}
      />
    </Screen>
  );
}
