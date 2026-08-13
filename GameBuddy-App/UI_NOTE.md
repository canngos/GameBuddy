# GameBuddy — UI redesign handover

A redesign pass ("Neon Arcade") was applied across the app: dark-first, near-black canvas,
violet→cyan primary gradient, glow on interactive elements, real icons, real game art.
~90 files changed. `npm run check` (typecheck + contrast) passes.

This note is for whoever picks the work up. It is split into **what landed**, **what is
broken**, **what is unfinished**, and **traps** — the last section is the one that will
save you the most time.

---

## 1. What landed (verified on an Android 14 emulator unless noted)

### Foundation
- **`src/theme/tokens.js` is the single source of colour.** It used to be three
  hand-maintained copies (`global.css`, `colors.ts`, `tailwind.config.js`). Tailwind now
  requires this file and emits the CSS variables itself; `colors.ts` imports it for the JS
  side. It is `.js` on purpose — Tailwind's Node loader reads it, not Metro.
- **Palette is contrast-enforced.** `scripts/check-contrast.js` runs in `npm run check` and
  fails the build if a token or gradient stop drops below its threshold. Several original
  colours were deepened to pass (`danger`, `success`, `gold`, `online`, `accent` in light).
- **Two violet gradients, not one.** `primary` (violet→cyan) is for surfaces only; `action`
  is the button-safe ramp. White on `#00E5FF` measures 1.54:1 — a label on the cyan end is
  invisible. See the comment block in `src/theme/gradients.ts`.
- **New primitives:** `glow.ts`, `Gradient.tsx`, `Icon.tsx` (Lucide), `haptics.ts`,
  `EmptyState.tsx`, and from the §5 pass: `Burst.tsx`, `CountUp.tsx`, `toast.ts` /
  `ToastHost.tsx`, `sound.ts`, `feedback.ts`.
- **Chakra Petch** display face on `hero` / `display` / `title` / `numeral` / `overline`.
  Poppins unchanged elsewhere.
- **`app/(dev)/gallery.tsx`** — dev-only component gallery, outside the route guard, so it
  opens with no session and no backend. Reach it with
  `npx uri-scheme open gamebuddy://gallery --android`. It has an always-visible theme
  toggle, depth/glow strips, token swatches, the deck card, and every component state.

### Components
- `Button` — gradient primary, glow, haptic. **Was badly broken mid-session, now fixed —
  see Traps.**
- `TextField` — lit focus ring, error outranks focus.
- `Avatar` / `ProfileBanner` — per-user gradients derived from the *same* hash as
  `avatarColor`, so a person is one colour everywhere (`avatarGradient` in `src/avatars.ts`).
- `TabIcon` — rewritten on Lucide. Now takes `focused: boolean` instead of `color`, which
  is what removed the old `ColorValue` vs `string` problem: it derives its own colour from
  tokens rather than accepting the navigator's tint.
- **Every emoji is gone**, replaced with Lucide. `KeywordIcon` and `PlatformIcon` stay
  hand-drawn — they are the product's own vocabulary and are already on Lucide's spec
  (24×24, 2px, round caps). Do not "unify" them away.
- **`brand` swept onto role tokens** — 88 lines across 36 files. The pink now means
  like/match/admirer *only*. Coins/prices/Gold → `gold`; everything else interactive →
  `primary`. The wordmark on the welcome screen deliberately keeps `brand`.

### Screens
- **Deck card** — per-user gradient identity block with a fixed dark scrim (the scrim is
  load-bearing: the gradient hue comes from a hash, so contrast must not depend on it),
  **game cover art on the pills**, platform glyphs.
- **Swipe feedback** — MATCH/PASS stamps glow and scale to full size exactly at the commit
  threshold, with a haptic tick on crossing it and a heavier one on release.
- **Match overlay** — `hero` type, accent gradient wash, glowing avatar, celebrate haptic.
- **Floating tab bar** — detached, rounded, lifted, uses the `elevated` token.
- **Market** — split into **Earn | Shop** segments. The daily streak is now a **7-day strip**
  rather than one row in a list of five. That is not an invented metaphor: the backend's
  `CoinFaucet.DAILY_BY_STREAK` has been a literal 7-entry table `{5,10,15,20,25,25,25}` all
  along and the client simply never showed it.
