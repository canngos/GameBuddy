package com.gamebuddy.shared.moderation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads the word lists off the classpath.
 *
 * <p>The lists are per-language text files rather than Java literals because they are data,
 * not code: they are generated from public corpora by
 * {@code tools/regenerate-profanity-lists.sh}, they run to hundreds of entries per language,
 * and nobody should have to recompile to fix a false positive.
 *
 * <p>Entries are written in their natural spelling — {@code scheiße}, {@code amcık} — and
 * folded here through the same {@link TextNormaliser} the message text goes through, so an
 * entry cannot quietly fail to match itself.
 */
@Slf4j
final class WordLists {

    /**
     * Entries shorter than this after normalisation are dropped.
     *
     * <p>Load-bearing, not cosmetic. Turkish {@code am} is a genuine profanity and would
     * mask every English "am"; two letters cannot carry enough signal to survive being
     * matched against seven languages at once. Note the run-collapse shrinks entries before
     * they get here, which is why English {@code ass} ({@code as}) has never been on the
     * list either.
     */
    private static final int MIN_LENGTH = 3;

    private WordLists() {}

    /**
     * @param resource absolute classpath path, e.g. {@code /moderation/profanity/tr.txt}
     * @return the normalised entries, deduplicated, in file order
     * @throws IllegalStateException if the file is missing or unreadable
     */
    static Set<String> load(String resource) {
        Set<String> entries = new LinkedHashSet<>();

        try (InputStream in = WordLists.class.getResourceAsStream(resource)) {
            if (in == null) {
                // Failing startup is the right outcome: a filter that silently loads nothing
                // looks identical to a filter that is working, and would stay that way until
                // somebody noticed slurs in production.
                throw new IllegalStateException("Missing word list on the classpath: " + resource);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                String normalised = TextNormaliser.normalise(trimmed);
                if (normalised.length() < MIN_LENGTH) {
                    log.warn("Dropped '{}' from {}: {} characters once normalised", trimmed, resource, normalised.length());
                    continue;
                }
                entries.add(normalised);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read word list: " + resource, e);
        }

        return entries;
    }
}
