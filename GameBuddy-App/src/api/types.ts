/**
 * Payload types, mirroring the backend DTOs. Field names are the JSON names, so these
 * can be diffed against `GameBuddy-backend/.../interfaces/dto` directly.
 */

export type Session = {
  accessToken: string;
  userId: string;
};

export type Game = {
  gameId: string;
  gameName: string;
  gameIcon: string;
  category: string;
  avgVote: number | null;
  description: string | null;
  /**
   * Platform enum names — the same strings as {@link PlatformId}, plus `OTHER`.
   *
   * `OTHER` is why this is not typed as `PlatformId[]`: four catalogue games run on the
   * SNES, N64 or arcade hardware, and the backend says so rather than leaving a blank that
   * would read as "never looked". Nobody is offered `OTHER` when choosing what *they* play,
   * so it belongs to games and not to {@link PlatformId}.
   *
   * Always present and possibly empty; the server sends `[]` rather than omitting it.
   */
  platforms: string[];
};

export type Keyword = {
  /** UUID. Named `id` here, unlike `Game.gameId` — that asymmetry is the backend's. */
  id: string;
  keywordName: string;
  description: string | null;
};

export type Avatar = {
  id: string;
  image: string;
};

/**
 * A badge on someone's profile.
 *
 * Deliberately smaller than `Badge` below. What another gamer sees is the picture and
 * what it was for — not the progress, the reward, or whether the coins were claimed.
 */
export type ShowcasedBadge = {
  code: string;
  title: string;
  description: string;
  icon: string;
  /** True when the artwork is animated WebP and the image needs `autoplay`. */
  animated: boolean;
};

export type GamerSummary = {
  userId: string;
  username: string;
  age: number;
  country: string;
  avatar: string;
  /** The worn frame's URL, or null for none — the common case, and not an error. */
  frame: string | null;
};

export type UserInfo = {
  userId: string;
  /** Null until onboarding's first step. */
  username: string | null;
  /** Own profile only; undefined when viewing someone else. */
  email?: string;
  /**
   * `yyyy-MM-dd`, own profile only, and null on accounts created before 18+ shipped.
   *
   * Other people are shown `age`. A date of birth narrows somebody down far more than an
   * age does, so it never leaves the account it belongs to.
   */
  birthDate?: string | null;
  /** A string, not a number — and null until the details step. */
  age: string | null;
  country: string | null;
  avatar: string | null;
  /** The worn frame's URL, or null for none — the common case, and not an error. */
  frame: string | null;
  /** The worn banner's URL, shown behind the profile header. Null for none. */
  banner: string | null;
  /**
   * The worn card theme's slug, or null for none.
   *
   * A slug rather than colours: `src/theme/cardThemes.js` holds the palette so the
   * contrast gate can check it at build time.
   */
  theme: string | null;
  gender: string | null;
  /** Own profile only. */
  coin?: number;
  /**
   * Own profile only, and the reason the console exists as a separate shell: an ADMIN
   * account has no age, games or keywords, so the onboarding check would otherwise
   * strand it on "finish your profile" forever.
   */
  role?: "USER" | "ADMIN";
  games: Game[];
  keywords: Keyword[];
  /**
   * What this gamer plays on, as labels ("PlayStation"), not enum names.
   *
   * Empty for accounts created before the field existed — the profile omits the section
   * rather than rendering an empty one, because an absent answer is not information.
   */
  platforms: string[];
  /** The three (at most) this gamer chose to display. Empty is normal. */
  badges: ShowcasedBadge[];
  /** How many have been earned in total. */
  badgeCount: number;
  /** Own profile only. */
  friends?: GamerSummary[];
  /**
   * Verified Discord accounts, already filtered to what this viewer may see.
   *
   * The server does the filtering, so a restricted handle is simply absent rather than
   * present-and-not-drawn — a client trusted to hide it would be one response inspection
   * away from publishing it.
   *
   * Optional for the same reason `platforms` is optional-chained on the settings screen: a
   * profile cached before this shipped has no such key, and reading `.length` off it is a
   * render error for everyone upgrading.
   */
  linkedAccounts?: LinkedAccount[];

  /**
   * Whether this account can sign in with an email and a password. Own profile only.
   *
   * Decides between "Change password" and "Set a password" in Settings, and whether the
   * delete-account card asks for one. Optional because a profile cached before this shipped
   * has no such key.
   */
  hasPassword?: boolean;

  /**
   * Which external identities can sign in as this account. Own profile only.
   *
   * Unrelated to `linkedAccounts` above, which is a profile decoration other people see:
   * these are credentials, nobody else is shown them, and the two lists can disagree.
   */
  authProviders?: AuthProvider[];
};

