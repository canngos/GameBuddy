import type { Dictionary } from './index';

/**
 * German.
 *
 * `du` rather than `Sie`. That is a real decision in German and not a casual one: `Sie` is
 * the safe default for business software and would be wrong here — the entire German gaming
 * audience addresses itself with `du`, and `Sie` would make the product sound like a tax
 * portal. It stays `du` even in the legal-adjacent copy on the deletion page, because
 * switching register halfway through a site is worse than either choice consistently.
 */
export const de: Dictionary = {
  meta: { htmlLang: 'de', ogLocale: 'de_DE' },

  nav: {
    features: 'Funktionen',
    how: 'So funktioniert es',
    support: 'Hilfe',
    toggleTheme: 'Zwischen hell und dunkel wechseln',
    language: 'Sprache',
  },

  hero: {
    badge: 'Erscheint zuerst für Android',
    titleBefore: 'Finde Leute, die',
    titleHighlight: 'wirklich spielen',
    titleAfter: 'was du spielst',
    subtitle:
      'Kein Server voller Fremder. GameBuddy bringt dich mit Leuten zusammen, die deine Spiele haben, auf deiner Plattform sind und so spielen wie du — und geht dir danach aus dem Weg.',
    comingSoon: 'Demnächst bei',
    releaseNote: 'GameBuddy steht kurz vor der Veröffentlichung. Android kommt zuerst.',
    deckAlt: 'Der GameBuddy-Stapel mit dem Profil eines Spielers und seinen Spielen',
    chatAlt: 'Die GameBuddy-Unterhaltungsliste mit einem wartenden neuen Match',
  },

  features: {
    title: 'Was ein Match zu einem Match macht',
    intro:
      'Über ein gemeinsames Interesse zu matchen ist leicht. Menschen zusammenzubringen, die tatsächlich miteinander spielen können, ist der schwierige Teil — dafür ist alles hier unten da.',
    matched: {
      title: 'Gematcht nach dem, was du spielst',
      body: 'GameBuddy lernt aus den Spielen und Spielweisen in deinem Profil, nicht aus einer Tag-Liste, an der du vorbeiscrollst. Die Leute, die du siehst, haben dieselben Spiele und spielen sie genauso.',
    },
    platform: {
      title: 'Plattform zählt mit',
      body: 'PC, PlayStation, Xbox, Switch oder Handy. Zwei Leute mit identischer Bibliothek auf verschiedenen Geräten können nicht zusammen spielen — das fließt ins Matching ein.',
    },
    chat: {
      title: 'Chat, der privat bleibt',
      body: 'Nachrichteninhalte werden verschlüsselt gespeichert — in der Datenbank steht Chiffretext, nicht dein Gespräch. Schreibanzeige und Lesestatus funktionieren wie erwartet.',
    },
    moderated: {
      title: 'Moderiert, und nur für Erwachsene',
      body: 'Jedes hochgeladene Foto wird geprüft, bevor es jemand anders sieht. Meldungen werden innerhalb von 24 Stunden bearbeitet. Für ein Konto musst du mindestens 18 sein, und das wird am Geburtsdatum geprüft.',
    },
    badges: {
      title: 'Abzeichen, die etwas bedeuten',
      body: 'Aufgaben und Abzeichen, die du dir durch echte Nutzung verdienst und im Profil zeigst. Keine Stufen, die man sich kauft.',
    },
    free: {
      title: 'Kostenlos, richtig kostenlos',
      body: 'Matching, Chat und Communities sind kostenlos. Gold bringt Filter und unbegrenzte Likes — es sperrt nicht das weg, wofür du gekommen bist.',
    },
  },

  how: {
    title: 'So funktioniert es',
    marketAlt: 'Der GameBuddy-Markt, in dem Münzen Profilrahmen und Banner kaufen',
    step1: {
      title: 'Sag, was du spielst',
      body: 'Wähle deine Spiele, deine Plattformen und deinen Stil — Tryhard, Completionist, entspanntes Co-op. Sechs Schritte, etwa eine Minute.',
    },
    step2: {
      title: 'Durch den Stapel wischen',
      body: 'Echte Profile, sortiert danach, wie gut sie wirklich zu deinen Angaben passen. Weiter oder Match.',
    },
    step3: {
      title: 'Matchen und spielen',
      body: 'Wenn ihr euch beide matcht, öffnet sich der Chat. Und dann: spielen.',
    },
  },

  faq: {
    title: 'Fragen',
    q1: { q: 'Ist GameBuddy kostenlos?', a: 'Ja. Matching, Chat und Communities sind kostenlos, mit einem täglichen Limit an Likes. Gold ist optional und bringt erweiterte Filter, unbegrenzte Likes und ein paar kosmetische Extras — es stellt das Matching selbst nicht hinter eine Bezahlschranke.' },
    q2: { q: 'Ist das eine Dating-App?', a: 'Nein. GameBuddy bringt Menschen über ihre Spiele und ihre Spielweise zusammen, damit du eine Gruppe, einen Co-op-Partner oder einen Raid findest. Es ist fürs gemeinsame Spielen gebaut, nicht fürs Daten.' },
    q3: { q: 'Welche Plattformen werden unterstützt?', a: 'Du sagst GameBuddy, worauf du spielst — PC, PlayStation, Xbox, Switch oder Handy — und das Matching richtet sich danach, weil dieselben Spiele auf verschiedenen Plattformen meist nicht zusammen spielbar sind. Die App selbst kommt zuerst für Android, iOS folgt.' },
    q4: { q: 'Warum ab 18?', a: 'GameBuddy bringt Fremde in private Gespräche miteinander, und das sollte man nicht für Minderjährige neben Erwachsenen betreiben. Es gibt keine jüngere Stufe und keinen beaufsichtigten Modus. Das Alter wird aus dem bei der Registrierung angegebenen Geburtsdatum berechnet, und Konten von unter 18-Jährigen werden geschlossen.' },
    q5: { q: 'Was tut ihr gegen Belästigung?', a: 'Du kannst jede Person aus dem Profil oder dem Gespräch heraus blockieren oder melden, und Meldungen werden innerhalb von 24 Stunden geprüft. Jedes hochgeladene Foto wird automatisch geprüft, bevor es jemand sehen kann. Kontaktdaten werden aus öffentlichem Text entfernt, sodass niemand eine Telefonnummer in eine Community stellen kann.' },
    q6: { q: 'Können andere meine Nachrichten sehen?', a: 'Nein. Nachrichteninhalte werden vor dem Speichern verschlüsselt, in der Datenbank steht also Chiffretext statt deines Gesprächs. Moderatoren sehen eine Nachricht nur, wenn du sie meldest.' },
    q7: { q: 'Wie lösche ich mein Konto?', a: 'In den Einstellungen der App, oder per Mail an den Support. Dein Profil, dein Foto und deine Vorlieben verschwinden; die andere Hälfte deiner Gespräche bleibt, weil sie nicht allein dir gehört. Die vollständige Erklärung steht auf der Seite zur Kontolöschung.' },
  },

  closing: {
    title: 'Hör auf, allein zu spielen',
    body: 'GameBuddy ist kostenlos, moderiert und ab 18.',
  },

  footer: {
    tagline: 'Finde Leute, die wirklich spielen was du spielst',
    builtBy: 'In Finnland von einer Person gebaut.',
    legalNav: 'Rechtliches und Hilfe',
    privacy: 'Datenschutz',
    terms: 'Nutzungsbedingungen',
    deleteAccount: 'Konto löschen',
    support: 'Hilfe',
    help: 'Hilfe',
    legalContact: 'Recht und Datenschutz',
  },

  legal: {
    englishOnly:
      'Dieses Dokument gibt es nur auf Englisch. Es ist eine bindende Vereinbarung, und eine Übersetzung könnte die Bedeutung einer Klausel verändern — statt eine ungefähre Fassung zu veröffentlichen, halten wir uns an einen maßgeblichen Text. Wenn etwas unklar ist, schreib uns und wir erklären es in deiner Sprache.',
  },

  support: {
    title: 'Hilfe',
    intro: 'GameBuddy wird von einer Person gemacht, Antworten kommen also von einem Menschen — meist innerhalb von ein paar Tagen.',
    emailCta: 'Schreib an',
    tryFirst: 'Probier zuerst das',
    writingIn: 'Wenn du schreibst',
    writingInBody:
      'Es hilft enorm, wenn du deinen Benutzernamen, dein Handymodell und deine Android-Version nennst — und was du erwartet hattest statt dessen, was passiert ist.',
    reportFaster:
      'Jemanden zu melden geht in der App schneller. Eine Meldung aus dem Profil oder dem Gespräch nimmt den Zusammenhang mit und landet direkt in der Moderationswarteschlange; eine Mail darüber nicht.',
    legalRoute:
      'Rechtliche Mitteilungen, Beschwerden und Datenschutzanfragen gehen stattdessen an die Adresse unten, damit sie nicht hinter Supportfragen liegen bleiben — für sie gilt eine gesetzliche Frist.',
    a1: { q: 'Ich habe keinen Bestätigungscode bekommen', a: 'Sieh zuerst im Spam-Ordner nach — dort ist er fast immer. Der Code läuft ab, wenn also Zeit vergangen ist, geh zurück zum Anmeldebildschirm und fordere einen neuen an. Ein neuer Code macht den alten ungültig, nimm also die neueste Mail.' },
    a2: { q: 'Mein Stapel ist leer', a: 'Meist sind die Filter zu eng — ein „jetzt online"-Filter an einem ruhigen Abend kann tatsächlich niemanden treffen. Setz die Filter zurück und schau, ob Leute wiederkommen. Ist der Stapel ohne Filter leer, schreib uns.' },
    a3: { q: 'Meine Likes sind aufgebraucht', a: 'Kostenlose Konten haben ein Tageskontingent, das sich alle 24 Stunden zurücksetzt. Gold hebt das Limit auf. Du kannst auch in der App Münzen verdienen und sie für Extras ausgeben, ohne etwas zu zahlen.' },
    a4: { q: 'Mein Foto wurde abgelehnt', a: 'Jedes hochgeladene Foto wird geprüft, bevor es jemand anders sieht, und alles Sexuelle wird abgelehnt — GameBuddy ist ab 18, aber nicht diese Art App. Wenn du meinst, ein Foto wurde zu Unrecht abgelehnt, schreib uns und ein Mensch sieht es sich an.' },
    a5: { q: 'Jemand belästigt mich', a: 'Blockiere die Person aus dem Profil oder dem Gespräch — das wirkt sofort und sie erfährt nichts davon. Melde sie danach. Meldungen werden innerhalb von 24 Stunden geprüft. Wenn du in Gefahr bist, wende dich zuerst an den Notruf; wir sind kein Notdienst.' },
    a6: { q: 'Ich habe bezahlt und nichts bekommen', a: 'Käufe werden vom Store bestätigt, das kann einen Moment dauern. Sind mehr als ein paar Minuten vergangen, starte die App neu — dann werden deine Berechtigungen erneut geprüft. Fehlt es immer noch? Schreib uns mit der Bestellnummer von deinem Google-Play-Beleg.' },
  },

  del: {
    title: 'Konto löschen',
    intro: 'Du kannst dein GameBuddy-Konto jederzeit schließen, ohne jemanden zu fragen. So geht es, und das passiert mit deinen Daten.',
    inApp: 'In der App',
    step1: 'Öffne GameBuddy und geh auf den Tab Profil.',
    step2: 'Tippe auf das Zahnrad und scroll zu Konto.',
    step3: 'Wähle Konto löschen und bestätige mit deinem Passwort. Das Passwort wird verlangt, damit ein gestohlenes Handy dein Konto nicht zerstören kann.',
    noApp: 'Wenn du die App nicht mehr hast',
    noAppBody:
      'Schreib von der E-Mail-Adresse des Kontos an den Support. Die App vom Handy zu löschen löscht das Konto nicht — und das ist wichtig, denn ein Konto ohne installierte App ist für andere weiter sichtbar.',
    formal:
      'Für einen förmlichen Antrag nach DSGVO — Auskunft, Berichtigung oder Löschung — schreib an die Rechtsadresse. Die werden innerhalb eines Monats beantwortet, wie das Gesetz es verlangt.',
    removedTitle: 'Was sofort entfernt wird',
    removedNote: 'Dein Profil verschwindet sofort aus allen Stapeln, und mit dem Konto ist keine Anmeldung mehr möglich.',
    r1: 'Dein Benutzername, deine E-Mail-Adresse und dein Passwort',
    r2: 'Dein Foto, aus dem Speicher gelöscht und nicht nur entkoppelt',
    r3: 'Dein Alter, dein Land und dein Geschlecht',
    r4: 'Die Spiele, Plattformen und Spielweisen in deinem Profil',
    r5: 'Deine Abzeichen und alles Gekaufte oder Ausgerüstete',
    r6: 'Jedes für Push-Nachrichten registrierte Gerät',
    keptTitle: 'Was nicht, und warum',
    keptIntro:
      'Lieber ehrlich, als zu behaupten, alles verschwinde — jeder dieser Punkte schützt jemanden, und in zwei Fällen bist dieser Jemand nicht du.',
    k1: { what: 'Nachrichten, die du in Gesprächen anderer geschrieben hast', why: 'Sie werden anonymisiert statt gelöscht. Die andere Person behält den Verlauf eines Gesprächs, an dem sie beteiligt war — der gehört nicht allein dir — und er lässt sich dir nicht mehr zuordnen.' },
    k2: { what: 'Kaufunterlagen', why: 'Handels- und Steuerrecht verlangt, sie eine bestimmte Zeit aufzubewahren. Für etwas anderes werden sie nicht genutzt.' },
    k3: { what: 'Material zu einer Meldung, auf die reagiert wurde', why: 'Bleibt erhalten, damit jemand, der wegen Schädigung einer anderen Person entfernt wurde, die Beweise nicht durch Kontolöschung tilgen kann.' },
    knowTitle: 'Ein paar Dinge, die du wissen solltest',
    n1: 'Die Löschung lässt sich nicht rückgängig machen. Keine Frist, keine Wiederherstellung — wer zurückkommen will, registriert sich neu, bei null.',
    n2: 'Münzen und Gold werden nicht erstattet. Erstattungen wickelt Google Play nach eigener Richtlinie ab, nicht wir.',
    n3: 'Unter 18? Schreib an die Rechtsadresse, dann wird das Konto geschlossen und die Daten gelöscht, ganz ohne Passwort.',
    fullDetail: 'Alle Einzelheiten stehen in der Datenschutzerklärung.',
  },
};
