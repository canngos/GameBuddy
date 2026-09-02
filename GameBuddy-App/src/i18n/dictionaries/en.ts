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
/**
 * One piece of the 18+/Terms consent sentence. The sentence is stored as ordered
 * segments rather than one string with markers, because each language needs to place
 * the bold age phrase and the two document links wherever its own grammar puts them —
 * Turkish ends the sentence with the verb, and a fixed English frame cannot.
 */
export type ConsentSegment = {
  text: string;
  bold?: boolean;
  link?: 'terms' | 'privacy';
};

export const en = {
  /**
   * The BCP-47 tag handed to `toLocaleString` and friends, so dates and lists follow the
   * language chosen *in the app* — the device locale and the app language are allowed to
   * disagree, and a Turkish reader with an English phone still deserves Turkish dates.
   */
  locale: 'en',

  common: {
    back: 'Back',
    cancel: 'Cancel',
    save: 'Save',
    remove: 'Remove',
    close: 'Close',
    retry: 'Try again',
    continue: 'Continue',
    next: 'Next',
    finish: 'Finish',
    skip: 'Skip',
    search: 'Search',
    send: 'Send',
    edit: 'Edit',
    done: 'Done',
    somethingWentWrong: 'Something went wrong. Please try again.',
  },

  /**
   * Compact time distances — "3h", "in 2d", "now" — for chips and rows that have no room
   * for `Intl.RelativeTimeFormat`'s full sentences. The in/ago wrappers take the already-
   * formatted unit so each language keeps its own word order ("vor 2 Std.", "2sa önce").
   */
  time: {
    now: 'now',
    minutesShort: (n: number) => `${n}m`,
    hoursShort: (n: number) => `${n}h`,
    daysShort: (n: number) => `${n}d`,
    inPhrase: (phrase: string) => `in ${phrase}`,
    agoPhrase: (phrase: string) => `${phrase} ago`,
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
    stepThree: 'STEP 3',
    /** A format example, not copy — localized so the shape reads as "an address like mine". */
    emailPlaceholder: 'you@example.com',

    login: {
      title: 'Welcome back',
      subtitle: 'Sign in with your username or email.',
      identifier: 'Username or email',
      password: 'Password',
      submit: 'Sign in',
      needsCode: 'We can email a new code to finish setting up this account.',
      emailCode: 'Email me a code',
      forgot: 'Forgot password?',
    },

    register: {
      title: 'Create your account',
      subtitle: 'We will email a six-digit code to confirm the address.',
      email: 'Email',
      password: 'Password',
      passwordHint: 'At least 8 characters, with a letter and a number.',
      signInInstead: 'Sign in instead',
      consent: [
        { text: 'I confirm I am ' },
        { text: '18 years of age or older', bold: true },
        { text: ' and I accept the ' },
        { text: 'Terms of Service', bold: true, link: 'terms' },
        { text: ' and ' },
        { text: 'Privacy Policy', bold: true, link: 'privacy' },
        { text: '.' },
      ] as ConsentSegment[],
      consentA11y: 'I am 18 or over and accept the Terms and Privacy Policy',
      consentRequired: 'You need to accept this to create an account.',
      emailTaken: 'That email is already registered.',
    },

    verify: {
      title: 'Check your email',
      /** The address is rendered in bold inside the sentence, hence the split. */
      sentCodeBefore: 'We sent a six-digit code to',
      code: 'Verification code',
      submit: 'Verify',
      resend: 'Send a new code',
      resendCooldown: (s: number) => `Send a new code (${s}s)`,
    },

    forgot: {
      title: 'Reset your password',
      subtitle: 'Give us the email you signed up with and we will send you a six-digit code.',
      email: 'Email',
      submit: 'Send the code',
      /**
       * Conditional on purpose, and the address is bold inside the sentence, hence the
       * split. The server answers the same way for an address with no account, so saying
       * "we sent you a code" here would undo that in the one place the user can read.
       */
      sentCodeBefore: 'If there is an account for it, a six-digit code is on its way to',
      verifySubmit: 'Continue',
      resetTitle: 'Choose a new password',
      resetSubtitle: 'Anyone signed in with the old password will be signed out.',
      resetSubmit: 'Set new password',
      doneTitle: 'Sign in with your new password',
      doneBody:
        'Every session that used the old password has ended, on this device and any other.',
      signIn: 'Sign in',
    },
  },

  onboarding: {
    /** Rendered through `useUpper()` by the step header, so it stays lowercase here. */
    stepOf: (step: number, total: number) => `Step ${step} of ${total}`,
    moreToGo: (n: number) => `${n} more to go`,

    profile: {
      title: 'About you',
      subtitle:
        'GameBuddy is for adults. We use your date of birth to confirm you are 18 or over — it is never shown to anyone.',
      ageHint: (n: number) => `You must be ${n} or over.`,
      countryRequired: 'Choose your country',
      gender: 'Gender',
      genderMan: 'Man',
      genderWoman: 'Woman',
      genderOther: 'Other',
      genderNone: 'Prefer not to say',
    },

    username: {
      title: 'Pick a username',
      subtitle: 'This is what other players see. You can change it later.',
      label: 'Username',
      taken: 'That username is taken. Try another.',
      hint: 'Letters, numbers and underscores.',
      placeholder: 'headshot_hero',
    },

    avatar: {
      title: 'Pick your look',
      subtitle: 'This is what people see first. You can upload a real photo later, from your profile.',
      pickOne: 'Pick one to carry on.',
    },

    games: {
      title: 'What do you play?',
      subtitle: (n: number) =>
        `Pick at least ${n}. This is most of what the recommendations are built from.`,
      searchPlaceholder: 'Search games',
    },

    platforms: {
      title: 'What do you play on?',
      subtitle: 'Pick everything you use. Other players filter by this, so more is better than fewer.',
    },

    keywords: {
      title: 'How do you play?',
      subtitle: (n: number) => `Pick at least ${n}. These are the habits and moods we match on.`,
    },

    birthDate: {
      day: 'Day',
      month: 'Month',
      year: 'Year',
    },

    country: {
      label: 'Country',
      choose: 'Choose a country',
      currentA11y: (name: string) => `Country: ${name}`,
      title: 'Where are you?',
      noMatch: (query: string) => `No country matches “${query}”.`,
    },
  },

  tabs: {
    home: 'Deck',
    lobby: 'Lobby',
    messages: 'Messages',
    market: 'Market',
    profile: 'Profile',
  },

  lobby: {
    /** The four moods a lobby can advertise; mirrors the backend enum. */
    tones: {
      competitive: 'Competitive',
      chill: 'Chill',
      casual: 'Casual',
      learning: 'Learning',
    },

    card: {
      plays: (when: string) => `Plays ${when}`,
      unknownGame: 'Unknown game',
      byOwner: (name: string) => `by ${name}`,
      unknownOwner: 'someone',
      seatsFull: (seats: string) => `${seats} full`,
      seatsPlayers: (seats: string) => `${seats} players`,
      requested: 'Requested',
      statusLocked: 'Team locked',
      statusEnded: 'Played',
      statusCancelled: 'Called off',
    },

    list: {
      title: 'Find your team',
      open: 'Open a lobby',
      ownsLive: 'You have a lobby open. End or cancel it to start another.',
      yourLobby: 'Your lobby',
      yourLobbies: 'Your lobbies',
      openLobbies: 'Open lobbies',
      soon: 'Now',
      soonA11y: 'Starting within 15 minutes',
      emptySoonTitle: 'Nothing starting right now',
      emptySoonBlurb:
        'Nothing kicking off in the next 15 minutes. Turn off Now to see what is planned later.',
      emptyToneTitle: 'Nothing with that vibe',
      emptyToneBlurb: 'Nothing with that vibe at the moment. Try another filter, or open your own.',
      emptyTitle: 'No open lobbies right now',
      emptyBlurb: 'Open one, set a time, and choose who joins you.',
    },

    boost: {
      badge: 'Boosted',
      action: (cost: number) => `Boost this lobby · ${cost}`,
      caption: 'Puts it at the top of the list until it starts.',
      activeCaption: 'Boosted — at the top of the list until this lobby starts.',
    },

    detail: {
      fallbackTitle: 'Lobby',
      gone: 'This lobby is gone.',
      wantsToJoin: 'Wants to join',
      requestSent: "Request sent. We'll notify you when the owner answers.",
      team: (taken: number, total: number) => `Team · ${taken}/${total}`,
      accept: 'Accept',
      pass: 'Pass',
      chat: 'Lobby chat',
      chatReadOnly: 'Lobby chat · read only',
      emptyChatTitle: 'Nothing said yet',
      emptyChatBlurb: 'Sort out the details with your team here.',
      composerPlaceholder: 'Message the team',
      playsBy: (when: string, owner: string) => `Plays ${when} · by ${owner}`,
      requirements: 'Requirements',
      statusLocked: 'Team locked — not taking requests',
      statusEnded: 'Played and closed',
      statusCancelled: 'Called off',
      statusArchived: 'Archived',
      lockTeam: 'Lock team',
      cancelLobby: 'Cancel lobby',
      unlock: 'Unlock',
      endLobby: 'End lobby',
      leaveLobby: 'Leave lobby',
      withdraw: 'Withdraw request',
      rejected: 'The owner filled this one with someone else.',
      askToJoin: 'Ask to join',
      full: 'This lobby is full.',
      owner: 'Lobby owner',
      unknownGamer: 'Unknown gamer',
    },

    create: {
      title: 'Open a lobby',
      goldTitle: 'Opening a lobby comes with Gold',
      goldBlurb: 'Browsing lobbies and asking to join are always free.',
      getGold: 'Get Gold',
      game: 'Game',
      change: 'Change',
      searchGame: 'Search for the game',
      titlePlaceholder: 'Title',
      descriptionPlaceholder: "What's the plan? (optional)",
      requirementsPlaceholder: 'Requirements — mic, rank, in-game chat (optional)',
      approveHint: "Tell people what you're looking for. You approve every request yourself.",
      tone: 'Tone',
      players: 'Players (including you)',
      when: 'When',
      presetNow: 'Now',
      presetIn1h: 'In 1 hour',
      presetIn3h: 'In 3 hours',
      pickATime: 'Pick a time',
      hour: 'Hour',
      minute: 'Minute',
      tooFar: 'Lobbies can be planned up to two weeks ahead.',
      playsAt: (stamp: string) => `Plays ${stamp}.`,
      use24h: 'Use a 24-hour clock.',
      zoneHint: (zone: string) => `Set in your time zone (${zone}). Everyone sees it in theirs.`,
      zoneHintNoZone: 'Everyone sees this time in their own time zone.',
      submit: 'Open lobby',
    },
  },

  deck: {
    header: {
      discover: 'Discover',
      title: "Who's playing",
      likesLeft: 'likes left',
      filter: 'Filter',
      filtersA11y: 'Filters',
      filtersOnA11y: (n: number) => `Filters, ${n} on`,
      loading: 'Finding people who play what you play…',
    },

    card: {
      tapForProfile: 'Tap for full profile',
      plays: 'Plays',
      playsOn: 'Plays on',
      style: 'Style',
      inCommon: (n: number) => `${n} in common`,
      playsOnA11y: (list: string) => `Plays on ${list}`,
      /** On the card while it is being dragged upwards. Two words, so it stays on one line. */
      superStamp: 'SUPER',
    },

    actions: {
      pass: 'Pass',
      passHint: 'Skip this gamer',
      match: 'Match',
      matchHint: 'Say yes to this gamer',
      superLike: 'Super Like',
      superLikeHint: 'Say yes and tell them straight away',
      superLikeGetHint: 'Get more Super Likes',
      /** The count sits under the button, so the balance is visible before it is spent. */
      superLikeLeft: (n: number) => `${n} left`,
      superLikeNone: 'None left',
      undo: 'Undo last swipe',
      undoCost: (cost: number) => `Undo last swipe, ${cost} coins`,
    },


    match: {
      title: "It's a match!",
      body: (name: string) => `You and ${name} both said yes.`,
      message: 'Send a message',
      keep: 'Keep swiping',
      someone: 'Someone',
    },

    limit: {
      likesTitle: "That's today's likes",
      likesBody: 'You can keep looking and passing. Gold lifts the cap.',
      swipesTitle: "That's today's swipes",
      swipesBody: 'The deck comes back tomorrow. Gold removes the daily limit.',
      goldTitle: 'GameBuddy Gold',
      goldBody: 'This one is part of Gold.',
      superLikesTitle: 'Out of Super Likes',
      superLikesBody: 'You can still match normally. Super Likes are bought with coins.',
      swipesLeft: 'Swipes left',
      likesLeft: 'Likes left',
      getGold: 'Get Gold — no daily limit',
      getSuperLikes: 'Get more Super Likes',
            noCoinsTitle: 'Not enough coins',
      noCoinsBody: 'A rewind costs coins. You can earn more in the Market.',
      nothingToRewindTitle: 'Nothing to take back',
      nothingToRewindBody: 'You have not swiped anyone yet — or that one turned into a match, which stands.',
      somethingWrongTitle: 'That did not go through',
      somethingWrongBody: 'Nothing was changed. Try again in a moment.',
      getCoins: 'Get more coins',
      keepLooking: 'Keep looking',
    },

    filters: {
      title: 'Filters',
      subtitle: 'Narrow the deck to the people you actually want to play with.',
      subtitleLocked: 'Part of Gold. Have a look at what it does.',
      game: 'Game',
      gameHint: 'Only people who play this one.',
      gameHintEmpty: 'Add games to your profile to filter by them.',
      anyGame: 'Any game',
      region: 'Region',
      regionHint: 'Same timezone, same servers, same evening.',
      regionHintEmpty: 'Set your country on your profile to filter by it.',
      onlyIn: (country: string) => `Only players in ${country}`,
      onlyNearMe: 'Only players near me',
      platform: 'Platform',
      platformHint: 'Whoever you can actually get in a lobby with.',
      anyPlatform: 'Any platform',
      availability: 'Availability',
      availabilityHint: 'Active in the last 15 minutes.',
      onlineNow: 'Online now',
      showFilters: (n: number) => (n === 1 ? 'Show 1 filter' : `Show ${n} filters`),
      showEveryone: 'Show everyone',
      clearFilters: 'Clear filters',
      unlock: 'Unlock filters with Gold',
      notNow: 'Not now',
    },

    locked: {
      title: 'Filters are part of Gold',
      blurb: 'Narrow the deck to one game, your region, or people who are online right now.',
      getGold: 'Get Gold',
      showEveryone: 'Show everyone instead',
    },

    exhausted: {
      filteredTitle: "That's everyone matching your filters",
      title: "That's everyone for now",
      filteredBlurb: 'Widening them brings more people back into the deck.',
      blurb:
        'New players join all the time, and people you passed on come back around after a while.',
      lookAgain: 'Look again',
    },
  },

  billing: {
    promptTitle: "It's working",
    promptBody: "You've matched with someone. Gold is for when you want to do more of it.",
    benefitNoLimit: 'No daily like limit',
    benefitSeeLikes: 'See everyone who liked you',
    benefitFilters: "Filter the deck by game, region and who's online",
    seeGold: 'See Gold',
    notNow: 'Not now',
    coinsAdded: 'Coins added',
    coinsAddedBody: 'They are in your balance now.',
    goldYours: 'Gold is yours',
    goldYoursBody: 'No daily limit, advanced filters, and the Gold frame.',
    /** `StoreUnavailableError` reasons, matched by name in `useErrorText`. */
    storeUnavailable: 'Purchases are not available in this build yet.',
    storeNotInBuild:
      'This build cannot make purchases. It was installed before in-app purchases were added.',
    storePlanMissing: 'That plan is not available right now.',
  },

  market: {
    shop: {
      header: 'Market',
      title: 'Show off',
      earnTab: 'Earn',
      shopTab: 'Shop',
      framesAndBanners: 'Frames and banners',
      framesAndBannersBlurb: 'Worn on your profile, and on every card you appear in.',
      frames: 'Frames',
      banners: 'Banners',
      notEnoughCoins: 'Not enough coins',
      notEnoughCoinsBody: (n: number) => `You have ${n}. Badges earn coins, or you can top up.`,
      seeCoinPacks: 'See coin packs',
      equipInInventory: 'Equip what you own in your Inventory',
      boughtTitle: (name: string) => `${name} is yours`,
      bought: 'Bought',
      boughtBody: 'Put it on from your Inventory.',
      owned: 'Owned',
      buy: 'Buy',
      claim: 'Claim',
      claimA11y: (name: string) => `Claim ${name}, free`,
      free: 'Free',
      coinsPrice: (n: number) => `${n} coins`,
      /** Appended to the item name in screen-reader labels; the preview plays for everyone else. */
      animatedSuffix: ', animated',
      /** The close-up sheet. Only reachable for items this account does not own yet. */
      /** Keyed by kind so a third one is a new entry rather than a new ternary. */
      previewOn: { FRAME: 'On your avatar', BANNER: 'On your profile', THEME: 'On your card' },
      themes: 'Themes',
      bundles: 'Bundles',
      bundlesBlurb: 'A matching set, cheaper than buying the pieces.',
      bundleParts: (n: number) => `${n} coins separately`,
      bundleSaving: (n: number) => `Save ${n}`,
      bundleTogether: 'Worn together',
      bundleOwned: 'You own this set',
      bundleA11y: (name: string, price: number, saving: number) =>
        `${name} bundle, ${price} coins, saves ${saving}`,
      previewA11y: (name: string) => `Preview ${name}`,
      ownedA11y: (name: string) => `${name}, owned. Equip it from your Inventory.`,
      buyA11y: (name: string, price: number) => `Buy ${name}, ${price} coins`,
      cantAffordA11y: (name: string, price: number) => `${name}, ${price} coins, not enough coins`,
      goldCardMemberA11y: 'Your Gold membership',
      goldCardGetA11y: 'Get GameBuddy Gold',
      goldCardTitleMember: 'You are a member',
      goldCardTitle: 'See who likes you',
      goldCardBodyMember: 'The Gold frame and banner are yours while your membership lasts.',
      goldCardBody: 'No daily limit, advanced filters, and the Gold frame and banner.',
    },

    earn: {
      header: 'Earn',
      blurb: 'Coins come back every day. Nothing here costs money.',
      earnedTitle: (n: number) => `+${n} coins`,
      earnedBody: 'Spend them on frames, banners or likes.',
      watchedBody: 'Thanks for watching.',
      rewardPendingTitle: 'Coins are on their way',
      rewardPendingBody: 'Check your balance again in a moment.',
      consentTitle: 'Videos need advert consent',
      consentBody: 'Change it in Settings → Advert privacy.',
      adFailedTitle: 'No video right now',
      adFailedBody: 'Nothing was available to show. Try again in a moment.',
      watchVideo: 'Watch a short video',
      videosLeft: (n: number) => `${n} left today`,
      videosDone: 'That is today’s videos — back tomorrow',
      stipendTitle: 'Gold monthly bonus',
      stipendReady: 'Yours this month',
      stipendBack: (when: string) => `Back ${when}`,
      missionDone: 'Done',
      missionProgress: (progress: number, target: number) => `${progress} of ${target}`,
      missionSetOf: (n: number, total: number) => `Set ${n} of ${total}`,
      missionSetVeteran: (n: number) => `Veteran set ${n}`,
      missionBand: { EASY: 'Easy', MEDIUM: 'Medium', HARD: 'Hard' },
      /**
       * The missions, by the code the backend sends alongside its own English title.
       *
       * Keyed by code rather than translated from that title, because the code is the
       * mission's name and the title is prose somebody will reword. `Mission` on the
       * backend is the list these must match; a code missing here falls back to the
       * server's English rather than rendering blank, which is what lets a mission be
       * added server-side without six languages going empty until the next release.
       */
      missionTitles: {
        'talk-10': 'Send 10 messages',
        'meet-2': 'Match with 2 gamers',
        'lobby-1': 'Join a lobby',
        'like-10': 'Like 10 profiles',
        'daily-2': 'Claim your daily reward twice',
        'advert-1': 'Watch a short video',
        'lobby-chat-5': 'Send 5 messages in a lobby',
        'super-like-1': 'Send a super like',
        'spend-100': 'Spend 100 coins',
        'talk-50': 'Send 50 messages',
        'meet-5': 'Match with 5 gamers',
        'lobby-3': 'Join 3 lobbies',
        'host-1': 'Open a lobby',
        'lobby-chat-15': 'Send 15 messages in lobbies',
        'like-40': 'Like 40 profiles',
        'advert-5': 'Watch 5 short videos',
        'buy-1': 'Buy something from the Market',
        'daily-5': 'Claim your daily reward 5 times',
        'talk-200': 'Send 200 messages',
        'meet-15': 'Match with 15 gamers',
        'host-3': 'Open 3 lobbies',
        'lobby-chat-50': 'Send 50 messages in lobbies',
        'super-like-5': 'Send 5 super likes',
        'spend-1000': 'Spend 1,000 coins',
      },
      claimA11y: (reward: number, title: string) => `Claim ${reward} coins: ${title}`,
      rowA11y: (title: string, detail: string) => `${title}, ${detail}`,
      claim: 'Claim',
      soon: 'soon',
      inAMoment: 'in a moment',
      inMinutes: (n: number) => `in ${n}m`,
      inHours: (n: number) => `in ${n}h`,
      inDays: (n: number) => `in ${n}d`,
      streakHeader: 'Daily streak',
      day: (n: number) => `Day ${n}`,
      notStarted: 'Not started',
      claimCoins: (n: number) => `Claim ${n} coins`,
      nextDay: (day: number, readyIn: string, weekTotal: number) =>
        `Day ${day} unlocks ${readyIn} — a full week is ${weekTotal} coins.`,
      back: (readyIn: string) => `Back ${readyIn}.`,
    },

    consumables: {
      header: 'Use your coins',
      blurb: 'Spent when you use them, not owned forever.',
      superLike: 'Super Like',
      superLikeDetail: 'They are told straight away, and it stands out.',
      extraLikes: '5 more likes today',
      extraLikesDetail: 'On top of your daily cap. Today only.',
      itemA11y: (title: string, cost: number) => `${title}, ${cost} coins`,
      addedTitle: (title: string) => `${title} added`,
      added: 'Added',
      addedBody: 'Use it from the deck.',
      goldHint: 'Buying likes often? Gold removes the limit',
    },

    coins: {
      header: 'Coins',
      blurbBuy: 'Top up now, or earn them free under Earn — a daily streak, quests and badges.',
      blurbNoBuy:
        'Earn them under Earn: a daily streak, quests and badges. Buying is not available in this build yet.',
      packCoins: (n: string) => `${n} coins`,
      takesYouTo: (n: string) => `Takes you to ${n}`,
      packA11y: (coins: number, price: string) => `Buy ${coins} coins for ${price}`,
      onTheWay: 'Your coins are on the way',
      onTheWayBody: 'They can take a moment to arrive. There is no need to buy again.',
    },

    gold: {
      done: 'Done',
      continuePrice: (price: string) => `Continue — ${price}`,
      unavailable: 'Purchases not available yet',
      notNow: 'Not now',
      heroThanks: 'Thanks for backing GameBuddy.',
      heroPitch: 'The whole app, with nothing in the way.',
      benefitLikesTitle: 'See who liked you',
      benefitLikesBody: 'Every face, not just the number.',
      benefitLimitTitle: 'No daily limit',
      benefitLimitBody: 'Swipe and like as much as you want.',
      benefitFiltersTitle: 'Advanced filters',
      benefitFiltersBody: 'Narrow the deck by game, region, and who is online now.',
      choosePlan: 'Choose a plan',
      planWeekly: 'Weekly',
      planMonthly: 'Monthly',
      planYearly: 'Yearly',
      periodWeek: '1 week',
      periodMonth: '1 month',
      periodYear: '12 months',
      noteTrial: '3-day free trial',
      /** Mirrors the saving GOLD_PLANS advertises; the price data itself stays there. */
      noteYearly: 'Save 58%',
      billedEvery: (period: string) => `Billed every ${period}`,
      purchasePending: 'Your purchase is going through',
      purchasePendingBody:
        'It can take a moment to arrive. Gold will switch on by itself — there is no need to buy again.',
      cancelNote: 'Cancel any time from your store account. A subscription renews until you cancel it.',
      member: 'You are a member',
      runsUntil: (date: string) => `Your membership runs until ${date}.`,
      active: 'Your membership is active.',
      wearBelow: 'The Gold frame and banner are yours — put them on below.',
      yoursToWear: 'Yours to wear',
      membersOnly: 'Only for members',
      withGold: 'With Gold',
      worn: 'Worn',
      equip: 'Equip',
      equipA11y: (name: string) => `Equip ${name}`,
      banner: 'Banner',
      frame: 'Frame',
    },

    inventory: {
      title: 'Inventory',
      subtitle: 'What you own, and what you are wearing',
      nothingYet: { FRAME: 'No frames yet', BANNER: 'No banners yet', THEME: 'No themes yet' },
      emptyBlurb: {
        FRAME: 'Anything you buy in the Market lands here. Free frames are already yours.',
        BANNER: 'Anything you buy in the Market lands here. Free banners are already yours.',
        THEME: 'Anything you buy in the Market lands here.',
      },
      /** What an owned item is, said once per kind. */
      kindLabel: { FRAME: 'Frame', BANNER: 'Banner', THEME: 'Card theme' },
      goToMarket: 'Go to the Market',
      takeOff: {
        FRAME: 'Take off my frame',
        BANNER: 'Take off my banner',
        THEME: 'Take off my theme',
      },
    },

    badges: {
      title: 'Badges',
      subtitle: 'Finish missions, claim coins, show off three',
      showcaseFull: (slots: number) => `You can show ${slots} badges. Take one off first.`,
      earned: 'Earned',
      tileA11y: (title: string, status: string) => `${title}. ${status}`,
      progressOf: (value: number, target: number) => `${value} of ${target}`,
      claimCoins: (n: number) => `Claim ${n} coins`,
      /**
       * The four hardest badges hand over a frame instead of coins, so the tile cannot
       * print `+125` and the button cannot say "claim 125 coins". Both have to name the
       * thing on offer — "a reward" is not something anybody can want.
       */
      rewardFrame: 'Frame',
      claimFrame: (name: string) => `Claim the ${name} frame`,
      removeFromProfile: 'Remove from profile',
      showOnProfile: 'Show on profile',
    },
  },

  messages: {
    header: 'Messages',
    title: 'Your conversations',
    friends: 'Friends',
    matches: 'Matches',
    friendsEmpty: 'Nobody yet. You can add a friend once you have matched with them.',
    matchesEmpty: 'No matches yet. Swipe on the Home tab to find someone.',
    sectionA11y: (title: string, count: number) => `${title}, ${count}`,
    conversationWith: (name: string) => `Conversation with ${name}`,
    startConversationWith: (name: string) => `Start a conversation with ${name}`,
    noMessagesYet: 'No messages yet',
    sayFirst: 'Say something first',
    emptyBlurb: 'You matched — someone has to go first.',
    conversation: 'Conversation',
    viewProfileA11y: (name: string) => `View ${name}'s profile`,
    thisGamer: 'this gamer',
    acceptRequest: 'Accept friend request',
    sendRequest: 'Send a friend request',
    alreadyFriends: 'Already friends',
    requestGoneTitle: 'That request is no longer there',
    reported: 'Reported. A moderator will look at it.',
    placeholder: 'Message',
    connecting: 'Connecting…',
    reconnecting: 'Reconnecting…',
    offline: 'Offline',
    typing: 'typing…',
    online: 'Online',
    lastSeen: (when: string) => `Last seen ${when}`,
    justNow: 'just now',
    minutesAgo: (n: number) => `${n}m ago`,
    hoursAgo: (n: number) => `${n}h ago`,
    aWhileAgo: 'a while ago',
    reportHint: 'Long press to report this message',
  },

  profile: {
    header: 'You',
    title: 'Profile',
    inventoryA11y: 'Inventory',
    settingsA11y: 'Settings',
    badgesA11y: 'Badges',
    goldMemberA11y: 'GameBuddy Gold member',
    friends: 'Friends',
    coins: 'Coins',
    badges: 'Badges',
    noShowcase: 'No badges on show yet — tap to pick some',
    games: 'Games',
    playsOn: 'Plays on',
    keywords: 'Play style',
    friendRequestsShort: 'Friend requests',
    acceptRequestA11y: (name: string) => `Accept ${name}'s friend request`,
    declineRequestA11y: (name: string) => `Decline ${name}'s friend request`,
    accept: 'Accept',
    no: 'No',

    friendsTitle: 'Friends',
    noFriendsTitle: 'No friends yet',
    noFriendsBody: 'You can add someone as a friend once you have matched with them.',
    removeConfirmTitle: (name: string) => `Remove ${name}?`,
    removeConfirmBody:
      'They go back to being a match — you can still message each other, and either of you can send a new friend request.',
    thisGamer: 'this gamer',

    fallbackTitle: 'Profile',
    actions: 'Actions',
    reported: 'Reported. A moderator will look at it.',
    removeFriend: 'Remove friend',
    reportProfile: 'Report profile',
    block: 'Block',
    blockConfirmTitle: (name: string) => `Block ${name}?`,
    blockConfirmBody:
      'You will not see each other anywhere in the app, and neither of you can message the other. You can undo this in Settings.',

    moreActionsA11y: 'More actions',
    matchBack: 'Match',
    matchBackCaption: 'They already liked you — match back to start talking.',
    matching: 'Matching…',
    addFriend: 'Add friend',
    addFriendCaption: 'Match first, then you can send a friend request.',
    acceptRequest: 'Accept friend request',
    withdrawRequest: 'Withdraw request',
    withdrawConfirmTitle: (name: string) => `Withdraw your request to ${name}?`,
    withdrawConfirmBody: 'They stop seeing it. You can send another one whenever you like.',

    admirersTitle: 'Who liked you',
    peopleLikeYou: (n: number): string => (n === 1 ? 'person likes you' : 'people like you'),
    upgradeToSee: ' — upgrade to see who',
    admirersEmptyTitle: 'Nobody yet',
    admirersEmptyBody:
      'When somebody likes you they show up here, whether or not you have liked them back.',
    revealing: 'Revealing…',
    revealOne: (cost: number) => `Reveal one for ${cost} coins`,
    orGold: 'Or get Gold: every face, and no daily like limit.',
    seeWhoLikesYou: 'See who likes you',
    likedYouBadge: 'liked you',
    likedYouBadgeA11y: (n: number) => `${n} people liked you`,
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
    keywords: 'Play style',
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
    adPrivacy: 'Advert privacy',
    adPrivacyHint: 'Change what adverts may use about you',
    password: 'Password',
    passwordHint: 'Signs you out everywhere',
    blocked: 'Blocked',
    blockedHint: 'Who you have blocked',
    promo: 'Promotion codes',
    promoHint: 'Redeem a code for coins or Gold',
    promoScreen: {
      title: 'Promotion codes',
      subtitle: 'Codes sent to you appear here. You can also type one in.',
      waiting: 'Waiting for you',
      waitingEmpty: 'Nothing is waiting right now.',
      enterTitle: 'Enter a code',
      codeLabel: 'Code',
      codeHint: 'Letters and numbers, however it was written.',
      redeem: 'Redeem',
      validUntil: (date: string) => `Valid until ${date}`,
      rewardCoins: (n: number) => `${n} coins`,
      rewardGold: (days: number) => `${days} ${days === 1 ? 'day' : 'days'} of Gold`,
      successCoinsTitle: 'Coins added',
      successCoinsBody: (balance: number) => `You now have ${balance} coins.`,
      successGoldTitle: 'Gold is yours',
      successGoldBody: (date: string) => `Gold runs until ${date}.`,
      history: 'Already used',
      done: 'Done',
    },
    gamesScreen: {
      title: 'Games you play',
      subtitle: (n: number) => `At least ${n}. Changing these changes who you are shown.`,
    },
    notificationsScreen: {
      title: 'Notifications',
      offTitle: 'Notifications are off for GameBuddy',
      offBody:
        'Android is blocking them, so nothing below can reach you until they are turned back on.',
      turnOn: 'Turn on notifications',
      openSystem: 'Open system settings',
      whatToSend: 'What to send',
      messagesTitle: 'Messages',
      messagesBody: 'When someone you matched with sends you a message.',
      socialTitle: 'Matches and friends',
      socialBody: 'New matches, friend requests and answers, lobby activity, badges you earn.',
      remindersTitle: 'Reminders',
      remindersBody: 'The occasional nudge when you have been away and something is waiting.',
      quietNote:
        'Turning everything off here keeps the app quiet without switching notifications off for it entirely — so anything you turn back on later still works.',
      onThisPhone: 'On this phone',
      soundsTitle: 'Sounds',
      soundsBody: 'A short cue when something happens in the app — a match, a purchase, a message.',
      vibrationTitle: 'Vibration',
      vibrationBody: 'A short buzz on a swipe, a match, or a purchase.',
      cuesNote:
        'Cues never play over the silent switch, and they never interrupt music you are already listening to.',
    },

    avatarScreen: {
      title: 'Your avatar',
      subtitle: 'Upload a photo, or pick one of ours.',
      saveLabel: 'Use this avatar',
      yourPhoto: 'Your own photo',
      takePhoto: 'Take a photo',
      fromPhotos: 'Choose from photos',
      fromFiles: 'Choose a file',
      uploading: 'Uploading and checking…',
      cropTitle: 'Place your photo',
      cropHint: 'Drag to move, pinch to zoom.',
      cropConfirm: 'Use this photo',
      cropFailed: 'That image could not be cropped. Try another one.',
      privacyNote:
        'Your photo is checked before anyone else can see it, and location data is removed from it automatically.',
      orOurs: 'Or pick one of ours',
      cameraDenied:
        "GameBuddy cannot open the camera without permission. You can grant it in your phone's settings, choose an existing photo, or pick one of ours below.",
      photosDenied:
        "GameBuddy cannot open your photos without permission. You can grant it in your phone's settings, take a photo instead, or pick one of ours below.",
      tooLarge: (sizeMb: string, limitMb: number) =>
        `That file is ${sizeMb}MB and the limit is ${limitMb}MB. Anything from your camera roll will be well under it.`,
      approvedTitle: "That's your avatar now",
      approvedBody: 'Everyone can see it.',
      pendingTitle: 'Waiting to be checked',
      pendingBody:
        'Someone will look at it shortly. Until then you are the only one who can see it — everyone else still sees your old avatar.',
      rejectedTitle: 'That photo was not accepted',
      rejectedBody:
        'It looks like it breaks the rules on sexual content. Nobody else has seen it. Try another photo, or pick one of ours.',
    },

    age: {
      title: 'Date of birth',
      subtitle:
        'Used to confirm you are old enough to be here. Other people see your age, never the date.',
      recordedNote: (min: number) =>
        `Changes to your date of birth are recorded. GameBuddy is for adults only, and a date that puts you under ${min} will be refused.`,
    },

    keywordsScreen: {
      title: 'How you play',
      subtitle: (n: number) => `At least ${n}. These are the habits and moods we match on.`,
    },

    platformsScreen: {
      title: 'What you play on',
      subtitle: 'Pick everything you use. Other players filter by this, so more is better than fewer.',
    },

    passwordScreen: {
      title: 'Change password',
      subtitle: 'This signs you out on every device, including this one.',
      changedTitle: 'Password changed',
      signedOutTitle: 'You have been signed out everywhere',
      signedOutBody:
        'Changing your password ends every session, on this device and any other. That is deliberate — if someone else had your old password, they are out too.',
      signInAgain: 'Sign in again',
      current: 'Current password',
      next: 'New password',
      wrongCurrent: 'That is not your current password.',
      sameAsOld: 'Pick something different from the old one.',
    },

    blockedScreen: {
      body: 'Blocking works both ways and covers everything: they are not shown to you, you are not shown to them, and neither of you can message the other.',
      emptyTitle: 'Nobody is blocked',
      emptyBody: 'You can block someone from their profile if you need to.',
      unblock: 'Unblock',
    },
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
      '186': 'You blocked this gamer, so you cannot message them.',
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
      '173': 'That lobby is already boosted.',
      '179': 'This lobby is already full.',
      '180': 'This lobby is no longer open.',
      '183': 'Finish or cancel your current lobby first.',
      '185': 'The owner already answered your request.',
      '187': 'That username is not allowed. Please choose another one.',
      '190': 'That code is not valid.',
      '191': 'That code has expired.',
      '192': 'That code has been fully used.',
      '193': 'You have already used this code.',
      '194': 'That code was sent to another account.',
      '195': 'A code with that name already exists.',
      '196': 'That account cannot receive a code.',
    },
  },

  /**
   * The messages `src/validation.ts` selects from. Limits (8 characters, 18 years) are
   * arguments, not copy — the policy lives in one place and the words in seven.
   */
  validation: {
    emailEmpty: 'Enter your email address',
    emailInvalid: 'That does not look like an email address',
    passwordEmpty: 'Enter a password',
    atLeastChars: (n: number) => `At least ${n} characters`,
    atMostChars: (n: number) => `At most ${n} characters`,
    letterAndNumber: 'Include at least one letter and one number',
    usernameEmpty: 'Pick a username',
    usernameCharset: 'Letters, numbers and underscores only',
    usernameLetterOrNumber: 'Include at least one letter or number',
    usernameUnavailable: 'That username is not available',
    birthDateEmpty: 'Enter your date of birth',
    notARealDate: 'That is not a real date',
    dateInFuture: 'That date is in the future',
    mustBeAge: (n: number) => `You must be ${n} or over to use GameBuddy`,
    checkYear: 'Check the year',
    sixDigitCode: 'Enter the 6-digit code',
  },

  /** The shared catalogue/avatar pickers. Game names and genres come from IGDB data. */
  pickers: {
    noMatchesQueryFiltered: (query: string) => `Nothing matches “${query}” with these filters.`,
    noMatchesFiltered: 'Nothing matches these filters.',
    noMatchesQuery: (query: string) => `Nothing matches “${query}”.`,
    clearFilters: (n: number) => `Clear ${n} filter${n === 1 ? '' : 's'}`,
    clearFiltersA11y: 'Clear all filters',
    filterPlatform: 'Platform',
    filterGenre: 'Genre',
    filterOther: 'Other',
    avatarN: (n: number) => `Avatar ${n}`,
  },

  /**
   * The keyword catalogue's second line, keyed by the keyword's own name in lower case.
   *
   * Here rather than in the database because the `keywords` table has one `description`
   * column and no locale, so the server can only ever answer in one language. See
   * `src/i18n/keywords.ts` for the lookup and what happens to a keyword this list has
   * never heard of.
   *
   * Written in the second person and about *matching* rather than about the word: somebody
   * picking keywords is answering "who do I want to be put in front of", so the useful
   * sentence is what picking it will get them. Kept to roughly seventy characters — it
   * renders small, on the second line of a list row.
   *
   * The English here is the same copy as `upgrade-2026-16-keyword-descriptions.sql`, which
   * remains what a gamer sees if a build ever loses this dictionary.
   */
  keywords: {
    descriptions: {
      'achievement hunter': 'You go for the full list, however long it takes.',
      'aim training': 'You warm up before you play, and it shows.',
      'anime fan': 'Anime art, anime games, anime everything.',
      builder: 'You would rather build the base than defend it.',
      casual: 'You play to unwind. No pressure, no schedule.',
      chaotic: 'Plans are optional. Something will happen.',
      chill: 'Relaxed sessions, easy company, nobody shouting.',
      clutch: 'You are calm when the round is on the line.',
      'co-op': 'You would rather beat the game with someone than alone.',
      collector: 'Every skin, every mount, every card.',
      'combo practice': 'You spend real time in the training room.',
      competitive: 'You are here to win, and you play like it.',
      completionist: 'The map is not done until all of it is done.',
      'couch co-op': 'Two controllers, one sofa, same screen.',
      decorator: 'Your base, island or house is the whole hobby.',
      'emulator user': 'Older systems, running on whatever you have now.',
      'endgame focused': 'The story is the tutorial. The endgame is the game.',
      esports: 'You follow the pro scene and know the rosters.',
      explorer: 'You take the side path before the main quest.',
      grinder: 'Long, repetitive, satisfying. You do not mind the hours.',
      'guild leader': 'You organise people, and you are good at it.',
      'jumpscare enjoyer': 'Horror games, lights off, sound up.',
      'long sessions': 'When you sit down it is for the evening.',
      'loot goblin': 'You open every chest and pick up everything.',
      'lore nerd': 'You have read the item descriptions. All of them.',
      'min-maxer': 'Optimal build, optimal route, spreadsheet open.',
      modder: 'You install mods, and probably write a few.',
      'no mic': 'You play without voice chat, and prefer it that way.',
      'no spoilers': 'Do not tell them anything. They are getting there.',
      nostalgic: 'The games you grew up with still hit hardest.',
      raider: 'Scheduled runs, full team, cleared bosses.',
      'ranked grinder': 'Climbing the ladder is the reason you log in.',
      roleplayer: 'You stay in character, and enjoy people who do too.',
      'short sessions': 'Half an hour here and there, whenever you can.',
      shotcaller: 'You make the calls so the team does not have to.',
      'sim racer': 'Wheel, pedals, and lap times you actually care about.',
      social: 'The people are the reason you are here.',
      'solo player': 'You play alone, and you like it that way.',
      speedrunner: 'You have a personal best and you are chasing it.',
      'squad player': 'Always in a party, never queuing alone.',
      'story-driven': 'You are here for the writing and the characters.',
      streamer: 'You play with an audience watching.',
      theorycrafter: 'You work out why it is good before you use it.',
      'tournament goer': 'You show up in person, or at least sign up.',
      'toxic-free': 'No abuse, no blaming. You leave lobbies that go that way.',
      tryhard: 'Even the casual mode is played properly.',
      'voice chat': 'Mic on, talking through it, calls being made.',
      'waifu collector': 'Gacha pulls and a roster you are attached to.',
    },
  },

  /** The pre-permission notification pitch. Push payload text itself is server-composed. */
  notifications: {
    header: 'Notifications',
    primerTitle: "Don't miss the good part",
    primerBody:
      'GameBuddy is other people. Most of what happens here happens while the app is closed.',
    reasonMatchTitle: 'When you match',
    reasonMatchBody: 'Both of you said yes — that is when a conversation can start.',
    reasonMessageTitle: 'When someone messages you',
    reasonMessageBody: 'So a reply does not wait until you next happen to open the app.',
    reasonLobbyTitle: 'When somebody wants into your lobby',
    reasonLobbyBody: 'And when the owner of one lets you in.',
    turnOn: 'Turn on notifications',
    notNow: 'Not now',
    changeLater: 'You can change this any time in Settings, and choose which kinds you want.',
  },

  tutorial: {
    steps: {
      home: {
        title: 'Find someone to play with',
        body: 'Swipe through gamers who play what you play. Right if you want to play together, left if not. When you both swipe right, you match. Swipe up to send a Super Like — they hear about it straight away.',
      },
      messages: {
        title: 'Talk to your matches',
        body: 'Matching opens a private chat. Nobody can message you unless you both agreed to it, and you can block or report anyone from inside a conversation.',
      },
      lobby: {
        title: 'Team up in a lobby',
        body: 'Open lobbies are games looking for players — the game, the time, and the vibe are on the card. Ask to join, and the owner picks the team. Opening your own comes with Gold.',
      },
      market: {
        title: 'Make your profile yours',
        body: 'Frames and banners for your profile, plus extra daily likes if you run out. Everything here is optional — the app works without spending anything.',
      },
      profile: {
        title: 'Your profile, and everything else',
        body: 'Your games, your play style, your friends and your badges. Settings live behind the gear, including this tutorial if you want it again.',
      },
    },
    stepOf: (n: number, total: number) => `${n} of ${total}`,
    startPlaying: 'Start playing',
  },

  language: {
    label: 'Language',
    /** The picker's own title, for screen readers. */
    choose: 'Choose a language',
    /** Screen-reader label for anything that shows the language currently in force. */
    current: (name: string) => `Language: ${name}`,
  },

  /**
   * The screen `AppErrorBoundary` shows when a render throws. The error detail itself is
   * deliberately left as-is — it is diagnostic output, and translating it would make bug
   * reports harder to read, not easier.
   */
  errorScreen: {
    title: 'GameBuddy hit a problem',
    blurb: 'This is a bug, not something you did. The details below are what we need to fix it.',
    copyHint: 'The text above can be selected and copied.',
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
