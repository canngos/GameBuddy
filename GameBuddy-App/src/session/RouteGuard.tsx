import { Redirect } from 'expo-router';
import type { ReactNode } from 'react';
import { landingRoute } from './routes';
import { useSession, type SessionStatus } from './store';

type RouteGuardProps = {
  /** True when this session status is allowed to be in this route group. */
  allow: (status: SessionStatus) => boolean;
  /** The group's navigator. Always rendered — see below. */
  children: ReactNode;
};

/**
 * Keeps a route group's navigator mounted while redirecting anyone who does not
 * belong in it.
 *
 * The obvious shape — `if (wrongStatus) return <Redirect />` — is a trap. A layout
 * that returns anything other than its navigator *unmounts the navigator* while the
 * child route beneath it is still mounted. The child then renders with no navigation
 * context, and `useNavigationBuilder` throws "Couldn't find a navigation context",
 * pointing at whatever component happened to be rendering rather than at the layout
 * that actually caused it.
 *
 * That failure never appeared on web and crashed instantly on Android, which is the
 * useful part: the navigator's mount/unmount timing differs between the two, so the
 * torn state is reachable on device and not in a browser.
 *
 * So the navigator is always rendered, and the redirect sits beside it. `Redirect`
 * renders nothing and navigates from an effect, so a frame of the wrong screen is the
 * whole cost.
 */
export function RouteGuard({ allow, children }: RouteGuardProps) {
  const status = useSession((s) => s.status);

  // 'loading' is not "not allowed" — it is "not known yet", and the root layout holds
  // the splash screen until it resolves. Redirecting on it would bounce every start.
  const misplaced = status !== 'loading' && !allow(status);

  return (
    <>
      {misplaced && <Redirect href={landingRoute[status] as never} />}
      {children}
    </>
  );
}
