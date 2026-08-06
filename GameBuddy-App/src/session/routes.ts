import type { SessionStatus } from './store';

/**
 * Where each session status belongs.
 *
 * One table, consulted by every group layout, so the guards cannot disagree about
 * where a given status should be — two guards each redirecting somewhere the other
 * rejects is an infinite loop, and it presents as a white screen.
 */
export const landingRoute: Record<Exclude<SessionStatus, 'loading'>, string> = {
  signedOut: '/welcome',
  needsUsername: '/username',
  needsDetails: '/profile',
  ready: '/home',
  admin: '/console',
};
