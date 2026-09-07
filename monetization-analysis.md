# GameBuddy — Monetization Implementation Notes

**Audience:** engineering. This is the implementation companion to the monetization strategy
report — it converts the strategy into work that can be sliced into tickets.

**Verified against:** `develop` @ `b1149af`, plus a walkthrough of the running build on
`emulator-5554`. Every file path and endpoint below was checked, not assumed.

**Before writing client code:** see `GameBuddy-App/AGENTS.md` — Expo has changed, and the
versioned SDK 57 docs are the authority.

---

## 0. The short version

| # | Work | Priority | Why |
|---|------|----------|-----|
| 1 | Wire the client to the existing billing backend | **P0** | Nothing can be sold today. Revenue is structurally $0 at any user count. |
| 2 | Build the "who liked you" screen | **P0** | Backend is done. The client function exists and has zero call sites. Highest-converting screen in the category. |
| 3 | Build the Gold paywall + plan picker | **P0** | The limit sheet currently dead-ends on "Keep looking". |
| 4 | Sell coin packs in the Market | **P0** | Three SKUs already defined server-side, unreachable by a buyer. |
| 5 | Advanced filters (build + enforce) | **P1** | Declared in the tier, enforced nowhere. Most gamer-native Gold benefit. |
| 6 | Boost + Rewind | **P1** | Gives Gold reasons beyond cap-removal, and gives us à-la-carte SKUs. |
| 7 | Gold cosmetics + raise free like cap to 15 | **P1** | Makes Gold visible; stops churning users before their first match. |
| 8 | Coin faucets — streak, quests, rewarded video | **P2** | Coin income is currently finite and then permanently zero. |
| 9 | Consumables in the Market | **P2** | Cosmetics are bought once; a recurring income needs a recurring sink. |
| 10 | ~~Season Pass — placeholder card only for v1~~ | — | **Dropped 31 Aug 2026 — no level/XP system to progress along. See the note under §4.** |

---

## 1. Current state, verified

### 1.1 Billing: backend complete, client absent

The backend is finished and careful. The client cannot reach any of it.

| Layer | State |
|---|---|
| `billing/application/controller/BillingController.java` | ✅ `POST /billing/redeem`, `GET /billing/subscription` |
| `billing/domain/AppleReceiptVerifier.java`, `GooglePlayReceiptVerifier.java` | ✅ Real store verification |
| `billing/domain/PurchaseService.java` | ✅ Idempotent replay handling |
| `billing/domain/Product.java` | ✅ 5 SKUs defined |
| `GameBuddy-App/src/api/billing.ts` | ❌ **Does not exist** |
| IAP library in `GameBuddy-App/package.json` | ❌ **Not installed** |
| Any upgrade/paywall screen in `app/` | ❌ **Does not exist** |

`src/match/LimitSheet.tsx:41-43` documents this in a comment: *"There is no purchase button
yet… a button here would lead nowhere."*

### 1.2 Gold entitlements: 2 of 5 enforced

| Entitlement | Declared in `SubscriptionTier` | Enforced | Visible in app |
|---|---|---|---|
| `dailySwipes` 50 → ∞ | ✅ | ✅ `SwipeQuota.charge()` | ✅ |
| `dailyAccepts` 5 → ∞ | ✅ | ✅ `SwipeQuota.charge()` | ✅ |
| `canSeeWhoLikedYou` | ✅ | ✅ `DefaultMatchService:298` | ❌ **no UI** |
| `canUseAdvancedFilters` | ✅ | ❌ nothing reads it | ❌ |
| `maxGroupChats` 3 → 25 | ✅ | ❌ nothing reads it | ❌ |

Enforcement was verified by searching the whole repo for each accessor.
`canUseAdvancedFilters` and `maxGroupChats` appear **only** in the enum that defines them,
the DTO that reports them, and the controller that populates the DTO.

### 1.3 Coin economy: finite income, unreachable catalogue

