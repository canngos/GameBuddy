# UI tests — Maestro on the Android emulator

Seven flows against the real app on a real emulator. They cover what the API suite
structurally cannot: whether a thumb dragging left actually moves a card, whether a sent
message appears in the thread, whether a settings screen renders at all.

```powershell
cd qa/maestro
.\run.ps1                 # all flows
.\run.ps1 -Flow 02        # one
.\run.ps1 -SkipProvision  # reuse the last provisioned accounts
.\run.ps1 -ReleaseBuild   # against an EAS build instead of the debug APK
```

## The pre-submission gate

`-ReleaseBuild` exists for one job: proving the app is sound in the configuration that
actually ships. A debug build keeps React Native's dev-support machinery attached and runs
its bundle from Metro, so it is more crash-prone in the native renderer than a release build
and an observation there proves nothing either way — which is exactly the position
`QA_FINDINGS.md` #6 is in.

```powershell
cd GameBuddy-App
eas build --profile lan --platform android
# download the APK from the build page, then
adb uninstall com.findgamebuddy.app     # signatures differ; install-over fails
adb install <the>.apk
cd ../qa/maestro; .\run.ps1 -ReleaseBuild
```

**`lan`, not `preview`, and this is the trap.** A `preview` build cannot reach the backend
at all, and the error it gives — "Could not reach GameBuddy. Check your connection" — points
at the network rather than at the build. The chain is:

1. `preview` sets no `EXPO_PUBLIC_API_URL`, so `src/api/config.ts` falls back to
   `http://10.0.2.2:8080` for the emulator.
2. `plugins/withLanCleartext.js` enables cleartext **only** when `EXPO_PUBLIC_API_URL`
   starts with `http://`. Unset means it stays off — deliberately, so a production build
   cannot ship with cleartext enabled by accident.
3. So the app resolves a plain-HTTP address in a build where Android blocks plain HTTP.

`lan` sets `EXPO_PUBLIC_API_URL` explicitly, which satisfies both halves. It hardcodes this
machine's LAN address (`192.168.50.169`) — check that still matches `ipconfig` before
building, or the same symptom returns for a different reason. Once the backend is deployed
behind HTTPS, `preview` becomes the right profile and this note can go.

Two differences worth expecting. **Metro must not be required** — the bundle is inside the
APK, and the switch skips that precondition rather than asking for a Metro that could not
serve this build anyway. And **LogBox does not exist**, so `_dismiss-logbox.yaml` becomes a
no-op; it is written entirely as guarded `runFlow: when: visible:` blocks, so it costs a
couple of seconds and does nothing. The backend is still reached at `10.0.2.2:8080`, which
the app resolves on its own when no packager host is present, so `adb reverse` is not doing
the work here that it does for a debug build.

## Prerequisites

`run.ps1` checks all four before doing anything, because each has failed once and produced
a confusing error somewhere else.

| | Check | Fix |
| --- | --- | --- |
| Emulator | `adb devices` shows one | `emulator -avd GameBuddy_API34` |
| **Metro** | `localhost:8081/status` | `cd GameBuddy-App; npx expo start --dev-client` |
| Backend | `localhost:8080/actuator/health` | `docker compose up -d` |
| Ports | `adb reverse` 8080 + 8081 | done automatically by `run.ps1` |

**Metro is not optional and this is the trap worth knowing.** The APK is a *debug* build:
it contains no JS bundle, and the app fetches one from Metro at launch. With Metro down the
app runs whatever bundle it cached last — so it starts, looks fine, and silently ignores
every source change you have made. Rebuilding the APK does not help, because the JS was
never in it.

Maestro is installed at `C:\Users\canba\.maestro` (v2.8.0); `run.ps1` puts it on PATH.

## What the flows found, and what they did not

**All seven flows pass. No app defect was found by any of them.** Every failure along the
way was in this harness. That is worth stating plainly, because an earlier version of this
file blamed several of them on the app and was wrong about all of them.

One thing did come out of these runs and is not a harness problem: an **intermittent native
crash** — `SIGSEGV` on the JS thread inside React Native's Fabric renderer, once in about
twenty launches. It is written up as finding 6 in `QA_FINDINGS.md`; it is not reproducible
on demand and is not confirmed against a release build.

### The traps, and what each one taught

These cost the most time. All of them look like app bugs from a screenshot.

**LogBox notifications cover the tab bar.** This is the big one — it failed five of the
seven flows and looked exactly like broken tab navigation. See `_dismiss-logbox.yaml`, which
exists solely to clear them.

**`hideKeyboard` is a Back press on Android.** It is only safe directly after `inputText`,
where the press is spent closing the keyboard. Called when no keyboard is open, it goes to
the navigator instead — and on the bottom screen of a stack it closes the app. The symptom
was `No visible element found: Continue` two steps later, on a screen that no longer
existed. logcat shows `Transition ... type = CLOSE ... numActivities=0` and there is no
crash report, which is how you tell this apart from a real crash.

**Maestro matches a text node in full, not as a substring.** `"Used to confirm you are old
enough"` matches nothing when the node reads `"Used to confirm you are old enough to be
here. Other people see your age, never the date."` Wrap partial text in `.*…*.`.

**A placeholder is reported as `text` as well as `hintText`.** So a field typed with its own
placeholder value looks identical to an empty one, and that mimics text injection failing
when it is working perfectly. Never type a value equal to the placeholder — `01-onboarding`
uses `03 / 11 / 1995` against placeholders of `24 / 08 / 1998` for exactly this reason. The
way to settle it is to press Backspace and see whether the value changes.

