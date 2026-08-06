import { Stack } from 'expo-router';

/**
 * Settings and everything reachable from it, on one stack.
 *
 * The sub-screens used to live under a separate `edit` route with a stack of its own,
 * which quietly made every row on Settings a *tab switch* rather than a push. A tab's
 * stack outlives leaving the tab, so the pages piled up: opening Password, going back,
 * then opening Notifications left both on the stack, and backing out of Notifications
 * landed on Password instead of on Settings.
 *
 * Same shape as the bug that used to send you from one conversation into another, and the
 * same fix — whatever you came from has to be the thing directly beneath you on a single
 * stack, not a screen stranded in another tab's history.
 */
export default function SettingsLayout() {
  return <Stack screenOptions={{ headerShown: false, animation: 'slide_from_right' }} />;
}
