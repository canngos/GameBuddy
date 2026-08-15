import { Stack } from 'expo-router';

/**
 * Lobbies, as a stack inside the tab.
 *
 * The tab bar stays visible, same argument as the old community stack: browsing lobbies
 * and stepping off to Messages mid-plan is an ordinary thing to want.
 */
export default function LobbyLayout() {
  return <Stack screenOptions={{ headerShown: false, animation: 'slide_from_right' }} />;
}
