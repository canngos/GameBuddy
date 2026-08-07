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

### iPhone — Expo Go today, TestFlight later

For testing against a local backend, **use Expo Go**. It is free, needs no Apple
account, and the API base URL resolves from Metro's host automatically:

```bash
npx expo start
```

Scan the QR code with the iPhone camera. Push notifications will not work — they need a
real build — but everything else does.

**TestFlight is the wrong tool for a local backend**, for two reasons that are not worth
fighting: iOS App Transport Security blocks plain HTTP in a real build, and a TestFlight
build is meant to reach a deployed server rather than a laptop on your Wi-Fi. It becomes
the right tool once the backend is deployed and on HTTPS.

When that day comes:

1. **Apple Developer Program — $99/year.** There is no free path to TestFlight.
2. Create the app in App Store Connect with bundle id `com.gamebuddy.app`.
3. `eas build --platform ios --profile production` — EAS builds on cloud macOS, so no Mac
   is required, and it generates and stores the signing certificates.
4. `eas submit --platform ios --latest`
5. Add yourself as an internal tester in App Store Connect → TestFlight.

### Build profiles

`eas.json` defines three:

| Profile | What it is for |
| --- | --- |
| `lan` | An APK pointed at this PC's LAN address. Update the IP when your router hands you a new one |
| `preview` | An internal-distribution APK against whatever `EXPO_PUBLIC_API_URL` the environment supplies |
| `production` | An `.aab` for the Play Store, and the iOS build TestFlight receives |

The IP in the `lan` profile is a convenience, not configuration — it is your current
DHCP lease and it will go stale.

## Known gaps

- **`plugins/withAndroidJdk17.js` hard-codes a Windows JDK path** as its default. It only
  applies on Windows now, so EAS builds are unaffected, but a second Windows machine needs
  `GAMEBUDDY_ANDROID_JDK` set.
- **The `lan` build profile hard-codes a DHCP address.** See above.
- **No `expo-system-ui`**, so `userInterfaceStyle` in `app.json` does nothing on Android.
  Prebuild says so on every run. The in-app theme switcher is unaffected — it does not go
  through that setting.
