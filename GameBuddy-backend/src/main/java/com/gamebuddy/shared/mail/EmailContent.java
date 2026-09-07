package com.gamebuddy.shared.mail;

import java.util.List;

/**
 * One transactional email, described rather than written out.
 *
 * <p>Every message goes out as {@code multipart/alternative} — an HTML part for the clients
 * that render it and a plain-text part for the ones that do not, plus spam filters, which
 * treat an HTML-only message as a small negative signal. Those two parts have to say the
 * same thing, and the reliable way to guarantee that is to describe the message once and
 * render it twice, rather than maintain two copies of every sentence and hope they are
 * edited together.
 *
 * <p>So this is the content, and {@link EmailRenderer} is the layout. Nothing here knows
 * about tables, hex colours or the mark; nothing there knows what any particular email is
 * for.
 *
 * @param subject the subject line
 * @param preheader the grey line clients show after the subject in the inbox list. Worth
 *     setting deliberately: left empty, clients fall back to scraping the first text in the
 *     body, which for a branded template is whatever the layout happens to start with.
 * @param heading the first line inside the card
 * @param intro the sentence under the heading, explaining why this arrived
 * @param code the six digits to display in a panel of their own, or {@code null} for a
 *     message that carries none
 * @param codeCaption the line under that panel — how long it lasts
 * @param paragraphs body text after the code, one string per paragraph
 * @param footnote the quieter closing line, below a rule: what to do if this was not you
 */
public record EmailContent(
        String subject,
        String preheader,
        String heading,
        String intro,
        String code,
        String codeCaption,
        List<String> paragraphs,
        String footnote) {

    public EmailContent {
        paragraphs = paragraphs == null ? List.of() : List.copyOf(paragraphs);
    }

    /** A message whose point is a code the reader has to type back in. */
    public static EmailContent withCode(
            String subject,
            String preheader,
            String heading,
            String intro,
            String code,
            String codeCaption,
            String footnote) {
        return new EmailContent(subject, preheader, heading, intro, code, codeCaption, List.of(), footnote);
    }

    /** A message that only tells the reader something. */
    public static EmailContent notice(
            String subject, String preheader, String heading, String intro, List<String> paragraphs, String footnote) {
        return new EmailContent(subject, preheader, heading, intro, null, null, paragraphs, footnote);
    }

    public boolean hasCode() {
        return code != null && !code.isBlank();
    }
}
