package com.gamebuddy.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * That the assembled message is actually a well-formed branded email.
 *
 * <p>The parts of this that can break are invisible from the outside: a message that is
 * HTML-only, a logo attached under a {@code cid} the HTML does not reference, a From header
 * with no display name. All three send successfully and all three look wrong in an inbox,
 * so they are asserted on the built {@link MimeMessage} rather than trusted.
 */
@ExtendWith(MockitoExtension.class)
class MailerTest {

    @Mock
    private JavaMailSender mailSender;

    private Mailer mailer;

    private static final EmailContent CONTENT = EmailContent.withCode(
            "123456 is your GameBuddy code",
            "Confirm your address.",
            "Confirm your email",
            "Enter this code in the app.",
            "123456",
            "The code expires in 15 minutes.",
            "If you did not sign up, ignore this email.");

    @BeforeEach
    void setUp() {
        mailer = new Mailer(mailSender);
        ReflectionTestUtils.setField(mailer, "from", "noreply@mail.findgamebuddy.com");
        ReflectionTestUtils.setField(mailer, "fromName", "GameBuddy");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    }

    private MimeMessage sendAndCapture() {
        mailer.send("player@example.com", CONTENT);
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage message = captor.getValue();
        try {
            // A real sender calls this before transmitting, and it is what writes the
            // Content-Type headers. Inspecting the message without it means asking every
            // part its type and being told "text/plain" all the way down.
            message.saveChanges();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return message;
    }

    /** Every leaf part, flattened, so the assertions can talk about content types. */
    private static List<Part> leaves(Part part) throws Exception {
        List<Part> found = new ArrayList<>();
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                found.addAll(leaves(multipart.getBodyPart(i)));
            }
        } else {
            found.add(part);
        }
        return found;
    }

    @Test
    @DisplayName("the message carries both a text and an HTML body")
    void isMultipartAlternative() throws Exception {
        List<Part> parts = leaves(sendAndCapture());

        assertThat(parts).anySatisfy(p -> assertThat(p.isMimeType("text/plain")).isTrue());
        assertThat(parts).anySatisfy(p -> assertThat(p.isMimeType("text/html")).isTrue());
    }

    @Test
    @DisplayName("the mark travels with the message, under the cid the HTML uses")
    void logoIsInlineUnderTheExpectedCid() throws Exception {
        MimeMessage message = sendAndCapture();

        Part logo = leaves(message).stream()
                .filter(p -> {
                    try {
                        return p.isMimeType("image/png");
                    } catch (Exception e) {
                        return false;
                    }
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("no image part was attached"));

        // Angle brackets are how Content-ID is written on the wire; the HTML refers to the
        // bare name. Getting this pair wrong is the classic broken-image-in-email bug.
        assertThat(logo.getHeader("Content-ID")[0]).isEqualTo("<" + EmailRenderer.LOGO_CID + ">");

        String html = leaves(message).stream()
                .filter(p -> {
                    try {
                        return p.isMimeType("text/html");
                    } catch (Exception e) {
                        return false;
                    }
                })
                .findFirst()
                .map(p -> {
                    try {
                        return String.valueOf(p.getContent());
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                })
                .orElseThrow();
        assertThat(html).contains("cid:" + EmailRenderer.LOGO_CID);
    }

    @Test
    @DisplayName("the inbox shows a name, not a noreply address")
    void fromHasADisplayName() throws Exception {
        InternetAddress from = (InternetAddress) sendAndCapture().getFrom()[0];

        assertThat(from.getPersonal()).isEqualTo("GameBuddy");
        assertThat(from.getAddress()).isEqualTo("noreply@mail.findgamebuddy.com");
    }

    @Test
    @DisplayName("subject and recipient are set from the arguments")
    void addressesTheRecipient() throws Exception {
        MimeMessage message = sendAndCapture();

        assertThat(message.getSubject()).isEqualTo(CONTENT.subject());
        assertThat(message.getAllRecipients()[0]).hasToString("player@example.com");
    }

    @Test
    @DisplayName("a dead relay surfaces as a MailException, which is what callers handle")
    void sendFailurePropagates() {
        org.mockito.Mockito.doThrow(new MailSendException("relay down"))
                .when(mailSender)
                .send(org.mockito.ArgumentMatchers.any(MimeMessage.class));

        assertThatThrownBy(() -> mailer.send("player@example.com", CONTENT)).isInstanceOf(MailSendException.class);
    }
}
