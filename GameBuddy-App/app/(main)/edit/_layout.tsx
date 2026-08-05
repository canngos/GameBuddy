import { Stack } from 'expo-router';

/**
 * Profile editing. A stack rather than tabs, because these are all "go in, change one
 * thing, come back" flows launched from Profile.
 */
export default function EditLayout() {
  return <Stack screenOptions={{ headerShown: false, animation: 'slide_from_right' }} />;
}
