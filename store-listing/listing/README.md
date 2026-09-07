# Play store listing copy

The text for **Play Console → Grow → Store presence → Main store listing**, one file per
language. The app's Play name is set once in the console and is the same in every language.

| File | Play Console language |
|---|---|
| `en.md` | English (United States) – en-US, the default listing |
| `fi.md` | Finnish – fi-FI |
| `sv.md` | Swedish – sv-SE |
| `de.md` | German – de-DE |
| `fr.md` | French – fr-FR |
| `es.md` | Spanish (Spain) – es-ES |
| `tr.md` | Turkish – tr-TR |

## How to use them

1. Open the main store listing in Play Console. The default (en-US) fields take the text from
   `en.md`.
2. Under **Manage translations → Add translations**, add the other six languages, then open each
   one and paste the three fields from the matching file.
3. Copy the contents of each ```` ```text ```` fence exactly, without the fence lines. The full
   description keeps its blank lines and `•` bullets; Play renders them as typed.
4. Bump the `Version:` date in every file you changed, so the console and the repo can be told
   apart later.

## Rules

- **English is the source.** Change `en.md` first, then carry the change into the other six.
  The other files are translations of the English, not independent copy, so a claim that is
  not in `en.md` should not appear anywhere else either.
- **The title never changes.** `GameBuddy: Find Gamers to Play` is live at 30/30 characters in
  every language; renaming resets store recognition.
- **Limits** (Unicode code points, what Play counts): title 30, short description 80, full
  description 4,000. The full description is written to land between 2,400 and 3,200.
- **What may not be said**, in any language: dating framing (the only permitted mention is the
  NOT A DATING APP section, which denies it), "end-to-end" encryption (messages are encrypted at
  rest only), "coming soon" (the app is in closed testing), superlatives, exclamation marks,
  emoji. Steam linking was removed from the app and is not a feature.
- Every full description states the 18+ age limit and ends with the closing lines from the
  website.
- Wording follows the website (`GameBuddy-Web/src/i18n/*.ts`) and the in-app names
  (`GameBuddy-App/src/i18n/dictionaries/*.ts`): a feature is called in the listing what the
  user will see it called in the app.

## Checking

```
node store-listing/check-listing.js
```

or, from `GameBuddy-App/`, `npm run check:listing` (it is also part of `npm run check`). It
verifies all seven files parse, the limits, the forbidden phrases per language and the 18+
line, prints one `lang/field: message` line per problem and exits 1, or prints a table of
lengths when everything passes.
