# GameBuddy — What else can we sell for coins?

**Date:** 31 August 2026
**Companion to:** `monetization-analysis.md` (which covers the money side — packs, Gold,
consumables). This one is only about **cosmetics**: things a gamer buys with coins that change
how they look to other people.

**Why now.** The Season Pass placeholder was dropped — there is no level or XP system for a
pass to progress along — and the shop needed more to sell rather than more to promise. Eight
frames and eight banners shipped alongside this note (`upgrade-2026-35-shelf-expansion.sql`).
This is the survey of what could come after them.

---

## 0. The short version

| # | Item | Effort | Why it is worth doing |
|---|------|--------|-----------------------|
| 1 | **Animated banners** | S | Steam charges ~4× for animated over static. We already prove animated WebP works on the frames. |
| 2 | **Limited-time drops** | S | Scarcity is the single strongest spend driver in every shop surveyed — and it is what the Season Pass was really for. |
| 3 | **Username colour** | S/M | The cheapest "everyone sees it" cosmetic after the frame. GameTree gates it behind XP; we can sell it. |
| 4 | **Bundles** | M | Discord's own answer to a shop with many small items. Raises average spend without new art. |
| 5 | **Extra badge showcase slot** | S | Zero art cost. A coin sink built entirely from something we already have. |
| 6 | **Match-moment effect** | M | The only cosmetic shown to the other person at the exact moment they care. |
| 7 | **Profile card theme** | M | Discord and Steam both sell this. Needs a contrast pass. |
| 8 | **Lobby host accent** | M | Ties cosmetics to the Gold-gated feature. |
| 9 | **Titles** | S | Cheap, fits the play-style tags, but needs copy in seven languages. |
| 10 | **Gifting** | M | Social by design; every gift is a coin purchase by someone else. |
| 11 | **Chat stickers** | M/L | Real revenue elsewhere, but private — low flex value, and a new moderation surface. |

Everything above is **cosmetic only**. Nothing on this list touches matching, ranking or
limits, which keeps us clear of the pay-to-win line the product has held so far.

---

## 1. What comparable products actually sell

Surveyed August 2026. Sources at the end.

| Product | What it sells | Price signal |
|---|---|---|
| **Discord Shop** | Avatar decorations (our "frames"), **profile effects** (an animation over the profile card), **nameplates** (a texture behind the username), profile frames. Sells **bundles** of a matching set at a discount. Nitro members get shop discounts and member-only items. | $5.99–$12.99 per item, bought outright |
| **Steam Points Shop** | Avatar frames 2 000 pts · animated avatars 3 000 · static profile backgrounds 500 · animated backgrounds 2 000 · mini-profile backgrounds · animated stickers 1 000 · emoticons 100 · chat effects · showcases. Themed whole-profile bundles up to 10 000. | **animated ≈ 4× static** |
| **Plink** (LFG app, closest competitor) | "Elite profile theme + avatar frame" bundled *into* the weekly pass; gifts to teammates; profile boost. | cosmetics as a subscription perk |
| **GameTree** (LFG app) | Cover image, **username colour**, profile themes — unlocked by XP, not sold. | free / XP-gated |
| **Yubo** | Coins buy profile customisation, stickers, boosts and spotlights. | coins + weekly packs |
| **PlayStation Stars** | Collectibles arranged in a display case on the PSN profile. | earned through campaigns |
| **Reddit** | Collectible avatars that also grant a special profile and comment treatment. | limited editions |
| **Tinder / Bumble / Hinge** | Boosts, Super Likes, Roses — **consumables, not cosmetics**. | $1.99–$4.99 |

**Two things stand out.**

First, **the dating apps sell almost no cosmetics at all** — their whole catalogue is
visibility. We should not read our monetization off them, despite the shared swipe mechanic.
The right comparables are Discord and Steam, which sell profile decoration to the same people
who use GameBuddy.

Second, **every one of those catalogues is broader than ours in kind, not just in count.**
Discord sells four *categories*; Steam sells eight. We sell two. Adding a ninth frame is worth
less than adding the first item of a new kind, because a gamer who does not like rings has
nothing else to spend on.

