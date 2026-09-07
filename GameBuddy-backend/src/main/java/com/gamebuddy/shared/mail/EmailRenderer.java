package com.gamebuddy.shared.mail;

/**
 * Turns an {@link EmailContent} into the two bodies a message carries.
 *
 * <h2>Why the HTML looks like 2005</h2>
 *
 * <p>Email is not the web. The layout is nested tables with inline styles because Outlook
 * on Windows renders mail through Word's HTML engine, which has no flexbox, no grid, no
 * {@code max-width} on a div worth relying on, and no external stylesheet. Anything modern
 * degrades there into a single unstyled column — for a client that is still a large share
 * of the mail people read on a desktop.
 *
 * <p>Specific consequences, each of which is a decision rather than an oversight:
 *
 * <ul>
 *   <li><strong>Inline styles, not classes.</strong> A {@code <style>} block is stripped
 *       outright by some clients and by Gmail's clipped-message view. The block below is
 *       therefore additive only — it carries the dark-mode and small-screen overrides,
 *       which are improvements when they survive and no loss when they do not.
 *   <li><strong>No SVG and no CSS gradient.</strong> Neither renders in Outlook or Gmail.
 *       The mark travels as a PNG (see {@code Mailer}), and the gradient that is the brand
 *       lives inside it rather than in the page.
 *   <li><strong>{@code border-radius} is used anyway.</strong> Outlook ignores it and
 *       squares off the corners, which is a fair trade for rounded cards everywhere else —
 *       the alternative is VML, and a mail template is not worth carrying VML for.
 *   <li><strong>A preheader.</strong> The hidden line at the top is what the inbox list
 *       shows next to the subject; without one, clients scrape whatever text comes first
 *       and show the reader something meaningless.
 * </ul>
 *
 * <h2>Escaping</h2>
 *
 * <p>Every interpolated value goes through {@link #escape}. Two of these messages echo the
 * recipient's own email address back at them, and an address is user-supplied text landing
 * in an HTML document — the same rule as anywhere else, and no less true because the
 * document happens to be an email.
 */
public final class EmailRenderer {

    private EmailRenderer() {}

    /** How wide the message body is. 600 is the width every email client is happy with. */
    private static final String WIDTH = "600";

    /** The {@code cid:} name the mark is attached under. Must match {@code Mailer}. */
    static final String LOGO_CID = "gamebuddy-logo";

    // Light palette, taken from the app's own tokens (GameBuddy-App/src/theme/tokens.js) so
    // an email and the screen it leads to are recognisably the same product.
    private static final String CANVAS = "#F7F7FB";
    private static final String SURFACE = "#FFFFFF";
    private static final String LINE = "#E2E2EC";
    private static final String CONTENT = "#16161F";
    private static final String MUTED = "#62627A";
    private static final String PRIMARY = "#6D3AF0";
    private static final String PANEL = "#F0EBFF";
    private static final String PANEL_LINE = "#DDD1FF";

    private static final String SANS = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";
    private static final String MONO = "ui-monospace,SFMono-Regular,Menlo,Consolas,'Liberation Mono',monospace";

    /**
     * The plain-text part.
     *
     * <p>Not a fallback nobody reads: it is what screen readers and text-mode clients get,
     * what some corporate gateways forward, and — in {@code MAIL_MODE=log} — the part the
     * local console prints and the QA suite reads the code out of.
     */
    public static String toPlainText(EmailContent content) {
        StringBuilder out = new StringBuilder();
        out.append(content.heading()).append("\n\n");
        out.append(content.intro()).append("\n");

        if (content.hasCode()) {
            // On its own line and nothing else on it, so it is selectable with one
            // double-click and unambiguous to anything scanning for it.
            out.append('\n').append(content.code()).append('\n');
            if (content.codeCaption() != null) {
                out.append('\n').append(content.codeCaption()).append('\n');
            }
        }

        for (String paragraph : content.paragraphs()) {
            out.append('\n').append(paragraph).append('\n');
        }

        if (content.footnote() != null) {
            out.append('\n').append(content.footnote()).append('\n');
        }

        out.append("\n--\nGameBuddy | findgamebuddy.com\nThis is an automated message, so replies do not reach us.\n");
        return out.toString();
    }

    /** The HTML part. */
    public static String toHtml(EmailContent content) {
        StringBuilder body = new StringBuilder();

        body.append("<h1 style=\"margin:0 0 12px;font:700 24px/1.25 ")
                .append(SANS)
                .append(";color:")
                .append(CONTENT)
                .append(";letter-spacing:-0.4px;\">")
                .append(escape(content.heading()))
                .append("</h1>");

        body.append(paragraph(content.intro(), CONTENT));

        if (content.hasCode()) {
            // A table rather than a styled div: Outlook will not give a div a background
            // and a border reliably, and the code panel is the one element of this message
            // that has to look deliberate.
            body.append(
                            "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\""
                                    + " style=\"margin:24px 0;\"><tr><td align=\"center\" class=\"gb-panel\" style=\"background:")
                    .append(PANEL)
                    .append(";border:1px solid ")
                    .append(PANEL_LINE)
                    .append(";border-radius:12px;padding:22px 16px;\">")
                    // The trailing letter-spacing on the last digit is padded out by the
                    // matching text-indent, or the digits sit visibly off-centre.
                    .append("<div class=\"gb-code\" style=\"font:700 32px/1 ")
                    .append(MONO)
                    .append(";letter-spacing:8px;text-indent:8px;color:")
                    .append(PRIMARY)
                    .append(";\">")
                    .append(escape(content.code()))
                    .append("</div></td></tr></table>");

            if (content.codeCaption() != null) {
                body.append(paragraph(content.codeCaption(), MUTED));
            }
        }

        for (String p : content.paragraphs()) {
            body.append(paragraph(p, CONTENT));
        }

        if (content.footnote() != null) {
            body.append("<div class=\"gb-rule\" style=\"border-top:1px solid ")
                    .append(LINE)
                    .append(";margin:24px 0 0;font-size:0;line-height:0;\">&nbsp;</div>")
                    .append(paragraph(content.footnote(), MUTED));
        }

        return document(content, body.toString());
    }