/**
 * Which external platforms a profile can carry.
 *
 * Steam was removed in September 2026 (no Web API key), but this stays a union rather than
 * a bare string: the server still sends an enum name, and a second provider should be a
 * compile error at every switch that reads one.
 */
export type LinkedProvider = 'DISCORD';

/** Which external identities can sign in. Distinct from `LinkedProvider` on purpose. */
export type AuthProvider = 'GOOGLE' | 'DISCORD';

/**
 * A session minted by a social sign-in.
 *
 * `newAccount` is the one thing the client cannot work out for itself: an account created a
 * second ago and one that never finished onboarding look identical on the wire afterwards.
 */
export type SocialSession = Session & { newAccount: boolean };


/**
 * One verified external account.
 *
 * The provider's own id is never sent — the profile shows what somebody is called, not the
 * key the link is built on.
 */
export type LinkedAccount = {
  provider: LinkedProvider;
  /**
   * The display name, or null when it did not survive screening.
   *
   * Null is drawn rather than hidden: the badge still says the account was verified,
   * without putting a name on it. It is never a masked string — that would be a name the
   * person is not actually called.
   */
  handle: string | null;
  /**
   * Who can see it. Own profile only; undefined when viewing someone else, because a
   * setting made to keep strangers out is not something to describe to a stranger.
   */
  visibility?: 'PUBLIC' | 'MATCHES';
};

/**
 * A gamer in the swipe deck.
 *
 * Note the field names differ from `GamerSummary` above — the match module has its own
 * DTO, and calls the name `gamerUsername` where the profile module calls it `username`.
 * Not worth papering over: the mismatch is the backend's and hiding it here would make
 * the next person doubt the mapping.
 */
export type Candidate = {
  userId: string;
  gamerUsername: string;
  age: number;
  country: string;
  gender: string | null;
  avatar: string | null;
  /** The worn frame's URL, or null for none — the common case, and not an error. */
  frame: string | null;
  /**
   * The worn card theme's slug, or null for none.
   *
   * The one cosmetic that colours the card itself rather than sitting on the avatar: the
   * deck paints its identity block in these colours instead of the hue derived from the
   * user id. Optional because an older server never sends it.
   */
  theme?: string | null;
  favoriteGames: { gameName: string; gameIcon: string | null }[];
  /** Names, not ids — this DTO flattens keywords to plain strings. */
  selectedKeywords: string[];
  /** Labels, not enum names. Empty for accounts that pre-date the field. */
  platforms: string[];
  /**
   * Whether this person's like was a super like.
   *
   * Only ever true in the "who liked you" list — everywhere else this describes somebody
   * who has not necessarily liked you at all, and the server leaves it false. Optional
   * because a client on an older build talks to a server that never sends it.
   */
  superLike?: boolean;
  /**
   * Verified handles shown under the name on the card.
   *
   * Public ones only — the backend never sends a matches-only handle to the deck, because a
   * card is by definition somebody you have not matched with yet.
   */
  linkedAccounts?: LinkedAccount[];
};

/** Today's swipe budget. One budget with a sub-cap, not two. */
export type SwipeAllowance = {
  /** "BASIC", "GOLD", … — whatever tier is actually in force after expiry checks. */
  tier: string;
  /** Decisions of any kind left today. Meaningless when `unlimited`. */
  remainingSwipes: number;
  /** How many of `remainingSwipes` may be accepts. Never greater than it. */
  remainingAccepts: number;
  unlimited: boolean;
  /** ISO instant, or null when unlimited. */
  resetsAt: string | null;
  /** Super Likes in hand. Bought, not rationed, so no reset applies to it. */
  superLikes: number;
};

