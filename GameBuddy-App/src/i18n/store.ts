import { create } from 'zustand';
import { secureStorage } from '../session/storage';
import { DEFAULT_LANG, deviceLang, isLang, type Lang } from './languages';

// Stored through the same helper as the theme preference, and for the same reason: it
// avoids pulling in async-storage for one short string. See `src/theme/scheme.ts`.
const KEY = 'gamebuddy.language';

type LangState = {
  lang: Lang;
  /**
   * Whether the choice has been read from storage yet.
   *
   * The splash waits on this exactly as it waits on the theme. Rendering before it is
   * known would show the first screen in English and then swap it, which looks like a
   * bug to the one group of people guaranteed to notice: everybody who does not read
   * English.
   */
  hydrated: boolean;
  /** True when the language came from the phone rather than from a deliberate choice. */
  fromDevice: boolean;
  load: () => Promise<void>;
  setLang: (lang: Lang) => void;
};

export const useLangStore = create<LangState>((set) => ({
  lang: DEFAULT_LANG,
  hydrated: false,
  fromDevice: false,

  /**
   * A stored choice wins; otherwise the phone decides; otherwise English.
   *
   * The stored value is deliberately checked first. Somebody who set the app to English
   * on a Finnish phone meant it, and re-reading the system language on every launch
   * would quietly undo that every time.
   */
  load: async () => {
    const stored = await secureStorage.get(KEY);
    if (isLang(stored)) {
      set({ lang: stored, hydrated: true, fromDevice: false });
      return;
    }

    const detected = deviceLang();
    set({ lang: detected ?? DEFAULT_LANG, hydrated: true, fromDevice: detected !== null });
  },

  setLang: (lang) => {
    set({ lang, fromDevice: false });
    // Not awaited: the language should change on the same frame as the tap, and a
    // failed write costs the choice at next launch rather than the choice itself.
    void secureStorage.set(KEY, lang);
  },
}));
