# GameBuddy-App

The mobile client. Expo SDK 57, React Native 0.86, TypeScript, Expo Router.

Replaces `GameBuddy-Android`, which is Kotlin and Android-only. Nothing is ported from
it except the palette and the typeface — the backend it talked to no longer exists in
that shape (chat moved to STOMP, and blocking, reporting, account deletion and billing
are all new surface).

---

## Running it

```bash
npm install
npx expo start
```

Then scan the QR code with Expo Go, or press `a` / `i` for an emulator.

`w` opens it in a browser. Web is a **debugging surface, not a shipping target** — it
exists because iterating on a layout in a browser is faster than on a device. Three
things differ there: there is no secure storage, so the token falls back to
`localStorage` (`src/session/storage.ts` warns about this once); swipe gestures are not
meaningfully testable with a mouse; and unlike native, the browser enforces CORS, so the
backend must allow the dev-server origin. `docker-compose.yml` sets
`CORS_ALLOWED_ORIGINS` to localhost patterns for exactly this. Running the backend
outside compose without that variable makes every call fail as *"Could not reach
GameBuddy"* — which is the browser refusing the response, not the server being down.

The backend has to be running too — see the root `README.md`. By default the app points
at **port 8080 on whatever host Metro is being served from**, which is the right answer
for a physical device on the same network and for both emulators, and needs no
configuration. Override it when that guess is wrong, or to point at production:

```bash
EXPO_PUBLIC_API_URL=https://api.example.com npx expo start
```

Because there is no mail server locally, the verification code is printed to the
backend log rather than sent:

```bash
docker compose logs backend | grep "verification code"
```

## Layout

```
app/                 Routes. The directory structure is the navigation.
  _layout.tsx        Fonts, splash, providers, session + theme bootstrap
  index.tsx          Redirects to wherever the session belongs
  (auth)/            welcome, register, verify, login
  (onboarding)/      username, profile, games, keywords
  (main)/            home, settings  — the swipe deck goes here
src/
  api/               Typed client for the backend's envelope
  session/           Token storage, session status, navigation guards
  onboarding/        The multi-screen profile draft and its pieces
  ui/                Text, Button, TextField, Screen, Chip, Avatar, Card, SelectRow
  theme/             Colour scheme store and the JS-side palette
global.css           Tailwind entry + the light/dark theme tokens
tailwind.config.js   Design tokens: brand colours, Poppins families, radii
```

## Styling and dark mode