/** What GET /billing/subscription reports. The tier is derived, never the stored one. */
export type Subscription = {
  /** 'BASIC' or 'GOLD'. */
  tier: string;
  /** ISO instant. Null on BASIC. */
  expiresAt: string | null;
  dailyAccepts: number;
  canSeeWhoLikedYou: boolean;
  canUseAdvancedFilters: boolean;
  /** Whether opening a game lobby is included. Gold's perk; joining is free for everyone. */
  canCreateLobby: boolean;
  /**
   * Whether the one-time day-3 Gold prompt is due right now.
   *
   * Every condition behind it — how old the account is, whether it has produced a match,
   * whether the prompt has already been shown — is evaluated on the server. The client is
   * told yes or no and is trusted only to report back when it actually rendered it.
   */
  upgradePromptDue: boolean;
};

/** What POST /match/rewind returns: the swipe that was taken back. */
export type Rewind = {
  /** Whoever came back, ready to be put on top of the deck again. */
  gamer: Candidate;
  /** Coins this cost. Zero on Gold, which gets rewinds as an entitlement. */
  coinsSpent: number;
  /** The balance afterwards, so the Market header does not need refetching. */
  coinBalance: number;
};

/** Which pool a mission set was drawn from. Rises across the campaign, then stays HARD. */
export type MissionBand = 'EASY' | 'MEDIUM' | 'HARD';

/** One dealt mission, with progress made since it was dealt. */
export type Mission = {
  /**
   * The stable kebab-case code. Both what a claim is addressed to and the key the title is
   * translated by — see `t.market.earn.missionTitles`.
   */
  code: string;
  /** English, from the server. A fallback for a mission this build has no key for yet. */
  title: string;
  /** 0, 1 or 2. Sent so the three keep their order rather than the list's. */
  slot: number;
  /** Capped at `target` by the server, so a progress bar needs no guard. */
  progress: number;
  target: number;
  /** Frozen when this was dealt: retuning the rates cannot move it under the gamer. */
  reward: number;
  claimed: boolean;
};

/** What GET /coins/earn reports, and what every claim returns. */
export type Earn = {
  dailyAvailable: boolean;
  /** What claiming now would pay, at the streak it would reach. */
  dailyReward: number;
  streak: number;
  /** ISO instant, or null when available now or never claimed. */
  dailyReadyAt: string | null;
  /** The three on screen. Never more, never fewer — finishing all three deals the next. */
  missions: Mission[];
  /** Which deal this is, counting from the gamer's first. 1-based. */
  missionSet: number;
  /** How long the campaign is, so the header can say "set 5 of 8" rather than a bare number. */
  missionSetsTotal: number;
  missionBand: MissionBand;
  /** True once the campaign is done and the pool has started repeating at the opening rate. */
  missionVeteran: boolean;
  stipendAvailable: boolean;
  stipendAmount: number;
  /** ISO instant, or null when available now or not a member. */
  stipendReadyAt: string | null;
  coinBalance: number;
  /**
   * Rewarded adverts that may still be paid for today.
   *
   * Sent even when zero, so the card can say "back tomorrow" rather than vanishing — a
   * faucet that disappears once spent reads as a bug, and nothing tells the gamer it will
   * be back.
   */
  adsLeftToday: number;
  /**
   * The whole streak cycle, and what one advert pays — the server's numbers, not ours.
   *
   * Optional because a client on a new build can be talking to an older server; the two
   * screens that draw them keep a local constant purely as that fallback.
   */
  dailyLadder?: number[];
  adCoins?: number;
};

/** What a consumable purchase leaves the gamer holding. */
export type Consumables = {
  coinBalance: number;
  /** Super likes owned. These do not expire. */
  superLikes: number;
  /** Extra likes bought for today only, on top of the tier's cap. */
  bonusAccepts: number;
};

export type LikedYou = {
  /** Always populated, on every tier. */
  count: number;
  /** Empty when `locked`. */
  likedYou: Candidate[];
  locked: boolean;
};

/** One row of the inbox. */
export type InboxEntry = {
  userId: string;
  username: string;
  avatar: string | null;
  lastMessage: string | null;
  /** ISO instant. */
  lastMessageTime: string | null;
  unreadCount: number;
};

/**
 * One message in a conversation.
 *
 * `sender` and `receiver` are user ids, so "is this mine" is a comparison against the
 * session's own userId rather than anything on the message itself.
 */
export type Conversation = {
  id: string;
  sender: string;
  receiver: string;
  message: string;
  /** ISO instant. */
  date: string;
};

