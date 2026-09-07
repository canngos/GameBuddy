# QA findings — GameBuddy

Black-box testing against the local stack (`docker compose up`), 2026-08-13/14. Findings
1–5 are backend defects, each reproduced and traced to a line; the reproductions are in
`qa/functional/` and run with `node qa/run-functional.js`. Finding 6 came out of the
Android UI suite (`qa/maestro/`) and is the one item here that is **not** traced to a line
of GameBuddy's own code.

## Status — all six addressed, 2026-08-14

Both tests that were marked `todo` are now ordinary passing tests, so the suite would fail
if any of this regressed. `node qa/run-functional.js` → **104 pass, 0 fail, 0 todo**.

| # | Severity | Summary | Status |
| - | -------- | ------- | ------ |
| [1](#1) | **High** | Gold's advanced filters fail once the population passes 10,000 | **Fixed** — the filter is sent as an eligible set, not its complement |
| [2](#2) | **Medium** | Oversized post/community text returns HTTP 500 | **Fixed** — `@Size` on both DTOs, and a sweep of the rest |
| [3](#3) | **Medium** | The documented way to delete the seeded bots does not work | **Fixed** — `db/delete-seed.sql`, run end to end on a clone |
| [4](#4) | Low | `/admin/**` denials return 401, which signs the user out | **Fixed** — an `accessDeniedHandler` returns 403 |
| [5](#5) | Info | `subscription_tier` stays `GOLD` after expiry | **Fixed** — cleared on expiry, and the column now carries a warning |
| [6](#6) | Low — needs confirming | Intermittent native crash on launch, inside React Native's renderer | **Instrumented** — Crashlytics, EAS builds only (a Windows path limit blocks it locally); release-build check still owed |
| [7](#7) | **Medium** | The `preview` EAS profile cannot reach any backend | Found 2026-08-14 while running the release gate |
| [8](#8) | **High** | A release build with the test RevenueCat key **closes itself**; the production profile has no key at all | Found 2026-08-14 while running the release gate |

Three things found while fixing these are worth carrying forward, because each contradicts
something the report below says:

- **Cascading foreign keys would not have simplified account deletion** (finding 3's
  preferred option). `DefaultAuthService.deleteAccount` anonymises the row and never issues
  a `DELETE`, so cascades would have served only the seed purge.
- **The seed owns 570 communities and 308 posts.** Nobody expected that, and it is exactly
  what a blanket cascade would have destroyed. The script transfers or removes them by the
  same rule the product uses when an owner leaves.
- **Nothing in the codebase was over-counting subscribers** (finding 5). All three readers
  of that column already pair it with the expiry. The risk was always the *next* ad-hoc
  query, which is why the durable fix is a comment on the column rather than a data change.

---

<a id="1"></a>
## 1. Gold's advanced filters break above 10,000 gamers — **High**

**The paid feature does not work in this database, and will not work in production.**

### What happens

Three of the four advanced filters return HTTP 503 with
*"Recommendation service unavailable"* for a subscriber who has paid for them:

```
?country=Finland   -> 503  code 123
?onlineNow=true    -> 503  code 123
?gameId=<any>      -> 503  code 123
?platform=PC       -> 200        (works, for now — see below)
```

### Why

A narrowing filter is implemented by sending the model every gamer the filter rules *out*,
as an exclusion list — `DefaultMatchService.java:150-156`:

```java
if (filters.narrowing()) {
    decided.addAll(gamerRepository.findIdsExcludedByFilters(...));
}
```

The model caps that list at 10,000 — `GameBuddy-ModelApi/api/main.py:70`:

```python
MAX_EXCLUSIONS = 10_000
```

and Pydantic rejects anything larger with a 422:

```
422 Unprocessable Content: {"detail":[{"type":"too_long","loc":["body","exclude"],
 "msg":"List should have at most 10000 items after validation, not 20001", ...
```

`predict()` catches every `RuntimeException` and rethrows it as
`RECOMMENDER_SERVICE_ERROR` (`DefaultMatchService.java:913-917`), so the user is told the
recommender is down. It is not — the backend sent it a request it had already declared too
large.

### Why this is the worst kind of bug

It is a **scaling bug that looks like a finished feature**. Below ~10,000 gamers every
filter works and every test passes. This database holds 20,001, so it is broken here right
now. `platform=PC` still works only because enough gamers share a platform to keep the
exclusion list under the cap — it fails the same way as the population grows, and it will
fail *after* launch rather than before.

Also note the failure is a paid one: `getRecommendations` charges a 402 to non-subscribers
before doing this work, so the only people who can reach the bug are the ones paying for it.

### Reproduce

```bash
node --test qa/functional/07-billing.test.js
#   ✖ Gold unlocks the advanced filters   # MAX_EXCLUSIONS
```

### Suggested fix

Invert the query. Sending "everyone who does not match" scales with the population;
sending "who does match" scales with the filter. Options, roughly in order of preference:

1. **Pass the filter to the model** as an include-list or as filter criteria, and let it
   rank within that set. Best fix, largest change.
2. **Send an include-list** (`findIdsMatchingFilters`) and have `/predict` rank only inside
   it. Same cap problem in principle, but a filtered set is small by definition.
3. **Cap and degrade**: if the exclusion list would exceed `MAX_EXCLUSIONS`, fall back to
   ranking unfiltered and applying `filters.matches()` to the result. Cheapest fix. It
   reintroduces the "filter can only remove from the top N" problem the current design was
   written to avoid (`DefaultMatchService.java:145-149`) — but a narrower deck beats a 503.

Whatever you pick, **do not** simply raise `MAX_EXCLUSIONS`. It moves the cliff rather than
removing it, and the comment on that constant is right about why the bound exists.

Finally: `predict()` swallowing a 422 into "service unavailable" hides the cause. A 4xx
from the model means *we* built a bad request and should be logged as such, separately from
the model actually being down.

### Fixed, 2026-08-14 — option 2, the include-list

`PredictRequest` and `ColdStartRequest` gained an `include` field; the model masks its
candidate pool with it before the cut (`Recommender._candidates`). `findIdsExcludedByFilters`
became `findIdsMatchingFilters`, the positive mirror.

The reason this direction is right, rather than merely different, is **when each one runs
out of room**. A complement is longest where the filter is most narrowing — exactly where
there is no fallback, because post-filtering the top N is the failure that made this design
necessary. An eligible set is longest where the filter narrows almost nothing, and there
dropping it and post-filtering costs nearly nothing. So above `MAX_ELIGIBLE = 50_000` the
backend logs a warning, sends no include list, and lets the existing `filtered()` narrow the
answer — a defined degradation instead of a 503.

Three things that were not obvious going in:

- **Exploration was filter-correct by accident.** It got that for free because `decided`
  contained every gamer the filter ruled out, so the random-sample query could not return
  one. Inverting removed that, and without a fix the exploration slots would come back empty
  under exactly the narrow filters worth having. The filter is now part of that query too.
- **Null and empty had to stay different.** Absent means "not filtering"; empty means "the
  filter matched nobody". Collapsing them answers a filter that matches nobody with a full
  unfiltered deck, which is worse than the 503 — it looks like the filter was ignored.
- **An empty eligible set short-circuits the model entirely.** An empty result normally
  means "the artefact has not met this gamer" and triggers the cold-start fallback; under a
  filter matching nobody it means something else with the same shape, and both round trips
  would be spent learning nothing.

`predict()` now catches `HttpClientErrorException` separately and logs the model's response
body at `error` — a 4xx is our bug, and conflating it with the model being down is what made
this read as an outage.

Measured after the fix, against the same 20,038-gamer database:

```
?country=Finland   -> 200   1 card,  all in Finland      (27 eligible)
?onlineNow=true    -> 200  50 cards, all active <15 min  (57 eligible)
?gameId=<any>      -> 200  50 cards, all play that game  (1,127 eligible)
?platform=PC       -> 200  50 cards, all on PC           (13,216 eligible)
```

`onlineNow` is the one to look at: the report measured 0 of 37 under the old post-filter
approach and a 503 today. The degraded path was proved separately by rebuilding with
`MAX_ELIGIBLE = 100`, which logged the warning, returned 200, and still gave a deck that was
entirely PC.

---

<a id="2"></a>
## 2. Oversized text returns HTTP 500 — **Medium**

Any authenticated user can produce server errors on demand:

| Request | Field | Column | Result |
| ------- | ----- | ------ | ------ |
| `POST /community/create/community` | `name` | `varchar(255)` | **500** |
| `POST /community/create/community` | `description` | `varchar(2000)` | **500** |
| `POST /community/create/post` | `title` | `varchar(255)` | **500** |
| `POST /community/create/post` | `body` | `varchar(4000)` | **500** |

From the log:

```
org.springframework.dao.DataIntegrityViolationException: could not execute statement
  [ERROR: value too long for type character varying(2000)]
POST /community/create/community -> 500 (96 ms)
```

`CreateCommunityRequest` and `PostRequest` declare `@NotBlank` but no `@Size`, so the
value passes validation, reaches Postgres, and the constraint violation escapes as a 500.

This is not a design decision — the sibling DTOs get it right, and they are the template:

- `CreateCommentRequest.java:16` — `@Size(max = 2000, message = "Comment cannot exceed 2000 characters")` → clean 400
- `UsernameRequest`, and the FCM token request → clean 400

### Fix

Add `@Size` matching the column on each field:

```java
// CreateCommunityRequest
@Size(max = 255,  message = "Community name cannot exceed 255 characters")   private String name;
@Size(max = 2000, message = "Description cannot exceed 2000 characters")     private String description;
@Size(max = 255)  private String avatar;
@Size(max = 255)  private String wallpaper;

// PostRequest
@Size(max = 255,  message = "Title cannot exceed 255 characters")            private String title;
@Size(max = 4000, message = "Post cannot exceed 4000 characters")            private String body;
@Size(max = 255)  private String picture;
```

Worth a sweep while you are there — these request DTOs also have unbounded `String` fields,
though not all of them reach a bounded column: `ChangeAvatarRequest`, `DeleteAccountRequest`,
`GameRequest`, `KeywordRequest`, `LoginRequest`, `SendCodeRequest`, `VerifyRequest`,
`CommunityRequest`, `GamerRequest`, `TypingRequest`, `FriendRequest`,
`SendNotificationTokenRequest`, `SendNotificationTopicRequest`.

### Reproduce

```bash
node --test qa/functional/08-security.test.js
#   ✔ oversized text is refused with a 4xx, not a 500
```

### Fixed, 2026-08-14

`@Size` matching the column on both DTOs, and the sweep done: identifiers bounded at 255,
passwords at `PasswordPolicy.MAX_LENGTH`, usernames at `UsernamePolicy.MAX_LENGTH`, and the
`List<String>` fields bounded **twice** — once on the list, once on the elements. An
unbounded list is an unbounded `IN (...)` clause and an unbounded element is a megabyte
inside one; neither could happen through the app, which is why neither was enforced.

One live instance of the same defect turned up during the sweep and is fixed with it:
**`FcmTokenRequest` accepted 512 characters into a `varchar(255)` column**. Firebase tokens
usually run around 160, so it had not fired yet — but they are not specified to be, they
have grown before, and a device that got a long one would have lost push notifications
permanently behind a 500 nobody would have connected to it. The column is widened to 512 in
`upgrade-2026-26-column-bounds.sql`.

The notification DTOs the report lists (`SendNotificationTokenRequest`,
`SendNotificationTopicRequest`) were deliberately left alone: they are constructed
server-side and never bound from HTTP, so `@Size` on them would never run.

---

<a id="3"></a>
## 3. The documented seed-deletion statement fails — **Medium**

`README.md:271` and `docker-compose.yml:143` both give this as the way to remove the
synthetic accounts before real users arrive:

```sql
DELETE FROM gamebuddy.gamer WHERE email LIKE '%@bot.gamebuddy.invalid';
```

It does not work:

```
ERROR:  update or delete on table "gamer" violates foreign key constraint
        "fk24su9eu5fhblvx77oiwxcfcog" on table "gamer_keywords_join"
```

Fifteen of the sixteen foreign keys referencing `gamer` are `NO ACTION`; only
`gamer_platform` cascades:

```
approved_matches, blocked_friends, comment_likes_join, community,
community_members_join, friends, gamer_badge, gamer_cosmetic,
gamer_games_join, gamer_keywords_join, post_likes_join, waiting_friends   -> NO ACTION
gamer_platform                                                            -> CASCADE
```

### Why it matters

This is the cleanup step that removes fake profiles a real person could swipe on and
message — which the README itself calls "the thing matching apps get investigated for". It
is meant to run alongside flipping `RETRAIN_INCLUDE_BOTS` to false. Someone will run it at
deploy time, watch it fail, and either skip it or start improvising `DELETE`s against
production under time pressure.

Note the join tables are also inconsistent about their key name — `gamer_games_join` and
`gamer_keywords_join` use `gamer_id`, everything else uses `user_id` — which makes hand-
writing the cleanup easy to get wrong.

### Fix

Either:

1. **Add `ON DELETE CASCADE`** to the child foreign keys in a migration, so the documented
   one-liner is true. Cleanest, and makes account deletion simpler everywhere.
2. **Ship the working statement.** A correct, ordered version already exists in
   `qa/functional/helpers/db.js` (`deleteGamers`) and in `qa/reset-fixtures.js` — lift it
   into `README.md` or a `db/delete-seed.sql`.

Option 1 is better. Check `DELETE /auth/account` first — it handles the same fan-out in
Java today, and cascades would let it shrink.

### Fixed, 2026-08-14 — option 2, and the reasoning above is wrong

**Cascades would not let `DELETE /auth/account` shrink.** That endpoint anonymises the row
and never deletes it (`DefaultAuthService.deleteAccount`), deliberately, so that the other
half of a conversation and other people's posts survive. Cascades would have served only the
seed purge — and would have been actively dangerous there, because:

**The seed owns 570 communities, 308 posts and 50 comments.** Nobody expected that, and it
is precisely what a blanket cascade from `gamer` would have destroyed. Today none of those
communities has a real member and no real user has posted in one, so a naive delete would
have looked fine — and would have stopped looking fine on the day the script matters, which
is the day real users are already there.

`db/delete-seed.sql` therefore does what the product does when an owner leaves
(`DefaultCommunityService`): a community with a surviving member passes to the
longest-standing one, a community with none is removed with its content, and posts or
comments written by a real person are never touched. Posts the *seed* wrote in a surviving
community do go — a fake account's post is still a fake account talking to real people, and
leaving it would outlive the profile behind it.

Run end to end against a clone of the real database, and both branches proved:

```
before:  20,002 gamers   570 communities   308 posts
after:        2 gamers     0 communities     0 posts     (81s, no FK errors)

with one real member added to a bot-owned community:
after:        2 gamers     1 community       1 post
             "QA Guild" survived, owner transferred to the real account,
             their post intact
```

`README.md` and `docker-compose.yml` now point at the script.

---

<a id="4"></a>
## 4. `/admin/**` denials return 401, which signs the user out — Low

An authenticated non-admin calling an admin route gets **401 with an empty body**, not 403:

```
POST /admin/ban/user/{id}   as an ordinary USER  ->  401, empty body
```

Authorisation itself holds — nothing is banned, and the two independent checks both work.
The status code is the problem.

`SecurityConfig.java:114` registers an `authenticationEntryPoint` and **no
`accessDeniedHandler`**:

```java
.exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
```

so the `AccessDeniedException` raised by the chain's `/admin/** → hasRole("ADMIN")` rule is
answered by the entry point.

### Consequence

`GameBuddy-App/src/api/client.ts:100-111` maps an empty 401 to `onSessionExpired()`. So an
ordinary gamer who reaches an admin route is **signed out of the app** rather than shown a
refusal.

The two admin surfaces disagree, which is how this was found: `/community/admin/reports`
correctly returns 403, because it sits under `/community/**` and its `@PreAuthorize` denial
never reaches the entry point.

### Fix

```java
.exceptionHandling(ex -> ex
        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
        .accessDeniedHandler((req, res, e) -> res.setStatus(HttpStatus.FORBIDDEN.value())))
```

### Fixed, 2026-08-14

Applied as written. `qa/functional/06-moderation.test.js` asserted the defect
(`assert.equal(res.status, 401, 'known defect: …')`) and now asserts the contract.

The app was given the other half: an empty 403 used to fall through to "The server returned
an empty response." It now reads "You do not have access to this." `ApiError.isSessionExpired`
already keyed strictly on 401, so a 403 was never going to sign anyone out once the backend
stopped sending one.

---

<a id="5"></a>
## 5. `subscription_tier` is stale after expiry — Info

Not a bug — a trap for whoever queries the database directly.

Expiry works by moving `subscription_expires_at` into the past
(`PurchaseService.expire()`), and the effective tier is derived from it. The
`gamer.subscription_tier` column is **not** updated:

```
after EXPIRATION:  API /billing/subscription -> BASIC     (correct)
                   accept-allowance.unlimited -> false    (correct)
                   gamer.subscription_tier    -> 'GOLD'   (stale)
```

Anything counting paying users straight from that column — an analytics query, a support
tool, a dashboard — will over-count Gold subscribers, permanently and silently.

Either clear the column in `expire()`, or add a comment on it saying it is not the source
of truth and pointing at `subscription_expires_at`.

### Fixed, 2026-08-14 — both, because neither alone is enough

**No query in the codebase was over-counting.** `AnalyticsRepository:57`,
`GamerCosmeticRepository:63` and `:81` all already pair the column with the expiry. The
claim above overstated it: the risk was never the code as it stands, it was the next person
writing an ad-hoc count against the obvious-looking column.

`PurchaseService.expire()` now clears the tier. But that only covers the webhook path, and
it is worth being clear that **it cannot cover the general case**: a subscription whose
expiry simply passes, with no event to react to, is never written to again by anybody, and
will read `GOLD` forever. The trap is structural, so the durable half is a
`COMMENT ON COLUMN` in `upgrade-2026-26-column-bounds.sql` — which is what `\d+ gamer` shows
to the person about to write that query.

Verified live through the RevenueCat webhook:

```
                  tier   | still active
  before          BASIC  | -
  after purchase  GOLD   | true
  after expiry    BASIC  | false      (was: GOLD | false)
```

---

<a id="6"></a>
## 6. Intermittent native crash on launch — Low, and **not confirmed against a release build**

The app died to the launcher during a UI-test run, taking the whole process with it. It is
a native segfault, not a JavaScript error, so there is no red box and nothing in the Metro
log — the only trace is in logcat.

```
F/libc  : Fatal signal 11 (SIGSEGV), code 2 (SEGV_ACCERR) in tid 21241 (mqt_v_js)
          Process uptime: 11s      Cmdline: com.gamebuddy.app

#01  facebook::react::MountingCoordinator::pullTransaction(bool) const
#02  facebook::react::FabricUIManagerBinding::schedulerDidFinishTransaction(...)
#03  facebook::react::Scheduler::uiManagerDidFinishTransaction(...)
#05  facebook::react::ShadowTree::mount(ShadowTreeRevision, bool) const
#06  facebook::react::ShadowTree::tryCommit(...)
```

`mqt_v_js` is the JS thread and every frame is inside `libreactnative.so` — this is the
Fabric renderer committing a shadow-tree transaction, not application code. Eleven seconds
into launch, on the welcome screen, before anyone had signed in.

### What is and is not known

- **Frequency:** once, in roughly twenty launches during one session. A second run of the
  same flow died at the same step and looked like a recurrence — it was not. That one was
  the test harness pressing Back with no keyboard open, which popped the onboarding stack
  and closed the activity cleanly (`Transition type = CLOSE`, no crash report). Worth saying
  because the two are indistinguishable from a screenshot, and only one of them is a bug.
- **Not reproducible on demand.** There is no set of steps to give you. It happened during
  `launchApp: clearState: true`, which is the harness's normal first step.
- **Debug build, x86_64 emulator, Fabric enabled.** All three are things a real device does
  not have. Debug builds run the bundle from Metro and keep the dev-support machinery
  attached, and native renderer crashes are more likely there than in a release build.

### Why it is still worth writing down

A segfault in the renderer is invisible to any error reporting the app installs in
JavaScript — Sentry-style JS handlers never run, because the process is already gone. If
this happens to a user it looks like the app "just closes", and nothing anywhere records
why.

### Suggested handling

1. Do not chase it yet. One occurrence in a debug build is not enough to act on.
2. **Before release, check it does not survive into a release build**, which is the only
   configuration that matters: `eas build --profile preview` and run the same flows.
3. If it recurs, it belongs upstream (React Native / Fabric) with this backtrace, not in
   GameBuddy's issue list. Worth noting the RN version in the report — this build is
   RN 0.86 / Expo SDK 57.
4. A native crash reporter — Crashlytics, or `expo-error-recovery` — is what would turn
   "the app just closed" into something you can count. There is none in this build.

The full report is preserved at
`~/.maestro/tests/2026-08-14_104611/01 onboarding/logs/crash-report.txt` (137 lines,
119 frames) alongside `device-logcat.txt`.

### 2026-08-14 — added, and enabled for EAS builds only

Not chased, per step 1. Crash reporting is in, with one constraint worth understanding
before touching it: **it cannot be compiled at this repo's location on Windows.**

```
ninja: error: Stat(C:/Users/canba/Desktop/workspace/gamebuddy-workspace/GameBuddy-App/
  node_modules/@react-native-firebase/crashlytics/android/src/main/java/io/invertase/
  firebase/crashlytics/generated/jni/react/renderer/components/
  RNFBCrashlyticsTurboModules/RNFBCrashlyticsTurboModulesJSI-generated.cpp):
  Filename longer than 260 characters
```

283 characters, against a 260 limit. Two things ruled out:

- **`LongPathsEnabled` is already `1`** on this machine, so the usual registry fix is not
  the answer. The registry key only lifts the limit for executables that declare
  `longPathAware` in their manifest, and `ninja` — which RN's CMake build shells out to —
  does not.
- **A `subst` drive does not help.** Mapping the repo to `X:\` brings the path to 233
  characters, but CMake resolves the virtual drive back to its real target and ninja still
  enters `C:\Users\canba\...`. Verified with a cleared `.cxx` cache.

**The limit is local only.** EAS builds on Linux, which has no `MAX_PATH`, so the build that
reaches the Play Store compiles Crashlytics normally. Only `npx expo run:android` is
affected — which happens to be what the Maestro suite runs against.

So the module is gated on `EAS_BUILD`, in **two** places, and both are required:

| | |
| --- | --- |
| `app.config.js` | adds the two Expo config plugins only when `EAS_BUILD=true` |
| `package.json` | `expo.autolinking.android.exclude` keeps the native modules out locally |
| `scripts/eas-enable-crashlytics.js` | removes that exclusion on the builder, via the `eas-build-pre-install` hook |

**Dropping the config plugin alone does nothing**, and that cost a build to learn:
autolinking discovers a native module from `package.json` alone, so the exclusion is the part
that actually decides. Change one half and you must change the other — the plugin without the
module configures Firebase for something absent, the module without the plugin has no
`google-services.json` wired in.

What this buys and what it costs: the shipped app reports native crashes, and local and store
builds no longer contain identical native modules — so a fault in Crashlytics' own
initialisation would first show up on EAS rather than on the emulator. That is the accepted
trade; the alternative was shipping with no way to see the crash at all.

The other code:

- `firebase.json` sets `crashlytics_ndk_enabled` explicitly. It is the default, but it is
  also the entire reason this was worth adding: the NDK signal handler is what sees a
  `SIGSEGV`, and a JavaScript reporter never runs because the process is already gone.
- `src/diagnostics/crashReporting.ts` ties reports to the account id — never an email or
  username, which would put personal data in a third-party dashboard for no diagnostic gain.
  It checks `NativeModules.RNFBCrashlyticsModule` and returns null when absent, so it is
  simply inert in a local build rather than throwing.

Verified: `npx expo run:android` → `BUILD SUCCESSFUL`, app installed and running with no
fatals; `tsc --noEmit` clean; `expo config --type introspect` shows no Firebase plugins
locally and both of them under `EAS_BUILD=true`; the pre-install hook removes the exclusion
and is idempotent.

If the repo is ever moved somewhere shorter (`C:\gb` saves 38 characters, 283 → 245), all of
this becomes unnecessary and the two gates can be deleted.

### The release gate has now been run — 2026-08-14

**7/7 Maestro flows pass against an EAS release build, and no native crash occurred.**

```
01-onboarding  PASS      05-settings   PASS
02-swipe       PASS      06-admin      PASS
03-chat        PASS      07-offline    PASS
04-market      PASS
```

Across today's release-build runs — 21 flow executions, each starting with
`launchApp: clearState: true` — logcat contains **no `SIGSEGV` and no `Fatal signal`** in any
run directory. At roughly one occurrence in twenty launches that is not proof of absence, and
it should not be reported as one. What it is: the first evidence gathered in the configuration
that actually ships, where the earlier observation was not.

Two builds were needed before the suite could run at all, and both failures were
configuration rather than app defects — findings 7 and 8 below. The suite now runs against a
`gate` profile via `.\run.ps1 -ReleaseBuild`.

**Still owed before submission:** re-run this against the *final* production build, since the
`gate` profile omits the RevenueCat key (finding 8) and therefore does not initialise the
purchases SDK. That is the one native subsystem this run did not exercise.

---

<a id="7"></a>
## 7. The `preview` EAS profile cannot reach any backend — **Medium**

Found on 2026-08-14 while running the release gate for finding 6, not during the original
black-box pass. An EAS build made with `--profile preview` fails at sign-in with:

> Could not reach GameBuddy. Check your connection and try again.

All seven Maestro flows failed on it, every one at the same step, for the same reason.

### Why

Two pieces of configuration that are each correct on their own and do not compose:

1. `eas.json`'s `preview` profile sets no `EXPO_PUBLIC_API_URL`. So
   `src/api/config.ts:18-29` takes its fallback and resolves `http://10.0.2.2:8080` on an
   Android emulator — a **plain HTTP** address.
2. `plugins/withLanCleartext.js` enables `android:usesCleartextTraffic` **only** when
   `EXPO_PUBLIC_API_URL` starts with `http://`. Unset means it stays off — which is
   deliberate and right, because it is what stops a production build shipping with cleartext
   enabled by accident.

So the app resolves a plain-HTTP address inside a build where Android blocks plain HTTP.
Android has refused cleartext by default since API 28, and the resulting error names the
network rather than the build, so nothing on screen points at the cause.

### Why it matters beyond the test run

`preview` is `distribution: internal` — it is the profile you would hand to a tester or use
for a store pre-release check. In its current state it produces an app that cannot sign in,
against any backend, on any device.

### Fix

For now, the release gate uses **`lan`**, which sets `EXPO_PUBLIC_API_URL` explicitly and so
satisfies both halves. That is what `qa/maestro/README.md` documents.

The durable fix belongs with the deployment (task #34): once the backend is behind HTTPS,
give `preview` an `EXPO_PUBLIC_API_URL` pointing at it. Cleartext then stays off — correctly,
because it is no longer needed — and `preview` becomes the right profile for both testers and
this gate. Until then, `preview` should either carry a URL or be understood as unusable; a
profile that builds successfully and cannot talk to anything is the worst of the three states.

---

<a id="8"></a>
## 8. A release build closes itself, or ships with billing dead — **High**

Found on 2026-08-14 running the release gate, and it is the most serious thing that came out
of that exercise. It cannot be reproduced in development: it is **release-only behaviour**,
which is precisely why a debug-only test suite has never seen it.

### What happens

Seconds after sign-in, on a release build made with the `lan` or `preview` profile, the
RevenueCat SDK puts up a native dialog and terminates the process:

> **Wrong API Key** — This app is using a test API key: `test_VV*******Afvx`. To prepare for
> release, update your RevenueCat settings to use a production key. **The app will close now
> to protect the security of test purchases.**

All seven Maestro flows failed on it, each at the same point.

### Why the app's own defences do not catch it

`src/billing/purchases.ts:95` wraps `Purchases.configure` in a `try`/`catch` whose comment
reads *"Never fatal. Failing to reach RevenueCat must not stop somebody using the app."*
That intention is sound and it does not hold here: **RevenueCat kills the process from native
code**, so the `catch` never runs. It is the same structural blind spot as finding 6 — a
native termination is invisible to JavaScript, whichever direction it comes from.

### Both configurations are wrong, in opposite ways

| profile | `EXPO_PUBLIC_REVENUECAT_API_KEY` | release-build behaviour |
| --- | --- | --- |
| `lan`, `preview` | `test_VVPmkcbxscVCUltzvloqUXiAfvx` | the app closes itself after sign-in |
| `production` | **not set** | runs — and billing is silently dead |

Both rows were the state as found. The test key has since been removed from `lan` and
`preview`, so all three profiles now behave like the second row — see *Fix* below.

The second row is the more dangerous one. With no key, `API_KEY` is `''`, so
`storeAvailable()` returns false and `identify()` returns before configuring. Nothing
crashes, nothing logs in production, and the paywall simply never works — a store build could
ship in that state and look fine until somebody tried to pay.

### Fix

**Done — the crash.** The `test_` key is gone from `lan` and `preview` in `eas.json`, and
`src/billing/purchases.ts` now validates the key's platform prefix (`goog_` on Android,
`appl_` on iOS) before it reaches `configure`. Anything else is treated as no key at all, so
a stray test key disables purchases instead of closing the app. This had to live in code, not
just in `eas.json`: the termination is native and unreachable from JavaScript, so refusing to
call `configure` is the only defence available.

**Done — the silence.** `storeAvailable()`'s missing-key path now logs at `error`
unconditionally rather than under `__DEV__`, so a release build that cannot sell anything
says so in logcat and Crashlytics.

**Done 2026-08-16 — the real key exists.** Google Play is now connected to RevenueCat (service
account `play-publisher@project-736aa984-61c3-4516-9cb…`, which RevenueCat validated as *Valid
credentials* against Play), and the resulting public SDK key `goog_xmTnFlrJ…` is set on
`production` and `preview`. `lan` and `gate` inherit it through `extends: production`, which
is a gain rather than an oversight: this finding used to note that `gate` omitted RevenueCat
and so never exercised that native subsystem. It does now, and a valid `goog_` key cannot
trigger the termination described above — only a `test_` key in a release build does that.

The local `.env` deliberately keeps the `test_` Test Store key, because debug builds are the
only place the Test Store is allowed to run, and that is where the six sandbox products
(`gamebuddy.gold.*`, `gamebuddy.coins.*`) can be exercised.

Worth knowing before that key is tested: Google Play Billing only answers a build the Play
Store recognises. A sideloaded `lan` APK cannot open a purchase sheet even with a correct key
— `getProducts` comes back empty — so the paywall has to be exercised from an internal
testing track, not from `eas build` output installed over USB.

Meanwhile the release gate uses a `gate` profile — `extends: production`, so no RevenueCat
key, plus an `EXPO_PUBLIC_API_URL` for the local backend. That matches production's native
behaviour exactly rather than working around it.

---

## Behaviour verified as correct

Recorded so it is not re-investigated, and because two of these looked like defects first:

- **Chat message bodies are genuinely encrypted at rest** — checked by reading the `bytea`
  column and comparing against the hex of the plaintext, not by trusting the class name.
- **A ban invalidates a token issued before it.** Revocation compares at second
  granularity on purpose (`JwtService.java:119-121`), so a token minted in the same second
  as the revocation survives — deliberate, documented, and worth knowing before someone
  reports it as a hole.
- **Chat rooms are created lazily**, on the first message, not at match time
  (`ChatMessageService.java:180`). Matches that never talk cost no rows.
- **An owner leaving a community transfers ownership** to the longest-standing member, or
  closes it if empty (`DefaultCommunityService.java:387-404`). Better than refusing —
  but `TransactionCode.USER_OWNER` ("Owner cannot leave their own community") is declared
  and never thrown. Dead constant; delete it or it will mislead the next reader.
- **The public/private text filter split works**: contact details are stripped to
  `[removed]` in posts and left alone in private chat, exactly as the README describes.
  Note a side effect — usernames and community names containing a long digit run are
  refused as phone numbers, so `player1234567` is not an available username.
- **Re-registering an unverified email is allowed; a verified one is not.** Deliberate, and
  the right call (`DefaultAuthService.java:174-181`).
- Age gating (12- and 17-year-olds refused), onboarding minimums, the 30/min decision rate
  limiter, the daily swipe quota, SQL injection resistance, horizontal access control on
  conversations and profiles, the RevenueCat webhook's token check, and actuator exposure
  are all correct.

---

## Running the suite

```bash
node qa/run-functional.js            # all 8 suites, ~25s
node qa/run-functional.js 07 08      # just those
node qa/reset-fixtures.js            # how many seeded fixtures are usable
node qa/reset-fixtures.js --apply    # put them back
```

104 tests. It creates accounts through the real signup flow and borrows seeded
`@bot.gamebuddy.invalid` fixtures, returning both afterwards. Nothing it creates outlives
the run.
