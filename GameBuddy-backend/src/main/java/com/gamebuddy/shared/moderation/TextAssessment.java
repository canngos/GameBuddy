package com.gamebuddy.shared.moderation;

/**
 * What the text filter decided, and the text to store if it did not refuse.
 *
 * @param blocked true when the text may not be stored at all
 * @param cleaned the text as it should be persisted — masked and, on a public surface,
 *     redacted. Meaningless when {@code blocked}.
 * @param modified whether {@code cleaned} differs from what was submitted, so a caller can
 *     tell the author their message was altered instead of silently changing their words
 */
public record TextAssessment(boolean blocked, String cleaned, boolean modified) {

    /** Named {@code refused} rather than {@code blocked}: the accessor owns that name. */
    static TextAssessment refused() {
        return new TextAssessment(true, "", false);
    }

    static TextAssessment allowed(String original, String cleaned) {
        return new TextAssessment(false, cleaned, !original.equals(cleaned));
    }
}