Styling is [NativeWind](https://nativewind.dev) v4 — Tailwind classes on React Native
components. Three files make it work and all three are required:

- `babel.config.js` sets `jsxImportSource: 'nativewind'`. Without it every `className`
  is silently ignored and nothing is styled.
- `metro.config.js` wraps the config with `withNativeWind` and names `global.css`.
- `nativewind-env.d.ts` adds `className` to React Native's prop types.

`babel-preset-expo` has to be a direct dependency. The blank template ships no Babel
config, so nothing pulled it in; when it is missing, Metro fails to build its
transformer and reports `Cannot read properties of undefined (reading 'transformFile')`
— which points nowhere near the real cause.

**Semantic colours are CSS variables**, declared for both themes in `global.css` and
exposed to Tailwind as `bg-surface`, `text-content`, `border-line` and so on. A screen
almost never needs a `dark:` variant: the token resolves differently and that is the
whole mechanism. Use `dark:` only where the *treatment* changes rather than the value —
`Card` is the example, since it lifts with a shadow in light mode and with a border in
dark, shadows being nearly invisible on a near-black canvas.

The theme is a **setting**, not just an OS mirror: `src/theme/scheme.ts` stores
`system` / `light` / `dark` and hands it to NativeWind's `colorScheme`. `system` is the
default and follows the phone. The choice is read before the splash screen is released,
so the first frame is already in the right theme. It lives under **Settings**, reached
from the button on the home screen.

Class conflicts go through `cn()` (`src/ui/cn.ts`) rather than raw string
concatenation. NativeWind resolves conflicts by CSS specificity, not string order, so
`"text-content text-brand"` is otherwise not reliably brand-coloured — which would make
every "base style, overridable from outside" component prop a coin toss.

## The response envelope

Every backend response has the same shape, success or failure:

```json
{ "status": { "code": "100", "message": "Success", "success": true },
  "body":   { "data": { } } }
```

`src/api/client.ts` unwraps it, so callers get `data` directly and a rejection arrives
as an `ApiError` carrying the application `code` — `"101"` for a taken email, `"109"`
for unfinished onboarding, and so on. Screens branch on those codes rather than on
message text.

One wrinkle worth knowing: a request rejected by Spring Security before it reaches a
controller comes back as a **bare 401 with no body**, so there is no code to read. That
is treated as an expired session, which is the only thing that produces it.

## Navigation is driven by session status, not by history

`src/session/store.ts` resolves the token into one of `signedOut`, `needsUsername`,
`needsDetails` or `ready`, and each route group redirects anything that does not belong
to it. The halfway states are not optional: verification issues a token *before* the
profile exists, so "has a token" does not mean "can use the app", and a user who closes
the app mid-onboarding must reopen into the step they left rather than a broken home
screen.

The stage is read back from the server (`/application/get/user/info`), not remembered
locally — reinstalling, or signing in on a second device, would defeat a local flag.

## Android

The package is **`com.findgamebuddy.app`**, and the odd-looking `find` prefix is
deliberate. `com.gamebuddy.app` was the original choice and it is **already taken on Google
Play by somebody else** — discovered at app-creation time, when the console refused it.
Package names are globally unique and permanent once an app exists, so this is not a
preference that can be revisited later.

It is also the more correct name. Reverse-DNS packages are supposed to mirror a domain you
control, and `gamebuddy.app` is a domain nobody here owns — the same reason the marketing
site ended up at `findgamebuddy.com`. Both now agree.

The Play Console app was created 2026-08-16 as *GameBuddy: Find Gamers to Play* (app id
`4972657633723197721`), free, categorised as an App rather than a Game — Play applies extra
policy to the Dating category and this is a gaming-partner app, so nothing in the listing
should invite that classification.

Minimum supported version is **API 24 (Android 7.0)** — that is React Native 0.86's
floor, from `node_modules/react-native/gradle/libs.versions.toml`. Tested on an
**API 26 (Android 8.0)** emulator, which is the oldest device this is claimed to work on.

Three things behaved differently on device than in a browser, and all three were
invisible on web:

- **No coloured shadows.** NativeWind's `shadow-lg shadow-brand/40` crashed the app when
  applied on a state change, and the error it produced — "Couldn't find a navigation
  context" — pointed nowhere near the cause. Android has no coloured shadow before API
  28 anyway, so the class was describing something the platform could not draw. Depth
  now goes through `lift()` in `src/ui/elevation.ts`: `elevation` on Android, a real
  shadow on iOS. **Do not reintroduce `shadow-*` classes.**
- **Safe-area insets are real.** Omitting `bottom` from a `Screen`'s `edges` put the
  deck's Pass/Match labels underneath the system navigation bar. On web the inset is
  zero and nothing looks wrong.
- **A layout must always render its navigator.** See `src/session/RouteGuard.tsx`.

### Running on an emulator

```bash
npx expo start --android
```

The API base URL resolves from Metro's own host, so it works without configuration on a
device, an emulator, or a LAN. The one case that needs help is an emulator reached over
an adb tunnel, where Metro reports `localhost` — `src/api/config.ts` rewrites that to
`10.0.2.2`, the emulator's alias for its host, because `localhost` there is the emulator
itself.

## Installing on a real device

### Android — an APK, built in the cloud

**A release APK cannot be built locally on Windows.** This is measured, not assumed — see
the section below. Use EAS Build, which compiles on Linux where the limit does not exist,
and which is the same command that later produces the Play Store bundle:

```bash
cd GameBuddy-App
npm install -g eas-cli
eas login                       # a free Expo account

# Once per project: hand EAS the Firebase config. See below.
eas env:set --scope project --name GOOGLE_SERVICES_JSON --type file --value ./google-services.json --visibility sensitive --environment production --environment preview

eas build --platform android --profile lan
```

#### google-services.json has to be handed over separately

> **The file's `package_name` has to match `android.package` exactly.** The Gradle plugin
> matches on that string and stops the build with `No matching client found for package
> name` when it does not. This bit once, during the rename to `com.findgamebuddy.app` (see
> *Android* above): the old file was still registered against `com.gamebuddy.app`.
>
> Hand-editing the JSON is **not** the fix — the app id and API key inside belong to whichever
> registration Firebase issued them for. Register the app in the Firebase console under the
> new package, download the fresh file, and replace *both* copies (repo root and
> `android/app/`), then re-run the `eas env:set` below so the builder gets it too. Resolved
> 2026-08-16; current file is project `gamebuddy-a4205`, app id ending `9738c2`, verified by
> `FirebaseApp initialization successful` in logcat on a running build.

Without that `env:set`, the build fails on the builder:

```
"google-services.json" is missing, make sure that the file exists.
Remember that EAS Build only uploads the files tracked by git.
```

The file is gitignored — the workspace root excludes every Firebase and service-account
file — so it is never uploaded, and `app.json` points straight at it.

**A `.easignore` does not fix this.** It can only *exclude* files from the upload; it
cannot include one that git does not track. That was tried and it failed with the same
error.

What works is an EAS **file environment variable**: the file is stored by EAS, written to
a temporary path on the builder, and its location exposed as `GOOGLE_SERVICES_JSON`.
`app.config.js` reads that variable and falls back to the path in `app.json` when it is
absent, so `expo prebuild` and `expo run:android` keep working locally with no setup at
all.

The other option is to un-ignore the file and commit it. Its API key is public by design —
it ships inside every APK and is restricted by the app's signing certificate — so this is
not as reckless as it sounds. It was not done here because the repository deliberately
excludes credential files, and one exception is how that convention stops being one.

EAS prints a URL when it finishes; open it on the phone and install. The `lan` profile
builds an APK (not an `.aab`), marks it internal-distribution so no store is involved, and
sets `EXPO_PUBLIC_API_URL` to this PC's address.

**Update the IP in `eas.json` when your router changes it.** It is a DHCP lease baked into
a build, not configuration.

The free tier gives a limited number of builds a month on a shared queue, so a build can
wait a while before it starts. Nothing here requires a paid plan.

#### Why not locally

Both attempts failed the same way, and the error names neither Windows nor path lengths:

```
ninja: error: manifest 'build.ninja' still dirty after 100 tries
```

Above it, CMake explains itself:

```
.../react-native-reanimated/android/.cxx/RelWithDebInfo/<hash>/arm64-v8a/
  CMakeFiles/reanimated.dir/    has 179 characters.
The maximum full path to an object file is 250 characters
  (see CMAKE_OBJECT_PATH_MAX).
```

CMake mirrors every absolute source path underneath the build directory, so the project's
location appears twice in each object path. Measured: **367 characters against a limit of
250.**

Things that do not fix it:

- **`LongPathsEnabled`** is already `1` in the registry on this machine. It is irrelevant —
  the 250 is CMake's own `CMAKE_OBJECT_PATH_MAX`, not the Windows `MAX_PATH` of 260.
- **A junction at `C:\gb`.** Tried it; Gradle resolves the link back to the real path
  before CMake ever sees it, and the error came back byte for byte identical.
- **Moving the app to `C:\gb`** would bring it to 245 — five characters under the limit,
  with one source file. Any longer filename in a future version of reanimated puts it back
  over. That is not a workflow, it is a coin toss.

Building a *debug* variant locally still works and is unaffected — `npx expo run:android`
is the normal loop. It is the release build, with its longer `RelWithDebInfo` directory
name and its full native compile, that goes over.

#### The phone also has to be able to reach the PC

Docker publishes 8080 on all interfaces — `curl http://192.168.50.169:8080/actuator/health`
from this machine already answers — but Windows Firewall drops inbound connections from
anywhere else. Once, as administrator:

```powershell
New-NetFirewallRule -DisplayName "GameBuddy backend (LAN)" -Direction Inbound `
  -LocalPort 8080 -Protocol TCP -Action Allow `
  -RemoteAddress 192.168.50.0/24
```

Scoped by **subnet**, not by firewall profile. `-Profile Private` looks like the tighter
choice and is a trap: Windows classifies this machine's Wi-Fi as *Public*
(`Get-NetConnectionProfile` says so), so a Private-only rule is created, listed by
`Get-NetFirewallRule`, and never applies. The connection is refused with nothing to
suggest a rule exists at all.

`-RemoteAddress` reaches the same restriction by a route that does not depend on how
Windows happened to label the network: only hosts on your own LAN can open the port,
whatever profile is active. Adjust the range if your router hands out something other than
`192.168.50.x`.

Then, from the phone's browser: `http://192.168.50.169:8080/actuator/health` should return
`{"status":"UP"}`. If that fails, nothing in the app will work either, and the problem is
the network rather than the build.

### iPhone — a simulator build in the browser today, TestFlight later

**Expo Go cannot run this app.** It ships Nitro Google Sign-In, Google Mobile Ads,
RevenueCat and Firebase; none of them exist in Expo Go, and `src/session/google.ts` and
`src/billing/purchases.ts` both degrade rather than crash precisely because a build can be
missing them. Expo Go would launch and then be unable to sign anybody in.

What works from Windows, with **no Apple developer account**:

```bash
eas build --platform ios --profile ios-simulator
```

That produces a `.tar.gz` holding `GameBuddy.app`, built for the iOS Simulator as a universal
binary with both `x86_64` and `arm64` slices. EAS asks for no Apple credentials at all for a
simulator build. Upload the archive as-is to [appetize.io](https://appetize.io/upload) — the
`.tar.gz` is already the shape Appetize wants, so do not unpack it — and it runs in a browser
tab. Pick a few different iPhone models while you are there; see `UI_NOTE.md` on checking
three screen geometries.

**Appetize needs a free account.** Anonymous upload no longer exists: the API answers
`401 {"message":"Invalid API token"}` without one, so the archive has to go through the web
upload form while signed in, or through the REST API with a token from
Account → API. That is the one step in this whole route that cannot be automated from here.

What a simulator build genuinely proves: that the native project compiles at all, that the
pods resolve, and that every screen lays out and navigates on iOS. Google sign-in exercises
the iOS OAuth client and its URL scheme. The API calls hit production over HTTPS.

What it cannot prove, because the Simulator has none of them: push delivery, in-app
purchases, App Tracking Transparency, and the camera. Those wait for a real device.

**TestFlight is the only honest "it fully works" test**, and it is behind the paid account:

1. **Apple Developer Program — $99/year.** There is no free path to TestFlight.
2. Create the app in App Store Connect with bundle id `com.findgamebuddy.app`.
3. Finish the four deferred items under *iOS: what is still missing* below.
4. `eas build --platform ios --profile production` — EAS builds on cloud macOS, so no Mac
   is required, and it generates and stores the signing certificates.
5. `eas submit --platform ios --latest`
6. Add yourself as an internal tester in App Store Connect → TestFlight.

### iOS: what is configured, and what is still missing

Registered on 2026-09-08, all free, all bundle id `com.findgamebuddy.app`:

| Thing | Where | Value lives in |
| --- | --- | --- |
| OAuth client (iOS) | Cloud project 656951909603 — the OAuth project, **not** Firebase | `eas.json` env + `.env` |
| Firebase iOS app | project `gamebuddy-a4205` | `GoogleService-Info.plist`, uploaded to EAS as `GOOGLE_SERVICES_INFO_PLIST` |
| AdMob iOS app + rewarded unit | publisher `pub-1806031824100901` | `eas.json` under `production.ios` |

Two of those carry a trap worth restating.

**The Google client id must be named explicitly on iOS.** Android is matched by package name
and signing certificate, so its client is never written down. Apple has no such signal, and
the SDK's fallback is `CLIENT_ID` inside `GoogleService-Info.plist` — which our plist does not
contain, because Firebase holds no OAuth clients. Left implicit, `configure()` throws and the
Google button silently does nothing. The reversed id is also the URL scheme, set through the
plugin option in `app.json`; the two always change together.

**Firebase forces the iOS linkage.** `@react-native-firebase` v26 resolves the Firebase iOS
SDK through Swift Package Manager, which requires dynamic frameworks, while Google Sign-In and
Mobile Ads want static. `app.config.js` passes `disableSPM` to put Firebase back on CocoaPods
and `expo-build-properties` sets `useFrameworks: "static"` to match. Change one and the pods
stop agreeing.

Still missing, each blocked on the $99 account:

- **RevenueCat.** Only the `goog_` key exists. `production.ios` sets the key to `none`, so the
  paywall reports itself switched off rather than erroring. Needs an App Store Connect app and
  IAP products before an `appl_` key can be issued.
- **Push.** `registerDeviceToken` returns early on iOS: `getDevicePushTokenAsync` gives an APNs
  token, the backend sends through FCM, and bridging them needs an APNs key from the Apple
  developer portal. Delete that guard once the key is in Firebase.
- **App Tracking Transparency.** The `NSUserTrackingUsageDescription` string ships, but the
  AdMob IDFA explainer message has not been created in Privacy & messaging.
- **Limited ad serving.** The iOS AdMob app stays limited until an App Store listing is linked,
  exactly as the Android one was before Play.

### Build profiles

`production` is the source of truth and **the other four all `extends` it**, so they cannot
silently drift from what ships. Each one overrides only what it genuinely needs to.

| Profile | What it is for | Differs from `production` by |
| --- | --- | --- |
| `production` | The build end users get. `.aab` for Play, and the iOS build TestFlight receives | — |
| `preview` | The same product, on **your own device**, to confirm the real thing is right before release | APK instead of `.aab` (an `.aab` cannot be sideloaded), internal distribution, own channel |
| `gate` | The Maestro release gate | + LAN backend |
| `lan` | Emulator or device, for **UI and flow** work | + LAN backend, **no RevenueCat key** |
| `ios-simulator` | An iOS build that runs in Appetize from Windows, with no Apple account | `extends` `preview`; `ios.simulator`, test rewarded unit |

**`production` carries an `ios` block, and that is where the platform's values diverge.** The
AdMob app id and rewarded unit are per platform — an app id from one account with a unit from
another is refused, which is the failure recorded in `src/ads/rewarded.ts` — and RevenueCat is
switched off entirely on iOS until an `appl_` key exists. Platform blocks are deep-merged over
the shared `env`, so everything else is still inherited rather than restated.

**`lan` and `gate` are Android-only in practice.** Both point at a plain-HTTP LAN address, iOS
App Transport Security blocks that in a real build, and `plugins/withLanCleartext.js` has no
iOS counterpart. Appetize could not reach a LAN machine regardless, which is why
`ios-simulator` extends `preview` and talks to production over HTTPS.

Three deliberate details, each of which has already gone wrong once:

**`preview` overrides nothing but packaging.** It used to hand-copy `production`'s `env` and
run under a separate `preview` EAS environment, which meant a secret added to `production`
would quietly be missing from the one build meant to prove production works. It now `extends`
and shares `environment: production`, so "the same as production" is structural rather than a
promise someone has to remember to keep.

**`lan` sets `EXPO_PUBLIC_REVENUECAT_API_KEY` to the empty string.** Not an oversight — it is
how you *un*-inherit. Without it `lan` picks up the real `goog_` key and every throwaway
UI build starts registering its test accounts as customers in the live RevenueCat project,
for no benefit: a sideloaded APK cannot transact with Play Billing anyway.

**`gate` keeps the real key on purpose.** Its entire job is catching release-only faults, and
the RevenueCat termination bug in `QA_FINDINGS.md` is exactly that class — `gate` passed while
`lan` and `preview` died, precisely because `gate` was the profile without a key. A gate that
does not carry what production carries is not a gate.

`autoIncrement` is `true` only on `production`. The others switch it off so a throwaway build
does not burn a number out of the remote `versionCode` counter that Play releases draw from.

**Every profile has its own EAS Update channel**, `production` included. That last one was
missing until 2026-08-16, which meant shipped builds could not receive an over-the-air update
at all — `updates.url` was configured in `app.json`, so the plumbing looked complete, but a
build without a channel subscribes to nothing. Publish to a release with
`eas update --branch <branch> --channel production`.

The channels are deliberately distinct, so an update pushed to `preview` can never reach a
store build. Combined with `runtimeVersion.policy: "appVersion"`, an update only reaches
builds of the *same* app version — so JS-only fixes ship over the air, while anything touching
native code correctly requires a new version and a new store release.

The IP in `lan` and `gate` is a convenience, not configuration — it is your current DHCP lease
and it will go stale.

## Known gaps

- **`plugins/withAndroidJdk17.js` hard-codes a Windows JDK path** as its default. It only
  applies on Windows now, so EAS builds are unaffected, but a second Windows machine needs
  `GAMEBUDDY_ANDROID_JDK` set.
- **The `lan` build profile hard-codes a DHCP address.** See above.
- **No `expo-system-ui`**, so `userInterfaceStyle` in `app.json` does nothing on Android.
  Prebuild says so on every run. The in-app theme switcher is unaffected — it does not go
  through that setting.

### iOS follow-ups noticed in the first successful build

- **`SKAdNetworkItems` is empty.** The Google Mobile Ads plugin only writes the ad-network
  identifiers when it is given them, and it was not. Nothing breaks without them, but iOS ad
  attribution is weaker, so add the list before the App Store release rather than before the
  next smoke test.
