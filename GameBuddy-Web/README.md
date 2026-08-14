# GameBuddy-Web

The public site at **findgamebuddy.com** — a promotional landing page, and the legal pages
both app stores require before you can submit.

```bash
npm install
npm run dev        # http://localhost:4321
npm run build      # static output in dist/
npm run preview    # serve dist/ — measure against this, never against dev
```

## What it is

Astro, static output. The HTML a crawler receives is the finished page — which is exactly
what a React SPA cannot offer and what the ranking goal needs. Measured on the built output:

```
Performance 99 · Accessibility 100 · Best Practices 100 · SEO 100
LCP 1.7s · CLS 0.017 · TBT 0ms · no failing audits
```

**23 pages**: three marketing pages (landing, `/support`, `/delete-account`) in seven
languages, plus `/terms` and `/privacy` in English.

About the JavaScript: the first version shipped none at all. The theme toggle, the language
menu and the scroll reveal need a little, and it comes to about 2 kB across three tiny
inline scripts — no framework, and total blocking time is still 0 ms. Worth knowing before
adding more: the reason the numbers above are what they are is that nothing here hydrates.

## Themes

Light and dark, from the app's own palette, switched by a button in the header.

The **screenshots swap with it** — each phone frame carries a light and a dark capture and
CSS shows one, so a light page never displays a dark app. Both are in the DOM rather than
swapped by script, because fetching on click makes the phone go blank for a beat every time.

The theme is applied by a blocking inline script in `<head>` **before first paint**. That is
not an optimisation: applied any later it produces a flash of the wrong theme on every
navigation, which is the most visible bug a themed static site can have.

## Languages

English, Finnish, Swedish, German, French, Spanish, Turkish. English lives at the bare paths
(`/support`); the rest are prefixed (`/fi/support`).

Each language is one typed dictionary in `src/i18n/`, all typed against `en.ts` — so adding a
key breaks the build until every language has it, rather than rendering `undefined` in
Turkish for a month. Keys are named for meaning rather than position, because these
translations are meant to become the app's own catalogue later.

**`/terms` and `/privacy` are English only**, and say so on the page in the reader's
language. They are binding documents; a mistranslated clause is a document that says
something nobody intended.

## Animations

Sections and cards fade up as they enter the viewport, with a small stagger; the two hero
phones drift slowly in opposite directions.

One mechanism — an IntersectionObserver — and that is a deliberate retreat. The first version
*also* used CSS scroll-driven animation where supported, and it hid every element on the page
without bringing any of them back. The guard that stopped that recurring is worth keeping:
the CSS that hides `.reveal` is scoped to an attribute the inline script sets **only when it
is about to run the observer**. No script means nothing is ever hidden.

The hero is excluded from the reveal entirely and uses a plain load animation — it is above
the fold, and animating the Largest Contentful Paint element on a timer delays the moment the
page counts as painted.

`prefers-reduced-motion` stops all of it, and explicitly forces `.reveal` back to visible.

## Two things it does not own

**The palette comes from the app.** `scripts/generate-tokens-css.mjs` reads
`../GameBuddy-App/src/theme/tokens.js` — the same file the app's own Tailwind config
consumes — and emits `src/styles/tokens.css` before every dev and build. That file is
git-ignored because it is a build artefact. Change a colour in the app and it changes here;
there is no second list of hexes to keep in step.

**The legal text comes from `documentation/legal/`.** `/terms` and `/privacy` import
`TERMS.md` and `PRIVACY.md` directly rather than holding a copy. Those documents were written
from the database schema and are what a lawyer reviews and what the server records a version
string against — a second copy here would be wrong the first time one was amended and the
other was not. `astro.config.mjs` carries the Vite permission that allows reading above the
project root.

`npm run check:legal` runs before every build and **fails while a placeholder is present**.
The operator and controller details were blank until 2026-08-14; a privacy policy that says
`[legal entity name]` fails Google Play review and does not satisfy the GDPR.

## Store links

`src/config.ts` holds `STORES.play` and `STORES.appStore` as empty strings, because there is
no listing yet. The buttons render "Coming soon" while they are empty and become real links
the moment either is filled in. **Do not put a placeholder URL there** — a dead link on the
landing page is worse than an honest waiting state.

## Screenshots

`src/assets/screens/` holds real captures from the Maestro UI suite, taken in dark mode so
they sit properly on the dark page. To refresh them:

```powershell
adb shell cmd uimode night yes
cd ../qa/maestro; .\run.ps1 -ReleaseBuild -Flow 02      # deck
.\run.ps1 -ReleaseBuild -Flow 03 -SkipProvision          # chat
.\run.ps1 -ReleaseBuild -Flow 04 -SkipProvision          # market
# copy from ~/.maestro/tests/<newest>/**/takeScreenshot/artifacts/
adb shell cmd uimode night no
```

Real screens rather than mockups, so the page cannot advertise a UI that does not exist.

## Deploying to Cloudflare Pages

Free, and the account already exists because R2 serves the app's images.

1. **Pages → Create → Connect to Git**, pick this repository.
2. Build settings:
   - Framework preset: **Astro**
   - Build command: `npm run build`
   - Output directory: `dist`
   - **Root directory: `GameBuddy-Web`** — this is a monorepo, and the default of `/` builds
     nothing and reports success.
3. Node version: set `NODE_VERSION` to `22` or newer in the environment variables.
4. **Custom domains**: add `findgamebuddy.com` and `www.findgamebuddy.com`, then add a
   redirect rule sending `www` to the apex so only one URL is indexed.

`api.findgamebuddy.com` is **not** part of this — it points at the Hetzner box and must stay
grey-clouded in Cloudflare DNS. `DEPLOY.md` §4 explains why proxying it breaks both
certificate issuance and chat.

### After the first deploy

- Register the site in **Google Search Console** and submit `/sitemap-index.xml`. Publishing
  is not indexing, and this is the step people skip before wondering why nothing appears.
- Set up **Cloudflare Email Routing** for `contact@` and `support@`, then send a real message
  to each and confirm it arrives. Both addresses are printed in the privacy policy and the
  terms; one that bounces is worse than none.