- **Total coins earnable in a lifetime: 975.** Source: 13 one-time badge rewards
  (`profile/domain/badge/Badge.java:31-58`). There is no other faucet.
- **Paid catalogue cost: 9,400 coins.** 9 frames (150–1,500) + 6 banners (200–800)
  (`db/seed-local.sql:211-231`).
- A player who earns **every badge in the game affords 10.4% of the shelf**, then income
  drops to zero permanently.
- `Product.java:34-36` defines coin packs of 500 / 1,200 / 3,000 — **not purchasable from
  the app.**

> **All four numbers above are historical.** Badges pay 775 from 10 (three retired with
> Community), the faucets of §3.4.1 all shipped and were retuned to ~320/week on
> 2026-08-31, and the catalogue is **44 items / 39,500 coins** after
> `upgrade-2026-44-shelf-reprice.sql`. Coin packs are purchasable, and there are four of
> them — see §3.2.2.

---

## 2. Decisions already taken

These are settled. Listed so nobody re-opens them mid-sprint.

1. **No paid loot boxes / gacha crates.** Evaluated and declined. At 15 paid items, full-set
   collection takes ~50 openings with ~70% duplicates, and we have no resale market and a
   ~2-second flex surface. Also costs us Belgium + Netherlands, forces odds disclosure on
   both stores, and pushes us into a "simulated gambling" content rating. **Revisit only at
   60–100+ cosmetics with a seasonal art pipeline.**
2. **Group chat is dropped — Communities cover it.** See §3.1.
3. ~~**Season Pass ships as a placeholder in v1.**~~ Reversed 31 Aug 2026 — dropped entirely,
   see the note under §4.
4. **One currency.** Coins, both earnable and purchasable. No second premium currency — we
   sell only cosmetics, so there is no pay-to-win line to protect and a second currency
   would just create unspendable remainders.
5. **Free like cap rises 5 → 15.** With current density, a user who hits the wall before
   their first match churns rather than upgrades.
6. **Ads: rewarded video only.** Opt-in, capped, pays coins. No banners, no interstitials.
7. **Prices live in the stores, not in our DB.** `Product.java` is already right about this;
   keep it that way.

---

## 3. Work breakdown

### 3.1 — Remove the group-chat entitlement (P0, small)

**Decision:** group chat will not be built. Communities already provide the many-to-many
social surface, with a fuller feature set than a group DM would have had: create, browse,
join, members, posts, comments, likes.

Group chat does **not** exist anywhere — no entity, no table, no service, no screen. The
only trace is a phantom entitlement that we report to the client and never honour.

**Touches — delete `maxGroupChats` from all three:**

- `common/src/main/java/com/gamebuddy/common/enums/SubscriptionTier.java` — field, ctor
  param, both enum constant arg lists, `maxGroupChats()` accessor
- `GameBuddy-backend/src/main/java/com/gamebuddy/billing/interfaces/dto/SubscriptionResponseBody.java` — field
- `GameBuddy-backend/src/main/java/com/gamebuddy/billing/application/controller/BillingController.java:65` — the arg passed to the DTO ctor
- `common/src/test/java/com/gamebuddy/common/enums/SubscriptionTierTest.java` — any assertion on it

**Done when:** `grep -ri maxGroupChats` returns nothing, and `GET /billing/subscription`
returns a body with no `maxGroupChats` key.

> **Note:** the existing `SubscriptionResponseBody` is `@AllArgsConstructor` — removing a
> field changes the constructor arity. Fix the call site in `BillingController`, don't add
> an overload.

---

### 3.2 — P0: Turn the till on

Everything else multiplies by zero until this ships.

#### 3.2.1 IAP client + billing API module

**Goal:** the app can complete a store purchase and hand the receipt to our backend.

**Suggested approach:** a plain IAP library (e.g. `react-native-iap`) rather than
RevenueCat. Our backend already does full receipt verification, so a service that
re-verifies would duplicate it and split the source of truth. We need the library only to
open the store sheet and return a receipt.

