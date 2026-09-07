import { useQueryClient } from '@tanstack/react-query';
import { Tabs } from 'expo-router';
import { useEffect } from 'react';
import { Platform } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { identify } from '../../src/billing/purchases';
import { storePricesQuery } from '../../src/billing/useStorePrices';
import { ChatSocketProvider } from '../../src/chat/ChatSocketProvider';
import { CoachmarkHost } from '../../src/hints/CoachmarkHost';
import { useHints } from '../../src/hints/store';
import { MatchCelebration } from '../../src/match/MatchCelebration';
import { NotificationPrimer } from '../../src/notifications/NotificationPrimer';
import { useInAppNotifications } from '../../src/notifications/useInAppNotifications';
import { useMatchNotifications } from '../../src/notifications/useMatchNotifications';
import { useNotificationRouting } from '../../src/notifications/useNotificationRouting';
import { usePushRegistration } from '../../src/notifications/usePushRegistration';
import { useT } from '../../src/i18n/useT';
import { RouteGuard } from '../../src/session/RouteGuard';
import { useSession } from '../../src/session/store';
import { TutorialOverlay } from '../../src/tutorial/TutorialOverlay';
import { useTutorial } from '../../src/tutorial/store';
import { useThemeColors } from '../../src/theme';
import { lift } from '../../src/ui/elevation';
import { useHairline } from '../../src/ui/hairline';
import { Screen } from '../../src/ui';
import { TabIcon, type TabIconName } from '../../src/ui/TabIcon';
import { ToastHost } from '../../src/ui/ToastHost';
import { useScreenScale } from '../../src/ui/useScreenScale';

/**
 * The floating bar's own height, per tier. Its distance off the bottom is computed
 * per-platform below.
 *
 * A fixed 64 was a tenth of a tall phone and a sixth of a short one — the single biggest piece
 * of chrome that did not scale, and it is subtracted from nine screens rather than one. The
 * tier comes from `useScreenScale` so the bar can never disagree with the deck about how small
 * the screen is.
 */
const TAB_BAR_HEIGHT = { tight: 52, compact: 58, roomy: 64 };

/**
 * The app proper. Reachable only once onboarding has actually been completed.
 *
 * Five tabs, carried over from the original Android app because that set maps onto
 * what the backend actually serves — communities, chat, the marketplace and the
 * profile all have endpoints behind them.
 *
 * The one change from the old layout: **Home is the deck**, not a hub with a "Start
 * matching" button on it. Swiping is what the product is for, and putting it one tap
 * behind a dashboard was costing the app its first screen. The hub's genuinely useful
 * part — pending friend requests — moves to Profile, which already counts friends.
 */
