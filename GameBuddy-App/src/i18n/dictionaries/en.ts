/**
 * English — the source language.
 *
 * Every other dictionary is typed against this one, so this file decides the shape: add a
 * key here and the build fails until all six others have it. That is the point. The
 * alternative is an app that renders `undefined` in Swedish for a month because nobody on
 * the team reads Swedish.
 *
 * Two habits worth keeping, carried over from the website's catalogue
 * (`GameBuddy-Web/src/i18n`), which these keys deliberately mirror:
 *
 * - **Name keys for meaning, not position.** `auth.login.title`, never `screen2.line1`.
 * - **Write the English first and translate from it.** Translating from a translation is
 *   how seven languages slowly stop agreeing about what the product does.
 *
 * Copy that varies with a value is a function, so each language keeps its own word order
 * and plural rules: `seats(2, 5)` is "2/5 players" here and something else elsewhere.
 *
 * **Not translated, on purpose:** the Terms and the Privacy Policy. They are binding
 * documents and a mistranslated liability or GDPR clause is a document nobody intended to
 * publish — the website says so on the page, and the app links out to the same English
 * originals.
 */
export const en = {
  common: {
    back: 'Back',
    cancel: 'Cancel',
    save: 'Save',
    remove: 'Remove',
    close: 'Close',
    retry: 'Try again',
    continue: 'Continue',
    done: 'Done',
    somethingWentWrong: 'Something went wrong. Please try again.',
  },

  welcome: {
    titleTop: 'Find your',
    tagline: 'People who play what you play, at the hours you actually play.',
    matchedTitle: 'Matched on taste',
    matchedBody: 'Ranked by what you play, not who is nearby.',
    lobbyTitle: 'Open a lobby',
    lobbyBody: "Say what you're playing and when — you pick who joins.",
    chatTitle: 'Chat after a match',
    chatBody: 'Both of you have to say yes first.',
    createAccount: 'Create an account',
    haveAccount: 'I already have one',
  },

  auth: {
    stepOne: 'STEP 1',
    stepTwo: 'STEP 2',

    login: {
      title: 'Welcome back',
      subtitle: 'Sign in with your username or email.',
      identifier: 'Username or email',
      password: 'Password',
      submit: 'Sign in',
      needsCode: 'We can email a new code to finish setting up this account.',
      emailCode: 'Email me a code',
    },

    register: {
      title: 'Create your account',
      email: 'Email',
      password: 'Password',
      passwordHint: 'At least 8 characters, with a letter and a number.',
      signInInstead: 'Sign in instead',
    },

    verify: {
      title: 'Check your email',
      /** The address is rendered in bold inside the sentence, hence the split. */
      sentCodeBefore: 'We sent a six-digit code to',
      code: 'Verification code',
      submit: 'Verify',
      resend: 'Send a new code',
    },
  },

  tabs: {
    home: 'Home',
    lobby: 'Lobby',
    messages: 'Messages',
    market: 'Market',
    profile: 'Profile',
  },

  settings: {
    title: 'Settings',
    sectionProfile: 'Profile',
    sectionAppearance: 'Appearance',
    sectionNotifications: 'Notifications',
    sectionAbout: 'About',
    sectionAccount: 'Account',
    avatar: 'Avatar',
    avatarHint: 'Change your picture',
    birthDate: 'Date of birth',
    birthDateHint: 'Confirms you are 18 or over',
    games: 'Games',
    platforms: 'Platforms',
    platformsUnset: 'Not set — other players filter by this',
    keywords: 'Keywords',
    selected: (n: number) => `${n} selected`,
    themeSystem: 'System',
    themeSystemHint: 'Match your phone',
    themeLight: 'Light',
    themeLightHint: 'Always light',
    themeDark: 'Dark',
    themeDarkHint: 'Always dark',
    themeNote: 'The theme applies immediately and is remembered on this device.',
    notificationsRow: 'What we send you',
    notificationsHint: 'Messages, matches, reminders',
    tutorial: 'Show the tutorial again',
    tutorialHint: 'Walks you through the five tabs',
    terms: 'Terms of Service',
    termsHint: 'Including the rules on content and conduct',
    privacy: 'Privacy Policy',
    privacyHint: 'What we collect, and what we do with it',
    password: 'Password',
    passwordHint: 'Signs you out everywhere',
    blocked: 'Blocked',
    blockedHint: 'Who you have blocked',
    signOut: 'Sign out',
    deleteAccount: 'Delete my account',
    deleteTitle: 'This cannot be undone',
    deleteBody:
      'Your profile, matches and messages are removed. Enter your password to confirm — a stolen phone should not be enough to do this.',
    keepAccount: 'Keep my account',
  },

  ui: {
    showPassword: 'Show',
    hidePassword: 'Hide',
    showPasswordA11y: 'Show password',
    hidePasswordA11y: 'Hide password',
    reportProfileTitle: 'Report this profile',
    reportMessageTitle: 'Report this message',
    reportBlurb: 'A moderator will look at it. You can only report something once.',
    reportCancelA11y: 'Cancel report',
    reasonHarassment: 'Harassment or abuse',
    reasonSexual: 'Sexual content',
    reasonSpam: 'Spam or advertising',
    reasonMinor: 'Someone may be a minor',
    reasonOther: 'Something else',
  },

  errors: {
    generic: 'Something went wrong. Please try again.',
    network: 'Could not reach GameBuddy. Check your connection and try again.',
    sessionExpired: 'Your session has expired. Please sign in again.',
    forbidden: 'You do not have access to this.',
    emptyResponse: 'The server returned an empty response.',
    unexpectedResponse: 'The server returned an unexpected response.',
    byCode: {
      '101': 'That email already has an account.',
      '103': 'No account found.',
      '105': 'That code is not right.',
      '106': 'This account has not been verified yet.',
      '107': 'That username is taken.',
      '108': 'Wrong username or password.',
      '113': 'This account is blocked.',
      '121': 'This gamer has blocked you.',
      '129': 'Not enough coins.',
      '144': 'That code has expired. Ask for a new one.',
      '145': 'Too many attempts. Wait a little and try again.',
      '146': 'Too many requests. Please wait a moment.',
      '147': 'Pick a stronger password.',
      '150': 'Your current password is not right.',
      '153': 'You have not matched with this gamer.',
      '157': 'You have already reported this.',
      '158': "You are out of likes for today.",
      '159': 'This is part of GameBuddy Gold.',
      '163': 'You are out of swipes for today.',
      '168': 'You must be at least 18 to use GameBuddy.',
      '170': 'That message breaks the rules.',
      '179': 'This lobby is already full.',
      '180': 'This lobby is no longer open.',
      '183': 'Finish or cancel your current lobby first.',
      '185': 'The owner already answered your request.',
    },
  },

  language: {
    label: 'Language',
    /** The picker's own title, for screen readers. */
    choose: 'Choose a language',
  },
};

/**
 * The shape every language must fill.
 *
 * Deliberately not `as const`: that would freeze each value to its literal English text
 * and make "Zurück" a type error in the German file. What is being shared is the set of
 * keys and the type of each value, not the words.
 */
export type Dictionary = typeof en;
