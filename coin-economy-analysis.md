# GameBuddy — The coin economy, and why it was giving coins away

**Date:** 31 August 2026
**Companion to:** `monetization-analysis.md` (the money side) and `cosmetics-market-research.md`
(what to sell). This one is about the currency itself: where coins come from, where they go,
and what they should be worth.

**Why now.** The shop is the business. Coins are what the shop is priced in, so how easily
they arrive decides whether anybody ever buys a pack. Auditing the shipped rates found an
engaged free player earning **≈950 coins a week against a documented target of 250–400** —
and the gap had been invisible because every rate was a hardcoded constant and nothing on the
admin dashboard split coin movement by where it came from.

This document is the audit, the fix, and what is still open.

---

## 1. What was actually shipped

Every faucet lived in `CoinFaucet.java` as a `static final` constant. No property, no
environment variable, no admin control: retuning the economy meant a recompile, a backend
deploy, **and** an app release, because the client kept its own copies of the numbers.

| Faucet | Shipped | The spec it was written against | Per week |
|---|---|---|---|
| **Rewarded video** | **20 coins × 5/day** | 15 × 3/day | **700** |
| Daily streak | `{5,10,15,20,25,25,25}`, then **flat 25 forever** | escalating | 175 |
| Weekly quests | 3 × 25 (10 messages, 2 matches, join a lobby) | ~75 | 75 |
| Gold stipend | 600 / 30 days | 600/mo | ≈140 |
| Badges | 775 total, once | — | one-off |
| | | **Engaged free total** | **≈950** |

Three separate faults, in order of size.

**1. The advert faucet shipped at more than twice its spec.** `monetization-analysis.md` §3.4.1
specified 15 coins × 3/day. The code shipped 20 × 5. That single deviation is **+385 coins a
week** and by itself accounts for the target being missed.

**2. The streak stopped rewarding streaks after a week.** `dailyReward` clamped its index to
the last rung, so day 8, day 80 and day 800 all paid 25. A streak is supposed to make
consecutiveness worth something; after the first week it was an unconditional 25 coins for
opening the app, forever.

**3. "Daily" did not mean daily.** The cooldown was 24h but the grace was 48h. Claiming every
~47 hours kept an unbroken run alive on **about 3.5 claims a week** — half the days, the full
ladder.

### What that costs in real money

A rewarded impression clears roughly **$0.010–0.020** on Android (rewarded video eCPM runs
$10–20 broadly, $15–40 in tier-1 markets). Five views a day is **$0.05–0.10 of revenue**.

Those same five views paid out 100 coins. At the smallest pack — 500 coins for $1.99 — a coin
is worth **$0.004**, so 100 coins is **$0.40 of pack value**.

> **The app was giving away roughly four to eight times what the advert earned**, and a free
> user matched the smallest coin pack every five days. There is no version of that where
> somebody buys coins.

### The sinks, for scale

The catalogue is the only large sink and it is finite: **34 items, 18,450 coins** (and this
wave adds five more). Recurring sinks are small — Super Like 100, Extra Likes (+5) 200, Unlock
Admirer 150, Rewind 50, Lobby Boost 300. A player earning 950 a week who already owns what
they want has nothing to spend on, which is the other half of why the rate mattered.

---

## 2. What changed

| Lever | Was | Now | Why |
|---|---|---|---|
| Rewarded advert | 20 coins, cap 5/day | **15, cap 3/day** | Back to spec. Adverts stay the largest single faucet — they should, they are the activity we want — but at roughly break-even against pack value instead of a 4–8× giveaway. |
| Daily ladder | `{5,10,15,20,25,25,25}`, plateau | **`{5,10,15,20,25,30,35}`, cycling** | Day 8 starts the week again. The best days now exist only at the end of an unbroken run, and breaking one forfeits them. No schema change: it is a modulo where there was a clamp. |
| Streak grace | 48h | **36h** | Still absorbs twelve hours of drift past the 24h cooldown, so claiming later each day costs nothing. No longer lets a "daily" streak survive on half the days. |
| Weekly quests | 3 × 25 | unchanged | They pay for the three behaviours the product actually wants. Not a leak. |
| Gold stipend | 600 / 30d | unchanged | A paid perk, not free-player income. |

