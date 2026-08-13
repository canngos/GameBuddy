import { Redirect, Stack } from 'expo-router';

/**
 * The development-only group.
 *
 * A group rather than a folder, so the route is `/gallery` and not `/dev/gallery`. That
 * is not cosmetic: being a sibling of `(auth)`, `(main)` and `(onboarding)` puts it
 * *outside* `RouteGuard`, so the gallery opens with no session and no backend running.
 * Reviewing a button should not require a logged-in account.
 *
 * Nothing links here. Reach it in development with:
 *
 *   npx uri-scheme open gamebuddy://gallery --android
 *   http://localhost:8081/gallery          (web)
 *
 * The `__DEV__` redirect is what keeps it out of a shipped build. A Metro `blockList`
 * would be the obvious alternative and is the wrong tool — expo-router enumerates `app/`
 * through `require.context`, so removing files from under it produces resolution errors
 * rather than a clean omission.
 */
export default function DevLayout() {
  if (!__DEV__) return <Redirect href="/" />;

  return <Stack screenOptions={{ headerShown: false }} />;
}
