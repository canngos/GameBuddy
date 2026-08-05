import { Stack } from 'expo-router';

/**
 * The inbox and the conversations, as a stack inside the tab.
 *
 * A conversation used to be a tab of its own — declared with `href: null` so it had no
 * button, but a tab all the same. That meant every chat you opened was pushed onto *its*
 * stack rather than onto the one you came from, and the stack outlived leaving the tab.
 * Back out of a chat and you landed on the last chat you had open, then the one before
 * it, instead of on the list.
 *
 * A detail screen belongs to the stack of the tab it was opened from. Communities were
 * already built this way; this is the same shape, and back is correct by construction
 * rather than by configuring the tab navigator to guess.
 */
export default function MessagesLayout() {
  return <Stack screenOptions={{ headerShown: false, animation: 'slide_from_right' }} />;
}
