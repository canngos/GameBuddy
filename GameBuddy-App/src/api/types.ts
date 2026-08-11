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
  gender: string | null;
  /** Own profile only. */
  coin?: number;
  /**
   * Own profile only, and the reason the console exists as a separate shell: an ADMIN
   * account has no age, games or keywords, so the onboarding check would otherwise
   * strand it on "finish your profile" forever.
   */
  role?: 'USER' | 'ADMIN';
  games: Game[];
  keywords: Keyword[];
  /** The three (at most) this gamer chose to display. Empty is normal. */
  badges: ShowcasedBadge[];
  /** How many have been earned in total. */
  badgeCount: number;
  joinedCommunities: { id: string; name: string }[];
  /** Own profile only. */
  friends?: GamerSummary[];
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
  favoriteGames: { gameName: string; gameIcon: string | null }[];
  /** Names, not ids — this DTO flattens keywords to plain strings. */
  selectedKeywords: string[];
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

/** Boost state, returned by both GET and POST /match/boost. */
export type Boost = {
  active: boolean;
  /** ISO instant. Null when nothing is running. */
  expiresAt: string | null;
  /** What the next boost costs. Zero when the weekly Gold one is available. */
  cost: number;
  freeAvailable: boolean;
  /** ISO instant, or null when a free one is available now or never. */
  nextFreeAt: string | null;
  coinBalance: number;
  /** What the call just spent, or null when nothing was bought. */
  coinsSpent: number | null;
};

/** One weekly quest, with this week's progress. */
export type Quest = {
  /** The enum name, which is what a claim is addressed to. */
  code: string;
  title: string;
  /** Capped at `target` by the server, so a progress bar needs no guard. */
  progress: number;
  target: number;
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
  quests: Quest[];
  stipendAvailable: boolean;
  stipendAmount: number;
  /** ISO instant, or null when available now or not a member. */
  stipendReadyAt: string | null;
  coinBalance: number;
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
  /** Keyword UUIDs. At least 5. */
  keywords: string[];
};

/**
 * One community in the directory.
 *
 * `isJoined` is computed per-caller by the backend, so the same community is a
 * different object for two different gamers. It is the only thing that decides whether
 * the posts endpoint will answer: a non-member gets 403, not an empty list.
 */
export type Community = {
  communityId: string;
  name: string;
  description: string;
  communityAvatar: string | null;
  wallpaper: string | null;
  /** ISO instant. */
  createdDate: string;
  memberCount: number;
  postCount: number;
  isJoined: boolean;
};

/**
 * One post.
 *
 * There is no author id here, only `username` — so "did I write this" is a comparison
 * against your own username rather than an id. That works because usernames are unique
 * (the backend refuses a duplicate with USERNAME_EXISTS), but it is also why a post
 * cannot link to its author's profile.
 */
export type Post = {
  postId: string;
  username: string;
  avatar: string | null;
  communityName: string;
  title: string;
  body: string | null;
  picture: string | null;
  /** ISO instant. */
  updatedDate: string;
  likeCount: number;
  commentCount: number;
  isLiked: boolean;
};

export type Comment = {
  commentId: string;
  username: string;
  avatar: string | null;
  message: string;
  likeCount: number;
  isLiked: boolean;
  /** ISO instant. */
  updatedDate: string;
};

/** A member of a community. `isOwner` marks the one who can delete or hand it on. */
export type CommunityMember = {
  userId: string;
  gamerUsername: string;
  avatar: string | null;
  /** The worn frame's URL, or null for none — the common case, and not an error. */
  frame: string | null;
  isOwner: boolean;
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
  status: 'APPROVED' | 'PENDING' | 'REJECTED';
  url: string;
};

/**
 * One item in the cosmetics store.
 *
 * `owned` is the server's answer, not something to re-derive from `price`: a free
 * cosmetic is owned by everyone without any purchase existing, and the rule for that
 * lives on the backend so there is one place it can be wrong.
 */
export type Cosmetic = {
  id: string;
  kind: 'FRAME' | 'BANNER';
  name: string;
  image: string;
  animated: boolean;
  price: number;
  owned: boolean;
  equipped: boolean;
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
  coins: number;
};

/**
 * One mission, and how far this gamer has got with it.
 *
 * `progress` is capped at `target` by the server, so a bar can be drawn straight from
 * the two without guarding against 47/10 on a badge whose counter has moved on.
 */
export type Badge = {
  code: string;
  title: string;
  /** What to do to earn it. */
  description: string;
  icon: string;
  progress: number;
  target: number;
  /** Coins credited on collection. */
  reward: number;
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
  /** Matches, friend requests and answers, badges earned. */
  social: boolean;
  /** Posts, comments and likes in communities you joined. */
  communities: boolean;
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
export type Report = {
  reportId: string;
  /** POST, COMMENT, MESSAGE or PROFILE. */
  contentType: string;
  contentId: string;
  authorId: string;
  authorUsername: string | null;
  reporterId: string;
  reason: string;
  status: string;
  createdAt: string;
  /** The reported text, or null once the content has been removed. */
  content: string | null;
  /** Open reports against this author across everything they have written. */
  authorOpenReportCount: number | null;
  /** Past the 24 hours the terms promise. Decided by the server, not here. */
  overdue: boolean | null;
  /** How long this has been waiting. */
  ageHours: number | null;
};

export type Reports = { reports: Report[] };

/** A banned account, as the console lists it. */
export type BlockedUser = {
  userId: string;
  username: string | null;
  email: string | null;
  avatar: string | null;
  createdDate: string | null;
};

export type BlockedUsers = { blockedUsers: BlockedUser[] };
