package com.gamebuddy.shared.moderation;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reduces text to the form a filter can actually match against.
 *
 * <p>Every naive word filter is defeated in about four seconds by someone typing
 * {@code f u c k}, {@code f.u.c.k}, {@code fuuuck} or {@code fück}. Matching the raw
 * string is therefore close to worthless — the only people it catches are the ones who
 * were not trying. This collapses the obvious evasions first so that one entry in the
 * word list covers all of them.
 *
 * <p>It is not, and cannot be, complete. Someone determined will get a slur past this,
 * and the answer to that person is a report and a ban, not a better regular expression.
 * What this buys is that casual abuse costs effort, which is most of the benefit.
 *
 * <p>The result is only ever used for <em>matching</em>. What gets stored and shown is
 * always derived from the original text, so normalisation cannot mangle anybody's
 * message.
 */
public final class TextNormaliser {

    /**
     * Characters commonly substituted to look like letters, plus the letters of the
     * supported languages that NFKD cannot decompose to an ASCII base. Not a complete
     * Unicode confusables table — that is enormous and mostly irrelevant to a keyboard.
     *
     * <p>{@code ı} (dotless i, U+0131) and {@code ß} have no decomposition, so without a
     * fold the separator strip deletes them and a Turkish-keyboard {@code sıktır} or a
     * German {@code scheiße} could never match a list entry. The {@code ß→s} fold relies
     * on {@link #collapseRuns}: {@code scheisse} and {@code scheiße} both end up
     * {@code scheise}. French {@code œ} survives NFKD too and is stripped — accepted,
     * since no list entry needs it.
     */
    private static final Map<Character, Character> LOOKALIKES = Map.ofEntries(
            Map.entry('0', 'o'),
            Map.entry('1', 'i'),
            Map.entry('3', 'e'),
            Map.entry('4', 'a'),
            Map.entry('5', 's'),
            Map.entry('7', 't'),
            Map.entry('8', 'b'),
            Map.entry('9', 'g'),
            Map.entry('@', 'a'),
            Map.entry('$', 's'),
            Map.entry('!', 'i'),
            Map.entry('|', 'i'),
            Map.entry('+', 't'),
            Map.entry('ı', 'i'),
            Map.entry('ß', 's'));

    /** Anything that is not a letter or digit, once accents have been stripped. */
    private static final Pattern SEPARATORS = Pattern.compile("[^a-z0-9]+");

    private TextNormaliser() {}

    /**
     * Lower-cases, strips accents, folds lookalikes, removes separators and collapses runs
     * of the same letter.
     *
     * <p>{@code "F.U.C.K"}, {@code "fuuuuck"}, {@code "f u c k"} and {@code "fück"} all
     * arrive here as {@code "fuck"}. So does {@code "book"} become {@code "bok"} — the
     * collapse is lossy, which is why the word list is written in already-collapsed form
     * and why matching is done on word boundaries rather than as a substring search.
     */
    public static String normalise(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        // Locale.ROOT: on a Turkish-default JVM the bare toLowerCase() turns "I" into
        // dotless "ı", which the separator strip then deletes — every entry containing an
        // "i" silently stops matching capitalised input. Turkish "İ" still folds
        // correctly: it lowers to "i" + combining dot, and the NFKD mark-strip eats the dot.
        String folded = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "");

        StringBuilder mapped = new StringBuilder(folded.length());
        for (char c : folded.toCharArray()) {
            mapped.append(LOOKALIKES.getOrDefault(c, c));
        }

        String stripped = SEPARATORS.matcher(mapped).replaceAll("");
        return collapseRuns(stripped);
    }

    /** {@code "aaabbbc"} to {@code "abc"}. */
    private static String collapseRuns(String text) {
        StringBuilder out = new StringBuilder(text.length());
        char previous = 0;
        for (char c : text.toCharArray()) {
            if (c != previous) {
                out.append(c);
                previous = c;
            }
        }
        return out.toString();
    }
}
