import { create } from 'zustand';
import type { DirectoryFilter, DirectoryUser } from '../api/types';

/**
 * The recipients being chosen for a promotion code, while the editor and the picker are
 * two screens.
 *
 * A store rather than route params. expo-router carries params as strings, so handing two
 * hundred ids back from the picker would mean serialising them into a URL and parsing them
 * out again — and the picker also has to know who is *already* selected when it opens,
 * which makes it a value both screens read and write rather than something one passes to
 * the other.
 *
 * Deliberately not persisted. A half-chosen recipient list is worth exactly as much as a
 * half-typed form, and restoring one on a later launch would attach people to a code
 * somebody had already thought better of.
 */
type PromoDraft = {
  /** Who is selected right now, keyed by id so the picker can toggle in constant time. */
  selected: Record<string, DirectoryUser>;
  /** Recipients who already redeemed. Shown selected, and refused a toggle. */
  locked: Set<string>;
  /** What the picker was last showing, so backing into it does not lose the search. */
  query: string;
  filter: DirectoryFilter;

  /** Replaces the selection — used when the editor loads an existing code. */
  reset: (people: DirectoryUser[], locked?: string[]) => void;
  toggle: (person: DirectoryUser) => void;
  add: (people: DirectoryUser[]) => void;
  clear: () => void;
  setSearch: (query: string, filter: DirectoryFilter) => void;
};

export const usePromoDraft = create<PromoDraft>((set) => ({
  selected: {},
  locked: new Set<string>(),
  query: '',
  filter: 'ALL',

  reset: (people, locked = []) =>
    set({
      selected: Object.fromEntries(people.map((person) => [person.userId, person])),
      locked: new Set(locked),
      query: '',
      filter: 'ALL',
    }),

  toggle: (person) =>
    set((state) => {
      // Somebody who has already redeemed cannot be taken off: removing them would not
      // claw anything back, it would only delete the record of who the code was for.
      if (state.locked.has(person.userId)) return state;

      const selected = { ...state.selected };
      if (selected[person.userId]) {
        delete selected[person.userId];
      } else {
        selected[person.userId] = person;
      }
      return { selected };
    }),

  add: (people) =>
    set((state) => ({
      selected: {
        ...state.selected,
        ...Object.fromEntries(people.map((person) => [person.userId, person])),
      },
    })),

  clear: () => set((state) => ({
    // The locked ones stay: they are on the code whatever this form does next.
    selected: Object.fromEntries(
      Object.entries(state.selected).filter(([userId]) => state.locked.has(userId)),
    ),
  })),

  setSearch: (query, filter) => set({ query, filter }),
}));

/** The selection as a list, in the order names read best: alphabetical. */
export function selectedPeople(selected: Record<string, DirectoryUser>): DirectoryUser[] {
  return Object.values(selected).sort((a, b) =>
    (a.username ?? '').localeCompare(b.username ?? ''),
  );
}