**`scrollUntilVisible` stops the moment its target is fully visible**, which leaves anything
below the target still off screen. Scroll to the element you are about to tap, not to the
section heading above it.

**Screens must be scrolled before their buttons exist.** Not "are hard to tap" — a button
below the fold is absent from the hierarchy, and the error is `Element not found`.

**Coordinate taps need the real geometry.** Three separate flows failed on this: the games
grid has ~775px rows so "the row below" was still the row above; the platform list rows have
gaps between them and a tap at 38% landed in one; the keyword list is full-width, so two taps
at different x on the same y toggled one row on and back off. Where a screen has a counter,
assert on it — `SelectionCount` renders `"N more to go"` until the minimum is met and
`"N selected"` after, which turns a mis-aimed tap into a failure on the screen where it
happened.

**Assertions must not depend on flow order.** Provisioning runs once per suite, so by the
time `07-offline` inspects the inbox, `03-chat` has already replied to that partner and the
preview shows 03's message rather than the seeded one. Assert on the row, not the text.

## The flows

| Flow | What it is meant to prove |
| --- | --- |
| `01-onboarding` | Unverified sign-in recovers to the code screen; all six onboarding screens; a new account lands on a **populated** deck — the cold-start path, whose failure means every new user's first screen is empty |
| `02-swipe` | The gesture moves the card *and* reaches the backend — the allowance counter is the oracle; the Gold paywall on filters |
| `03-chat` | The inbox shows a decrypted preview; a sent message renders, and survives leaving and returning (persisted, not just echoed) |
| `04-market` | Market, frames, banners and the earn screen render; purchase is not driven (the build uses a RevenueCat **test store** key) |
| `05-settings` | Every settings sub-screen opens and returns — shallow and wide, because these are the screens nobody opens by hand |
| `06-admin` | The moderator reaches the console **and an ordinary account cannot**, including by deep link |
| `07-offline` | Airplane mode produces a readable error rather than a blank deck, and the app recovers |

`_login.yaml` and `_dismiss-logbox.yaml` are subflows, not tests — the leading underscore
keeps them out of the run. `_login.yaml` signs in as the provisioned member and ends on the
deck; it calls `_dismiss-logbox.yaml` last, so anything that starts from a signed-in app
gets a usable tab bar without asking.

## Why provisioning exists

Maestro drives a screen. It cannot read a verification code out of Postgres or arrange a
match between two accounts, and both are needed before a flow can begin.

`provision.js` builds that starting state through the same API the app uses and hands it
over as `-e` variables:

| Account | State | Used by |
| --- | --- | --- |
| `PENDING_*` | registered, code issued, unverified | 01 finishes the signup |
| `MEMBER_*` | fully onboarded, matched | 02, 03, 04, 05, 07 |
| `PARTNER_*` | the other half of the match, has sent one message | 03 |
| `QA_ADMIN_*` | from `.env`; 06 skips if empty | 06 |

Everything is `@qa.gamebuddy.invalid` and removable:

```bash
node qa/maestro/provision.js --cleanup
```

## Selectors, and the one change made to the app

Inputs are addressed by `id`, buttons by their visible text or accessibility label.

The app had **no `testID`s at all**, and that produced a real failure on the first run: the
label of a `TextField` renders as its own Text node *above* the input, so
`tapOn: "Password"` hit the caption and the password was typed into the email box. Both
values ended up in one field and the flow failed as though sign-in were broken.

One change fixed it for every field in the app —
`GameBuddy-App/src/ui/TextField.tsx` now derives a `testID` from the label:

```tsx
const fieldTestId = (label?: string) =>
  label ? `field-${label.toLowerCase().replace(/[^a-z0-9]+/g, '-')...}` : undefined;
```

So `label="Username or email"` becomes `field-username-or-email`. An explicit `testID`
still wins. That is the **only** production-code change made for these tests: one shared
component, no per-screen edits, and `npx tsc --noEmit` is clean.

Two selector habits worth keeping:

- **Never select an input by its label text.** It is a sibling node, not the field.
- **Catalogue content is tapped by position, not by name.** Games and keywords are seeded
  data; a flow that depends on "Valorant" being present breaks when the catalogue changes.

## Gotchas

- **`clearState: true` signs out whoever was using the emulator.** The flows need a known
  starting state. If you were signed in as yourself, sign back in afterwards.
- **Usernames must be letters.** They are a `PUBLIC` text surface, so the filter strips
  phone numbers — a digit-heavy random string is refused as one. `provision.js` generates
  letters only.
- **Screenshots do not land in `qa/maestro/artifacts/`.** Maestro writes them under its own
  run directory, at
  `~/.maestro/tests/<timestamp>/<flow>/takeScreenshot/artifacts/`, together with a
  screenshot and a full hierarchy dump of the failing step under `screenshots/` and
  `screen-hierarchy/`, and `logs/device-logcat.txt`. That last one is the only place a
  native crash appears. An empty `artifacts/` in this folder means nothing.
- **A flow that fails at the first `assertVisible` is usually a stale bundle**, not a
  regression. Check Metro is running and reload the app.
- **Give the first launch of a run 60 seconds.** The APK carries no JS, so a cold Metro
  bundle takes ~18s — and the splash says "GameBuddy" the whole time, so an assertion on
  that word passes on a screen with no buttons on it yet.
- **Metro's file watcher does not always fire on Windows.** A source edit can go unnoticed,
  leaving the app on the previous bundle while you conclude your change had no effect.
  `Bundled … (N modules)` in the Metro output is the confirmation; if it does not appear,
  restart Metro rather than trusting fast refresh.
