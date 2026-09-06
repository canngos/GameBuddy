import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { Link2, Link2Off } from 'lucide-react-native';
import { useEffect } from 'react';
import { Linking, View } from 'react-native';
import { authApi } from '../../../src/api/auth';
import { profileApi } from '../../../src/api/catalogue';
import type { LinkedAccount, LinkedProvider } from '../../../src/api/types';
import { useT } from '../../../src/i18n/useT';
import { DiscordButton } from '../../../src/profile/DiscordButton';
import { PROVIDER_LABELS, PROVIDERS, ProviderMark } from '../../../src/profile/ProviderMark';
import {
  BackButton,
  Button,
  ErrorNotice,
  RowGroup,
  Screen,
  SelectRow,
  Text,
  showToast,
} from '../../../src/ui';

/**
 * Linking Discord.
 *
 * Unlike its neighbours this is not an `EditScreen`: there is nothing to save. Each row is
 * its own small transaction that takes effect the moment it is tapped, because "link",
 * "unlink" and "who can see this" are three separate decisions and a Save button over the
 * top of them would imply they were one.
 *
 * **This screen is also the landing pad.** Linking leaves the app for the system browser,
 * and the backend redirects to `gamebuddy://settings/linked?provider=…&status=…` when it is
 * done — which routes back here with those parameters. So the same file starts the flow and
 * reports how it ended, and the result arrives as route params rather than as a promise.
 */
export default function LinkedAccounts() {
  const t = useT();
  const queryClient = useQueryClient();
  const me = useQuery({ queryKey: ['me'], queryFn: profileApi.me });

  // Which providers this backend can actually offer. Deployment configuration, so it is
  // cached hard — it cannot change without a redeploy.
  const available = useQuery({
    queryKey: ['linkProviders'],
    queryFn: authApi.linkProviders,
    staleTime: Infinity,
  });

  // Fail open. While this is loading, or if it fails, show both rather than an empty screen:
  // a provider wrongly offered gives a clear error when tapped, whereas one wrongly hidden
  // looks like the feature does not exist. An empty list means nothing is configured at all,
  // which is also better shown as the old behaviour than as a blank screen.
  const visibleProviders = available.data?.providers?.length ? available.data.providers : PROVIDERS;

  useLinkResult();

  const start = useMutation({
    mutationFn: (provider: LinkedProvider) => authApi.linkStart(provider),
    // The system browser, not a WebView: a password typed into a browser this app controls
    // is a password it could have read, and the address bar is the only thing that tells
    // somebody they are really on discord.com.
    onSuccess: (data) => void Linking.openURL(data.authorizeUrl),
  });

  const unlink = useMutation({
    mutationFn: (provider: LinkedProvider) => authApi.unlink(provider),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['me'] }),
  });

  const setVisibility = useMutation({
    mutationFn: ({ provider, isPublic }: { provider: LinkedProvider; isPublic: boolean }) =>
      authApi.setLinkVisibility(provider, isPublic ? 'PUBLIC' : 'MATCHES'),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['me'] }),
  });

  const linked = (provider: LinkedProvider): LinkedAccount | undefined =>
    me.data?.linkedAccounts?.find((account) => account.provider === provider);

  return (
    <Screen scroll edges={['top', 'bottom']}>
      <View className="gap-1 pb-6 pt-8">
        <BackButton className="-ml-2" />
        <Text variant="title">{t.settings.linkedScreen.title}</Text>
        <Text variant="body" className="text-muted">
          {t.settings.linkedScreen.subtitle}
        </Text>
      </View>

      <View className="gap-8">
        {visibleProviders.map((provider) => (
          <ProviderSection
            key={provider}
            provider={provider}
            account={linked(provider)}
            // Scoped to the provider actually being worked on. A shared flag would put a
            // spinner on one provider's button because another was busy.
            busy={
              (start.isPending && start.variables === provider) ||
              (unlink.isPending && unlink.variables === provider)
            }
            onLink={() => start.mutate(provider)}
            onUnlink={() => unlink.mutate(provider)}
            onVisibility={(isPublic) => setVisibility.mutate({ provider, isPublic })}
            // Tapping the row that is already ticked is a natural "yes, that one" gesture, and
            // without this it costs an authenticated write plus a refetch of the whole profile
            // — games, keywords, badges, friends — to arrive back at what the cache already
            // held.
            currentlyPublic={linked(provider)?.visibility === 'PUBLIC'}
          />
        ))}

        {!!start.error && <ErrorNotice error={start.error} />}
        {!!unlink.error && <ErrorNotice error={unlink.error} />}
        {!!setVisibility.error && <ErrorNotice error={setVisibility.error} />}
      </View>
    </Screen>
  );
}

/**
 * One provider: what it is, whether it is linked, and what can be done about that.
 *
 * The visibility rows only exist once there is something to hide. Offering "who can see
 * this" above a Link button would be asking a question about a thing that does not exist.
 */