/** Everything `POST /auth/details` needs. Ids throughout, never display names. */
export type ProfileDetails = {
  /** `yyyy-MM-dd`. The server derives the age from it and refuses anything under 18. */
  birthDate: string;
  country: string;
  /** Avatar UUID. */
  avatar: string;
  /** Single character: the backend caps this at length 1. */
  gender: string;
  /** Game UUIDs. At least 3. */
  favoriteGames: string[];
  /** Platform enum names, at least 1. See `src/profile/platforms.ts`. */
  platforms: string[];
  /** Keyword UUIDs. At least 5. */
  keywords: string[];
};

export type LobbyTone = "COMPETITIVE" | "CHILL" | "CASUAL" | "LEARNING";

/**
 * OPEN takes requests; LOCKED is "team found, stop asking" and starts no timer; ENDED and
 * CANCELLED keep the chat readable (read-only); ARCHIVED never reaches the client — the
 * backend answers 404 for it.
 */
export type LobbyStatus =
  "OPEN" | "LOCKED" | "ENDED" | "CANCELLED" | "ARCHIVED";

/** REJECTED is final for that lobby; LEFT and KICKED may request again. */
export type LobbyMemberStatus =
  "OWNER" | "PENDING" | "ACCEPTED" | "REJECTED" | "LEFT" | "KICKED";

/** One lobby: the card in the browse feed and the header of the detail screen. */
export type Lobby = {
  id: string;
  ownerId: string;
  ownerUsername: string | null;
  ownerAvatar: string | null;
  gameId: string;
  gameName: string | null;
  gameIcon: string | null;
  title: string;
  description: string | null;
  /** Free text the owner wrote: mic, rank, in-game chat. Screened, not enforced. */
  requirements: string | null;
  tone: LobbyTone;
  /** Including the owner. */
  maxPlayers: number;
  /** Seats taken, owner included — the card's "3/5". */
  playerCount: number;
  /** ISO instant. The planned start, per the owner. */
  startsAt: string;
  status: LobbyStatus;
  /** The caller's own standing, or null for a stranger browsing. */
  myStatus: LobbyMemberStatus | null;
  /** Chat messages newer than the caller's watermark. Only filled on `mine`. */
  unreadCount: number;
  /**
   * The owner paid to pin this to the top of the list, and it is still open.
   *
   * The server does the ordering and turns this off by itself when the lobby stops being
   * open, so a card only has to decide whether to wear the frame.
   */
  boosted: boolean;
  createdAt: string;
};

export type LobbyMember = {
  userId: string;
  username: string | null;
  avatar: string | null;
  status: LobbyMemberStatus;
  requestedAt: string;
};

export type LobbyMessage = {
  id: string;
  senderId: string;
  senderUsername: string | null;
  message: string;
  /** ISO instant. */
  date: string;
};

export type LobbyDetail = {
  lobby: Lobby;
  /** The team: OWNER and ACCEPTED. */
  members: LobbyMember[];
  /** PENDING requests — filled only for the owner, empty otherwise. */
  pendingRequests: LobbyMember[];
};

/**
 * The result of uploading an avatar.
 *
 * Three outcomes, not two. `status` is the thing that matters: `APPROVED` is live,
 * `PENDING` is waiting on a moderator and visible to nobody else, `REJECTED` was refused.
 * `url` comes back in every case, including the two where others cannot see it, so the
 * owner can look at what they uploaded while being told it is under review.
 */
export type AvatarUpload = {
  status: "APPROVED" | "PENDING" | "REJECTED";
  url: string;
};

/**
 * One item in the cosmetics store.
 *
 * `owned` is the server's answer, not something to re-derive from `price`: a free
 * cosmetic is owned by everyone without any purchase existing, and the rule for that
 * lives on the backend so there is one place it can be wrong.
 */
export type CosmeticKind = "FRAME" | "BANNER" | "THEME";

export type Cosmetic = {
  id: string;
  kind: CosmeticKind;
  name: string;
  /** Null for a theme, which is a pair of colours rather than a picture. */
  image: string | null;
  /**
   * A theme's slug, and null for every other kind.
   *
   * The colours themselves live in `src/theme/cardThemes.js` so the contrast gate can
   * check them at build time; the server only names which one is worn.
   */
  theme: string | null;
  animated: boolean;
  price: number;
  owned: boolean;
  equipped: boolean;
  /**
   * Comes with Gold rather than being for sale.
   *
   * The price alone cannot express this — a membership item is stored at zero, which is
   * indistinguishable from a free one — and the server refuses to sell it at any price.
   */
  membershipOnly: boolean;
};

