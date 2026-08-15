import type { Lang } from '../languages';
import { de } from './de';
import type { Dictionary } from './en';
import { en } from './en';
import { es } from './es';
import { fi } from './fi';
import { fr } from './fr';
import { sv } from './sv';
import { tr } from './tr';

/**
 * Every language, eagerly imported.
 *
 * No lazy loading: seven dictionaries of a few hundred short strings are smaller than one
 * of the game covers this app downloads without thinking about it, and a language that
 * arrives a frame late is a screen that visibly changes language after it has been read.
 */
export const dictionaries: Record<Lang, Dictionary> = { en, fi, sv, de, fr, es, tr };
