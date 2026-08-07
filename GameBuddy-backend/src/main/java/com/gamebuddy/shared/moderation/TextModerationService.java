package com.gamebuddy.shared.moderation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Screens everything a person types, before it is stored.
 *
 * <p>Two outcomes, not one. Ordinary profanity is <strong>masked</strong> — people swear
 * at video games, and refusing the message outright would be both annoying and useless,
 * since the author simply retypes it. Slurs and sexual abuse aimed at somebody are
 * <strong>blocked</strong>: there is no version of those worth storing, and masking them
 * would leave the intent perfectly legible while pretending something had been done.
 *
 * <p><strong>What this is not.</strong> A word list is a weak instrument. It does not
 * understand context, it will mask a word somebody used innocently, and anyone determined
 * to get a slur through will manage it — {@link TextNormaliser} raises the cost of the
 * obvious evasions and no more. This is the floor that stops casual abuse and satisfies
 * the requirement that user text be filtered at all. What actually protects people is the
 * report button and a moderator who answers it, which is why the 24-hour commitment
 * matters more than this file does.
 *
 * <p>The lists are deliberately short and deliberately clinical: a starting set, not a
 * considered corpus. Replacing the whole approach with a managed classification service is
 * the right move the moment there is money to pay for one.
 */
@Slf4j
@Service
public class TextModerationService {

    /**
     * Refused outright, matched against whole words only.
     *
     * <p>Whole words rather than substrings, because substring matching on a list this
     * short is worse than no matching at all: {@code spic} appears inside
     * <em>suspicious</em> and {@code pedo} inside <em>pedometer</em>. Refusing somebody's
     * message because they used the word "suspicious" would be a bug with a moral tone.
     *
     * <p>Every entry here refuses a message, so the bar for adding one is that there is no
     * innocent reading.
     */
    private static final Set<String> BLOCKED_WORDS = normalisedSet(
            "nigger", "nigga", "faggot", "tranny", "kike", "spic", "chink", "paki", "coon", "wetback", "retard", "loli",
            "shota", "pedo", "kys");

    /**
     * Refused wherever they appear, including split across what looks like other words.
     *
     * <p>Matched as a substring of the whole normalised text, which is safe only because
     * every entry is long and distinctive. Nothing short goes in here. This is what catches
     * {@code k i l l   y o u r s e l f}, which the word list cannot see because
     * normalisation has already removed the spaces that made it two words.
     */
    private static final Set<String> BLOCKED_PHRASES =
            normalisedSet("killyourself", "childporn", "childpornography", "rapeyou", "iwillrapeyou");

    /** Masked rather than refused. What people say when they lose a round. */
    private static final Set<String> PROFANITY = normalisedSet(
            "fuck",
            "fucking",
            "fucker",
            "motherfucker",
            "shit",
            "bullshit",
            "bitch",
            "cunt",
            "asshole",
            "dickhead",
            "whore",
            "slut",
            "wanker",
            "bastard",
            "prick",
            "twat",
            "cock",
            "pussy",
            "piss");

    private static final char MASK = '*';

    /** How many consecutive single letters count as somebody spelling a word out. */
    private static final int MIN_SPELLED_RUN = 3;

