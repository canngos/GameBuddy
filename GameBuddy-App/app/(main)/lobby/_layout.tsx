import { Stack } from 'expo-router';

/**
 * Lobbies, as a stack inside the tab.
 *
 * The tab bar stays visible, same argument as the old community stack: browsing lobbies
 * and stepping off to Messages mid-plan is an ordinary thing to want.
 */
/**
 * Same anchor as the messages stack: a lobby opened straight from a notification must
 * have the browse list underneath it, or backing out of the lobby leaves the tab.
 */
export const unstable_settings = { anchor: 'index' };

export default function LobbyLayout() {
  return <Stack screenOptions={{ headerShown: false, animation: 'slide_from_right' }} />;
}
