import { Stack } from 'expo-router';

/**
 * Communities, as a stack inside the tab.
 *
 * Unlike the profile-editing stack, the tab bar stays visible here. These screens are
 * browsing, not a form: going from a feed into a community into a post and then off to
 * Messages is an ordinary thing to want, and hiding the bar three levels deep would
 * strand you.
 */
export default function CommunityLayout() {
  return <Stack screenOptions={{ headerShown: false, animation: 'slide_from_right' }} />;
}
