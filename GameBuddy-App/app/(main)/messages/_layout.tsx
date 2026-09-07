import { Stack } from 'expo-router';

/**
 * The inbox is the floor of this stack, even when a screen deeper in is the first one
 * mounted. A push from another tab (an admirer's profile, a notification's conversation)
 * creates this navigator ex nihilo holding only the target, and backing out of it then
 * falls out of the tab instead of landing on the list. Anchoring declares that the list
 * always sits underneath — callers opt in per-push with `withAnchor: true`.
 */
export const unstable_settings = { anchor: 'index' };

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
