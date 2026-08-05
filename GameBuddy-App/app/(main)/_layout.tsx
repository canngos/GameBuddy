import { Tabs } from 'expo-router';
import { Platform, type ColorValue } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { NotificationPrimer } from '../../src/notifications/NotificationPrimer';
import { useNotificationRouting } from '../../src/notifications/useNotificationRouting';
import { usePushRegistration } from '../../src/notifications/usePushRegistration';
import { RouteGuard } from '../../src/session/RouteGuard';
import { useThemeColors } from '../../src/theme';
import { Screen } from '../../src/ui';
import { TabIcon, type TabIconName } from '../../src/ui/TabIcon';

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

  // Here rather than at the root: this layout mounts only once a gamer is signed in and
  // onboarded, which is exactly when there is an account to attach a device token to.
  // Asking on the sign-up screen would be asking before there is anything to notify about.
  const { shouldPrime, onPrimerDone } = usePushRegistration(true);
  useNotificationRouting(true);

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
          tabBarActiveTintColor: colors.brand,
          tabBarInactiveTintColor: colors.muted,
          sceneStyle: { backgroundColor: colors.canvas },
          tabBarStyle: {
            backgroundColor: colors.surface,
            borderTopColor: colors.line,
            borderTopWidth: 1,
            // Android draws its navigation bar over this; without the inset the
            // labels sit underneath it. iOS gets a little breathing room instead of
            // the home indicator's full inset, which is already handled.
            height: 60 + insets.bottom,
            paddingBottom: Platform.OS === 'android' ? insets.bottom + 6 : insets.bottom,
            paddingTop: 8,
          },
          tabBarLabelStyle: { fontFamily: 'Poppins_500Medium', fontSize: 11 },
        }}
      >
        <Tabs.Screen name="home" options={tab('Home', 'deck')} />
        <Tabs.Screen name="community" options={tab('Community', 'community')} />
        <Tabs.Screen name="messages" options={tab('Messages', 'messages')} />
        <Tabs.Screen name="market" options={tab('Market', 'market')} />
        <Tabs.Screen name="profile" options={tab('Profile', 'profile')} />

        {/* Reachable from Profile, but not tabs of their own. The bar is hidden while
            they are open: these are "change one thing and come back" flows, and a tab
            press mid-edit would discard the change without saying so. */}
        <Tabs.Screen
          name="settings"
          options={{ href: null, tabBarStyle: { display: 'none' } }}
        />
        <Tabs.Screen
          name="edit"
          options={{ href: null, tabBarStyle: { display: 'none' } }}
        />
        {/* A conversation is no longer a tab. It was one — `href: null`, so no button,
            but a tab all the same — and that is what made backing out of a chat land on
            the previous chat instead of on the inbox: each one was pushed onto the chat
            tab's own stack, which outlived leaving the tab. It lives in the Messages
            stack now, where the thing you came from is the thing you go back to. */}

        {/* Somebody else's profile, pushed from a conversation. */}
        <Tabs.Screen
          name="gamer"
          options={{ href: null, tabBarStyle: { display: 'none' } }}
        />
        {/* Badges keeps the tab bar: it is somewhere to browse rather than a flow with
            an unsaved change in it, and claiming a reward then heading to the market to
            spend it is the path this is meant to make short. */}
        <Tabs.Screen name="badges" options={{ href: null }} />
      </Tabs>
    </RouteGuard>
  );
}

function tab(title: string, icon: TabIconName) {
  return {
    title,
    // `color` is a ColorValue, not a string — it can be an opaque platform colour.
    // TabIcon only ever passes it straight back into a style, so widening the
    // parameter is honest rather than casting it to something it is not.
    tabBarIcon: ({ color }: { color: ColorValue }) => (
      <TabIcon name={icon} color={color} />
    ),
  };
}
