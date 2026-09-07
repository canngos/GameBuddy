# GameBuddy — Privacy Policy

**Version 2026-08-20.**

GameBuddy is operated from Finland and is subject to the GDPR.

This policy covers the app and the website at findgamebuddy.com. **The website sets no
cookies, runs no analytics, and loads nothing from anybody else's server** — the only thing it
keeps in your browser is whether you chose the light or the dark theme.

---

## 1. What we collect, and why

**To create your account**
| Data | Why |
|---|---|
| Email address | Signing in, verification codes, and reaching you about your account |
| Password | Stored only as a bcrypt hash; we cannot read it |
| Username | How other people see you |
| Date of birth | Confirming you are 18 or over. We also store the age derived from it |

**To match you with people**
| Data | Why |
|---|---|
| Country and gender | Shown on your profile; used by the recommender |
| Favourite games and keywords | The basis of who you are matched with |
| Profile photograph | Shown to other account holders |
| Who you liked, passed and matched with | Making the feed work, and not showing you the same person twice |
| Which profiles you were shown | Improving the recommendations. Kept for 90 days, then deleted |

**To make the app work**
| Data | Why |
|---|---|
| Chat messages | Encrypted at rest. See section 3 |
| Device notification token | Sending push notifications. You can turn these off per category |
| Last active time | Showing whether somebody is online, and re-engagement notifications |
| Purchase records | Delivering what you bought and honouring refunds |
| Server logs | Diagnosing faults and investigating abuse. See section 9 |
| Crash reports | Finding out why the app broke. Carries your account id — see section 4 |
| Advertising identifier | Only if you choose to watch an advert for coins — see section 4 |

**If you sign in with Google or Discord** — entirely optional; an email address and a
password work just as well

| Data | Why |
|---|---|
| Your account id at Google or Discord | Recognising you the next time you sign in. It is the only thing we match on, so changing the email address on that account does not lock you out |
| Your email address there | Finding your existing GameBuddy account if you already had one, and reaching you afterwards. We only accept it if the provider has verified it |
| Your display name there | Suggesting a username. You can change it |

Signing in this way sends you to Google or Discord, so they learn that you have a GameBuddy
account and when you used it. We never see your password to them, and we keep no access
token afterwards — so we cannot read anything else about you there or act on your behalf. An
account made this way has no password until you set one, which you can do at any time in
Settings; you can also remove a sign-in method there, as long as one way in remains.

**If you link a Discord account** — entirely optional, and nothing here exists unless
you do

| Data | Why |
|---|---|
| Your Discord account id | Proving the account is yours, and making sure nobody else can claim it |
| Your display name on that service | Shown on your profile, so people know where to find you |
| Who can see it | Your choice: everyone, or only people you have matched with. It starts on the private setting |

We do not receive your Discord password — you sign in to them, not to us — and we
keep no access token afterwards, so we cannot read anything else about you there or act on
your behalf. Unlinking deletes all of the above immediately.

We do **not** collect your location, your contacts, or your browsing outside GameBuddy.

Three third-party services receive data about you as part of features you use: an advert
network, a crash reporter and the service that processes purchases. Section 4 says which, what
each one gets, and when. One more — Discord — is involved only if you choose to link
that account; see section 5.

Separately, a handful of providers hold data *on our behalf* so that the service can run at
all — the server it runs on, the storage that holds your photograph, the network that carries
push notifications, the company that sends our email, and the service that ships app updates.
They are named in section 5. **We use no analytics or tracking service of any kind**, and
nothing beyond what sections 4 and 5 name receives anything.

## 2. Your photograph

Your profile photograph is screened by an automated classifier before it becomes visible to
anyone. The image is sent to a service we run ourselves — it is not sent to a third party —
and the classifier's score is stored alongside it. If the classifier is unsure, a moderator
looks at the image. Deleting your account deletes the image from storage.

## 3. Your messages

Chat messages are encrypted before they are written to the database, so a stolen copy of
the database does not yield readable conversations.

This is **not** end-to-end encryption, and we want to be straightforward about what that
means: we hold the key, so we are technically able to read messages. We do so in one
circumstance — when a message has been reported, so that a moderator can judge the report.
There is no other route by which conversations are read, and messages are never used for
advertising or training.

