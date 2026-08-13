import { Tabs } from 'expo-router';
import { Platform } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { RouteGuard } from '../../src/session/RouteGuard';
import { useThemeColors } from '../../src/theme';
import { TabIcon, type TabIconName } from '../../src/ui/TabIcon';

/**
 * The moderator console — a different app, sharing a binary.
 *
 * A separate route group rather than an extra tab on the main shell, and the guard is
 * why: `allow={(s) => s === 'admin'}` here and `s === 'ready'` there means the two
 * cannot overlap. A hidden tab would have put the deck, the market and a profile screen
 * one mis-render away from an account that has none of those things — the moderator has
 * no age, no games and no keywords, so half of the main shell has nothing to draw.
 *
 * There is also no chat socket here. Presence is broadcast from that connection, so
 * mounting it would announce the moderator as online to a population that is not
 * supposed to know the account exists.
 *
 * Five tabs, in the order the work actually arrives: what happened (Overview), what needs
 * judging (Reports, Avatars), who has already been dealt with (Accounts), and Settings —
 * which is also the way out, because sign out belongs somewhere reachable from every
 * screen rather than in one screen's header.
 */
export default function AdminLayout() {
  const colors = useThemeColors();
  const insets = useSafeAreaInsets();

  return (
    <RouteGuard allow={(s) => s === 'admin'}>
      <Tabs
        backBehavior="history"
        screenOptions={{
          headerShown: false,
          tabBarActiveTintColor: colors.primary,
          tabBarInactiveTintColor: colors.muted,
          sceneStyle: { backgroundColor: colors.canvas },
          tabBarStyle: {
            backgroundColor: colors.surface,
            borderTopColor: colors.line,
            borderTopWidth: 1,
            height: 60 + insets.bottom,
            paddingBottom: Platform.OS === 'android' ? insets.bottom + 6 : insets.bottom,
            paddingTop: 8,
          },
          tabBarLabelStyle: { fontFamily: 'Poppins_500Medium', fontSize: 11 },
        }}
      >
        <Tabs.Screen name="console" options={tab('Overview', 'analytics')} />
        <Tabs.Screen name="reports" options={tab('Reports', 'reports')} />
        <Tabs.Screen name="avatars" options={tab('Avatars', 'avatars')} />
        <Tabs.Screen name="accounts" options={tab('Accounts', 'profile')} />
        <Tabs.Screen name="settings" options={tab('Settings', 'settings')} />

        {/* Reached from Settings, and not a tab of its own. The bar is hidden while it
            is open: changing the password ends every session, so a tab press mid-edit
            would abandon a half-typed change without saying so. */}
        <Tabs.Screen name="password" options={{ href: null, tabBarStyle: { display: 'none' } }} />
      </Tabs>
    </RouteGuard>
  );
}

function tab(title: string, icon: TabIconName) {
  return {
    title,
    tabBarIcon: ({ focused }: { focused: boolean }) => <TabIcon name={icon} focused={focused} />,
  };
}