    private static String paragraph(String text, String colour) {
        return "<p class=\"" + (MUTED.equals(colour) ? "gb-muted" : "gb-text") + "\" style=\"margin:0 0 14px;font:400 "
                + (MUTED.equals(colour) ? "14px/1.6" : "16px/1.6")
                + " " + SANS + ";color:" + colour + ";\">" + escape(text) + "</p>";
    }

    private static String document(EmailContent content, String cardBody) {
        return "<!doctype html>\n"
                + "<html lang=\"en\" xmlns:v=\"urn:schemas-microsoft-com:vml\">\n"
                + "<head>\n"
                + "<meta charset=\"utf-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n"
                // Stops iOS Mail shrinking the whole message to fit, which makes 16px text
                // arrive at about 11px.
                + "<meta name=\"x-apple-disable-message-reformatting\">\n"
                // Without these two, a client in dark mode inverts the colours itself and
                // does it badly — grey text on a grey card. With them it uses the overrides
                // in the block below instead.
                + "<meta name=\"color-scheme\" content=\"light dark\">\n"
                + "<meta name=\"supported-color-schemes\" content=\"light dark\">\n"
                + "<title>" + escape(content.subject()) + "</title>\n"
                + "<style>\n" + styleBlock() + "</style>\n"
                + "</head>\n"
                + "<body class=\"gb-canvas\" style=\"margin:0;padding:0;width:100%;background:" + CANVAS + ";\">\n"
                + preheader(content.preheader())
                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\""
                + " class=\"gb-canvas\" style=\"background:" + CANVAS + ";\">\n"
                + "<tr><td align=\"center\" style=\"padding:32px 12px 40px;\">\n"
                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"" + WIDTH
                + "\" class=\"gb-wrap\" style=\"width:" + WIDTH + "px;max-width:100%;\">\n"
                + brandRow()
                + "<tr><td class=\"gb-card\" style=\"background:" + SURFACE + ";border:1px solid " + LINE
                + ";border-radius:16px;padding:32px;\">\n"
                + cardBody
                + "\n</td></tr>\n"
                + footerRow()
                + "</table>\n</td></tr>\n</table>\n</body>\n</html>";
    }

    /** The mark and the wordmark, above the card. */
    private static String brandRow() {
        // The wordmark is HTML text, not part of the image, so it survives a client that
        // blocks images and stays crisp at any pixel density.
        return "<tr><td style=\"padding:0 4px 20px;\">\n"
                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>\n"
                + "<td width=\"56\" style=\"width:56px;\"><img src=\"cid:" + LOGO_CID + "\" width=\"56\" height=\"56\""
                + " alt=\"GameBuddy\" style=\"display:block;width:56px;height:56px;border:0;border-radius:13px;\"></td>\n"
                + "<td style=\"padding-left:14px;font:700 22px/1 " + SANS + ";color:" + CONTENT
                + ";letter-spacing:-0.4px;\" class=\"gb-text\">GameBuddy</td>\n"
                + "</tr></table>\n</td></tr>\n";
    }

    private static String footerRow() {
        return "<tr><td style=\"padding:20px 8px 0;\">\n"
                + "<p class=\"gb-muted\" style=\"margin:0;font:400 12px/1.6 " + SANS + ";color:" + MUTED + ";\">"
                + "GameBuddy &middot; findgamebuddy.com<br>"
                + "This is an automated message, so replies do not reach us."
                + "</p>\n</td></tr>\n";
    }

    /**
     * The inbox preview line.
     *
     * <p>Hidden every way clients need it hidden, then padded with zero-width spaces:
     * without the padding, the preview runs on into the first visible text of the message,
     * which for this template is the word "GameBuddy" repeated.
     */
    private static String preheader(String text) {
        return "<div style=\"display:none;max-height:0;overflow:hidden;mso-hide:all;"
                + "font-size:1px;line-height:1px;color:" + CANVAS + ";opacity:0;\">"
                + escape(text)
                + "&#8203;".repeat(60)
                + "</div>\n";
    }

    /**
     * Overrides that are pure upside.
     *
     * <p>Everything structural is inline, so a client that drops this block still renders
     * the light-mode design correctly at 600px. What is here only improves matters where it
     * survives: real dark mode instead of the client's own inversion, and a card that fits
     * a narrow phone.
     */
    private static String styleBlock() {
        return """
                @media (max-width:620px){
                  .gb-wrap{width:100%!important;}
                  .gb-card{padding:24px 20px!important;border-radius:14px!important;}
                  .gb-code{font-size:28px!important;letter-spacing:6px!important;text-indent:6px!important;}
                }
                @media (prefers-color-scheme:dark){
                  .gb-canvas{background:#0B0B12!important;}
                  .gb-card{background:#14141F!important;border-color:#2A2A3C!important;}
                  .gb-text,.gb-card h1{color:#ECECF5!important;}
                  .gb-muted{color:#8A8AA3!important;}
                  .gb-panel{background:#221B3A!important;border-color:#3A2E63!important;}
                  .gb-code{color:#8F66FF!important;}
                  .gb-rule{border-top-color:#2A2A3C!important;}
                }
                """;
    }

    /**
     * HTML-escapes a value for use in text or an attribute.
     *
     * <p>Quotes included, because the subject is interpolated into {@code <title>} and any
     * of these strings could grow an attribute use later.
     */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