- **Gold paywall** — gold hero with a lit crown, benefit rows with icons, plan rows with
  price + saving badge instead of the generic `SelectRow`.

### Fixes made along the way
- **`MatchOverlay`'s "Send a message" never navigated.** It only called `dismissMatch()`
  behind a stale comment claiming chat wasn't built. Chat has been built for a long time.
  Now pushes to the conversation. **Verified.**
- **Kotlin build failure** (pre-existing, `react-native-google-mobile-ads` ships Kotlin 2.3
  metadata against Expo's 2.1 compiler) — fixed durably with `plugins/withKotlinVersion.js`,
  which survives `expo prebuild`.
- **Game covers were missing** because `game_icon` was `NULL` for all 100 seeded games.
  `backfill_game_covers.py --apply` was run: **99/100 written** (only *eFootball 2026* has no
  cover on IGDB). It also canonicalised titles from IGDB, so some games renamed themselves.

---

## 2. Broken / unresolved

### 2.1 The Lottie match burst — **resolved by removal**
Lottie is gone. `lottie-react-native`, `assets/lottie/` and `scripts/make-match-burst.py`
were all deleted, and the burst is now `src/ui/Burst.tsx` — Reanimated particles, no native
module.

For the record, since the failure was never explained: `LottieView` mounted, the JSON parsed,
and nothing drew. No error in logcat. Rewriting the easing handles from scalars (`i.x: 0.2`)
to arrays (`i.x: [0.2,0.2,0.2]`) for the multi-dimensional tracks did not fix it. It was
reproducible in one tap from the dev gallery and still could not be diagnosed, which is what
made replacing it the cheaper option — Reanimated was already a dependency, is how everything
else in the app moves, and its particles are real views the inspector can see.

`Burst` takes `play` (a counter — incrementing replays, and mounting counts as a play),
`color`, `radius`, `particles`, `duration`. One shared value drives every particle; each
particle is its own component so the hook count does not depend on the prop. Two callers:
the match overlay (accent, on mount) and the Market coin balance (gold, when coins arrive).

### 2.2 The match push → in-app celebration path is built but **not verified**
Requirements implemented this session:
- `src/match/celebration.ts` — app-wide store, deduped by `userId`.
- `src/match/MatchCelebration.tsx` — mounted once in `app/(main)/_layout.tsx`, above the tabs.
- `src/notifications/useMatchNotifications.ts` — a foreground `MATCH` push fetches the other
  gamer's profile and raises the celebration.
- `usePushRegistration.ts` — suppresses the *banner* for `MATCH` only when foregrounded, so
  the news is not delivered twice. It still lands in the notification list.

**Why this matters:** the backend has always called `notifyMatched` for **both** parties, but
the client only ever celebrated the swiper. The person who liked first got a grey system
banner while the other got fireworks.

**Not verified** because the local Postgres and Spring backend were both stopped before this
could be exercised end-to-end. The deck→overlay path *was* verified. The push→overlay path
was not. Test it with two accounts, or by sending a local notification with
`data: { kind: 'MATCH', targetId: '<userId>' }`.

**Offline push needs no work** — it already worked before this session.

### 2.3 The dev database was mutated for testing
To force a mutual match I edited `gamebuddy.approved_matches` on the local dev DB:
- inserted ~1200 rows making every registered gamer accept the test account
  (`cbaturlar@gmail.com`),
- deleted that account's own accepts,
- zeroed `accepts_used` / `swipes_used`.

**Reseed the local DB before trusting the deck or match counts.** Nothing on any shared
environment was touched.

---

## 3. Unfinished (deliberately, not forgotten)

| Item | Notes |
|---|---|
| **`Text` metrics** | Only family and colour changed. Every variant still hardcodes `text-[Npx] leading-[Npx]`, and `Text` has ~68 importers — changing leading reflows every list row and every `numberOfLines` truncation point. Needs its own commit and a screen-by-screen pass. |
| **Navigation / IA** | Nothing beyond the floating tab bar. |
| **Settings has no back affordance at the top** | Its "Back" is a ghost button at the bottom of a long scroll; the tab bar is hidden on that screen. Android system back works. Worth a `BackHeader`. |
| **`/gold` is not deep-linkable** | It is an `href: null` tab, so `gamebuddy://gold` lands on Profile. Only matters if you want a push to open the paywall. |
| **Haptics unverified** | An emulator cannot produce them. Needs a physical device. |
| **`expo-haptics` added `VIBRATE`** to the merged manifest — a review-visible change on an existing Play listing. Decide deliberately before shipping. |
| **Web export fails** | Pre-existing and unrelated: `market.tsx → EarnCoins → rewarded → react-native-google-mobile-ads` pulls a native-only module into the web graph. |
| **Light-mode gold reads as bronze** | `#8A5C00` is what it takes to clear 4.5:1 on white. If you want it brighter, stop using gold for small *text* in light mode and reserve it for fills — do not lighten the token. |
| **Seen once, not reproducible** | The keyword picker rendered in light while the app was dark. Could not reproduce afterwards; every later theme flip repainted correctly. This repo has three post-mortems about exactly this class of bug, so if you see it again it is a real lead. |

---

## 4. Traps — read before touching the UI

These cost real time this session. Each one fails **silently**.

1. **Never put an `active:` / `hover:` / `focus:` class on a plain `View` nested inside a
   `Pressable`.** NativeWind gives that element its own press handling, so the inner View
   becomes the touch target and swallows the gesture — `onPress` never fires at all. It does
   not degrade; it goes completely dead while still looking and measuring like a working
   button. This shipped for a few hours and killed **every Cancel and Back in the app**
   (`ghost`, `secondary`, `danger`). `primary` was spared only because it has no `active:`
   class, which made it look like a navigation bug. See the comment in `src/ui/Button.tsx`.

2. **Class *keys* must not appear and disappear between renders.** Conditional *values*
   within a stable key set are fine (`selected ? 'border-gold' : 'border-line'`); swapping
   `'hidden'` for `'gap-3 pb-8'` is not. NativeWind 4.2.6 stops painting the subtree. Mount
   conditionally instead. Same rule for style objects — that is why `glow()` and `lift()`
   have a `'none'` level that still emits its keys.

3. **`className` never goes on an `Animated.View`.** Reanimated creates its components
   lazily. `src/ui/Gradient.tsx` looks like it breaks this rule and does not — `LinearGradient`
   is a stable module-scope component; the distinction is lazy vs eager creation.

4. **Android `boxShadow` follows the node's box.** A glow on a square wrapper is a square
   halo — this is what the active tab looked like on first run. Give the node a
   `borderRadius`. Also: `overflow: hidden` clips a node's *own* shadow, so anything that
   both clips a gradient and carries a glow needs two nodes (outer = depth, inner = clip).

5. **React Navigation overrides `left`/`right`/`bottom` on an absolute `tabBarStyle`.** Use
   `marginHorizontal` / `marginBottom` instead. And an absolute tab bar leaves the layout
   flow, so every screen silently loses ~74px — that is paid for centrally via
   `sceneStyle.paddingBottom` in `app/(main)/_layout.tsx`, derived from the same constant as
   the bar's offset so the two cannot drift.

6. **An animated `transform` replaces a static one, it does not merge.** Declaring a tilt in
   `StyleSheet.create` and a scale in `useAnimatedStyle` silently drops the tilt.

7. **NativeWind `shadow-*` classes are forbidden** (crashed Android 8). Depth goes through
   `lift()` / `glow()`.

8. **Metro's file watcher intermittently missed edits** on this machine. If behaviour does
   not match the source, reload from the dev menu before debugging anything else — a good
   half hour went into "diagnosing" stale code.

9. **A colour class that does not exist fails silently, and only in one theme.** The filter
   sheet spent its whole life with `text-ink` on the game and toggle labels. There is no
   bare `ink` colour — `tailwind.config.js` defines `ink-500/700/900` and nothing else — so
   Tailwind emitted nothing, NativeWind dropped the class, and the labels fell through to
   React Native's default text colour, which is **black**. On a light chip that reads as a
   deliberate near-black; on `raised` in dark mode it is black on near-black and the text
   effectively disappears. Nothing errors, nothing warns, and it looks fine in whichever
   theme you happened to build in.

   Two consequences. First, always take colour from `tokens.js` role names (`content`,
   `muted`, `primary`…) rather than from the `ink-*` ramp, which exists for fixed dark
   scrims and not for text. Second, when something is unreadable in exactly one theme,
   suspect a class that resolves to nothing before suspecting the palette — the token is
   usually fine and the class name is usually the lie.

   Worth a guard: a scan comparing every `text-`/`bg-`/`border-` class against
   `Object.keys(tokens.semantic)` would have caught this in `npm run check`. A one-off scan
   found `text-ink` was the only instance in the app, so the codebase is otherwise clean.

---

## 5. Product feedback — **built, needs verifying on a device**

Three items raised after the redesign pass. All three are now implemented.

**Status: the primitives are verified on the API-34 emulator via the dev gallery. The
screens that need a backend are not.**

Seen working, in both themes, on `GameBuddy_API34`:

| | |
|---|---|
| `Burst` | Both colours. Staggered particles, mixed circles/squares, shockwave ring. Replaces the Lottie that never drew. |
| `ToastHost` | Single toast, and **two at once** — the second waits for the first rather than replacing it. Correct icon and tone per kind. Draws over the pinned header. |
| `CountUp` | Caught mid-count at 1456 between 1440 and 1690. Settles on the exact value. |
| Sound | `com.gamebuddy.app` registers a media session and reports playback position in logcat. **Heard: no** — nobody listened to the emulator's audio, only confirmed it played. |
| Theme flip | Toast, counter and rows all repaint light↔dark with no blanking — the failure mode the three post-mortems in `hairline.ts`/`elevation.ts` are about. |

**Not verified, and each needs something the emulator or this machine does not have:**
- **Haptics** — an emulator cannot produce them. Physical device. (Unchanged from §3.)
- **Every coin path in §5.1** — buying a frame, a consumable, a claim, a boost. All need the
  Spring backend and Postgres running, and §2.3's DB mutation means the counts are untrustworthy
  until a reseed anyway.
- **Push → toast**, and the §2.2 push → celebration path. Needs FCM and a second account.
- **iOS silent switch** and `mixWithOthers`. Needs an iPhone.
- **RevenueCat entitlement success.** Not configured for this project at all.

One thing worth knowing before you debug a "bug" here: the toast's entry spring is fast
enough that a single `adb screencap` after tapping catches it **mid-flight**, half
transparent and still travelling, overlapping the status bar. That is the animation, not a
layout error. Capture a second frame before concluding anything.

The dev gallery is the fastest way to check all of it — it needs no session and no backend,
and now carries **BURST**, **TOAST**, **COUNT UP** and **SOUND AND HAPTICS** sections. It
mounts its own `ToastHost` because it lives outside the `(main)` layout that hosts the real
one.

### 5.1 A sense of purchase after a transaction — done, for **coins**

Scoped to spending and earning coins, not real money: RevenueCat is not configured yet, so
nothing reaches the IAP path in practice.

Every coin event now gets the same three-part acknowledgement — `feedback.*` (haptic + cue),
a toast, and the balance moving:

| Event | Where | What fires |
|---|---|---|
| Cosmetic bought | `app/(main)/market.tsx` | `feedback.purchase()` + toast naming the item |
| Consumable bought | `src/market/ConsumableShelf.tsx` | `feedback.purchase()` + toast |
| Coins claimed / advert watched | `src/market/EarnCoins.tsx` | `feedback.reward()` + `+N coins` toast |
| Boost started | `src/match/BoostButton.tsx` | `feedback.commit()` only — the countdown is already the feedback |

`buy`/`equip`/`unequip` in the Market used to share one `applyStore` handler, which is why
success was indistinguishable between them. The cache write is still shared; the
acknowledgement is not. Equip and unequip stay silent on purpose — they are adjustments, not
transactions.

`src/market/CoinBalance.tsx` replaces the hardcoded header figure. It counts to its new value
(`src/ui/CountUp.tsx`, deliberately *not* Reanimated — a digit is text, not a style) and
bursts gold **only when coins go up**. Sparks for money leaving would congratulate somebody
for spending; the celebration for a purchase belongs on the item, which is what the toast is.

`usePurchase` now exposes `granted` — it was computed and thrown away — and fires the same
`feedback.purchase()` + toast, on the **entitlement arriving**, not on the store sheet
closing. That path is wired but untested; exercise it when RevenueCat is live.

### 5.2 In-app toasts instead of OS banners — done

- `src/ui/toast.ts` — zustand store with a queue (max 4 waiting, head never dropped), deduped
  on a caller-supplied id. **In `src/ui/`, not `src/notifications/`**, because the Market
  raises toasts too and `src/market/` importing from `src/notifications/` would be a lie
  about what depends on what.
- `src/ui/ToastHost.tsx` — mounted in `app/(main)/_layout.tsx` **between** `TutorialOverlay`
  and `MatchCelebration`: above the app, below a match. Spring in from the top, 4.2s, swipe
  up or tap to dismiss. Three exit paths race, so a `leaving` ref guards `onDone` against
  running twice — without it, tapping just before the timer dismisses the *next* toast too.
- `src/notifications/useInAppNotifications.ts` — the sibling of `useMatchNotifications`.
  Every known kind except `MATCH` becomes a toast, reusing `routeFor` (now exported) so a tap
  lands exactly where the notification would have.
- `usePushRegistration`'s handler now suppresses the system banner for **every kind this build
  knows**, and — the important half — still shows it for kinds it does not. An installed app
  will meet kinds added after it shipped and has no in-app treatment for them; suppressing
  those would swallow them entirely. The kind list is exported data in
  `useNotificationRouting.ts` (`NOTIFICATION_KINDS`, `isKnownKind`) so the router, the toast
  and the handler cannot drift apart.

Match stays the full-screen celebration.

### 5.3 Sound — done

- `scripts/make-sounds.py` generates four cues into `assets/sounds/` (`match`, `purchase`,
  `reward`, `message`). Stdlib `wave` only. **They are placeholders** — pentatonic sine
  blips, quiet, all under 800ms. Replace the files with designed audio and no call site
  changes.
- `src/ui/sound.ts` — named intents mirroring `haptics.ts`. `playsInSilentMode: false` (the
  silent switch wins) and `interruptionMode: 'mixWithOthers'` (never duck someone's music).
  Players are created once and reused.
- `src/ui/feedback.ts` — **prefer this at call sites.** It fires the haptic and the cue
  together so they cannot drift. `Button` keeps calling `haptics.tapLight()` directly: a
  click on every button is the fastest way to get sound turned off entirely.
- Preference is **device-local** (zustand + `secureStorage`, key `gamebuddy.soundEnabled`),
  not a fifth field on `NotificationPreferences` — that endpoint writes all four values on
  every save, so adding to it is a backend change. Toggle is in its own group at the bottom
  of `app/(main)/settings/notifications.tsx`. Unlike the theme, launch does **not** block on
  hydrating it.
- The `expo-audio` config plugin entry in `app.json` turns off `recordAudioAndroid`,
  `microphonePermission` and both background modes. At their defaults it would have added
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, a foreground `AudioControlsService`, an iOS `audio`
  background mode and a microphone usage string — review-visible changes on an existing Play
  listing, for four sub-second blips that need none of them.
  **Checked against the merged manifest after a real build:** the only permission this
  module adds is `MODIFY_AUDIO_SETTINGS`, from expo-audio's own library manifest — a normal
  permission, not a runtime one, and not configurable away. `RECORD_AUDIO` never appears:
  `expo-image-picker`'s existing `microphonePermission: false` emits a `tools:node="remove"`
  that strips it. **`VIBRATE` is still there** and is still the open question from §3.

---

## 6. Useful commands

```bash
npm run check                      # typecheck + contrast gate
npx expo start --dev-client        # Metro
python scripts/make-sounds.py      # regenerate the sound cues
npx uri-scheme open gamebuddy://gallery --android   # dev component gallery
npx expo prebuild                  # withKotlinVersion.js survives this by design
cd android && ./gradlew assembleDebug -PreactNativeArchitectures=x86_64
```

Native rebuild is required after installing any native module — `expo-linear-gradient` and
`expo-haptics` both needed one, and **`expo-audio` needs one now**. The same rebuild also
clears out the removed `lottie-react-native`.
