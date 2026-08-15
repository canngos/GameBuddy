import { dictionaries } from './dictionaries';
import type { Dictionary } from './dictionaries/en';
import { useLangStore } from './store';

/**
 * The copy, in the language currently in force.
 *
 * Returns the dictionary object rather than a `t('some.key')` function, which is the
 * whole reason this needs no library: `t.auth.signIn` is checked by the compiler, so a
 * key that does not exist is a build error and a key that exists in English but not in
 * Turkish is *also* a build error — the Turkish dictionary is typed against the English
 * one. The alternative, string keys resolved at runtime, fails silently in exactly the
 * language nobody on the team reads.
 *
 * Copy that varies with a value is a function in the dictionary
 * (`t.lobby.seats(2, 5)`), so each language keeps control of its own word order and
 * plural rules instead of having them assembled by concatenation in a component.
 */
export function useT(): Dictionary {
  const lang = useLangStore((s) => s.lang);
  return dictionaries[lang];
}