### Before and after

| | Before | After |
|---|---|---|
| Daily streak (max) | 175 | 140 |
| Weekly quests | 75 | 75 |
| **Passive subtotal** | **250** | **215** |
| Rewarded adverts (max) | 700 | 315 |
| **Ceiling** | **950** | **530** |
| Typical (claims + ~1 advert/day) | — | **320** |
| Adverts as a share of the ceiling | 74% | **59%** |

**320 a week is the number to hold on to.** It sits inside the 300–350 band, and it is only
reachable by turning up *and* watching an advert — the passive ceiling on its own is 215. The
full 530 requires claiming every day and watching all three adverts every day, which should
feel like effort rather than like the default. This is asserted in `CoinFaucetTest` so it
cannot drift again silently.

### And the rates are now deployable

They moved out of Java constants into `CoinEconomyProperties`, bound from
`gamebuddy.coins.*` in `application.yml` with `COINS_*` environment overrides. Retuning is now
a deploy, not a release. The defaults in that class are exactly what this build was tested
with, so an environment that sets nothing behaves as verified.

The client stopped keeping its own copies: `GET /coins/earn` now returns `dailyLadder` and
`adCoins`, and the streak strip and the advert row render from those. The old constants
survive only as a fallback for a response from an older build. That mattered — it was half the
reason the numbers were never retuned, because changing them meant an app release to stop the
UI lying about them.

---

## 3. What is now visible

The ledger has recorded a `reason` on every movement since `upgrade-2026-22`, but nothing
queried it: the admin dashboard reported two aggregate sums, so "coins earned" and "coins
spent" were the only questions it could answer. Neither can tell you *why*.

Analytics now group by reason — earned, spent and row count per `CoinReason` over the window.
That is the query that makes the leak above self-evident rather than something you have to go
looking for, and it is how the effect of this change should be judged in a month: the advert
share of issuance should fall from roughly three quarters toward a half.

---

## 4. Still open

- **Multi-accounting.** The advert cap is per account and nothing prevents a second account.
  The verification itself is sound — real AdMob server-side verification, ECDSA over the raw
  query string, and the transaction id is claimed before any coins move, so a replayed
  callback pays nothing. The cap is honest; the number of accounts is not bounded.
- **No rate limit on the earn endpoints.** `/coins/earn/**` and `/ads/reward` sit outside the
  rate-limit namespace that covers match, lobby and auth. Not currently exploitable — every
  claim is idempotent against its own state — but `/ads/reward` is `permitAll` and each bogus
  hit costs a signature verification. Recommend `coins.earn` at 30/min per account and
  `ads.reward` at 30/day (10× the cap), in the existing config block.
- **The advert user id is not bound to the session.** The id is signed by Google in transit,
  so it cannot be tampered with, but nothing ties it to who was logged in when the advert
  played. It is a gifting vector rather than a minting one, and it consumes the *victim's*
  cap. Noted at `RewardedAdService`.
- **Doc drift worth fixing.** `monetization-analysis.md` §1.3 still says 975 coins from 13
  badges and a 9,400-coin catalogue; the truth is 775 from 10 (three retired with Community)
  and 18,450 across 34 items. §3.4.2 says Extra Likes gives +10; it ships +5.
- **The sinks are still finite.** Retuning income buys time; it does not create somewhere for
  a long-term player's coins to go. That is what limited-time drops and the other items in
  `cosmetics-market-research.md` are for.

---

## Sources

- Rewarded video eCPM benchmarks 2026 — Business of Apps; MonetizeMore AdMob playbook;
  AdReact app ad-revenue benchmarks; Playwire AdMob eCPM benchmarks.
- Free-to-play faucet/sink design — Machinations.io on game-economy design; general
  soft-currency balance guidance.
- Internal: `CoinFaucet.java`, `Badge.java`, `Consumable.java`, `Product.java`,
  `AnalyticsRepository.java`, `monetization-analysis.md` §1.3 and §3.4.
