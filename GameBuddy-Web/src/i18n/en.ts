/**
 * English — the source language.
 *
 * Every other dictionary is typed against this one, so this file decides the shape. Two
 * habits worth keeping when adding to it:
 *
 * - **Name keys for meaning, not position.** `hero.title`, not `section1.line1`. These
 *   strings are intended to move into the app's own catalogue later, where "section 1" will
 *   mean nothing.
 * - **Write the English first and translate from it.** Translating from a translation is how
 *   a set of seven languages slowly stops agreeing about what the product does.
 */
export const en = {
  meta: {
    /** The `lang` attribute, and the locale in Open Graph. */
    htmlLang: 'en',
    ogLocale: 'en_US',
  },

  nav: {
    features: 'Features',
    how: 'How it works',
    support: 'Support',
    toggleTheme: 'Switch between light and dark',
    language: 'Language',
  },

  hero: {
    badge: 'Launching on Android first',
    titleBefore: 'Find people who',
    titleHighlight: 'actually play',
    titleAfter: 'what you play',
    subtitle:
      'Not a server full of strangers. GameBuddy matches you on your games, your platform and how you like to play — then gets out of the way so you can go play.',
    comingSoon: 'Coming soon to',
    releaseNote: 'GameBuddy is in the final stages before release. Android comes first.',
    deckAlt: "The GameBuddy deck, showing a gamer's profile with the games they play",
    chatAlt: 'A GameBuddy conversation between two matched players',
  },

  features: {
    title: 'What makes a match a match',
    intro:
      'Matching on a shared interest is easy. Matching on people who can actually play together is the part everything below exists for.',
    matched: {
      title: 'Matched on what you play',
      body: 'GameBuddy learns from the games and play styles on your profile, not from a list of tags you scroll past. The people it shows you own the same games and play them the same way.',
    },
    platform: {
      title: 'Platform-aware',
      body: 'PC, PlayStation, Xbox, Switch or mobile. Two people with identical libraries on different boxes cannot actually play together, so the match takes that into account.',
    },
    chat: {
      title: 'Chat that stays private',
      body: 'Message bodies are encrypted at rest — the database stores ciphertext, not your conversation. Typing indicators and read state work the way you expect.',
    },
    moderated: {
      title: 'Moderated, and adults only',
      body: 'Every uploaded photo is screened before anyone else sees it. Reports get looked at within 24 hours. You must be 18 or over to hold an account, and it is checked against a date of birth.',
    },
    badges: {
      title: 'Badges worth having',
      body: 'Missions and badges you earn by actually using the app, shown on your profile. No pay-to-win tiers.',
    },
    free: {
      title: 'Free to play, properly',
      body: 'The matching, the chat and the communities are free. Gold adds filters and unlimited likes — it does not gate the thing you came for.',
    },
  },

  how: {
    title: 'How it works',
    marketAlt: 'The GameBuddy market, where coins buy profile frames and banners',
    step1: {
      title: 'Say what you play',
      body: 'Pick your games, your platforms and how you like to play — tryhard, completionist, chill co-op. Six screens, about a minute.',
    },
    step2: {
      title: 'Swipe the deck',
      body: 'Real profiles, ranked by how well they actually fit what you said. Pass or match.',
    },
    step3: {
      title: 'Match and play',
      body: 'When you both match, the conversation opens. Then go play something.',
    },
  },

  faq: {
    title: 'Questions',
    q1: { q: 'Is GameBuddy free?', a: 'Yes. Matching, chat and communities are free, with a daily limit on likes. Gold is optional and adds advanced filters, unlimited likes and a few cosmetic extras — it does not put the matching itself behind a paywall.' },
    q2: { q: 'Is it a dating app?', a: 'No. GameBuddy matches people on the games they play and how they play them, so you can find a squad, a co-op partner or a raid group. It is built around playing together, not dating.' },
    q3: { q: 'Which platforms does it support?', a: 'You tell GameBuddy what you play on — PC, PlayStation, Xbox, Switch or mobile — and it matches accordingly, because two people with the same games on different platforms usually cannot play together. The app itself is Android first, with iOS to follow.' },
    q4: { q: 'Why is it 18+?', a: 'GameBuddy puts strangers in private conversation with each other, and that is not something to run for minors alongside adults. There is no younger tier and no supervised mode. Your age is calculated from a date of birth given at registration, and accounts found to belong to under-18s are closed.' },
    q5: { q: 'What do you do about creeps?', a: 'You can block or report anyone from their profile or from a conversation, and reports are reviewed within 24 hours. Every uploaded photo is screened automatically before anybody else can see it. Contact details are stripped out of public text, so nobody can post a phone number into a community.' },
    q6: { q: 'Can other people see my messages?', a: 'No. Message bodies are encrypted before they are stored, so the database holds ciphertext rather than your conversation. Moderators see a message only when you report it.' },
    q7: { q: 'How do I delete my account?', a: 'From Settings inside the app, or by writing to support. Your profile, photo and taste data go; the other half of your conversations stays, because those are not only yours to erase. The full explanation is on the account deletion page.' },
  },

  closing: {
    title: 'Stop playing alone',
    body: 'GameBuddy is free, moderated, and 18+.',
  },

  footer: {
    tagline: 'Find people who actually play what you play',
    builtBy: 'Built in Finland by one person.',
    legalNav: 'Legal and support',
    privacy: 'Privacy',
    terms: 'Terms',
    deleteAccount: 'Delete your account',
    support: 'Support',
    help: 'Help',
    legalContact: 'Legal and privacy',
  },

  legal: {
    /** Shown on /terms and /privacy in every language, because those pages are English only. */
    englishOnly:
      'This document is available in English only. It is a binding agreement, and a translation could change what a clause means — so rather than publish an approximate version, we keep one authoritative text. If anything here is unclear, write to us and we will explain it in your language.',
  },

  support: {
    title: 'Support',
    intro: 'GameBuddy is made by one person, so answers come from a human — usually within a couple of days.',
    emailCta: 'Email',
    tryFirst: 'Try this first',
    writingIn: 'Writing in',
    writingInBody:
      'It helps enormously to include your username, your phone model and Android version, and what you expected to happen instead of what did.',
    reportFaster:
      'Reporting someone is faster in the app. A report from the profile or the conversation carries the context with it and reaches the moderation queue directly; an email about it does not.',
    legalRoute:
      'Legal notices, complaints and data-protection requests go to the address below instead, so they are not sitting behind support questions — those have a statutory deadline.',
    a1: { q: 'I did not get my verification code', a: 'Check the spam folder first — it is almost always there. The code expires, so if it has been a while, go back to the sign-in screen and ask for a new one. Requesting a new code invalidates the old one, so use the most recent email.' },
    a2: { q: 'My deck is empty', a: 'That usually means the filters are narrow — an "online now" filter on a quiet evening can genuinely match nobody. Clear the filters from the deck and see whether people come back. If the deck is empty with no filters set, write to us.' },
    a3: { q: 'I ran out of likes', a: 'Free accounts get a daily allowance, which resets every 24 hours. Gold removes the limit. You can also earn coins in the app and spend them on extras without paying anything.' },
    a4: { q: 'My photo was rejected', a: 'Every uploaded photo is screened before anybody else sees it, and anything sexual is refused — GameBuddy is 18+ but it is not that kind of app. If you think a photo was refused wrongly, write to us and a person will look at it.' },
    a5: { q: 'Someone is harassing me', a: 'Block them from their profile or from the conversation — that is immediate and they are not told. Then report them. Reports are reviewed within 24 hours. If you are in danger, contact your local emergency services first; we are not an emergency service.' },
    a6: { q: 'I paid and did not get what I bought', a: 'Purchases are confirmed by the store and can take a moment to arrive. If it has been more than a few minutes, restart the app — that re-checks your entitlements. Still missing? Write to us with the order number from your Google Play receipt.' },
  },

  del: {
    title: 'Delete your account',
    intro: 'You can close your GameBuddy account at any time, without asking anyone. Here is how, and exactly what happens to your data.',
    inApp: 'In the app',
    step1: 'Open GameBuddy and go to the Profile tab.',
    step2: 'Tap the settings cog, then scroll to Account.',
    step3: 'Choose Delete my account and confirm with your password. The password is required so that a stolen phone cannot destroy your account.',
    noApp: 'If you no longer have the app',
    noAppBody:
      'Write to support from the email address on the account. Deleting the app from your phone does not delete your account, so this matters — an uninstalled account is still visible to other people.',
    formal:
      'For a formal request under the GDPR — access, correction or erasure — write to the legal address. Those are answered within one month, as the law requires.',
    removedTitle: 'What is removed immediately',
    removedNote: "Your profile stops appearing in anyone's deck at once, and the account can no longer sign in anywhere.",
    r1: 'Your username, email address and password',
    r2: 'Your photograph, deleted from storage and not merely unlinked',
    r3: 'Your age, country and gender',
    r4: 'The games, platforms and play styles on your profile',
    r5: 'Your badges and anything you had bought or equipped',
    r6: 'Every device registered for push notifications',
    keptTitle: 'What is not, and why',
    keptIntro:
      'Being straight about this rather than claiming everything disappears — each of these exists to protect somebody, and in two cases that somebody is not you.',
    k1: { what: "Messages you sent, inside other people's conversations", why: 'They are anonymised rather than erased. The other person keeps a record of a conversation they took part in — which is not solely yours to delete — and it stops being attributable to you.' },
    k2: { what: 'Purchase records', why: 'Accounting and tax law requires them to be kept for a fixed period. They are not used for anything else.' },
    k3: { what: 'Material relating to a report that was acted on', why: 'Kept so that somebody removed for harming another user cannot erase the evidence by deleting their account.' },
    knowTitle: 'A few things worth knowing',
    n1: 'Deletion cannot be undone. There is no grace period and no recovery — if you want to come back you register again, from nothing.',
    n2: 'Coins and Gold are not refunded. Refunds are handled by Google Play, not by us, under their policy.',
    n3: 'Under 18? Write to the legal address and the account is closed and its data deleted, without needing a password.',
    fullDetail: 'The full detail is in the privacy policy.',
  },
} as const;