export default function MainLayout() {
  const colors = useThemeColors();
  const t = useT();
  const insets = useSafeAreaInsets();
  const hairline = useHairline();
  const { pick } = useScreenScale();

  const barHeight = pick(TAB_BAR_HEIGHT.tight, TAB_BAR_HEIGHT.compact, TAB_BAR_HEIGHT.roomy);
  const barPadding = pick(4, 6, 8);
  const iconSize = pick(20, 22, 24);
  // 9 is the floor. The note below records a label truncating at 11, and a tab that cannot
  // show its own word is worse than a slightly smaller one.
  const labelSize = pick(9, 10, 10);

  /*
   * The floating bar's geometry, in one place, because two numbers have to agree: how far
   * the bar sits off the bottom, and how much room every screen leaves for it. Deriving the
   * second from the first is the only way they cannot drift.
   */
  const barInset = (Platform.OS === 'android' ? insets.bottom : insets.bottom * 0.5) + 10;
  const tabBarSpace = barInset + barHeight;

  // Here rather than at the root: this layout mounts only once a gamer is signed in and
  // onboarded, which is exactly when there is an account to attach a device token to.
  // Asking on the sign-up screen would be asking before there is anything to notify about.
  const { shouldPrime, onPrimerDone } = usePushRegistration(true);
  useNotificationRouting(true);
  // A match landing while the app is open becomes the celebration rather than a banner.
  useMatchNotifications(true);
  // Every other kind becomes a toast from the top. Between them these two cover every kind
  // this build knows, which is the set `usePushRegistration` suppresses the OS banner for.
  useInAppNotifications(true);

  // Tells RevenueCat which account is buying, before anybody can reach a paywall.
  //
  // This is the whole of billing's client-side identity, and skipping it does not fail
  // loudly — purchases would succeed, arrive at our webhook under an anonymous id, and be
  // refused with nowhere to go while the buyer waits for a subscription they paid for.
  //
  // There is no purchase-recovery step to go with it, on purpose: a charge that never
  // reached us is RevenueCat's to retry, not this app's to remember. That is most of why
  // billing moved there.
  const userId = useSession((s) => s.userId);
  const queryClient = useQueryClient();
  useEffect(() => {
    if (!userId) return;
    // Prices are fetched straight after, and chained rather than fired alongside because
    // `getProducts` throws before `configure` has run. Doing it here means the Market and
    // the Gold screen open with the answer already cached, so the placeholder those screens
    // render while they wait is, in practice, never seen.
    void identify(userId).then(() => queryClient.prefetchQuery(storePricesQuery()));
  }, [userId, queryClient]);

  // The walkthrough, once, after onboarding. Started here rather than from the deck so
  // it owns the whole tab bar from the first frame — it navigates between tabs, and a
  // tour that begins inside one of the screens it is touring fights itself.
  //
  // Held back until the notification primer is done: two full-screen things asking for
  // attention at the same moment is one too many, and the primer spends the single
  // system permission prompt, so it goes first.
  //
  // Field selectors rather than `useTutorial()`. Subscribing to the whole store gave this
  // component a new state object on every tutorial change — and since that object was also
  // the effect's dependency, the entire tab navigator re-rendered and the effect re-ran on
  // each of the five walkthrough steps. The actions are identity-stable, so the deps below
  // are now three primitives and two constants.
  const tutorialHydrated = useTutorial((s) => s.hydrated);
  const tutorialSeen = useTutorial((s) => s.seen);
  const tutorialStep = useTutorial((s) => s.step);
  const loadTutorial = useTutorial((s) => s.load);
  const loadHints = useHints((s) => s.load);
  const startTutorial = useTutorial((s) => s.start);
  useEffect(() => {
    if (!tutorialHydrated) {
      void loadTutorial();
      return;
    }
    if (!tutorialSeen && tutorialStep === null && !shouldPrime) startTutorial();
  }, [tutorialHydrated, tutorialSeen, tutorialStep, loadTutorial, startTutorial, shouldPrime]);

  // The first-use hints read their own "already seen" list. Loaded here rather than on the
  // deck because two of them live on other tabs, and a hint that has to wait for its screen
  // to mount before it can find out it was already dismissed is a hint that flashes.
  useEffect(() => {
    void loadHints();
  }, [loadHints]);

  // Shown once, over the app, before the operating system's own prompt — see
  // NotificationPrimer. Rendered instead of the tabs rather than on top of them: it asks
  // one question and the app behind it is not usable until it is answered, so letting
  // somebody tap through to a half-covered deck would only produce an accidental answer.
  if (shouldPrime) {
    return (
      <RouteGuard allow={(s) => s === 'ready'}>
        <Screen>
          <NotificationPrimer onDone={onPrimerDone} />
        </Screen>
      </RouteGuard>
    );
  }

  return (
    <RouteGuard allow={(s) => s === 'ready'}>
      {/* Here, not on the conversation screen. The socket is what tells everyone else
          you are online, so its lifetime has to be "the app is open" rather than "this
          chat is open" — otherwise walking back to the deck reads as going offline. It
          also means the inbox hears about messages, which it previously did not. */}
      <ChatSocketProvider>
      <Tabs
        // Back returns to the tab you came from, not to Home.
        //
        // The default is `firstRoute`, which sends every back press to the first tab —
        // so opening a chat from Messages and pressing back landed on the deck, and the
        // same for Settings and Badges out of Profile. Those screens are declared here
        // with `href: null`, which makes them tabs without buttons, and a tab navigator
        // has no stack of tabs to pop unless it is told to keep one.
        //
        // `history` keeps the visit order and drops duplicates, so it also does the
        // right thing for a plain tab switch: Home → Market → back is Home.
        backBehavior="history"
        screenOptions={{
          headerShown: false,
          /*
           * Blurred tabs stop re-rendering.
           *
           * Bottom tabs mount lazily but never unmount, so by mid-session several screen
           * subtrees are live at once — the deck's queries, the inbox's fifteen-second
           * poll, a running boost countdown — all doing work for a screen nobody is
           * looking at.
           *
           * Scoped to this navigator rather than the global `enableFreeze()`: it is the
           * same mechanism, it leaves onboarding's stacks alone, and it can be turned off
           * for a single screen if one ever misbehaves.
           *
           * Safe for everything that must keep running, because none of it is inside a
           * tab: `ChatSocketProvider` wraps `<Tabs>` below, and the four notification
           * hooks are in this component, above it.
           */
          freezeOnBlur: true,
          // `primary`, not `brand`. The pink is now the like/match colour and nothing
          // else — see the gradient table in `src/theme/gradients.ts`. This only tints the
          // label; the icon derives its own colour from `focused`.
          tabBarActiveTintColor: colors.primary,
          tabBarInactiveTintColor: colors.muted,
          /*
           * `paddingBottom` is what pays for the floating bar, and it is not optional.
           *
           * An absolutely-positioned tab bar is out of flow, so react-navigation stops
           * reserving height for it and every screen silently grows ~74px taller than the
           * space it can actually use — the deck's Pass/Match row, the last row of every
           * list, the compose bar. Doing it here, on `sceneStyle`, fixes all of them at
           * once instead of per screen; the two screens that hide the bar override it back
           * to zero where they are declared below.
           */
          sceneStyle: { backgroundColor: colors.canvas, paddingBottom: tabBarSpace },
          /*
           * A floating bar: detached from the screen edges, rounded, and lifted.
           *
           * `position: 'absolute'` is what takes it out of flow — react-navigation then
           * stops reserving height for it, which is why every screen below already passes
           * `edges={['top']}` and leaves the bottom to the bar. Screens that scroll pad
           * their own content (`pb-8` in `Screen`), so nothing ends up trapped underneath.
           *
           * The insets are still honoured, just as margin rather than padding — on Android
           * the gesture bar sits under this, and without the offset the bar lands on top of
           * it and swallows the swipe-up.
           *
           * `elevated`, not `surface`: this is the one element in the app that floats over
           * content with no scrim behind it, which is exactly the case the fourth depth step
           * exists for. See `src/theme/tokens.js`.
           */
          tabBarStyle: {
            position: 'absolute',
            /*
             * Margins, not `left`/`right`/`bottom`.
             *
             * React Navigation positions the bar itself once `position: 'absolute'` is set,
             * and its own `bottom: 0` wins over one passed in here — the first attempt used
             * offsets and produced a full-width bar welded to the bottom edge, which is
             * exactly what this was trying not to be. Margins are applied on top of its
             * positioning rather than competing with it.
             */
            marginHorizontal: 12,
            marginBottom: barInset,
            height: barHeight,
            borderRadius: 24,
            backgroundColor: colors.elevated,
            borderTopWidth: 0,
            paddingTop: barPadding,
            paddingBottom: barPadding,
            // Depth without a class — `shadow-*` is forbidden here, see src/ui/elevation.ts.
            ...lift('lg'),
            ...hairline,
          },
          tabBarItemStyle: { borderRadius: 18, paddingHorizontal: 2 },
          // 10, not 11: the floating bar is inset from both edges, and at 11 "Community"
          // truncated to "Communi…". A tab label that cannot show its own word is worse
          // than a slightly smaller one.
          tabBarLabelStyle: { fontFamily: 'Poppins_500Medium', fontSize: labelSize },
        }}
      >
        <Tabs.Screen name="home" options={tab(t.tabs.home, 'deck', iconSize)} />
        <Tabs.Screen name="lobby" options={tab(t.tabs.lobby, 'lobby', iconSize)} />
        <Tabs.Screen name="messages" options={tab(t.tabs.messages, 'messages', iconSize)} />
        <Tabs.Screen name="market" options={tab(t.tabs.market, 'market', iconSize)} />
        <Tabs.Screen name="profile" options={tab(t.tabs.profile, 'profile', iconSize)} />


        {/* Reachable from Profile, but not a tab of its own. One entry, not two: the
            screens it leads to are inside its stack rather than in a second `edit` tab.
            Splitting them made every settings row a tab switch, and the edit tab's stack
            survived leaving it, so back went to whichever page had been opened before.

            The bar is hidden while it is open: these are "change one thing and come back"
            flows, and a tab press mid-edit would discard the change without saying so. */}
        <Tabs.Screen
          name="settings"
          // No bar, so no room reserved for one — see `sceneStyle` above.
          options={{ href: null, tabBarStyle: { display: 'none' }, sceneStyle: { paddingBottom: 0 } }}
        />
        {/* A conversation is no longer a tab. It was one — `href: null`, so no button,
            but a tab all the same — and that is what made backing out of a chat land on
            the previous chat instead of on the inbox: each one was pushed onto the chat
            tab's own stack, which outlived leaving the tab. It lives in the Messages
            stack now, where the thing you came from is the thing you go back to. */}

        {/* Somebody else's profile is not here either. It is pushed from a conversation
            and nowhere else, so it lives in the Messages stack — as `messages/gamer/
            [userId]` — for the same reason the conversation itself does. As its own tab
            it had the same fault: opening one person's profile, backing out, then opening
            another left both on that tab's stack, and back went to the first one. */}
        {/* Badges keeps the tab bar: it is somewhere to browse rather than a flow with
            an unsaved change in it, and claiming a reward then heading to the market to
            spend it is the path this is meant to make short. */}
        <Tabs.Screen name="badges" options={{ href: null }} />

        {/* Reached from the Friends count on Profile, the same way Badges is reached
            from its own. Keeps the tab bar: it is a list to look through, not a form. */}
        <Tabs.Screen name="friends" options={{ href: null }} />

        {/* A friend's profile. The same screen also exists inside the Messages stack,
            for the copy of it opened from a conversation — two mounts so that backing
            out returns to whichever list you came from.

            Registered under its full path, not `gamer`: the folder holds one route and
            no `_layout`, so Expo Router flattens it and the screen this navigator sees
            is literally `gamer/[userId]`. Naming it `gamer` matches nothing, and an
            unmatched name is not ignored — it becomes a sixth tab, with the route id
            for a label and a placeholder for an icon. */}
        <Tabs.Screen name="gamer/[userId]" options={{ href: null }} />

        {/* Reached from Profile, and from the Market once something has been bought. Keeps
            the tab bar: deciding what to wear and going back to the shop for more is the
            loop these two screens make between them. */}
        <Tabs.Screen name="inventory" options={{ href: null }} />

        {/* Reached from the badge in the deck header rather than a tab of its own. It
            keeps the tab bar: seeing who liked you and going straight back to swiping is
            the loop this screen exists to close. */}
        <Tabs.Screen name="admirers" options={{ href: null }} />

        {/* The paywall. Tab bar hidden: it is a decision with a price on it, and a stray
            tab press mid-purchase is not a decision anybody meant to make. */}
        <Tabs.Screen
          name="gold"
          options={{ href: null, tabBarStyle: { display: 'none' }, sceneStyle: { paddingBottom: 0 } }}
        />
        </Tabs>

      {/* After the tabs, so it draws over them — and inside the guard, so it can never
          appear for an account that has not finished onboarding. */}
      <TutorialOverlay />

      {/* Above the tabs and below the celebration, which is the whole of its z-order
          argument: a toast must cover the app it is announcing something about, and must
          never cover a match. Fed by `useInAppNotifications` above and, for purchases, by
          the Market — see `src/ui/toast.ts`. */}
      <ToastHost />

      {/* After the toasts and before the celebration. A first-use hint may cover the tab
          bar it is pointing at, and a match must cover the hint — see CoachmarkHost for
          why this one is an ordinary sibling rather than a Modal. */}
      <CoachmarkHost />

      {/* Last, so it draws over everything including the tutorial. A match is the best
          thing that happens in this app and nothing should cover it. Mounted here rather
          than on the deck because it is now raised from a push too — see
          `src/match/celebration.ts`. */}
      <MatchCelebration />
      </ChatSocketProvider>
    </RouteGuard>
  );
}

function tab(title: string, icon: TabIconName, size: number) {
  return {
    title,
    // `focused`, not `color`. TabIcon owns what an active tab looks like — see the header
    // comment there for why taking the navigator's tint was the wrong seam.
    tabBarIcon: ({ focused }: { focused: boolean }) => (
      <TabIcon name={icon} focused={focused} size={size} />
    ),
  };
}
