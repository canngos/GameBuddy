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

## Known gaps

- **Avatar images have nowhere to live.** The backend returns bare filenames like
  `avatar-01.png`; the old Firebase URLs are gone. Until the art is hosted and
  `EXPO_PUBLIC_AVATAR_BASE_URL` points at it, `src/avatars.ts` falls back to coloured
  initials. See the comment in that file.
- **`fcmToken` is the literal string `pending`.** Push is not wired up; the field is
  required at registration, and `PUT /auth/fcm-token` replaces it once it is.
- **Username rules are enforced only on the client** (`src/validation.ts`). The backend
  requires non-blank and unique, nothing more.
