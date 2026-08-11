# GameBuddy

A matching app for gamers: you swipe through other players, and the ones you both accept
become matches you can chat with. Ranking is done by a Python recommender trained on
profile similarity.

Two things run: a Spring Boot modular monolith (`GameBuddy-backend`) and a FastAPI model
service (`GameBuddy-Model`). One Postgres database behind them.

---

## Running it locally

```bash
cp .env.example .env
docker compose up --build
```

That is the whole setup. It brings up Postgres, the model, the backend and the log stack,
creates the schema, seeds the game and keyword catalogue, and waits for each service to
report healthy before starting the next one.

| Service       | URL                                         |
| ------------- | ------------------------------------------- |
| Backend       | http://localhost:8080                       |
| Swagger       | http://localhost:8080/swagger-ui/index.html |
| Health        | http://localhost:8080/actuator/health       |
| Model         | http://localhost:8000/health                |
| Postgres      | localhost:5432, user/db `gamebuddy`         |
| **Kibana**    | **http://localhost:5601/app/discover**      |
| Elasticsearch | http://localhost:9200                       |

Import `documentation/GameBuddy.postman_collection.json` with the `GameBuddy-Local`
environment for the full API surface — 86 requests, and login stores the token for the
rest of them automatically.

### Signing up locally

There is no mail server, so verification codes are **printed to the log** instead of sent.
Register, then read the code out of the backend log:

```bash
curl -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"Str0ng!Passw0rd","acceptedTerms":true}'

docker compose logs backend | grep "verification code"
```

Then `POST /auth/verify` with that code, which returns a token. `POST /auth/login` will
still refuse with *"Registration not finished"* until onboarding is completed via
`POST /auth/username` and `POST /auth/details` — that is the intended flow, not a fault.

This is controlled by `MAIL_MODE=log`, set in `docker-compose.yml`. It exists because
registration deliberately rolls its whole transaction back when mail fails, so that a mail
outage cannot leave an account holding an address nobody has proved they own. The
consequence is that without SMTP, and without this switch, no account can be created at
all.

**`MAIL_MODE=log` must never be set in production.** Anyone who can read the logs could
verify any address they liked. The default is `smtp`, and only the exact word `log` changes
it — `logging`, `true` and the like leave real sending in place.

### Common tasks

```bash
docker compose logs -f backend        # follow the backend
docker compose down                   # stop, keep data
docker compose down -v                # stop and wipe the database
docker compose up -d --build backend  # rebuild just the backend after a code change
docker compose exec postgres psql -U gamebuddy -d gamebuddy
```

Running the backend on the host instead of in its container is fine — set the same
variables and point `SPRING_DATASOURCE_URL` at `localhost:5432`. Note `INTERNAL_API_KEY`
must match what the model container was started with.

### Tests

```bash
./gradlew build                                   # 647 tests (579 backend, 68 common)
cd GameBuddy-Model && python -m pytest            # 50 model tests
```

---

## How the pieces fit

```
GameBuddy-App  ──►  backend (Spring Boot)  ──►  Postgres
 (React Native)          │
                         └──►  model (FastAPI)   ranking only, no database access
```

The backend is a **modular monolith**: one deployable, eight modules
(`shared`, `auth`, `profile`, `community`, `match`, `notif`, `billing`, `config`) whose
boundaries are enforced by ArchUnit rather than by convention. It was five separate
services; they are still on disk as `GameBuddy-*-service` directories, each its own GitHub
repository, but `settings.gradle` no longer includes them and nothing should be added to
them.

The model is the only out-of-process dependency, because it is a different runtime.
Everything that used to be an HTTP call between services is now a method call.

---

## Logs

**http://localhost:5601/app/discover** — Kibana, with the `GameBuddy logs` data view
already created. Everything the backend logs is searchable there within a second or two.

```
backend ──writes──► /app/logs/gamebuddy.json ──tails──► filebeat ──► elasticsearch ──► kibana
         (ECS JSON)      (shared volume)
```