## 4. Adverts, crash reports and purchases

Three companies receive data about you, and they are worth describing individually rather
than hiding behind the phrase "our partners".

**Google AdMob — adverts.** GameBuddy has one advert: an optional video you can watch in
exchange for coins. Nothing else in the app shows advertising. When you watch one, Google
receives your device's advertising identifier, your IP address, whether you watched to the
end, and technical diagnostics — and your account id, because Google's server is what tells
ours to pay you, and without an id there is nobody to pay. You can reset or delete the
advertising identifier in your Android settings.

These are personalised adverts, which in the EEA, the UK and Switzerland you have to agree
to first. So the app asks, on the first launch, with a form provided by Google. Say no and
the app still works — you simply cannot trade a video for coins. You can change the answer
whenever you like, in **Settings → Advert privacy**, and withdrawing is as easy as giving it.

Be aware of what agreeing covers, because "Google" is doing a lot of work in that sentence.
Personalised advertising runs through an industry framework, and consenting admits not only
Google but the advertising partners it works with — currently around two hundred of them.
The form names every one before you decide, under **List of partners**, and refusing is what
keeps that list at zero.

Being precise about the order, because it is the part people assume: the advert code starts
when the app starts, in order to ask that question. It does not wait until you tap the
button. Outside the regions where consent is required, no form appears and nothing is asked.

**Google Crashlytics — crash reports.** When the app crashes, a report goes to Google:
what the app was doing, the device and operating-system version, and your account id. The
id is attached deliberately, because "this happens to everybody once" and "this happens to
the same person every time" are different bugs. It carries no message content and no
profile data.

**RevenueCat — purchases.** Handles subscriptions and coin purchases. It receives your
account id when you sign in — not only when you buy something — along with what you have
bought and whether a subscription is active. It never sees your card details; neither do we.

None of them — nor any advertising partner — receives your messages, your photograph, your
games and keywords, or who you liked. What the advertising side gets is the identifier and
the technical detail described above, and nothing from inside your profile.

## 5. Who your data is shared with

- **Other account holders** see your username, photograph, age, country, gender, games and
  keywords, and anything you write where other people can read it.
- **Google AdMob, Google Crashlytics and RevenueCat**, as described in section 4.
- **Google**, but only if you sign in with it. It learns that you have a GameBuddy account
  and when you signed in. What comes back to us is your account id there, your email address
  and your display name.
- **Discord**, but only if you link it or sign in with it. Linking sends you to their sign-in page, so
  they learn that you have a GameBuddy account and when you linked it. We send them nothing
  about you beyond the request itself, and what comes back is your account id and display
  name. Their handling of your data is theirs, under their own privacy policy.