    /**
     * A word, including the punctuation people hide inside one.
     *
     * <p>Keeping {@code . _ - @ $ ! | +} inside the token is what lets {@code f.u.c.k}
     * normalise to a single word and be caught. The cost is that {@code fuck-you} is also
     * one token, normalises to {@code fuckyou}, and matches neither list — so it goes
     * through unmasked. That is an accepted miss: it is profanity, the difference between
     * masked and not is cosmetic, and the alternative — substring matching — brings the
     * Scunthorpe problem with it.
     */
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}'’._@$!|+-]+");

    private static final Pattern EMAIL = Pattern.compile("\\b[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}\\b");
    private static final Pattern URL = Pattern.compile("\\b(?:https?://|www\\.)\\S+", Pattern.CASE_INSENSITIVE);

    /**
     * Seven or more digits, however they are spaced. Long enough not to catch a score, a
     * date or a version number; short enough to catch a national phone number.
     */
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:[+(]?\\d[\\d\\s().-]{5,}\\d)(?!\\d)");

    private static final String REDACTED = "[removed]";

    /**
     * @param text what the author typed
     * @param surface who will be able to read it
     * @return the text to store, or a refusal
     */
    public TextAssessment screen(String text, TextSurface surface) {
        if (text == null || text.isBlank()) {
            String empty = text == null ? "" : text;
            return TextAssessment.allowed(empty, empty);
        }

        String whole = TextNormaliser.normalise(text);
        for (String phrase : BLOCKED_PHRASES) {
            if (whole.contains(phrase)) {
                log.info("Refused {} text containing a blocked phrase", surface);
                return TextAssessment.refused();
            }
        }

        String cleaned = surface == TextSurface.PUBLIC ? redactContactDetails(text) : text;

        List<Token> tokens = tokenise(cleaned);
        Set<Integer> spelledOut = spelledOutMatches(tokens);
        if (spelledOut == null) {
            log.info("Refused {} text containing a blocked word", surface);
            return TextAssessment.refused();
        }

        StringBuilder out = new StringBuilder(cleaned.length());
        int cursor = 0;
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);

            if (BLOCKED_WORDS.contains(token.normalised())) {
                log.info("Refused {} text containing a blocked word", surface);
                return TextAssessment.refused();
            }

            out.append(cleaned, cursor, token.start());
            boolean mask = PROFANITY.contains(token.normalised()) || spelledOut.contains(i);
            out.append(mask ? mask(token.raw()) : token.raw());
            cursor = token.end();
        }
        out.append(cleaned, cursor, cleaned.length());

        return TextAssessment.allowed(text, out.toString());
    }

    private record Token(String raw, String normalised, int start, int end) {}

    private List<Token> tokenise(String text) {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group();
            tokens.add(new Token(raw, TextNormaliser.normalise(raw), matcher.start(), matcher.end()));
        }
        return tokens;
    }

    /**
     * Catches {@code f a g g o t}, which per-word matching cannot see: each letter is its
     * own token and no single letter is on any list.
     *
     * <p>Only runs of at least {@value #MIN_SPELLED_RUN} consecutive single-character
     * tokens are joined. Ordinary writing does not produce those — "I a m o k" would have
     * to be typed deliberately — so this does not reach into normal prose the way
     * substring matching on the whole text would, which is the approach that refuses the
     * word <em>suspicious</em> for containing a slur.
     *
     * @return the token indices to mask, or null when the run spells something blocked
     */
    private Set<Integer> spelledOutMatches(List<Token> tokens) {
        Set<Integer> toMask = new HashSet<>();

        int start = 0;
        while (start < tokens.size()) {
            if (tokens.get(start).normalised().length() != 1) {
                start++;
                continue;
            }
            int end = start;
            StringBuilder joined = new StringBuilder();
            while (end < tokens.size() && tokens.get(end).normalised().length() == 1) {
                joined.append(tokens.get(end).normalised());
                end++;
            }

            if (end - start >= MIN_SPELLED_RUN) {
                String word = TextNormaliser.normalise(joined.toString());
                if (BLOCKED_WORDS.contains(word)) {
                    return null;
                }
                if (PROFANITY.contains(word)) {
                    for (int i = start; i < end; i++) {
                        toMask.add(i);
                    }
                }
            }
            start = end;
        }
        return toMask;
    }

    /**
     * For text that has to be refused whole rather than masked — a username or a community
     * name, where {@code f***} as a display name is worse than being told to pick another.
     *
     * @return true when the text is clean enough to use as an identifier
     */
    public boolean isCleanIdentifier(String text) {
        TextAssessment assessment = screen(text, TextSurface.PUBLIC);
        if (assessment.blocked() || assessment.modified()) {
            return false;
        }

        // Substring matching, which the message filter deliberately avoids — here it is
        // worth its cost. A username has no spaces to hide behind, so "fuckerpants" is one
        // token that matches nothing on any list, and it would sit on every profile card
        // and every message this account ever sends.
        //
        // The price is the Scunthorpe problem: somebody who wants that town in their
        // username will be told to pick another. For a handle that is a mild annoyance
        // with an easy way out. For a message it would be censorship of a place name,
        // which is why the two are not treated the same.
        String normalised = TextNormaliser.normalise(text);
        for (String word : BLOCKED_WORDS) {
            if (normalised.contains(word)) {
                return false;
            }
        }
        for (String word : PROFANITY) {
            if (normalised.contains(word)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Removes what should not be broadcast to strangers.
     *
     * <p>Public surfaces only. Private chat keeps every character: two matched adults
     * arranging to carry on in a party chat is the app doing its job, and redacting a
     * Discord tag out of that conversation would be sabotage dressed as safety.
     */
    private String redactContactDetails(String text) {
        String result = EMAIL.matcher(text).replaceAll(REDACTED);
        result = URL.matcher(result).replaceAll(REDACTED);
        return PHONE.matcher(result).replaceAll(REDACTED);
    }

    private static String mask(String word) {
        return String.valueOf(MASK).repeat(word.length());
    }

    /**
     * Runs the lists through the same normalisation as the text they are compared against,
     * so an entry cannot quietly fail to match itself — {@code bullshit} collapses to
     * {@code bulshit} on both sides, and would never match if only one side were folded.
     */
    private static Set<String> normalisedSet(String... words) {
        return Arrays.stream(words)
                .map(TextNormaliser::normalise)
                .filter(w -> !w.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