/**
 * The whole store, from the asking gamer's point of view.
 *
 * Buying and equipping return this same shape, so a purchase updates the shelf and the
 * balance together rather than leaving the screen briefly disagreeing with itself.
 */
export type CosmeticStore = {
  frames: Cosmetic[];
  banners: Cosmetic[];
  themes: Cosmetic[];
  bundles: Bundle[];
  coins: number;
};

/**
 * A set sold for less than the sum of its parts.
 *
 * There is no "owned bundle" — buying one grants its items exactly as buying them
 * separately would, so `owned` here is derived from the parts. See `CosmeticBundle` on the
 * server for why it is a price rather than a possession.
 */
export type Bundle = {
  id: string;
  name: string;
  /** What the set costs. */
  price: number;
  /** What the parts cost separately — the number the saving is quoted against. */
  partsPrice: number;
  items: Cosmetic[];
  owned: boolean;
};

/**
 * One mission, and how far this gamer has got with it.
 *
 * `progress` is capped at `target` by the server, so a bar can be drawn straight from
 * the two without guarding against 47/10 on a badge whose counter has moved on.
 */
export type BadgeTier = 'BRONZE' | 'SILVER' | 'GOLD' | 'PRISMATIC';

export type Badge = {
  code: string;
  title: string;
  /** What to do to earn it. */
  description: string;
  icon: string;
  progress: number;
  target: number;
  /**
   * Coins credited on collection.
   *
   * Zero exactly when `cosmeticName` is set — the four hardest badges hand over a frame
   * instead of coins, so the two are never both present and never both absent.
   */
  reward: number;
  /** How hard it was. PRISMATIC is the hard tier, and the only one the app animates. */
  tier: BadgeTier;
  /** True when the artwork is animated WebP and the image needs `autoplay`. */
  animated: boolean;
  /** The frame this unlocks, for the badges that pay in cosmetics. Null for the rest. */
  cosmeticName: string | null;
  earned: boolean;
  /** Earned and the coins already claimed. Always false on an unearned badge. */
  collected: boolean;
  showcased: boolean;
};

/**
 * The whole board.
 *
 * Collecting and re-showcasing both answer with this same shape, for the reason the
 * cosmetics store does: a claim moves the balance and the badge together, and a client
 * that refetches to find out renders a screen that disagrees with itself in between.
 */
export type BadgeBoard = {
  badges: Badge[];
  coins: number;
  earned: number;
  total: number;
  /** How many badges a profile can show. The server owns this number. */
  showcaseSlots: number;
};

/**
 * The notification switches shown in Settings.
 *
 * All default to true. The operating system's permission is a separate thing above
 * these: with it denied, none of them can deliver anything however they are set.
 */
export type NotificationPreferences = {
  /** Chat messages. */
  messages: boolean;
  /** Matches, friend requests and answers, badges earned — lobby activity included. */
  social: boolean;
  /** "Come back" reminders when you have been away. */
  reminders: boolean;
};

// --- the moderator console -------------------------------------------------
// Only an ADMIN account can reach any of these. Ordinary builds of the app never
// render them, but the types live here with the rest so the console is not a second
// parallel API layer.

/** One day on the growth graph. */
export type GrowthPoint = {
  /** ISO date, UTC. */
  date: string;
  signups: number;
  /** Running total across the window, not all time. */
  total: number;
};

/**
 * The overview screen, in one response.
 *
 * Aggregates only — no account is named anywhere in here. A dashboard is the screen
 * most likely to be left open or screenshotted, so it deliberately cannot leak a
 * person.
 */
export type Analytics = {
  accounts: number;
  registered: number;
  activeToday: number;
  activeWeek: number;
  activeMonth: number;
  newToday: number;
  newWeek: number;
  banned: number;
  deleted: number;
  subscribers: number;
  /** Under-18 accounts. Drives the compliance position, not vanity. */
  minors: number;
  openReports: number;
  avatarsPending: number;
  /**
   * How long the oldest unanswered report has been waiting. The terms promise 24 hours,
   * so anything approaching that is the one number on this screen that needs acting on.
   */
  oldestOpenReportHours: number;
  mutualMatches: number;
  messages: number;
  growth: GrowthPoint[];
  funnel: Funnel;
  retention: CohortRetention[];
};

