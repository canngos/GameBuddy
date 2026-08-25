package com.gamebuddy.shared.moderation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("TextModerationService")
class TextModerationServiceTest {

    private final TextModerationService service = new TextModerationService();

    private TextAssessment publicText(String text) {
        return service.screen(text, TextSurface.PUBLIC);
    }

    private TextAssessment privateText(String text) {
        return service.screen(text, TextSurface.PRIVATE);
    }

    @Nested
    @DisplayName("masking profanity")
    class Masking {

        @Test
        @DisplayName("the word is replaced, the rest of the sentence survives")
        void masksTheWordOnly() {
            TextAssessment result = privateText("that boss fight was shit honestly");

            assertFalse(result.blocked());
            assertTrue(result.modified());
            assertEquals("that boss fight was **** honestly", result.cleaned());
        }

        @ParameterizedTest
        @DisplayName("the obvious ways round it")
        @ValueSource(strings = {"f.u.c.k", "F U C K", "fuuuuck", "fück", "FUCK", "f_u_c_k"})
        void defeatsEvasion(String written) {
            TextAssessment result = privateText("you " + written + " me");

            assertFalse(result.blocked());
            assertTrue(result.modified(), written + " was not masked");
            assertFalse(result.cleaned().toLowerCase().contains("fuck"));
        }

        @Test
        @DisplayName("clean text comes back untouched and says so")
        void leavesCleanTextAlone() {
            String clean = "want to run a dungeon later? I'm on around 8";
            TextAssessment result = privateText(clean);

            assertFalse(result.blocked());
            assertFalse(result.modified());
            assertEquals(clean, result.cleaned());
        }

        @Test
        @DisplayName("a word that merely contains a masked one is left alone")
        void noScunthorpe() {
            // "cock" is on the profanity list; "cockpit" is a word about aeroplanes.
            assertFalse(privateText("get in the cockpit").modified());
            // "spic" and "pedo" are blocked words, and both hide inside ordinary English.
            assertFalse(privateText("that looks suspicious").blocked());
            assertFalse(privateText("my pedometer says 9000 steps").blocked());
        }
    }

    @Nested
    @DisplayName("the other six languages")
    class Multilingual {

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("profanity is masked whatever language it is in")
        @CsvSource({
            "fi, vittu",
            "sv, fitta",
            "de, scheisse",
            "fr, putain",
            "es, mierda",
            "tr, siktir",
        })
        void masksEachLanguage(String language, String word) {
            TextAssessment result = privateText("gg " + word + " lol");

            assertFalse(result.blocked());
            assertTrue(result.modified(), language + ": '" + word + "' was not masked");
            assertFalse(result.cleaned().toLowerCase().contains(word), language + ": the word survived");
        }

        @ParameterizedTest
        @DisplayName("evasion works the same in Turkish, including the dotted capital I")
        @ValueSource(strings = {"s!ktir", "siktiiiir", "s i k t i r", "SİKTİR", "sıktır"})
        void defeatsTurkishEvasion(String written) {
            TextAssessment result = privateText("ya " + written + " be");

            assertFalse(result.blocked());
            assertTrue(result.modified(), written + " was not masked");
        }

        @ParameterizedTest
        @DisplayName("German sharp s folds onto ss")
        @ValueSource(strings = {"scheiße", "SCHEISSE", "sche!sse", "scheisssse"})
        void defeatsGermanEvasion(String written) {
            assertTrue(privateText("das ist " + written).modified(), written + " was not masked");
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @DisplayName("ordinary sentences in those languages are left alone")
        @ValueSource(
                strings = {
                    "filmen är slut i morgon", // sv: "slut" = end. The allowlist case.
                    "biljetterna är slutsålda", // sv: sold out
                    "tengo 25 años y juego mucho", // es: "años" folds near "anos"
                    "le concours commence à huit heures", // fr: "con" hides inside "concours"
                    "kiitos paljon, pelataan huomenna", // fi
                    "sık sık oyun oynuyorum", // tr: "sık" folds onto "sik"
                    "die Analyse ist fertig", // de
                })
        void leavesOrdinaryForeignTextAlone(String sentence) {
            TextAssessment result = privateText(sentence);

            assertFalse(result.blocked(), sentence + " was blocked");
            assertFalse(result.modified(), sentence + " was masked: " + result.cleaned());
        }
    }

