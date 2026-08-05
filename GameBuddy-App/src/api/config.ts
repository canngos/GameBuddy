import Constants from 'expo-constants';
import { Platform } from 'react-native';

const PORT = 8080;

/**
 * Where the backend lives.
 *
 * `EXPO_PUBLIC_API_URL` wins if it is set — that is how a build points at production,
 * and how anyone on a network the guesses below get wrong can unblock themselves.
 *
 * Otherwise this resolves the development machine, which is not simply "localhost":
 * on a device or emulator, localhost is the *device*, and the request dies on a closed
 * port with no hint as to why. So we take the host Metro is already being reached on
 * (`hostUri`, e.g. "192.168.1.20:8081") and swap the port. That is by construction an
 * address this device can route to, since it just downloaded the bundle from it.
 */
function resolveBaseUrl(): string {
  const explicit = process.env.EXPO_PUBLIC_API_URL;
  if (explicit) return explicit.replace(/\/+$/, '');

  const hostUri = Constants.expoConfig?.hostUri;
  const host = hostUri?.split(':')[0];
  if (host) return `http://${androidSafeHost(host)}:${PORT}`;

  // No packager host: a production bundle without EXPO_PUBLIC_API_URL, or the web
  // build.
  return `http://${androidSafeHost('localhost')}:${PORT}`;
}

/**
 * Rewrites a loopback address for the Android emulator.
 *
 * The emulator is a separate machine with its own loopback, so `localhost` there is
 * the *emulator*, not the development machine — and `10.0.2.2` is the alias the
 * emulator provides for its host. This matters because Expo reaches the emulator over
 * an adb reverse tunnel and therefore reports `hostUri` as `localhost:8081`: taking
 * that host at face value sends every API call to a closed port inside the emulator,
 * which surfaces as "Could not reach GameBuddy" and looks like the backend is down.
 *
 * Only loopback is rewritten. A LAN address from `hostUri` is already routable and
 * must be left alone, or a physical device on the same Wi-Fi would break instead.
 */
function androidSafeHost(host: string): string {
  if (Platform.OS !== 'android') return host;
  return host === 'localhost' || host === '127.0.0.1' ? '10.0.2.2' : host;
}

export const API_BASE_URL = resolveBaseUrl();

/**
 * Long enough for a cold backend to answer, short enough that a dead network is
 * reported rather than spun on. The recommendation feed is the slow one: it calls
 * the model service before it can respond.
 */
export const REQUEST_TIMEOUT_MS = 20_000;
