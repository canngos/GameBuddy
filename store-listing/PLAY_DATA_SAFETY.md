# Play Console — Data safety answers

**Derived from `documentation/legal/PRIVACY.md` version 2026-08-20 and the dependency list in
`GameBuddy-App/package.json`.** Transcribe it into **Play Console → App content → Data safety**.

Two reasons this document exists rather than the form being filled from memory:

1. **Google requires the two to agree.** The Data safety section "must be consistent with the
   disclosures made in the app's privacy policy", and a mismatch is an enforcement action, not a
   correction request. Reconstructing thirty answers from memory a year from now is how they
   drift apart.
2. **The declaration covers SDKs, not just our code.** Google is explicit that it "includes data
   collected and handled through any third-party libraries or SDKs used in their apps", and Play
   Console now runs automated checks against the uploaded binary — a data type the SDKs touch
   and the form does not declare gets flagged before a human ever looks at it.
   `GameBuddy-App/scripts/check-sdk-inventory.mjs` fails the build when a dependency appears
   that section 5 below does not account for.

---

## 1. The global answers

| Question | Answer | Why |
|---|---|---|
| Is all of the user data collected by your app encrypted in transit? | **Yes** | TLS everywhere, no plaintext route in — `PRIVACY.md` §7, `deploy/Caddyfile` |
| Do you provide a way for users to request that their data is deleted? | **Yes** | In-app **Settings → Delete account**, and `https://findgamebuddy.com/delete-account` without installing anything — `PRIVACY.md` §10, `TERMS.md` §8 |
| Does your app use an advertising ID? | **Yes** | `react-native-google-mobile-ads` declares `com.google.android.gms.permission.AD_ID`. Only read when a user consents and watches a rewarded advert — `PRIVACY.md` §4 |

## 2. What is collected

"Collected" means transmitted off the device, by our code or by any SDK. "Required" means the
app does not function without it; "Optional" covers anything a user can decline and still use
GameBuddy.

| Play category → data type | Collected | Shared | Purposes | Required / Optional | What produces it | Policy |
|---|---|---|---|---|---|---|
| Personal info → Name | Yes | No | App functionality | Required | Username at registration | §1 |
| Personal info → Email address | Yes | No | App functionality, Account management, Developer communications | Required | Registration, verification codes | §1 |
| Personal info → User IDs | Yes | **Yes** | App functionality, Advertising or marketing | Required | Account id; sent to Google for rewarded-advert callbacks | §4 |
| Personal info → Other info | Yes | No | App functionality, Personalization | Required (date of birth, country) / Optional (gender) | Date of birth and country are mandatory at onboarding; gender is not | §1 |
| Photos and videos → Photos | Yes | No | App functionality | **Optional** | Profile photograph; without one the app falls back to initials | §1, §2 |
| Messages → Other in-app messages | Yes | No | App functionality | Required | Chat and lobby messages. **Must be declared**: they are encrypted at rest but *not* end-to-end, so Google's end-to-end exemption does not apply | §3 |
| Financial info → Purchase history | Yes | No | App functionality | Optional | RevenueCat entitlements; only if something is bought | §4 |
| App activity → App interactions | Yes | No | App functionality, Personalization | Required | Likes, passes, matches, impressions — what the recommender ranks on | §1 |
| App activity → Other user-generated content | Yes | No | App functionality | Optional | Profile keywords and text, lobby names and descriptions | §1 |
| App info and performance → Crash logs | Yes | No | Analytics, App functionality | Required | Crashlytics, enabled at build time with no in-app opt-out | §4 |
| App info and performance → Diagnostics | Yes | No | Analytics, App functionality | Required | Crashlytics device and OS detail | §4 |
| Device or other IDs → Device or other IDs | Yes | **Yes** | Advertising or marketing, App functionality | Optional | Advertising ID (only on consent, only for a rewarded advert) and the FCM push token (only with notification permission) | §1, §4 |

## 3. What is *shared*, and why almost nothing is

Google's definition of sharing excludes transfer to "an entity that processes user data on
behalf of the developer and based on the developer's instructions". Every provider in
`PRIVACY.md` §5 is on our instructions and for no purpose of their own, so they are **collected,
not shared**:

Hetzner (the server), Cloudflare R2 (photographs and backups), Firebase Cloud Messaging (push),
Brevo (verification email), Expo (app updates), **Crashlytics** and **RevenueCat**.

**AdMob is the exception, and is declared as sharing.** Personalised advertising is Google's own
purpose, not processing on our instructions, and what reaches it — the advertising ID, the IP
address, and the account id used for the reward callback — leaves under Google's own terms. So
two rows carry Shared = Yes: *User IDs* and *Device or other IDs*.

That is a deliberately conservative reading. If the rewarded advert is ever removed, both rows
become Shared = No and this section is why.

## 4. What we answer "No" to

Recording the negatives matters as much as the positives — an automated check that finds a data
type the binary touches and the form denies is the failure mode this document exists to prevent.

| Category | Answer | Why |
|---|---|---|
| **Location** (approximate and precise) | No | Country is chosen by the user in their profile. Nothing derives location from an IP address: no GeoIP library, no `CF-IPCountry` header, nothing in the backend reads one |
| Contacts, Calendar, Files and docs, Audio, Health and fitness, Web browsing history, Installed apps | No | Never accessed. No permission is requested for any of them |
| Financial info → payment info, credit score | No | Google Play and Apple process payments. We never see a card number, and neither does RevenueCat |
| Personal info → sexual orientation | No | The app collects gender but has no "interested in" field and no gender filter — the deck filters are game, country, platform and online-now. Declaring an orientation the app does not collect would be as wrong as omitting one it does |
| Personal info → phone number, address, race, political or religious beliefs | No | Never asked for |

## 5. SDK inventory

Every dependency that can move data off the device, and the rows above it drives. This is the
list `check-sdk-inventory.mjs` enforces; adding an SDK means updating both.

| Package | What leaves the device | Rows it drives |
|---|---|---|
| `react-native-google-mobile-ads` | Advertising ID, IP, account id, ad-view diagnostics | User IDs (shared), Device or other IDs (shared) |
| `@react-native-firebase/crashlytics` | Stack traces, device and OS version, account id | Crash logs, Diagnostics |
| `react-native-purchases` (RevenueCat) | Account id, entitlements, purchase state | Purchase history |
| `expo-notifications` | FCM device token — straight to Firebase, no Expo push service | Device or other IDs |
| `expo-updates` | IP address and app/runtime version, to `u.expo.dev` | **None.** IP alone is not a declarable data type unless location is derived from it, and nothing here does. Disclosed in `PRIVACY.md` §5 regardless |
| `expo-image-picker`, `expo-image-manipulator`, `expo-file-system` | Nothing on their own — they hand the chosen image to our upload | Photos |
| `expo-device`, `expo-constants` | Nothing. Read locally, and on-device-only processing is exempt from disclosure | None |
| Everything else in `package.json` | Nothing. UI, navigation, state, styling, fonts | None |

Server-side processors carry no SDK in the binary and so drive no row here, but they are still
recipients and are named in `PRIVACY.md` §5: Brevo, Hetzner, Cloudflare R2.

## 6. Redo this when

- **A dependency is added to `GameBuddy-App/package.json`.** `npm run check` fails until the new
  package is classified in section 5.
- **`PRIVACY.md` changes** in §1, §4 or §5.
- **The rewarded advert changes or is removed** — see section 3.
- **Play's own categories change.** Google revises the form; the answers above are pinned to the
  version current on 2026-08-20.