function ProviderSection({
  provider,
  account,
  busy,
  onLink,
  onUnlink,
  onVisibility,
  currentlyPublic,
}: {
  provider: LinkedProvider;
  account: LinkedAccount | undefined;
  busy: boolean;
  onLink: () => void;
  onUnlink: () => void;
  onVisibility: (isPublic: boolean) => void;
  currentlyPublic: boolean;
}) {
  const t = useT();
  const name = PROVIDER_LABELS[provider];
  const isPublic = account?.visibility === 'PUBLIC';

  return (
    <View className="gap-3">
      <View className="flex-row items-center gap-2 px-1">
        <ProviderMark provider={provider} size={18} />
        <Text variant="overline">{name}</Text>
      </View>

      <RowGroup>
        <View className="gap-1 px-4 py-3">
          <Text variant="bodyStrong">
            {account
              ? (account.handle ?? t.settings.linkedScreen.verifiedNoHandle)
              : t.settings.linkedScreen.notLinked(name)}
          </Text>
          <Text variant="caption">
            {account
              ? account.handle
                ? t.settings.linkedScreen.linkedAs(name)
                : t.settings.linkedScreen.handleWithheld
              : t.settings.linkedScreen.notLinkedHint(name)}
          </Text>
        </View>
      </RowGroup>

      {account && (
        <RowGroup>
          <SelectRow
            label={t.settings.linkedScreen.visibilityMatches}
            hint={t.settings.linkedScreen.visibilityMatchesHint}
            selected={!isPublic}
            onPress={() => currentlyPublic && onVisibility(false)}
            position="first"
          />
          <SelectRow
            label={t.settings.linkedScreen.visibilityPublic}
            hint={t.settings.linkedScreen.visibilityPublicHint}
            selected={isPublic}
            onPress={() => !currentlyPublic && onVisibility(true)}
            position="last"
          />
        </RowGroup>
      )}

      <View className="gap-2">
        {/* The provider's own button, not ours. Discord specifies how "sign in with us" is
            presented, and a generic app-styled button here would be GameBuddy's button
            wearing their name — see DiscordButton for the colour and contrast note. */}
        {!account && provider === 'DISCORD' && (
          <DiscordButton
            label={t.settings.linkedScreen.signInWithDiscord}
            disabled={busy}
            onPress={onLink}
          />
        )}

        {account && (
          <Button
            label={t.settings.linkedScreen.unlink(name)}
            variant="ghost"
            disabled={busy}
            onPress={onUnlink}
          />
        )}
      </View>
    </View>
  );
}

/**
 * Reads the outcome the backend redirected back with, and says so once.
 *
 * The guard matters more than it looks. This screen re-renders on every mutation and the
 * route params stay put for as long as the screen is mounted, so without it a single
 * successful link would toast again on every subsequent tap — and it must key on the
 * params rather than on a plain "already ran" flag, or linking a second provider straight
 * after Discord would report nothing at all.
 */
function useLinkResult() {
  const t = useT();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { provider, status } = useLocalSearchParams<{ provider?: string; status?: string }>();

  useEffect(() => {
    if (!status) {
      return;
    }

    // A deliberate change of mind, not a failure. Backing out of a consent screen should
    // leave the app exactly as the user left it — an error toast for a button they chose to
    // press reads as the app being broken.
    if (status === 'cancelled') {
      router.setParams({ provider: undefined, status: undefined });
      return;
    }

    if (status === 'ok') {
      // The profile is what changed, and this screen reads it — so the refetch is the
      // update, not a nicety.
      void queryClient.invalidateQueries({ queryKey: ['me'] });
      showToast({
        id: `link:${provider}:ok`,
        title: t.settings.linkedScreen.linkedToast,
        icon: Link2,
        tone: 'success',
      });
    } else {
      showToast({
        id: `link:${provider}:${status}`,
        title:
          status === 'already_linked'
            ? t.settings.linkedScreen.alreadyLinked
            : t.settings.linkedScreen.failed,
        body: status === 'already_linked' ? t.settings.linkedScreen.alreadyLinkedBody : undefined,
        icon: Link2Off,
        tone: 'danger',
      });
    }

    // Consume the params rather than remembering that we handled them.
    //
    // Something has to stop this firing again on every re-render, and a "already reported"
    // ref keyed on the values does not: this screen is never unmounted while the browser is
    // away, so linking Discord, unlinking, and linking it again arrives with byte-identical
    // params. The effect would not re-run at all — no toast, and worse, no refetch, so the
    // screen would go on showing the account as unlinked after a link that worked.
    //
    // Clearing them makes the next arrival a real change, and the guard above makes the
    // extra pass free.
    router.setParams({ provider: undefined, status: undefined });
  }, [provider, status, queryClient, router, t]);
}
