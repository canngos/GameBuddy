import { Stack } from 'expo-router';

/** One screen per gamer, pushed from a chat, a member list or a friend row. */
export default function GamerLayout() {
  return <Stack screenOptions={{ headerShown: false, animation: 'slide_from_right' }} />;
}