Requires an Expo config plugin and a dev build — no Expo Go. Check the exact library and
version against the SDK 57 docs before committing to it.

**Touches:**
- `GameBuddy-App/package.json`, `app.json` / `app.config.*` (config plugin)
- **New:** `GameBuddy-App/src/api/billing.ts`

**Contract that already exists — do not redesign:**

```
POST /billing/redeem
  body: { platform, productId, receipt }   // RedeemPurchaseRequest
  200:  { productId, status, entitlementExpiresAt }

GET  /billing/subscription
  200:  { tier, expiresAt, dailyAccepts, canSeeWhoLikedYou, canUseAdvancedFilters }
        // after §3.1 removes maxGroupChats
```

**Error codes to handle** (from `TransactionCode`, already mapped in
`src/api/envelope.ts`):

| Code | Meaning | Client behaviour |
|---|---|---|
| 160 | `PRODUCT_NOT_FOUND` | Bug — log it, generic failure message |
| 161 | `PURCHASE_VERIFICATION_FAILED` | Do **not** grant anything. "We couldn't verify that purchase." |
| 162 | `PURCHASE_ALREADY_PROCESSED` | **Not an error to the user.** Treat as success, refresh entitlement |
| 159 | `SUBSCRIPTION_REQUIRED` (402) | Route to paywall — already mapped at `envelope.ts:61` |

**Done when:** a sandbox purchase of `gamebuddy.gold.monthly` completes, `/billing/redeem`
returns 200, and `GET /billing/subscription` reports `GOLD` with a future `expiresAt`.

**Critical:** redemption must be retried on next app launch if the app dies between the
store charging and our `/redeem` call. A charged user with no entitlement is the worst
possible failure. Persist unredeemed receipts locally and flush on startup.

#### 3.2.2 Register SKUs in both consoles

Existing (in `Product.java`):
```
gamebuddy.gold.monthly      GOLD, 30 days
gamebuddy.gold.yearly       GOLD, 365 days
gamebuddy.coins.500         500 coins
gamebuddy.coins.1200        1200 coins
gamebuddy.coins.3000        3000 coins
```

**Add:** `gamebuddy.gold.weekly` — GOLD, 7 days. Needs a new `Product` enum constant.

**Pricing** (set in the consoles, not in code):

| SKU | Price | Role |
|---|---|---|
| Weekly | $3.99 | Impulse buy at the moment of need |
| Monthly | $7.99 | Anchor. Pre-selected. Carries a **3-day free trial** |
| Yearly | $39.99 | Shown as a derived saving, ~58% at US prices |

> **Updated 7 September 2026.** Gold prices are unchanged. The coin packs doubled and a
> fourth was added, because priced off the pack people actually buy a coin was worth
> $0.0030 and the dearest item in the shop cost $4.50 — below Discord's cheapest
> decoration:
>
> | SKU | Coins | Price | Bonus |
> |---|---|---|---|
> | `gamebuddy.coins.500` | 500 | **$3.99** | — |
> | `gamebuddy.coins.1200` | 1 200 | **$7.99** | +20% |
> | `gamebuddy.coins.3000` | 3 000 | **$16.99** | +41% |
> | `gamebuddy.coins.7000` | 7 000 | **$34.99** | +60% |
>
> The largest pack is deliberately below Gold yearly: a coin pack costing the same as a
> year of Gold invites the comparison and loses it. The yearly "save 58%" is now *derived*
> from the store's own monthly and yearly prices rather than hardcoded, because regional
> pricing can make a fixed figure false — see `yearlySaving` in `app/(main)/gold.tsx`.
>
> Prices are no longer duplicated in the bundle either: `useStorePrices` reads
> `priceString` from RevenueCat, so a reprice is a console edit rather than an app release,
> and a Turkish buyer is shown lira instead of dollars.

