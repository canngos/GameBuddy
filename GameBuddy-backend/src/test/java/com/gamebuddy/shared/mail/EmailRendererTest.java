package com.gamebuddy.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What the two bodies have to guarantee.
 *
 * <p>Not a test of the design — nothing here asserts a colour or a pixel, because those are
 * judgements and a test that pins them only makes the design harder to change. What is
 * tested is the part that can be silently wrong: that both bodies carry the same facts,
 * that user-supplied text cannot escape into markup, and that the plain-text half stays in
 * the shape everything downstream reads it in.
 */
class EmailRendererTest {

    private static final EmailContent CODE_MAIL = EmailContent.withCode(
            "123456 is your GameBuddy code",
            "Confirm your address.",
            "Confirm your email",
            "Enter this code in the app.",
            "123456",
            "The code expires in 15 minutes.",
            "If you did not sign up, ignore this email.");

    @Nested
    class BothParts {

        @Test
        @DisplayName("the code appears in the HTML and in the text, or one of them is a lie")
        void codeIsInBoth() {
            assertThat(EmailRenderer.toHtml(CODE_MAIL)).contains("123456");
            assertThat(EmailRenderer.toPlainText(CODE_MAIL)).contains("123456");
        }

        @Test
        @DisplayName("every sentence of the content reaches both bodies")
        void sentencesAreInBoth() {
            String html = EmailRenderer.toHtml(CODE_MAIL);
            String text = EmailRenderer.toPlainText(CODE_MAIL);

            for (String sentence :
                    List.of(CODE_MAIL.heading(), CODE_MAIL.intro(), CODE_MAIL.codeCaption(), CODE_MAIL.footnote())) {
                assertThat(html).contains(sentence);
                assertThat(text).contains(sentence);
            }
        }

        @Test
        @DisplayName("a message with no code renders without an empty panel")
        void noticeHasNoCodePanel() {
            EmailContent notice = EmailContent.notice(
                    "Your GameBuddy password was changed",
                    "Every device was signed out.",
                    "Your password was changed",
                    "The password for me@example.com was just changed.",
                    List.of("If that was you, there is nothing else to do."),
                    "If it was not, reset it again straight away.");

            assertThat(notice.hasCode()).isFalse();
            // The class, not the name: the style block always defines .gb-code, and only
            // the panel itself uses it.
            assertThat(EmailRenderer.toHtml(notice)).doesNotContain("class=\"gb-code\"");
            assertThat(EmailRenderer.toHtml(notice)).contains("If that was you");
            assertThat(EmailRenderer.toPlainText(notice)).contains("If that was you");
        }
    }

    @Nested
    class PlainText {

        /**
         * The console in {@code MAIL_MODE=log} prints this body, and the functional suite
         * reads the code out of it with {@code \b(\d{6})\b} — see
         * {@code qa/functional/helpers/db.js}. A code wrapped in prose on a shared line
         * would still match, but a code that picked up punctuation would not.
         */
        @Test
        @DisplayName("the code sits alone on its own line")
        void codeIsOnItsOwnLine() {
            assertThat(EmailRenderer.toPlainText(CODE_MAIL).lines()).contains("123456");
        }

        @Test
        @DisplayName("no markup leaks into the text part")
        void textCarriesNoTags() {
            assertThat(EmailRenderer.toPlainText(CODE_MAIL)).doesNotContain("<").doesNotContain("&amp;");
        }
    }

    @Nested
    class Escaping {

        /**
         * Two of these messages echo the recipient's own address back at them. An address is
         * user-supplied text arriving in an HTML document, and the fact that the document is
         * an email changes nothing about that.
         */
        @Test
        @DisplayName("an address that looks like markup is escaped, not rendered")
        void addressesCannotInjectMarkup() {
            EmailContent hostile = EmailContent.notice(
                    "Your GameBuddy password was changed",
                    "preheader",
                    "Your password was changed",
                    "The password for <script>alert('x')</script>@example.com was changed.",
                    List.of(),
                    "footnote");

            String html = EmailRenderer.toHtml(hostile);

            assertThat(html).doesNotContain("<script>");
            assertThat(html).contains("&lt;script&gt;");
            assertThat(html).contains("&#39;");
        }

        @Test
        @DisplayName("an ampersand in the subject does not break the title")
        void ampersandsAreEscaped() {
            EmailContent amp = EmailContent.notice("Rock & Roll", "p", "h", "i", List.of(), "f");
            assertThat(EmailRenderer.toHtml(amp)).contains("<title>Rock &amp; Roll</title>");
        }
    }

    @Nested
    class Structure {

        @Test
        @DisplayName("the mark is referenced by the cid the sender attaches it under")
        void logoIsReferencedByCid() {
            // These two constants are the contract between the renderer and Mailer; if they
            // drift the message renders with a broken image and nothing else complains.
            assertThat(EmailRenderer.toHtml(CODE_MAIL)).contains("src=\"cid:" + EmailRenderer.LOGO_CID + "\"");
        }

        @Test
        @DisplayName("the logo carries alt text, for the clients that block images")
        void logoHasAltText() {
            assertThat(EmailRenderer.toHtml(CODE_MAIL)).contains("alt=\"GameBuddy\"");
        }

        @Test
        @DisplayName("the preheader is present and hidden")
        void preheaderIsHidden() {
            String html = EmailRenderer.toHtml(CODE_MAIL);
            assertThat(html).contains(CODE_MAIL.preheader());
            assertThat(html).contains("mso-hide:all");
        }

        @Test
        @DisplayName("layout is inline, so a client that drops the style block still renders it")
        void criticalStylingIsInline() {
            String html = EmailRenderer.toHtml(CODE_MAIL);
            // The two that decide whether the message looks designed or looks broken.
            assertThat(html).contains("style=\"background:#FFFFFF");
            assertThat(html).contains("width=\"600\"");
        }
    }
}
