import { Redirect } from 'expo-router';
import { landingRoute } from '../src/session/routes';
import { useSession } from '../src/session/store';

/**
 * The entry route. It renders nothing — the root layout has already held the splash
 * screen until the session is resolved, so by the time this mounts there is a real
 * answer to redirect towards.
 */
export default function Index() {
  const status = useSession((s) => s.status);
  if (status === 'loading') return null;
  return <Redirect href={landingRoute[status] as never} />;
}