    @Nested
    @DisplayName("blocking")
    class Blocking {

        @Test
        void refusesASlur() {
            assertTrue(privateText("shut up you faggot").blocked());
        }

        @Test
        @DisplayName("a slur is refused however it is spelled")
        void refusesAnObfuscatedSlur() {
            assertTrue(privateText("f a g g o t").blocked());
            assertTrue(privateText("f4gg0t").blocked());
        }

        @Test
        @DisplayName("a phrase split by spaces, which the word list cannot see")
        void refusesAPhrase() {
            assertTrue(privateText("just kill yourself").blocked());
            assertTrue(privateText("k i l l   y o u r s e l f").blocked());
        }

        @Test
        @DisplayName("blocked beats masked: nothing is stored, not even asterisks")
        void blockedTextIsNotReturned() {
            TextAssessment result = privateText("you shit faggot");

            assertTrue(result.blocked());
            assertEquals("", result.cleaned());
        }
    }

    @Nested
    @DisplayName("contact details")
    class ContactDetails {

        @Test
        @DisplayName("stripped from a public post")
        void redactedOnPublicSurfaces() {
            TextAssessment result = publicText("add me: bob@example.com or 040 123 4567, see gamebuddy.example");

            assertFalse(result.blocked());
            assertFalse(result.cleaned().contains("bob@example.com"));
            assertFalse(result.cleaned().contains("040 123 4567"));
        }

        @Test
        @DisplayName("left alone in private chat — swapping tags is what the app is for")
        void keptInPrivateChat() {
            String message = "sure, add me on discord, it's canb#1234, or bob@example.com";
            TextAssessment result = privateText(message);

            assertFalse(result.blocked());
            assertEquals(message, result.cleaned());
        }

        @Test
        @DisplayName("a number that is not a phone number stays")
        void doesNotEatOrdinaryNumbers() {
            assertFalse(publicText("we won 3-1 and I hit 240k damage").modified());
        }
    }

    @Nested
    @DisplayName("identifiers")
    class Identifiers {

        @Test
        void rejectsAnythingThatWouldBeMaskedOrBlocked() {
            assertFalse(service.isCleanIdentifier("fuckerpants"));
            assertFalse(service.isCleanIdentifier("faggot"));
        }

        @Test
        void acceptsAnOrdinaryName() {
            assertTrue(service.isCleanIdentifier("can_baturlar"));
            assertTrue(service.isCleanIdentifier("ShadowStrike99"));
        }

        @Test
        @DisplayName("foreign profanity in a handle is refused too")
        void rejectsForeignProfanity() {
            assertFalse(service.isCleanIdentifier("vittupaa"));
            assertFalse(service.isCleanIdentifier("SiktirGamer"));
        }

        @Test
        @DisplayName("the allowlist reaches the substring pass, so Swedish handles survive")
        void acceptsAllowlistedWordInsideAHandle() {
            assertTrue(service.isCleanIdentifier("Slutspurt99"));
        }

        @ParameterizedTest
        @DisplayName("a three-letter entry is not hunted for inside a name")
        @ValueSource(strings = {"EpicGamer", "Diana", "Emily", "Picasso", "Anakin", "Bokchoy"})
        void doesNotRefuseOrdinaryNamesForShortEntries(String handle) {
            // "pic", "ana", "emi" and "bok" are all real entries — as whole words. Matching
            // them inside a handle refuses half the names people actually pick.
            assertTrue(service.isCleanIdentifier(handle), handle + " should be an acceptable username");
        }

        @Test
        @DisplayName("but a short entry is still masked when it is the whole word")
        void stillMasksShortEntriesAsWholeWords() {
            assertTrue(privateText("amk ya").modified());
        }
    }

    @Test
    @DisplayName("empty and null are not errors")
    void handlesEmpty() {
        assertFalse(privateText("").blocked());
        assertFalse(privateText(null).blocked());
        assertEquals("", privateText(null).cleaned());
    }
}