Set **regional price tiers from day one** — Turkey, Brazil, India, SEA convert near zero at
US pricing.

#### 3.2.3 The "who liked you" screen

**This is the highest-value screen in the whole plan.** The backend is already done and
already correct.

`DefaultMatchService.getWhoLikedYou()` returns the **count free to everyone** and withholds
identities behind Gold — exactly the right shape, deliberately so per its comments.

```
GET /match/get/liked-you
  200: { count: number, likedYou: Candidate[], locked: boolean }
       // likedYou is [] when locked; count is always populated
```

The client function **already exists** at `src/api/match.ts:34` (`likedYou`) and has **zero
call sites**. This is a UI task, not a feature task.

**Build:**
- New screen, reachable from Home. Badge the entry point with `count` when > 0.
- `locked: true` → blurred candidate cards, `count` shown large, upgrade CTA.
- `locked: false` → real cards, tappable through to the profile.

**Done when:** a BASIC user with pending admirers sees an accurate count and blurred cards;
a GOLD user sees faces.

#### 3.2.4 Gold paywall + plan picker

**Build:** a paywall screen showing the benefit list, three plans with monthly
pre-selected, trial terms, and restore-purchases.

**Placement — four entry points:**
1. **Admirers screen** (strongest by a wide margin)
2. **Limit sheet** — replace the `Keep looking` button in `src/match/LimitSheet.tsx:98`
   with the plan picker. The 402 routing already exists at `src/match/useDeck.ts:85`.
3. **Filter controls** — visible to free users, locked with a Gold chip
4. **Day 3**, one-time, after the user has matched at least once

**Do not** put a hard paywall in onboarding. Apps that deliver value before gating convert
1.5–2× better, and our free tier is already good enough to sell itself.

#### 3.2.5 Coin packs in the Market

Add a Coins section to `app/(main)/market.tsx` with the three packs and the live balance
(`CosmeticsResponseBody.coins` already returns it).

**Rule:** never drop a store sheet on a user inside a failure. When a cosmetic purchase
fails with `129 COIN_NOT_ENOUGH`, say so and let them choose to visit Coins.

---

### 3.3 — P1: Make Gold worth buying

#### 3.3.1 Advanced filters

Build the feature and enforce `canUseAdvancedFilters`. Filters: **game, platform, region,
online-now**. This is the most gamer-native benefit we have — "only show me Valorant
players on PC in my region" is worth real money to someone who wants a squad tonight.

Show the controls to free users, locked, so the value is legible before purchase.

Enforcement goes in the recommendation path alongside the existing tier check pattern in
`DefaultMatchService`. Refuse with `159 SUBSCRIPTION_REQUIRED` (402) — the client already
routes it.

#### 3.3.2 Boost and Rewind

- **Boost** — 30 min at the front of the deck in the user's region. Gold gets 1/week.
  Also our best à-la-carte SKU.
- **Rewind** — undo the last swipe. Cheap, removes a real regret, category-standard.

#### 3.3.3 Gold identity + free-tier rebalance

- Gold frame, Gold profile badge, Gold-exclusive cosmetics. Makes the tier **visible to
  other users** — every subscriber advertises it for free.
- **Raise `BASIC.dailyAccepts` from 5 to 15** in `SubscriptionTier`. Keep `dailySwipes` at
  50. Treat both as retention dials, not revenue levers, and watch day-7 retention.

---

### 3.4 — P2: Fix the economy

#### 3.4.1 Coin faucets

| Source | Yield | Cadence | State |
|---|---|---|---|
| Badges | 975 total | Once, lifetime | ✅ shipped |
| Daily login streak | 5 → 25/day | Daily, escalating | new |
| Weekly quests | ~75/week | Weekly reset | new |
| Rewarded video | 15/view, 3/day cap | Daily | new |
| Gold stipend | 600/month | While subscribed | new |
| Coin packs | 500/1200/3000 | On purchase | backend ✅ |