/**
 * The monetisation funnel.
 *
 * Every ratio arrives as its two counts rather than as a percentage, and is rendered that
 * way. A rate with a denominator of three is noise, and a screen that shows "33%" without
 * showing the three invites somebody to act on it — which in the first weeks after launch
 * is exactly when the denominators are smallest.
 */
export type Funnel = {
  /** Distinct accounts that opened the paywall. */
  paywallViewers: number;
  /** Of those, how many asked for a store sheet. */
  checkoutStarters: number;
  trialsStarted: number;
  paidStarted: number;
  renewals: number;
  /** Accounts that turned 30 days old in the window — the free-to-paid denominator. */
  cohort30: number;
  /** How many of them had bought Gold by day 30. */
  cohort30Paid: number;
  /**
   * Coins in and coins out over the window, kept apart rather than netted: a net of zero
   * is produced both by a healthy economy and by one where nothing happens at all.
   */
  coinsEarned: number;
  coinsSpent: number;
};

/**
 * Day-7 retention, split by the like-cap experiment.
 *
 * The point of the cohort column. Retained means active at least seven days after signing
 * up, and only accounts old enough to have had the chance are counted — so this list is
 * legitimately empty until the first cohort is a week old.
 */
export type CohortRetention = {
  cohort: string;
  signups: number;
  retained: number;
};

/** An upload waiting on a human verdict. */
export type PendingAvatar = {
  userId: string;
  username: string | null;
  /** ISO instant, or null for uploads that predate the column. */
  uploadedAt: string | null;
  /**
   * P(sexual content) from the classifier, or null when it never answered.
   *
   * Null is the interesting case and does not mean "clean": it means the model was
   * unreachable when the image arrived, so nothing has judged it. Those are re-screened
   * automatically, so a null here should be transient.
   */
  score: number | null;
};

export type PendingAvatars = { pending: PendingAvatar[] };

/** One report in the moderation queue. */
/** The moderation ladder — mirrors ModerationAction.Action on the backend. */
export type ModerationAction =
  | 'DISMISS'
  | 'WARN'
  | 'REMOVE_PHOTO'
  | 'SUSPEND_24H'
  | 'SUSPEND_7D'
  | 'BAN';

/** A reason code — mirrors ContentReport.ReasonCode. */
export type ReportReasonCode =
  | 'HARASSMENT'
  | 'SEXUAL'
  | 'SPAM_SCAM'
  | 'UNDERAGE'
  | 'IMPERSONATION'
  | 'OTHER';

/** One case in the moderation queue: everything currently said about one person. */
export type CaseSummary = {
  caseId: string;
  targetId: string;
  targetUsername: string | null;
  /** OPEN or URGENT (CLOSED never appears in the queue). */
  status: string;
  /** Sum of the reporters' weights, not a count. */
  weightedScore: number;
  distinctReporters: number;
  openedAt: string;
  ageHours: number;
  /** Past the 24 hours the terms promise. */
  overdue: boolean;
  /** Hidden from decks automatically, pending this decision. */
  autoHidden: boolean;
  /** How many times this account has been actioned before. */
  priorSanctions: number;
};

export type Cases = { cases: CaseSummary[] };

/** One report inside a case. */
export type CaseReportItem = {
  reportId: string;
  contentType: string;
  reasonCode: string | null;
  note: string | null;
  reporterId: string;
  /** The reporter's weight, 0–1, as a string. */
  reporterWeight: string;
  createdAt: string;
  /** The frozen snapshot as JSON, or null on a legacy report. */
  evidence: string | null;
};

/** One decrypted message of context for a message report. */
export type CaseMessageContext = {
  messageId: string;
  senderId: string;
  senderUsername: string | null;
  message: string;
  sentAt: string;
  /** True for the message the report was actually about. */
  reported: boolean;
};

/** One past action against the target. */
export type CaseHistoryItem = {
  action: string;
  reasonCode: string | null;
  note: string | null;
  actorId: string;
  createdAt: string;
  expiresAt: string | null;
};

/** A case in full: its reports, the target, the decrypted context, and the history. */
export type CaseDetail = {
  summary: CaseSummary;
  targetUsername: string | null;
  targetAvatarKey: string | null;
  targetAvatarStatus: string | null;
  targetJoinedAt: string | null;
  targetSuspended: boolean;
  targetSuspendedUntil: string | null;
  reports: CaseReportItem[];
  messageContext: CaseMessageContext[] | null;
  history: CaseHistoryItem[];
};