The backend logs to two places at once. The console keeps Spring's human-readable format,
so `docker compose logs -f backend` is unchanged. The file is one JSON object per line in
[Elastic Common Schema](https://www.elastic.co/guide/en/ecs/current/index.html) — the field
names Elasticsearch and Kibana already understand — which is what Filebeat ships.

Nothing in the application talks to Elasticsearch. It writes a file and is finished, and
Filebeat does the rest from its own container. That is deliberate:

- Elasticsearch being down, slow or restarting cannot add latency to a request, and cannot
  fail one. A logging call that can block is a logging call that will eventually take the
  site down.
- Logs written while the cluster was down are still shipped when it comes back, because
  Filebeat resumes from where it left off rather than from wherever the application
  happens to be now.
- `docker compose up backend` still starts only Postgres, the model and the backend. The
  log stack is genuinely optional.

Everything lands in one Elasticsearch data stream, `gamebuddy-logs`, and the field types
come out right without a hand-written mapping: `@timestamp` is a `date`,
`event.duration_ms` and `http.response.status_code` are `long`s, `log.level` and `trace.id`
are `keyword`s.

### Finding things

Every request gets an id, returned to the caller in the `X-Request-Id` header and attached
to **every** line that request produces — the access line, a warning four layers down, and
the stack trace if it fails. So a bug report that quotes an id is one query:

| I want                       | Kibana query                                       |
| ---------------------------- | -------------------------------------------------- |
| One request, start to finish | `trace.id: "0f6c…"`                                 |
| Everything that failed       | `log.level: ERROR`                                  |
| One user's session           | `user.id: "…"`                                      |
| Slow requests                | `event.duration_ms > 500`                           |
| Failed logins                | `message: "Failed login attempt*"`                  |
| One endpoint                 | `url.path: "/match/recommendations"`                |

`http.response.status_code` and `event.duration_ms` are indexed as numbers, not strings, so
ranges and averages work — which is the point of shipping structured logs rather than
grepping text.

Or without Kibana at all:

```bash
curl 'localhost:9200/gamebuddy-logs/_search?q=log.level:ERROR&size=5&pretty'
curl 'localhost:9200/gamebuddy-logs/_count'         # is anything arriving?
docker compose logs filebeat                        # if it is not, why not
```

### Knobs

| Variable           | Effect                                                          |
| ------------------ | --------------------------------------------------------------- |
| `LOG_LEVEL`        | Application log level, `com.gamebuddy` only. `DEBUG` for detail  |
| `ACCESS_LOG_LEVEL` | `WARN` keeps only failed requests; `OFF` silences the access log |
| `ENVIRONMENT`      | Tags every document, so one cluster can hold several deployments |
| `KIBANA_PORT`      | Default 5601                                                     |

Health checks are logged at DEBUG rather than INFO on purpose — polled every ten seconds
forever, they would otherwise be most of the index.

### Not production-ready as configured

Elasticsearch runs here with `xpack.security.enabled=false`: no authentication, no TLS.
That is fine for a cluster reachable only from this compose network and from localhost, and
the honest alternative for a local stack is a certificate dance that ends with everyone
disabling verification anyway. A deployed cluster needs security enabled, real credentials
in Filebeat's output, and `setup.ilm.enabled: true` so the data stream rolls over and old
indices are deleted rather than one backing index growing forever.

---

## The database

The schema is **owned by SQL**, not generated by Hibernate at startup. Both environments run
`DDL_AUTO=validate`, so the application refuses to start if the entities and the database
disagree — rather than Hibernate quietly reshaping the database, which is convenient right
up until local has silently diverged from production.

| File                              | Purpose                                        |
| --------------------------------- | ---------------------------------------------- |
| `db/schema-baseline.sql`          | Creates everything from nothing. Apply first.  |
| `db/upgrade-2026-4-monolith.sql`  | Schema consolidation, chat tables.             |
| `db/upgrade-2026-5-outbox-and-declines.sql` | Notification outbox, decline expiry. |
| `db/upgrade-2026-6-user-avatars.sql` | Uploaded avatars and their review state.  |
| `db/upgrade-2026-7-cosmetics.sql` | Frames and banners; paid avatars dropped.    |
| `db/upgrade-2026-8-badges.sql`    | Badges replace achievements.                   |
| `db/upgrade-2026-9-profile-reports.sql` | A gamer's profile can be reported.       |
| `db/upgrade-2026-10-notifications.sql` | Deep links, and last-active tracking.  |
| `db/upgrade-2026-11-notification-preferences.sql` | Per-category opt-outs. |
| `db/upgrade-2026-12-recommender-staleness.sql` | Flags profiles the model predates. |
| `db/upgrade-2026-13-avatar-review.sql` | Classifier score and upload time on avatars. |
| `db/upgrade-2026-14-username-and-device-token.sql` | Unique device tokens, case-insensitive usernames. |
| `db/upgrade-2026-15-adults-only.sql` | Date of birth and recorded terms acceptance. |
| `db/upgrade-2026-16-keyword-descriptions.sql` | Real explanations for the keyword catalogue. |
| `db/upgrade-2026-17-row-timestamps.sql` | created_at everywhere, updated_at where rows change. |
| `db/upgrade-2026-18-membership-cosmetics.sql` | The Gold frame and banner come with membership. |
| `db/seed-local.sql`               | Games, keywords, avatars, cosmetics.           |

They live in `GameBuddy-backend/src/main/resources/db/`. On a fresh database apply the
baseline and then any upgrade files newer than it, in filename order. Compose does this
automatically, but **only on an empty volume** — after changing the schema, either write an
upgrade script and apply it by hand, or `docker compose down -v` and start clean.

The seed is reference data only. No gamers: accounts should come from the signup flow,
because that is the part worth exercising.

### Populating the swipe deck locally

The deck needs other people to show, and the signup flow will not give you a thousand of
them. `GameBuddy-Model/tools/seed_local_gamers.py` writes swipeable fixtures:

```bash
cd GameBuddy-Model && python tools/seed_local_gamers.py
docker compose exec -T postgres psql -U gamebuddy -d gamebuddy < tools/local-gamers.sql
```

Every account it creates is marked, and removable in one statement:

```sql
DELETE FROM gamebuddy.gamer WHERE email LIKE '%@bot.gamebuddy.invalid';
```

`.invalid` is reserved by RFC 2606 so those addresses can never be real, and the stored
password is not a valid bcrypt hash so none of them can be signed into. **They are
fixtures, not users** — never run this against production. A fake profile a real person
can swipe on and message is the thing matching apps get investigated for.

It seeds 1200 by default, which is more than it sounds like it needs: the recommender
ranks against the population it was *trained* on, and only ids that also exist in this
database survive. A small seed intersects the model's top-ranked candidates barely at
all, and the deck looks broken when it is merely under-populated.

## Adults only

GameBuddy is an 18+ service, and that is a decision about what the app is rather than a
rating chosen to please a store. The product shows photographs of strangers, asks for a
yes or no, and opens a private conversation on a mutual yes. Whatever the intent — and the
intent is finding people to play with — that is the mechanic every store and regulator
reads as dating, and mixing adults with children inside it is indefensible however
carefully the pools are separated.

The previous minimum was 12. It sat below the age of digital consent under GDPR Article 8
in Finland (13) and below COPPA's threshold in the United States, and the separation
between minors and adults rested on a number the account holder could retype in Settings
at any moment.

What enforces it:

| Where | What happens |
| --- | --- |
| `AgePolicy` | The single definition of eligible. Computes the age from a date of birth; refuses under 18, future dates and implausible ones |
| Onboarding | Asks for a date of birth, never an age. The client cannot assert the number |
| `PUT /auth/change/age` | Re-validates, and logs every change. The field an abuser would edit is the field that leaves a trail |
| `AgeBand` | Kept. With an 18+ floor it is no longer the primary control, but it still catches an account whose age is missing or wrong, and it costs nothing |
| `BirthdayJob` | Keeps the cached `age` column true to the date it came from |

`gamer.age` is a cache of `gamer.birth_date`, not a separate fact. The recommendation feed
filters on it in native SQL and the model reads it as a feature, which is why it stays a
column; nothing but `AgePolicy` and `BirthdayJob` may write it.

**The local seed contains under-18 profiles.** They are synthetic and predate this change;
`upgrade-2026-15` deliberately reports them rather than deleting them, because a migration
that removes accounts is not something to run on autopilot. Production must start clean.

## What the stores require, and where it lives

| Requirement | Where |
| --- | --- |
| Terms containing no tolerance for objectionable content or abusive users | `documentation/legal/TERMS.md`, section 2 |
| Active acceptance, recorded | `TermsPolicy`, `gamer.terms_accepted_at` / `terms_version`. Registration returns 169 without it |
| In-app account deletion | Settings → Delete account. Requires the password; no email, no website |
| Reports acted on within 24 hours | Promised in the terms; measured by `ReportDto.overdue` and `analytics.oldestOpenReportHours`, both surfaced in the console |
| An admin dashboard | The console — see [The moderator console](#the-moderator-console) |
| Text filtering | `TextModerationService`. Masks profanity, refuses slurs, strips contact details from public surfaces only |
| Image filtering | `ImageModerationService` and the NSFW classifier — see [Avatar review](#avatar-review) |
| An age gate at registration | Date of birth at onboarding, plus a 18-or-over confirmation the account holder has to tick |
| A privacy policy URL, in the app and on the listing | `documentation/legal/PRIVACY.md`; linked from Settings → About via `EXPO_PUBLIC_PRIVACY_URL` |

Both documents carry placeholder operator details in their final section and need a lawyer
before submission. The URLs must serve them before the first store review — until the site
exists, the links in the app resolve to nothing, which is a launch blocker rather than a
bug.

### Why the text filter treats chat and posts differently

`TextSurface.PRIVATE` masks profanity and refuses slurs. It does **not** strip contact
details, because two matched adults swapping Discord tags is this app working — that is
the point of a service for finding people to play with, and redacting it would be sabotage
dressed as safety.

`TextSurface.PUBLIC` — posts, comments, community names, usernames — also removes email
addresses, links and phone numbers, which are a different thing when broadcast to
strangers.

The word list is a floor, not a solution. It does not understand context, it will mask a
word somebody used innocently, and anyone determined will get a slur through;
`TextNormaliser` only raises the cost of the obvious evasions. What protects people is the
report button and a moderator who answers it.

### Regenerating the baseline schema

Needed after changing an entity, and **it is not optional**: the baseline once went sixteen
columns behind the entities across five separate features, and nothing complained until a
fresh database refused to start. Nothing in the build checks this, so it has to be done
with the change that causes it.

**Regenerate from the migrations, never from the entities.** Build a scratch database by
replaying the current baseline plus every upgrade, and dump that:

```bash
docker compose exec postgres psql -U gamebuddy -d postgres -c "CREATE DATABASE scratch;"

docker compose exec -T postgres psql -U gamebuddy -d scratch \
  -f /dev/stdin < GameBuddy-backend/src/main/resources/db/schema-baseline.sql

# Numeric order, not filename order — `ls` puts upgrade-10 before upgrade-4.
for n in 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
  f=$(ls GameBuddy-backend/src/main/resources/db/upgrade-2026-$n-*.sql)
  docker compose exec -T postgres psql -U gamebuddy -d scratch -f /dev/stdin < "$f"
done

docker compose exec postgres pg_dump -U gamebuddy -d scratch \
  --schema-only --no-owner --no-privileges --schema=gamebuddy
```

Then delete the `\restrict` and `\unrestrict` lines: they are psql session meta-commands
from newer `pg_dump` versions, and they fail with "wrong key" when the file is piped to
`psql` rather than replayed exactly as written.

Prove it before committing — none of this fails loudly when it is wrong. Load the result
into an empty database, run `seed-local.sql` against it, and boot with
`DDL_AUTO=validate`. A clean `Started GameBuddyApplication` is the only evidence that
counts.

### Two ways to get this badly wrong

**Do not generate it with `ddl-auto=create`.** Pointing Hibernate at an empty database
produces the same tables and columns, so a diff of table names looks perfect — and the
result carries **no column DEFAULTs at all**, because Hibernate supplies those values from
Java instead. All 50 defaults in the baseline came from migrations, and `seed-local.sql`
and `tools/local-gamers.sql` both depend on them. Regenerating this way drops every one,
and the first symptom is a seed failing on a NOT NULL column several steps later.

**`DB_URL` is not a variable this project reads.** `docker-compose.yml` sets
`SPRING_DATASOURCE_URL`, and that is what `application.yml` binds. Overriding `DB_URL` on a
`docker compose run` changes nothing, the container quietly uses the *main* database from
compose, and with `DDL_AUTO=create` that drops and recreates every table in it. Always
override `SPRING_DATASOURCE_URL`, and never combine an override with `DDL_AUTO=create`
without checking which database actually got it.

Recovering from that: rebuild as above, then `seed-local.sql`, then
`GameBuddy-Model/tools/local-gamers.sql` for the swipeable population. Only the accounts
you registered by hand are unrecoverable.

Then re-apply the one thing the entities cannot express: `idx_outbox_pending` is **partial**
(`WHERE sent_at IS NULL`). The outbox is overwhelmingly delivered rows, and a full index
would mostly index notifications nobody will query again.

### Regenerating the seed

`db/seed-local.sql` is generated from `gamebuddy_model/catalogue.py`. The names have to
match: the recommender was trained on those exact games and keywords, and a database seeded
with a different set produces profiles whose features the model has never seen — the feed
degrades to noise in a way that looks like a bad model rather than bad fixtures.

---

## Configuration

Everything is environment variables. Constraints that will stop startup if you get them
wrong:

| Variable              | Notes                                                                    |
| --------------------- | ------------------------------------------------------------------------ |
| `JWT_SECRET`          | Must decode to ≥32 bytes for HS256. `openssl rand -base64 32`             |
| `CHAT_ENCRYPTION_KEY` | Must decode to exactly 16, 24 or 32 bytes. `openssl rand -base64 32`      |
| `INTERNAL_API_KEY`    | **Same value in backend and model.** `openssl rand -hex 24`               |
| `DB_SCHEMA`           | Also sets the Hikari `schema` property — native queries ignore Hibernate's `default_schema` |
| `DDL_AUTO`            | `validate` everywhere. Never `update` in production                       |
| `MAIL_MODE`           | `smtp` in production. `log` only locally                                  |
| `FIREBASE_ENABLED`    | Startup fails if `true` without a credentials file                        |
| `CORS_ALLOWED_ORIGINS`| Empty by default, refusing every browser. Only a web client needs it       |
| `MODERATOR_EMAIL` / `MODERATOR_PASSWORD` | Creates the one staff account on first start. Empty means no staff account |
| `REVENUECAT_WEBHOOK_TOKEN` | **Startup fails without it.** The only thing that can grant a paid entitlement — see Billing below |
| `AVATAR_PUBLISH_AFTER`| ISO-8601 duration. How long an unreviewed ambiguous avatar waits before publishing |
| `NSFW_APPROVE_THRESHOLD` / `NSFW_REJECT_THRESHOLD` | On the **model** service. The band between them is what needs a human |

A mismatched `INTERNAL_API_KEY` is the one worth remembering, because it does not announce
itself: the model answers 503 and the feed reports "Recommendation service unavailable",
which reads as the model being down rather than a wrong secret.

---

## Billing

**This backend never sees a receipt.** RevenueCat talks to Apple and Google, decides whether
a purchase is real, and posts the outcome to `POST /billing/revenuecat/webhook`. We map that
onto an account and grant the entitlement.

The consequence to understand before changing anything here: **that webhook is the entire
security boundary for billing.** There is no second opinion and no receipt to re-check, so
anybody who can post a convincing body to that URL with the right header can hand themselves
a subscription. Hence:

- `REVENUECAT_WEBHOOK_TOKEN` is required and the application refuses to start without it. An
  unset secret would not fail closed on its own — an empty expected value matches an empty
  header — so the only safe unconfigured state is not running.
- The comparison is constant-time, and a rejection returns a bare 401 with no body.
- There is deliberately **no endpoint the app can call to claim a purchase.** `POST
  /billing/redeem` used to exist and was removed with the verifiers; re-adding one would
  make the paywall decorative.

Set the same value in two places: this variable, and the Authorization field of the webhook
in the RevenueCat dashboard. `openssl rand -base64 32`.

### What the events mean

| Event | Effect |
| ----- | ------ |
| `INITIAL_PURCHASE`, `RENEWAL`, `UNCANCELLATION`, `PRODUCT_CHANGE`, `NON_RENEWING_PURCHASE` | Grant, using the store's own expiry |
| `EXPIRATION` | Revoke now |
| `CANCELLATION` with `cancel_reason: CUSTOMER_SUPPORT` | Refund: revoke now, mark the row `REFUNDED` |
| `CANCELLATION` for any other reason | **Nothing.** Auto-renew is off; they keep what they paid for until `EXPIRATION` |
| `BILLING_ISSUE`, `SUBSCRIPTION_PAUSED` | Nothing. The store is still retrying, and revoking would cut off somebody whose card needs updating |
| Anything else | Logged and ignored |

Two behaviours that look wrong and are not. **Every authorised call answers 200**, including
ones we could not make sense of: RevenueCat retries non-2xx with backoff and eventually
disables a webhook that keeps failing, which would take every other purchase down with it.
And a **replayed transaction grants nothing while still answering 200** — retries are normal,
not exceptional, and the unique constraint on `(platform, store_transaction_id)` is what makes
that safe.

### The app side

`react-native-purchases` (RevenueCat's SDK), installed and wired in
`GameBuddy-App/src/billing/purchases.ts` — the only file that imports it.
`react-native-purchases-ui` is deliberately **not** installed: that package renders
RevenueCat's own paywall templates, and ours is already built and wired into four entry
points.

The SDK is loaded with `require` on first use rather than imported at the top of the file,
and `NativeModules.RNPurchases` is checked before anything else. That check is the load-
bearing part: the package does not throw when its native half is missing, it just leaves
`NativeModules.RNPurchases` undefined, so a try/catch around the import would report success
on a build that cannot purchase. Getting this wrong turns a disabled button into a crash
inside `configure`.

The consequence is useful: **a development build made before this install keeps working.**
Everything runs; only the purchase button is disabled, with "Purchases not available yet"
on it. `storeAvailable()` is what decides.

`EXPO_PUBLIC_REVENUECAT_API_KEY` is the public SDK key. Public by design — it ships inside
the binary and can only read offerings and start purchases. It comes from `eas.json` for
EAS builds and from `GameBuddy-App/.env` for local work (gitignored, so each machine needs
its own).

It is set on the `lan` and `preview` profiles and **deliberately not on `production`**: the
only key this project has is a Test Store key, and a shipped build configured against a
store that takes no money is worse than one that plainly cannot sell. Add the `goog_`/`appl_`
key to `production` when Google Play verification completes.

### `app_user_id` has to be our user id

The app must call `Purchases.logIn(userId)` before any purchase, so the webhook arrives
carrying `gamer.user_id`. Without it RevenueCat invents an anonymous id
(`$RCAnonymousID:…`), the event has nowhere to be delivered, and the backend logs an error
while somebody sits there having paid. `src/billing/purchases.ts` in the app is the one place
that wiring lives.

---

## The moderator console

One staff account, and no way to sign up for it. `ModeratorBootstrap` creates it on first
start from `MODERATOR_EMAIL` and `MODERATOR_PASSWORD`, and **does nothing at all if an
account with that address already exists** — including when the configured password
differs. That is deliberate: a restart with a stale environment variable must not silently
change the moderator's password, and an attacker who can set an environment variable must
not be able to take over an existing account. To change the password, sign in as the
moderator and use `PUT /auth/change/pwd`.

The account has no age, no games and no keywords, and it never completes onboarding. It is
not a participant:

- `Gamer.isDiscoverable()` is false for ADMIN, so `isPairableWith` refuses it everywhere —
  the deck, the exploration slots, who-liked-you.
- `findRandomPairable` and `findPendingAdmirers` repeat the rule in SQL, because a native
  query cannot call that method. Filtering only in Java would still cost an exploration
  slot every time the moderator was drawn.
- Fetching its profile by id answers `USER_NOT_FOUND`, the same as a blocked account, so
  the response cannot confirm the account exists.
- `DefaultMatchService.reload` refuses an ADMIN outright, so the moderator cannot swipe
  either. Without it the account's null age would put it in the minor band and build it a
  deck of children.

The app routes on the `role` field of `/application/get/user/info` — own profile only,
never anyone else's — into a separate `(admin)` route group with four tabs:

| Tab      | What it does                                                              |
| -------- | ------------------------------------------------------------------------- |
| Overview | `GET /admin/analytics` — population, growth graph, engagement, what is waiting |
| Reports  | The moderation queue: remove the content, or keep it                       |
| Avatars  | Uploads the classifier was unsure about — approve or reject                |
| Accounts | Banned accounts, and restoring one                                         |

### Avatar review is meant to be ignorable

REVIEW marks an upload PENDING and leaves it in the private bucket. Before this there was
no endpoint that listed or resolved those, so a picture the model hesitated over was
refused forever while its owner was told a human would look.

The queue must not become a job, though — this is a solo-operated product, and a queue that
has to be watched is one that will not be. `AvatarReviewJob` drains it, using the fact that
PENDING has two quite different causes:

| Why it is pending | `avatar_score` | What happens |
| ----------------- | -------------- | ------------ |
| The classifier was unreachable | NULL | Re-screened every 10 minutes until it answers |
| The classifier looked and was unsure | a number | Held for `AVATAR_PUBLISH_AFTER` (3 days), then published |

The first is the common case in practice: a restart or an out-of-memory on the model
container queues every upload in the window, and none of them have been judged at all.
Sending those to a person is what would make review feel like work.

Publishing the second after the deadline is a deliberate trade, and it is the Steam
posture rather than the pre-moderation one: anything the model was confident about was
already rejected outright, everything published stays reportable and bannable, and holding
an innocent user's photograph indefinitely because nobody opened the console is the more
likely harm. Measured before choosing it — ordinary images score around **0.001** against
an approve threshold of **0.20**, so the ambiguous band is expected to be nearly empty.

What the classifier cannot do is unchanged, and is why the report button carries the real
weight: it does not recognise a minor, a person who did not consent to being photographed,
or a picture of a screen showing either.

The image under review is returned as a `data:` URI inside JSON rather than as raw
`image/jpeg`. React Native's image loader drops the `Authorization` header on Android, so
the raw form arrived unauthenticated and the moderator saw a blank square with a 401
visible only in the server log.

---

## Production

Same images, different configuration:

- **Real SMTP.** Set `MAIL_HOST`, `EMAIL`, `EMAIL_PWD`, and leave `MAIL_MODE` unset.
- **`DDL_AUTO=validate`**, with migrations applied as a deliberate step before the new
  version starts.
- **Fresh secrets.** Everything in `.env.example` is public — it is in this repository.
- **Firebase on:** `FIREBASE_ENABLED=true` plus `FIREBASE_CONFIG_PATH` pointing at a
  mounted service-account JSON. Never commit that file.
- **Billing on,** deliberately, per store: `gamebuddy.billing.google.enabled` and
  `gamebuddy.billing.apple.enabled`. Nothing is enabled by default and unconfigured
  platforms are refused, so a misconfigured deployment declines purchases rather than
  giving product away.
- **Disable the API docs:** `springdoc.api-docs.enabled=false` and
  `springdoc.swagger-ui.enabled=false`.
- **Leave `CORS_ALLOWED_ORIGINS` unset.** Mobile clients are not subject to CORS, so
  nothing legitimate needs it in production. Compose sets it to localhost patterns so
  the Expo web build works during development; carrying that into production would let
  any page on `localhost` — including one an attacker asked a victim to open — call the
  API with the victim's browser.

Not yet done: the actual deployment target (Hetzner), and Google Play billing has never
been exercised against a real Play Console — the verifier is unit-tested, but do one
sandbox purchase before switching `gamebuddy.billing.google.enabled` on.

---

## Layout

```
GameBuddy-backend/     Spring Boot monolith (Java 25, Boot 4.1)
GameBuddy-Model/       Recommender + FastAPI service (Python 3.12)
GameBuddy-App/         Mobile client (React Native, Expo SDK 57) — see its own README
GameBuddy-Android/     The previous Kotlin client. Reference only; superseded by GameBuddy-App
common/                Shared framework code used by the backend
observability/         Filebeat configuration for the log stack
documentation/         Postman collection and environment
k8s/                   Kubernetes manifests, from the previous architecture
GameBuddy-*-service/   Superseded by the modules in GameBuddy-backend
```
