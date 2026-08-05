import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';

/**
 * Token storage.
 *
 * `expo-secure-store` puts this in the iOS keychain and Android's EncryptedSharedPreferences,
 * which is where a long-lived bearer token belongs — AsyncStorage is a plaintext file
 * readable by anything with access to the app sandbox.
 *
 * There is no secure store on web. Rather than pretend, the web build falls back to
 * `localStorage` and says so once: web is the debugging surface here, not a shipping
 * target, and a silent downgrade is worse than a noisy one.
 */
const isWeb = Platform.OS === 'web';
let warnedAboutWeb = false;

function webFallbackWarning() {
  if (warnedAboutWeb) return;
  warnedAboutWeb = true;
  console.warn('[session] No secure storage on web; the token is in localStorage.');
}

export const secureStorage = {
  async get(key: string): Promise<string | null> {
    if (isWeb) {
      webFallbackWarning();
      return globalThis.localStorage?.getItem(key) ?? null;
    }
    try {
      return await SecureStore.getItemAsync(key);
    } catch (error) {
      // A keychain read can fail on a device that was restored from a backup, or
      // when the item was written under a different biometric state. Treat it as
      // "no session" rather than crashing on launch.
      console.warn('[session] Could not read secure storage', error);
      return null;
    }
  },

  async set(key: string, value: string): Promise<void> {
    if (isWeb) {
      webFallbackWarning();
      globalThis.localStorage?.setItem(key, value);
      return;
    }
    await SecureStore.setItemAsync(key, value);
  },

  async remove(key: string): Promise<void> {
    if (isWeb) {
      globalThis.localStorage?.removeItem(key);
      return;
    }
    // Deleting something that is not there is not an error worth surfacing during
    // sign-out, and sign-out must not be blockable.
    try {
      await SecureStore.deleteItemAsync(key);
    } catch (error) {
      console.warn('[session] Could not clear secure storage', error);
    }
  },
};
