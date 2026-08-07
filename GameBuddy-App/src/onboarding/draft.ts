import { create } from 'zustand';
import { MIN_GAMES, MIN_KEYWORDS } from '../validation';

/**
 * The profile being assembled across the three onboarding screens.
 *
 * `POST /auth/details` takes a date of birth, country, gender, avatar, games and keywords in one
 * call and applies them in one transaction — there is no partial save. So the answers
 * accumulate here and are submitted once, from the last screen.
 *
 * Deliberately in memory only. If the app is killed mid-onboarding the user starts the
 * three screens again, which is a minute of tapping; persisting it would mean keeping
 * a half-built profile on disk and reconciling it with whatever the server has.
 */
export type Draft = {
  /**
   * The three parts of a date of birth, kept as typed rather than as a Date.
   *
   * A half-entered date is not a Date, and forcing it to be one would mean either
   * inventing the missing parts or throwing away what has been typed on every keystroke.
   */
  birthDay: string;
  birthMonth: string;
  birthYear: string;
  country: string;
  /**
   * `null` means "not answered yet"; `''` means "prefer not to say", which is a real
   * answer the backend accepts (the field is nullable and capped at one character).
   *
   * The distinction is the whole point. When both were `''` the decline option matched
   * the initial state, so it rendered as already chosen and the user was shown an
   * answer they had never given.
   */
  gender: string | null;
  avatarId: string | null;
  gameIds: string[];
  keywordIds: string[];
};

type DraftState = Draft & {
  set: (patch: Partial<Draft>) => void;
  toggleGame: (id: string) => void;
  toggleKeyword: (id: string) => void;
  reset: () => void;
};

const empty: Draft = {
  birthDay: '',
  birthMonth: '',
  birthYear: '',
  country: '',
  gender: null,
  avatarId: null,
  gameIds: [],
  keywordIds: [],
};

export const useDraft = create<DraftState>((set) => ({
  ...empty,
  set: (patch) => set(patch),
  toggleGame: (id) => set((s) => ({ gameIds: toggle(s.gameIds, id) })),
  toggleKeyword: (id) => set((s) => ({ keywordIds: toggle(s.keywordIds, id) })),
  reset: () => set(empty),
}));

function toggle(list: string[], id: string): string[] {
  return list.includes(id) ? list.filter((x) => x !== id) : [...list, id];
}

export const draftRules = { MIN_GAMES, MIN_KEYWORDS };