- **Apple and Google** process purchases. We never see your payment details.
- **Authorities**, where the law requires it, or where content involves the sexual
  exploitation of children. See our [Child Safety Standards](https://findgamebuddy.com/child-safety).
- **Our infrastructure providers**, who hold data on our behalf, under contract, and for no
  purpose of their own:

| Provider | Where | What it holds |
|---|---|---|
| Hetzner | Germany | The server, the application and the database — so, in principle, everything |
| Cloudflare R2 | EU storage region | Profile photographs, and the nightly database backups |
| Google Firebase Cloud Messaging | Google infrastructure | Your device notification token and the text of each notification sent to you |
| Brevo | France | Your email address, and the verification and password-reset emails we send you |
| Expo | United States | Nothing you give us. When the app checks for an update it reveals your IP address and the app and device version |

We do not sell your data, and we do not share it for anybody else's advertising — including
under the meanings those words are given by US state privacy laws. There is nobody to sell it
to who would not be a worse custodian of it than we are.

## 6. Sending data outside the EEA

Most of what we hold stays in the EU: the server is in Germany, the photographs and backups
are in an EU storage region, and our email provider is French.

Some of it does not. Google (AdMob, Crashlytics, Firebase), RevenueCat and Expo are United
States companies and process data there and elsewhere. Those transfers rest on the European
Commission's standard contractual clauses, on the provider's certification under the EU–US
Data Privacy Framework where it holds one, or on both. Write to the address in section 13 and
we will tell you which mechanism covers which provider, and give you a copy of the clauses.

## 7. How we protect it

- Everything between the app and our server travels over TLS. There is no unencrypted route in.
- Passwords are stored as bcrypt hashes. We cannot read them, and neither can anybody who
  steals the database.
- Chat messages are encrypted with AES-GCM before they are stored, under a key held outside
  the database.
- The database is not reachable from the internet; it accepts connections only from the
  application, behind a firewall enforced outside the machine.
- One person — the controller named in section 13 — has administrative access. There is no
  wider staff to extend it to.

If a breach occurs that is likely to put you at risk, we will report it to the Finnish Data
Protection Ombudsman within 72 hours of becoming aware of it, and tell you directly where the
GDPR requires it.

None of this makes a system unbreakable, and we would rather describe what is actually in
place than claim "industry-standard security", which means nothing.

## 8. Automated decisions

Three things about GameBuddy are decided by software rather than by a person, and you should
know which:

- **Who appears in your feed.** A recommender ranks other account holders by how similar their
  games, keywords and profile are to yours, together with how popular a profile is. It is a
  suggestion about who to show first — nothing about it decides anything else about you.
- **Whether your photograph is published.** A classifier screens it first, as described in
  section 2. If it is unsure, a person decides. If your photograph was refused and you think it
  was wrong, write to us and a person will look at it.
- **Whether a message is delivered.** Text is filtered for slurs and sexual abuse, and refused
  outright when it matches.

None of these produces a legal effect or anything comparable to one, and no account is
suspended or terminated by software: those decisions are made by a person, who will tell you
why. You can always ask for a human to review an automated refusal, using the address in
section 13.

## 9. How long we keep it

- **Your account**: until you delete it.
- **After deletion**: your profile, photograph and personal details are removed
  immediately. Your messages inside other people's conversations are anonymised rather than
  erased — the other participant keeps a record of a conversation they took part in, and it
  is no longer attributable to you.
- **Impression history**: 90 days.
- **Backups**: the database is backed up nightly, and each backup is destroyed seven days
  after it is taken. So an account deleted today is gone from the live service at once, and
  out of the last backup within a week.
- **Server logs**: 7 days. They record IP addresses, request paths and errors; they do not
  record message content. Three separate logs are involved — the web server's access log, the
  application's own log, and the copy the container runtime keeps — and all three are pruned on
  the same seven-day rule, plus a size cap that can only remove things sooner.
- **Records we must keep by law** (purchases, and material relating to a report we acted
  on): as long as the law requires.

## 10. Your rights

Under the GDPR you can ask us to give you a copy of your data, correct it, delete it,
restrict what we do with it, or object to it — and you can ask for it in a portable form.
Most of this you can do yourself in the app; for the rest, write to the address in section
13 and we will answer within 30 days.

You can delete your account entirely from **Settings → Delete account** in the app, or from
[findgamebuddy.com/delete-account](https://findgamebuddy.com/delete-account) without opening
the app at all.

You can complain to the Finnish Data Protection Ombudsman (*Tietosuojavaltuutetun
toimisto*) if you think we have handled your data wrongly.

## 11. Legal basis

- **Performance of a contract** — running the service you signed up for.
- **Legitimate interests** — keeping the service safe, preventing abuse, and improving
  recommendations.
- **Legal obligation** — retaining what the law requires us to retain.
- **Consent** — push notifications and personalised adverts. Both are asked for, and both
  can be withdrawn at any time in Settings.

Giving us the data in section 1 is not a statutory requirement, but it is what the account
needs to exist: without an email address, a date of birth and a username there is no account,
and without the profile there is nobody to match you with.

## 12. Children

GameBuddy is for people aged 18 and over. We do not knowingly collect data from anybody
under 18. If we discover an account belongs to a minor, we close it and delete its data. If
you believe a child is using GameBuddy, write to the address below and we will act within
24 hours. Our [Child Safety Standards](https://findgamebuddy.com/child-safety) set out what
else we do about this, and who to contact.

## 13. Who we are

**Controller:** Can Baturlar, Finland
**Data protection contact:** contact@findgamebuddy.com

GameBuddy is run by one person, not a company. There is no data protection officer, because
the scale of processing does not require one under Article 37 — the address above reaches the
controller directly.

## 14. Changes

If we change how we handle your data we will update this policy and tell you in the app
before the change takes effect.
