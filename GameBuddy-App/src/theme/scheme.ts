import { colorScheme } from 'nativewind';
import { create } from 'zustand';
import { secureStorage } from '../session/storage';

/**
 * What the user picked, which is not the same as what is on screen.
 *
 * `system` follows the OS and is the default — an app that ignores a phone already set
 * to dark is the thing people complain about. The other two are deliberate overrides,
 * because plenty of people run their phone in light mode and still want a dark app at
 * night, and the reverse.
 */
export type ThemePreference = 'system' | 'light' | 'dark';

// Stored through the same helper as the auth token, which means the keychain on
// native. A theme preference is not a secret and does not need that protection — this
// is here to avoid pulling in async-storage for a single string. If a second
// non-sensitive preference appears, add the plain store and move this with it.
const KEY = 'gamebuddy.themePreference';

type SchemeState = {
  preference: ThemePreference;
  /** False until storage has been read, so nothing renders in the wrong theme first. */
  hydrated: boolean;
  load: () => Promise<void>;
  setPreference: (preference: ThemePreference) => void;
};

export const useScheme = create<SchemeState>((set) => ({
  preference: 'system',
  hydrated: false,

  load: async () => {
    const stored = await secureStorage.get(KEY);
    const preference = isPreference(stored) ? stored : 'system';
    apply(preference);
    set({ preference, hydrated: true });
  },

  setPreference: (preference) => {
    apply(preference);
    set({ preference });
    // Not awaited: the theme should flip on the same frame as the tap. A failed write
    // costs the preference at next launch, which is better than a laggy toggle.
    void secureStorage.set(KEY, preference);
  },
}));

/**
 * Hands the choice to NativeWind, which owns the `dark` class the CSS variables key
 * off. Passing `'system'` puts it back under `Appearance`, so later OS changes are
 * picked up without us subscribing to anything.
 */
function apply(preference: ThemePreference): void {
  colorScheme.set(preference);
}

function isPreference(value: string | null): value is ThemePreference {
  return value === 'system' || value === 'light' || value === 'dark';
}

/** Human labels for the settings screen, in the order they should be shown. */
export const THEME_OPTIONS: { value: ThemePreference; label: string; hint: string }[] = [
  { value: 'system', label: 'System', hint: 'Match your phone' },
  { value: 'light', label: 'Light', hint: 'Always light' },
  { value: 'dark', label: 'Dark', hint: 'Always dark' },
];