**Target: 250–400 coins/week for an engaged free player.** That makes the cheapest frame
(150) reachable in a week and the 1,500-coin flagship a month-long goal.

Weekly quests should reuse the existing `BadgeMetric` sources where possible rather than
introducing a second counting system.

#### 3.4.2 Consumables (coin sinks)

| Item | Coins | Direct price |
|---|---|---|
| Boost | 300 | $1.99 / 5 for $6.99 |
| Super Like | 100 | 5 for $4.99 |
| **Unlock one admirer** | 150 | — |
| Rewind | 50 | — |
| Extra likes today (+10) | 200 | — |

**"Unlock one admirer" is the most important row.** It lets a free user taste the best Gold
benefit at a price they can earn, and every buyer is a qualified subscription lead.

#### 3.4.3 Rewarded video

Opt-in only, 3/day, pays coins. **No banners, no interstitials** — they'd make a $7.99
subscription look like a bribe to remove them, and Gold then has to "remove ads" instead of
adding value.

#### 3.4.4 Market tab layout

Restructure `app/(main)/market.tsx` into sections, in this order:

1. **Gold card** — status if subscribed, pitch + trial if not
2. ~~**Season Pass**~~ — dropped, see the note under §4
3. **Coins** — balance, 3 packs, "free coins" entry to rewarded video + quests
4. **Quests & streak** — today's streak, this week's quests, with progress
5. **Consumables** — Boost, Super Like, Rewind
6. **Cosmetics** — Frames/Banners as today, plus a Featured row and locked Gold-exclusives

Keep the current header pattern that shows the balance next to what's being bought — it
already answers the one question a user has on this screen.

---

## 4. Season Pass — v1 is a placeholder only

> **Update, 31 August 2026 — the Season Pass was dropped entirely.** Not deferred: dropped.
> A pass needs a track to progress along, and GameBuddy has no level or XP system and is not
> getting one. The teaser card, the `seasonPassTeaser` field on `GET /billing/subscription`,
> the `gamebuddy.season-pass.teaser` property and the `SEASON_PASS_TEASER` env var are all
> removed. The shop was widened instead — eight frames and eight banners, see
> `db/upgrade-2026-35-shelf-expansion.sql` and `cosmetics-market-research.md` for what else
> coins could buy. Everything below in this section, plus §0 row 10, §2 item 3, §3.4.4 item 2,
> §6 and §7 Q2, is kept as the record of a decision that has since been reversed.

### 4.1 Decision

Full Season Pass logic is **out of scope for the first release.** We are not rushing a
seasonal system with a content pipeline behind it. Ship a teaser card in the Market so the
slot exists and users can see it's coming.

### 4.2 Placeholder spec

Render a non-interactive card in Market position 2.

**Copy — recommended:**

> **Season Pass**
> Coming soon — new rewards every season.

I changed the wording from *"Season Pass is loading. Check out later!"* for one specific
reason: **"loading" reads as a network state.** Users will assume it's a spinner that will
resolve in a few seconds, then read it as a bug when it never does — and some will report
it. "Coming soon" says the same thing without implying a broken request. Happy to use the
original string if you'd rather; this is the only change I made to the brief.

**Requirements:**
- Must look **deliberate, not broken** — styled as a teaser card, not a skeleton loader or
  an empty state. No spinner, no shimmer.
- Not tappable. No press feedback, no navigation, `accessibilityState: { disabled: true }`.
- Behind a flag so it can be hidden without a code change.

**On the flag:** there is no remote config in the app today — `src/api/config.ts` only
resolves the API base URL. Two options:

- **Simple:** an `EXPO_PUBLIC_SEASON_PASS_TEASER` env flag. Turning it off needs a rebuild.
- **Better, small:** add a boolean to the existing `GET /billing/subscription` response.
  The client already calls it, so this costs one field and makes the card server-switchable
  with no release.