### Does our pricing make sense against theirs?

**Superseded on 7 September 2026. The conclusion below was wrong, and the reason it was
wrong is worth keeping.**

It priced a coin off the *smallest* pack — 500 for $1.99, so $0.0040 a coin — and almost
nobody buys the smallest pack. Against the pack people actually buy (3000 for $8.99) a coin
was worth $0.0030, and the whole ladder was a quarter cheaper than the table below claimed:

| Our item | Coins | Claimed above | Actually |
|---|---|---|---|
| Signature frame | 100 | $0.40 | $0.30 |
| Hive frame | 400 | $1.60 | $1.20 |
| Glitch frame (animated) | 1 300 | $5.20 | $3.90 |
| Ember frame (animated) | 1 500 | $6.00 | **$4.50** |

So the dearest item in the shop cost $4.50 against a market whose *cheapest* comparable —
a single Discord decoration — is $5.99. The ladder did not land inside the band; it sat
underneath it. And at ~320 earned coins a week the flagship was under five weeks of free
play, which is not a purchase decision at all.

**What was done.** Coin packs doubled and a fourth was added (`$3.99 / $7.99 / $16.99 /
$34.99` for 500 / 1 200 / 3 000 / 7 000), and the shelf went up 40%
(`upgrade-2026-44-shelf-reprice.sql`). The Ember frame is now 2 100 coins ≈ **$11.89**,
inside Discord's $5.99–$12.99 band, and about 6.6 weeks of free play. The entry rung moved
100 → 150, still about three days.

The original text follows, kept because the comparables in it are still good and the
mistake in it is instructive.

> A 500-coin pack is $1.99, so coins are worth roughly $0.004 each.
>
> | Our item | Coins | ≈ Real money | Comparable |
> |---|---|---|---|
> | Signature frame | 100 | $0.40 | Steam emoticon (100 pts) |
> | Hive frame | 400 | $1.60 | Steam static background (500 pts) |
> | Glitch frame (animated) | 1 300 | $5.20 | Discord decoration ($5.99), Steam animated frame (2 000 pts) |
> | Ember frame (animated) | 1 500 | $6.00 | Discord decoration, mid-range |
>
> The ladder lands inside the band the market already charges, and the ~4× animated-to-static
> ratio matches Steam's. **No repricing is indicated.** Worth remembering that most of our coins
> are *earned* rather than bought, so these numbers are a ceiling on perceived value, not a
> revenue forecast.

---|---|---|---|
| Signature frame | 100 | $0.40 | Steam emoticon (100 pts) |
| Hive frame | 400 | $1.60 | Steam static background (500 pts) |
| Glitch frame (animated) | 1 300 | $5.20 | Discord decoration ($5.99), Steam animated frame (2 000 pts) |
| Ember frame (animated) | 1 500 | $6.00 | Discord decoration, mid-range |

The ladder lands inside the band the market already charges, and the ~4× animated-to-static
ratio matches Steam's. **No repricing is indicated.** Worth remembering that most of our coins
are *earned* rather than bought, so these numbers are a ceiling on perceived value, not a
revenue forecast.

---

## 2. The ranked list, with what each one actually costs to build

Effort is backend + app together. Everything reuses `DefaultCosmeticService.buy` — the
read-check-write inside the `@Version` optimistic lock on `Gamer` — which is the rule for any
new coin sink.

### 1. Animated banners — small

Steam prices animated backgrounds at 4× static, and we already have the whole pipeline:
`ProfileBanner` renders through `expo-image` with `autoplay`, and the six animated frames prove
animated WebP decodes on Android. No schema change — `cosmetic.animated` already exists.

Cost is a generator function and a migration. Watch file size: the frames run 94–361 KB at
256×256, and a 1200×400 canvas is 5.5× the pixels, so these need short loops and few moving
elements. Price at 900–1 400.

### 2. Limited-time drops — small

**This is what the Season Pass was really for**, and it works without any notion of level or XP.
Add `available_from` / `available_until` to `cosmetic`, filter in `store()`, and leave ownership
alone so anything already bought stays bought forever.

