package com.gamebuddy.shared.moderation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("WordLists")
class WordListsTest {

    private static final List<String> LANGUAGES = List.of("en", "fi", "sv", "de", "fr", "es", "tr");

    @ParameterizedTest(name = "[{index}] {0}.txt")
    @DisplayName("every language file is on the classpath and has entries in it")
    @ValueSource(strings = {"en", "fi", "sv", "de", "fr", "es", "tr"})
    void loadsEveryLanguage(String language) {
        Set<String> entries = WordLists.load("/moderation/profanity/" + language + ".txt");

        assertFalse(entries.isEmpty(), language + ".txt loaded no entries");
    }

    @Test
    @DisplayName("nothing short enough to match an ordinary word gets through")
    void dropsShortEntries() {
        for (String language : LANGUAGES) {
            for (String entry : WordLists.load("/moderation/profanity/" + language + ".txt")) {
                assertTrue(entry.length() >= 3, language + ".txt let through '" + entry + "'");
            }
        }
    }

    @Test
    @DisplayName("entries are stored already folded, so they can match themselves")
    void entriesAreNormalised() {
        for (String entry : WordLists.load("/moderation/profanity/de.txt")) {
            assertEquals(entry, TextNormaliser.normalise(entry), "'" + entry + "' is not in normalised form");
        }
    }

    @Test
    @DisplayName("a missing file fails loudly rather than loading an empty filter")
    void failsOnAMissingFile() {
        assertThrows(IllegalStateException.class, () -> WordLists.load("/moderation/profanity/xx.txt"));
    }

    @Test
    @DisplayName("the allowlist actually removes words from what the filter uses")
    void allowlistIsSubtracted() {
        Set<String> allowed = WordLists.load("/moderation/profanity/allowlist.txt");
        assertFalse(allowed.isEmpty(), "the allowlist test proves nothing while the file is empty");

        TextModerationService service = new TextModerationService();
        for (String word : allowed) {
            TextAssessment result = service.screen("this is " + word + " ok", TextSurface.PRIVATE);
            assertFalse(result.modified(), "'" + word + "' is allowlisted but was still masked");
        }
    }
}