Take the second if the endpoint is being touched anyway for §3.1.

### 4.3 What the real thing looks like later

Recorded so the placeholder isn't designed into a corner:

- **6-week seasons**, 8–9 per year.
- **Free track:** 2 cosmetics, ~400 coins, 1 Boost across the season.
- **Premium track:** $4.99 or **included with Gold** — 6 cosmetics including one animated
  season-exclusive, ~1,200 coins, 4 Boosts, a season badge for the profile showcase.
- Bundling premium into Gold is the stronger play: it's a second reason to subscribe and,
  more valuably, a reason to stay subscribed *through* the season instead of cancelling
  after month one.
- Progression should feed off the same quest/metric system as §3.4.1 — don't build a third
  counter.

**Blocker to be honest about:** a season cadence is an *art* commitment before it's an
engineering one — 6 new cosmetics every 6 weeks, forever. Don't promise seasons in the UI
beyond "coming soon" until that pipeline is real.

---

## 5. Existing contracts — do not rebuild

| Endpoint | Returns | Client fn |
|---|---|---|
| `POST /billing/redeem` | `{ productId, status, entitlementExpiresAt }` | ❌ needs `billing.ts` |
| `GET /billing/subscription` | `{ tier, expiresAt, dailyAccepts, canSeeWhoLikedYou, canUseAdvancedFilters }` | ❌ needs `billing.ts` |
| `GET /match/get/liked-you` | `{ count, likedYou[], locked }` | ✅ `match.ts:34` — **no call sites** |
| `GET` swipe allowance | `{ tier, remainingSwipes, remainingAccepts, unlimited, resetsAt }` | ✅ wired |
| Cosmetics store / buy / equip / unequip | `CosmeticsResponseBody` incl. `coins` | ✅ `cosmetics.ts` |

Two backend patterns worth preserving as you extend them:

- **`SubscriptionTier.effective(stored, expiresAt, now)` is the only correct way to ask what
  tier someone holds.** It fails closed on a missing expiry. Never read the stored tier
  directly — that's how cancelled subscribers keep GOLD forever.
- **Coin spends run read-check-write inside one transaction** under the `@Version`
  optimistic lock on `Gamer` (`DefaultCosmeticService.buy`). Every new coin sink must do the
  same, or double-taps will charge once and grant twice.

---

## 6. Out of scope for v1

- Paid loot boxes / crates of any kind (§2.1)
- Group chat (§3.1)
- Season Pass — dropped entirely, not merely deferred (see the note under §4)
- Banner and interstitial ads (§3.4.3)
- Secondary market / cosmetic trading

---

## 7. Open questions for product

1. **Trial length** — 3 days is the recommendation. 7 days converts more trials to paid but
   delays first revenue and raises the refund window. Worth an A/B test once volume exists.
2. ~~**Is the premium Season Pass track included with Gold, or sold separately?**~~ Moot —
   the pass was dropped on 31 Aug 2026, see the note under §4.
3. **Rewarded video network** — needs picking, and it changes the eCPM assumption in the
   revenue model.
4. **Does the free like cap move to 15 at launch, or do we A/B it?** A/B is better data but
   needs the analytics in place first.

---

## 8. Instrument from day one

| Metric | Target | Reads on |
|---|---|---|
| Paywall view → trial start | > 8% | Whether the offer is legible and well-placed |
| Trial → paid | > 45% | Whether Gold delivers in the first 3 days |
| Free → paid, 30-day | 5–8% | Overall funnel health |
| Gold renewal, month 2 | > 70% | Whether benefits survive novelty |
| Coins earned vs. spent, weekly | ~1:1 | Whether the economy inflates or starves |
| Day-7 retention by like-cap cohort | rising | Whether the free tier is generous enough |

The last one matters most in the first quarter. In a matching product **liquidity precedes
revenue** — every retention point compounds into match quality, and match quality is what
anyone is ultimately paying for.