Scarcity is the strongest driver in every shop surveyed, and unlike a season pass it commits us
to nothing: if no new art is ready, nothing rotates and nobody is promised anything. It also
gives a reason to open the Market on a particular day, which nothing currently does except the
streak.

The honest caveat from the old §4 still applies — a rotation is an **art commitment**. The
difference is that a drop that does not happen is invisible, whereas a season that does not
happen is a broken promise on the card.

### 3. Username colour / name style — small-to-medium

GameTree gates username colour behind XP; Discord sells nameplates. It is the cheapest
"everyone sees it" cosmetic after the frame, because the name is already rendered everywhere
the avatar is.

Add a `NAME` value to `CosmeticKind`, an `equipped_name_id` slot on `gamer`, and pass the colour
through the roughly eight places a display name is drawn (deck card, profile header, admirer
card, friends, messages list and thread, lobby member list). No new art at all — the item is a
token, not an image.

**One caution:** colour has to come from the theme's role names, not free hex, or `npm run
check`'s contrast gate cannot pass judgement on it. Offer a curated set that is legible in both
themes.

### 4. Bundles — medium

Discord's answer to a shop full of small items: a matching frame and banner for less than the
sum. Two ways in — a `bundle` row that grants several ownership rows in one transaction, or a
rule that discounts a matching pair at checkout. The first is cleaner and survives repricing,
because `gamer_cosmetic.paid` already records what was actually charged per item.

Raises average spend without a single new drawing, which is what makes it worth doing before
the art-heavy items below.

### 5. Extra badge showcase slot — small

Badges already carry `showcaseSlot`. Selling a fourth and fifth slot is a coin sink with **zero
art cost**, and it rewards the players who have most engaged with the app.

Strictly this is utility rather than decoration, so it belongs on the consumables shelf rather
than in Frames and Banners — but it is the cheapest new thing we could sell, so it is on this
list.

### 6. Match-moment effect — medium

Discord's "profile effects", aimed at our best moment. A short animation on the match screen
that **both people see**. Every other cosmetic here is passive; this one fires at the instant
two people have just agreed to play together, which is the highest-attention second in the app.

Needs a new `CosmeticKind`, a slot, and the match screen to read the other gamer's equipped
effect as well as your own. Test on the low-end device before committing — the match screen is
already animated.

### 7. Profile card theme / accent — medium

Sold by both Discord (profile themes) and Steam (backgrounds). A colour set applied to the
profile header and the deck card. Higher risk than the rest: it touches text contrast on a
screen full of text, so it needs a real design pass and the contrast gate has to stay green for
every combination sold.

### 8. Lobby host accent — medium

A banner or accent on the lobby card, visible to everyone browsing lobbies. Ties cosmetics to
the Gold-gated feature and gives hosts — the most invested users — something to buy. Waits on
lobbies having enough traffic for the browse list to be a real audience.

### 9. Titles — small build, awkward content

A short curated label under the name — "Night owl", "IGL", "Completionist" — sold from a fixed
list. It fits the play-style tags the product already has, and it is a token rather than art.

The catch is not engineering: **every title needs copy in seven languages**, and titles are
exactly the kind of short punchy text that translates badly. Budget for a translator, not a
dictionary entry.

### 10. Gifting — medium

Plink sells gifts to teammates. Socially it is the strongest item here — a gift is a purchase by
one person for another, and it arrives with a notification. Needs a receipt on the ownership
row, a notification, and a rule about gifting something already owned (refuse before charging).

Worth doing only once there are enough friendships for gifting to have a target.

### 11. Chat stickers / emote packs — medium-to-large

Real money elsewhere (Steam sells emoticons and stickers; Yubo bundles them into packs), but for
us the weakest of the list: chat is private, so a sticker is seen by one person, and it opens a
**new moderation surface** — an image sent between users is exactly the kind of content the
avatar pipeline exists to screen. Revisit after the profile surfaces are exhausted.

---

## 3. Not recommended

Settled previously and unchanged by this survey:

- **Loot boxes / gacha crates.** Declined in `monetization-analysis.md` §2.1 and the reasoning
  holds: we have no resale market, a ~2-second flex surface, and it costs us Belgium and the
  Netherlands plus a "simulated gambling" content rating. Revisit at 60–100+ cosmetics.
- **A second premium currency.** One currency, per §2.4. We sell only cosmetics, so there is no
  pay-to-win line to protect and a second currency would only create unspendable remainders.
- **Stock avatars.** Rejected in `market.tsx`'s own docstring, and it is still right: once
  gamers upload their own picture, a stock image anybody else can also buy is a weak thing to
  charge for. A frame composes with the photo somebody already chose.
- **Anything XP- or level-gated.** There is no such system, which is why the Season Pass was
  dropped. Do not reintroduce one to justify a cosmetic.
- **Cosmetics that affect matching or visibility.** That is the consumables shelf's job, and
  keeping the two separate is what lets us say cosmetics are decoration only.

---

## 4. Constraints anything new has to respect

Learned from the current implementation; each of these has a test or a gate behind it.

1. **Exactly one free item.** `qa/functional/10-feedback-fixes.test.js` asserts that the free
   non-membership catalogue is exactly Steel, and that something costs exactly 100. New
   categories launch paid-only.
2. **Free does not mean owned.** Since `upgrade-2026-30`, ownership is a row in
   `gamer_cosmetic` and nothing else. A free item is *claimed* at a price of zero.
3. **Coin spends go through the optimistic lock.** `DefaultCosmeticService.buy` does
   read-check-write inside one transaction under `@Version` on `Gamer`. A new sink that skips it
   will charge once and grant twice on a double tap.
4. **Membership items are withdrawn on lapse.** `MembershipCosmeticsJob` runs hourly and strips
   anything worn but not owned. Anything granted with Gold inherits that behaviour.
5. **The art is generated, and it is licence-free.** Everything in `cosmetics/generate.py` is
   drawn from primitives — no sourced packs, no fonts, no logos, characters or sprites. Banners
   may evoke a *genre*; they may not borrow from a game. This is a legal rule before it is a
   taste one, because these are sold.
6. **Frames have a geometry contract.** `HOLE_R = FRAME_SIZE * 0.390` in the generator and
   `HOLE_RATIO = 0.78` in `FramedAvatar.tsx` are the same number twice. Nothing opaque goes
   inside it.
7. **Banners are cropped and partly covered.** The art is 1200×400 but renders in a box nearer
   4:1 with `contentFit="cover"`, so roughly the top and bottom sixth never appear — and the
   avatar sits over the bottom-left corner. Compose for the middle band and keep the important
   part away from that corner.

---

## 5. Suggested order

**2 → 1 → 3 → 4.** Limited-time drops first because they make everything already on the shelf
worth more; then animated banners, which are pure generator work; then username colour, the
first genuinely new *kind*; then bundles, which need several items to bundle.

Leave 6, 7 and 8 until there is data on which items actually sell. We have no such data today —
the shop has never been open with a full catalogue — so any further ranking past this point
would be a guess dressed up as a plan.

**Instrument first.** The one thing worth adding alongside the next drop is a count of
purchases per cosmetic. Everything in §2 becomes a much shorter argument once we know whether
the 1 500-coin animated frames sell at all.

---

## Sources

- Discord — Shop FAQ, support.discord.com/hc/en-us/articles/17162747936663
- Discord shop pricing 2026 — slaykeys.com/en/guides/discord-shop-prices; howtogeek.com
- Steam — Points Shop Items, partner.steamgames.com/doc/marketing/pointsshopitems; community
  pricing guides on steamcommunity.com
- Plink — plink.gg and the Google Play listing (tech.plink.PlinkApp)
- GameTree — gametree.me and the Google Play listing (com.gametreeapp)
- Yubo — App Store listing and product pages
- PlayStation Stars — pushsquare.com coverage of digital collectibles, Sept 2022
- Reddit — Collectible Avatars, support.reddithelp.com/hc/en-us/articles/6213835889044
- Dating-app paid features — eddie-hernandez.com, unstar.app paywall comparisons 2026
