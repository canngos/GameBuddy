import { Tabs } from 'expo-router';
import { useEffect } from 'react';
import { Platform } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { identify } from '../../src/billing/purchases';
import { ChatSocketProvider } from '../../src/chat/ChatSocketProvider';
import { MatchCelebration } from '../../src/match/MatchCelebration';
import { NotificationPrimer } from '../../src/notifications/NotificationPrimer';
import { useInAppNotifications } from '../../src/notifications/useInAppNotifications';
import { useMatchNotifications } from '../../src/notifications/useMatchNotifications';
import { useNotificationRouting } from '../../src/notifications/useNotificationRouting';
import { usePushRegistration } from '../../src/notifications/usePushRegistration';
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

/** The floating bar's own height. Its distance off the bottom is computed per-platform. */
const TAB_BAR_HEIGHT = 64;

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
  const insets = useSafeAreaInsets();
  const hairline = useHairline();

  /*
   * The floating bar's geometry, in one place, because two numbers have to agree: how far
   * the bar sits off the bottom, and how much room every screen leaves for it. Deriving the
   * second from the first is the only way they cannot drift.
   */
  const barInset = (Platform.OS === 'android' ? insets.bottom : insets.bottom * 0.5) + 10;
  const tabBarSpace = barInset + TAB_BAR_HEIGHT;

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
  useEffect(() => {
    if (userId) void identify(userId);
  }, [userId]);

  // The walkthrough, once, after onboarding. Started here rather than from the deck so
  // it owns the whole tab bar from the first frame — it navigates between tabs, and a
  // tour that begins inside one of the screens it is touring fights itself.
  //
  // Held back until the notification primer is done: two full-screen things asking for
  // attention at the same moment is one too many, and the primer spends the single
  // system permission prompt, so it goes first.
  const tutorial = useTutorial();
  useEffect(() => {
    if (!tutorial.hydrated) {
      void tutorial.load();
      return;
    }
    if (!tutorial.seen && tutorial.step === null && !shouldPrime) tutorial.start();
  }, [tutorial, shouldPrime]);

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
            height: TAB_BAR_HEIGHT,
            borderRadius: 24,
            backgroundColor: colors.elevated,
            borderTopWidth: 0,
            paddingTop: 8,
            paddingBottom: 8,
            // Depth without a class — `shadow-*` is forbidden here, see src/ui/elevation.ts.
            ...lift('lg'),
            ...hairline,
          },
          tabBarItemStyle: { borderRadius: 18, paddingHorizontal: 2 },
          // 10, not 11: the floating bar is inset from both edges, and at 11 "Community"
          // truncated to "Communi…". A tab label that cannot show its own word is worse
          // than a slightly smaller one.
          tabBarLabelStyle: { fontFamily: 'Poppins_500Medium', fontSize: 10 },
        }}
      >
        <Tabs.Screen name="home" options={tab('Home', 'deck')} />
        <Tabs.Screen name="community" options={tab('Community', 'community')} />
        <Tabs.Screen name="messages" options={tab('Messages', 'messages')} />
        <Tabs.Screen name="market" options={tab('Market', 'market')} />
        <Tabs.Screen name="profile" options={tab('Profile', 'profile')} />

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

      {/* Last, so it draws over everything including the tutorial. A match is the best
          thing that happens in this app and nothing should cover it. Mounted here rather
          than on the deck because it is now raised from a push too — see
          `src/match/celebration.ts`. */}
      <MatchCelebration />
      </ChatSocketProvider>
    </RouteGuard>
  );
}

function tab(title: string, icon: TabIconName) {
  return {
    title,
    // `focused`, not `color`. TabIcon owns what an active tab looks like — see the header
    // comment there for why taking the navigator's tint was the wrong seam.
    tabBarIcon: ({ focused }: { focused: boolean }) => <TabIcon name={icon} focused={focused} />,
  };
}