export type ResolveCaseInput = {
  action: ModerationAction;
  reasonCode?: ReportReasonCode;
  note?: string;
  removePhoto?: boolean;
};

/** A banned account, as the console lists it. */
export type BlockedUser = {
  userId: string;
  username: string | null;
  email: string | null;
  avatar: string | null;
  createdDate: string | null;
};

export type BlockedUsers = { blockedUsers: BlockedUser[] };

// --- promotion codes -------------------------------------------------------

/** What a promotion code hands over. */
export type PromoCodeKind = 'COIN' | 'GOLD';

/** Why a code can or cannot be redeemed right now. Worked out by the server's clock. */
export type PromoCodeStatus = 'ACTIVE' | 'EXPIRED' | 'EXHAUSTED' | 'DISABLED';

/** One recipient of a code, as the console's edit screen lists them. */
export type PromoAssignee = {
  userId: string;
  username: string | null;
  email: string | null;
  /** Null if the code was never emailed to this account. */
  emailedAt: string | null;
  /** Already used it. Those recipients cannot be taken off the list. */
  redeemed: boolean;
};

/** One promotion code, as the console shows it. */
export type PromoCode = {
  id: string;
  code: string;
  kind: PromoCodeKind;
  coinAmount: number | null;
  goldDays: number | null;
  expiresAt: string;
  /** Null means unlimited. */
  maxRedemptions: number | null;
  redemptionCount: number;
  /** How many accounts it was addressed to. Zero means anybody may type it. */
  assigneeCount: number;
  emailedCount: number;
  disabledAt: string | null;
  status: PromoCodeStatus;
  note: string | null;
  createdAt: string;
  /** Only present when one code was asked for by id. */
  assignees?: PromoAssignee[];
  /** How the send went, on the answer to a create or an edit that asked for one. */
  emailed?: { sent: number; failed: number };
};

export type PromoCodes = { codes: PromoCode[] };

/** The body both creating and editing a code take. */
export type PromoCodeInput = {
  kind: PromoCodeKind;
  coinAmount?: number | null;
  goldDays?: number | null;
  /** Creating only. A code cannot be renamed — it may already be in somebody's inbox. */
  code?: string;
  validDays: number;
  maxRedemptions?: number | null;
  /** Empty makes the code public. */
  assigneeIds?: string[];
  sendEmail?: boolean;
  /** Editing only. */
  disabled?: boolean;
  note?: string | null;
};

/** One account in the console's picker. */
export type DirectoryUser = {
  userId: string;
  username: string | null;
  email: string | null;
  avatar: string | null;
  createdDate: string | null;
  /** Null for an account that has never done anything. */
  lastActiveAt: string | null;
  gold: boolean;
};

/** Which group of accounts the picker is showing. */
export type DirectoryFilter =
  | 'ALL'
  | 'OFFLINE_14D'
  | 'REPORT_CONTRIBUTORS'
  | 'GOLD'
  | 'FREE'
  | 'NEW_7D';

export type UserDirectory = {
  users: DirectoryUser[];
  page: number;
  totalPages: number;
  total: number;
};

/** What "select everybody matching" resolves to. */
export type UserIds = {
  ids: string[];
  /** True when more accounts matched than the 200 a single send allows. */
  truncated: boolean;
};

/** A code addressed to this account that has not been used yet. */
export type WaitingPromoCode = {
  id: string;
  code: string;
  kind: PromoCodeKind;
  coinAmount: number | null;
  goldDays: number | null;
  expiresAt: string;
};

export type RedeemedPromoCode = {
  code: string;
  kind: PromoCodeKind;
  coinAmount: number | null;
  goldDays: number | null;
  redeemedAt: string;
};

export type MyPromoCodes = {
  waiting: WaitingPromoCode[];
  redeemed: RedeemedPromoCode[];
};

/** What a redemption actually did, so the screen can show the resulting numbers. */
export type PromoRedemption = {
  kind: PromoCodeKind;
  coinAmount: number | null;
  goldDays: number | null;
  /** The balance after crediting. */
  coinBalance: number;
  /** When Gold now runs out, or null. */
  goldExpiresAt: string | null;
};
