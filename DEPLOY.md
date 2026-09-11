# Deploying GameBuddy

The backend, model, database and proxy run as six Docker containers on a single **Hetzner CX23** (2 vCPU, 4 GB, 40 GB NVMe, ~€4/mo). Per `QA_PERFORMANCE.md` that carries 50,000 daily active users with ~7.7× headroom, answering in 67 ms at p95.

Work top to bottom. Two steps will bite you if you skip them — **the firewall** and **the secrets** — and they're marked accordingly. Everything else is recoverable.

Throughout, this alias keeps the commands short:

```bash
cd ~/gamebuddy
C="docker compose -f docker-compose.yml -f docker-compose.prod.yml"
```

The six containers: `caddy`, `backend`, `postgres`, `redis`, `model`, `model-retrain`. (There is no log stack — reading logs is [docs/OPERATIONS.md](docs/OPERATIONS.md).)

---

## The box

Provisioned 2026-08-16 — this is a record of what exists, not a shopping list.

| | |
| --- | --- |
| Name / type | `gamebuddy-prod` · CX23, Ubuntu 24.04 LTS |
| Location | Falkenstein (`fsn1`) — closest well-peered hub to EU + Turkey + US users |
| IPv4 | `REDACTED_SERVER_IP` |
| SSH | `~/.ssh/gamebuddy_hetzner` (ed25519), user `gamebuddy` |

<details>
<summary>How it was set up (first-boot hardening)</summary>

```bash
ssh -i ~/.ssh/gamebuddy_hetzner root@REDACTED_SERVER_IP
adduser gamebuddy && usermod -aG sudo gamebuddy
rsync --archive --chown=gamebuddy:gamebuddy ~/.ssh /home/gamebuddy/

# SSH: keys only
sed -i 's/^#*PermitRootLogin.*/PermitRootLogin no/;s/^#*PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
systemctl restart ssh

# Auto security updates, Docker, and 2 GB of swap (the CX23 ships with none)
apt update && apt install -y unattended-upgrades && dpkg-reconfigure -plow unattended-upgrades
curl -fsSL https://get.docker.com | sh && usermod -aG docker gamebuddy
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
sysctl -w vm.swappiness=10 && echo 'vm.swappiness=10' >> /etc/sysctl.conf
```
</details>

---

## 1. Firewall — critical

> **Use Hetzner's Cloud Firewall, not just `ufw`.** Docker writes its own `iptables` rules that are evaluated *before* ufw's, so a published container port is reachable from the internet even while `ufw status` shows it denied. Hetzner's firewall runs outside your VM, where Docker can't bypass it.

In the Hetzner console → **Firewalls** → apply to this server:

| Inbound | Source | Why |
|---|---|---|
| 22/tcp | your IP | SSH |
| 80/tcp | any | ACME challenge + redirect to 443 |
| 443/tcp + 443/udp | any | API and chat socket (udp = HTTP/3) |
| everything else | — | **denied** |

Verify **from your laptop**, not the server:

```bash
nmap -Pn -p 22,80,443,5432,6379,8000,8080,9200,5601 REDACTED_SERVER_IP
```

`5432`, `6379`, `8000`, `8080`, `9200`, `5601` must all be `filtered`/`closed`. An open `5432` or `6379` is an internet-facing database or an unauthenticated Redis — stop and fix it. (`docker-compose.prod.yml` already unpublishes those ports; the scan confirms it.)

Add ufw as a second layer:

```bash
ufw default deny incoming && ufw allow 22,80,443/tcp && ufw allow 443/udp && ufw --force enable
```

---

## 2. Secrets — critical

> **Everything in `.env.example` is a public placeholder.** Generate fresh values for every secret. A JWT secret copied from the example lets anyone who read the repo mint a token for any account.

```bash
sudo -u gamebuddy -i
git clone <your repo> gamebuddy && cd gamebuddy
cp .env.example .env && chmod 600 .env

# Generate, then paste each into .env:
openssl rand -base64 32   # JWT_SECRET
openssl rand -base64 32   # CHAT_ENCRYPTION_KEY
openssl rand -hex  32     # INTERNAL_API_KEY
openssl rand -base64 32   # REVENUECAT_WEBHOOK_TOKEN  (same value in the RevenueCat dashboard)
openssl rand -base64 24   # DB_PASSWORD      (the bootstrap superuser; init, migrations, backups)
openssl rand -base64 24   # DB_APP_PASSWORD  (the least-privileged login the backend runs as)
openssl rand -base64 24   # REDIS_PASSWORD
```

> **`CHAT_ENCRYPTION_KEY` decrypts every stored message.** Lose it and the entire chat history is unrecoverable. Keep a copy somewhere that is not this server.

Then set the rest of `.env`:

```bash
DOMAIN=api.yourdomain.com
ACME_EMAIL=you@yourdomain.com
TZ=Europe/Helsinki                    # your users' timezone

MAIL_HOST=smtp-relay.brevo.com
MAIL_PORT=587
SMTP_EMAIL=... ; SMTP_EMAIL_PWD=... ; MAIL_FROM=noreply@yourdomain.com
# Do not set MAIL_MODE here. `docker-compose.prod.yml` sets it to `smtp`; the
# application default is `log`, which prints verification codes to the container
# log and would let anyone reading it verify any address. Overriding it in .env is
# the one way to undo the overlay's protection, so leave it to the overlay.

R2_ENDPOINT=https://<account>.r2.cloudflarestorage.com
R2_ACCESS_KEY_ID=... ; R2_SECRET_ACCESS_KEY=... ; R2_PUBLIC_URL=https://pub-....r2.dev
# Avatars go to R2, never local disk — 40 GB fills, and losing the volume loses every image.

FIREBASE_SERVICE_ACCOUNT=./secret/firebase-service-account.json
HIDE_SEED_ACCOUNTS=true               # don't serve fixtures to real users
RETRAIN_INCLUDE_BOTS=true             # until the seed is deleted (step 6)
```

Copy the Firebase key up separately — it must never be committed:

```bash
scp firebase-service-account.json gamebuddy@REDACTED_SERVER_IP:~/gamebuddy/secret/
chmod 600 ~/gamebuddy/secret/firebase-service-account.json
```

---

## 3. DNS

Domain **findgamebuddy.com**, DNS on Cloudflare. Three records:

| Record | Target | Proxy |
|---|---|---|
| `findgamebuddy.com`, `www` | Cloudflare Pages (marketing site) | proxied (orange) |
| `api.findgamebuddy.com` | this server's IPv4 | **DNS only (grey)** |
| MX + TXT | Cloudflare Email Routing | — |

> **The grey cloud on `api` is mandatory.** Caddy issues its own TLS over ACME; Cloudflare's proxy would terminate TLS itself and answer the challenge path, so issuance never completes — and it sits in front of the chat WebSocket. Proxying `api` looks like a free upgrade and silently breaks both HTTPS issuance and messaging.

Point `api` at the server **before** starting Caddy (the ACME challenge needs it on port 80, and Let's Encrypt allows only five failures per domain per week):

```bash
dig +short api.findgamebuddy.com    # must return the server IP before continuing
```

To rehearse issuance safely, uncomment the staging `acme_ca` line in `deploy/Caddyfile` first. The marketing site is **not** here — it's static on Cloudflare Pages (see `GameBuddy-Web/README.md`), so the legal pages stay up even while this box is redeployed.

---

## 4. Build & start

> **Don't build on the server.** The images total ~7.5 GB and the build cache would fill the 40 GB disk in a few rebuilds. Build in CI or locally, push to a registry, pull here.

```bash
$C up -d
$C ps
```

If you must build on the box for a first deploy, prune straight after: `$C build && docker builder prune -af`.

> **A changed `deploy/Caddyfile` needs `--force-recreate`, silently.** It's bind-mounted as a *file*, so the container keeps the inode it started with; `git pull` writes a new file and renames it over the top (a new inode), so the container serves the old config while the disk shows the change — and `up -d` won't notice.
> ```bash
> $C up -d --force-recreate caddy
> $C exec caddy cat /etc/caddy/Caddyfile    # read the config out of the container — the check that matters
> ```
> Certificates live in the `caddy-data` volume and survive this, so it costs a couple of seconds and no ACME traffic.

---

## 5. Verify

```bash
curl -sI https://api.yourdomain.com/actuator/health     # 200, valid cert
curl -s  https://api.yourdomain.com/actuator/health     # {"status":"UP"}

# These must all 404 — docs disabled, actuator closed at the proxy:
for p in swagger-ui/index.html api-docs actuator/env; do
  curl -so /dev/null -w "$p %{http_code}\n" https://api.yourdomain.com/$p
done

curl -sI http://api.yourdomain.com/actuator/health | head -1    # 308 (HTTP→HTTPS)
nmap -Pn -p 5432,6379,8000,8080,9200 REDACTED_SERVER_IP         # all filtered/closed
```

The functional suite does **not** run against prod: it reads verification codes out of the
local backend log (`MAIL_MODE=log`) and resets fixtures through `docker compose exec postgres`,
neither of which exists on the box. Prove prod with the checks above plus one manual sign-up
from a production build (a real inbox), then delete that account in-app.

---

## 6. Backups — critical (and a legal promise)

`PRIVACY.md` §9 tells users each backup is destroyed **7 days** after it's taken. A backup that outlives that is a copy of someone who asked to be deleted — so every part below is load-bearing, not housekeeping. The DB is small (91 MB seeded), so there's no excuse.

**1 — Nightly dump.** `deploy/backup.sh` dumps Postgres, uploads to R2, and prunes both local and remote copies past 7 days.

```bash
mkdir -p ~/backups
cp ~/gamebuddy/deploy/backup.sh ~/backup.sh && chmod +x ~/backup.sh
crontab -e     # as gamebuddy:  30 3 * * * /home/gamebuddy/backup.sh >> /home/gamebuddy/backup.log 2>&1
```

The rclone remote is configured **as the `gamebuddy` user** (cron runs as that user; a remote defined under root is invisible to it), with an R2 token scoped Object **Read & Write** on `gamebuddy-backups` only:

```bash
rclone config create r2 s3 provider=Cloudflare region=auto \
  access_key_id=YOUR_KEY secret_access_key=YOUR_SECRET \
  endpoint=https://<account>.eu.r2.cloudflarestorage.com   # the .eu. endpoint — the bucket is EU-jurisdiction
rclone config update r2 no_check_bucket true
```

> Two gotchas that cost real time: **`no_check_bucket = true` is not optional** — without it rclone pre-checks the bucket (a bucket-level op an object-scoped token can't do) and the `403` gets reported against the *file*, looking exactly like a wrong token. And **expect one `501 NotImplemented` per run** — Ubuntu's apt rclone (v1.60-DEV) sends something R2 rejects on the first PUT, then retries and succeeds (exit 0); install current rclone from rclone.org if the nightly ERROR line bothers you.

**2 — Log retention.** Caddy's access log and Docker's console capture rotate by *size* and won't reach it for months, so the IPs inside would outlive the 7 days. `deploy/enforce-log-retention.sh` truncates by the age of the oldest entry (a `find -mtime` never fires on an appended file). Needs root:

```bash
sudo cp ~/gamebuddy/deploy/enforce-log-retention.sh /usr/local/bin/ && sudo chmod +x /usr/local/bin/enforce-log-retention.sh
sudo crontab -e    # as root:  45 3 * * * /usr/local/bin/enforce-log-retention.sh >> /var/log/gamebuddy-log-retention.log 2>&1
```

(The backend's own log is handled by `LOG_MAX_HISTORY: 7` in the prod compose.)

**3 — R2 lifecycle rule.** The script only prunes on nights it runs; the bucket rule holds when cron is dead or the disk is full. On `gamebuddy-backups` (EU jurisdiction, Standard class): rule `expire-backups-after-7-days`, no prefix, **delete after 7 days**, enabled. Verify: `rclone lsl r2:gamebuddy-backups/` — nothing older than a week.

**4 — Restore-test it once, now.** A backup you've never restored is a hypothesis.

> **There are no disk-level backups** (Hetzner automatic backups off, no snapshots — checked 2026-08-20), by design: it keeps §9 honest, since nothing outlives the 7-day `pg_dump`. The trade is that recovery means *rebuild the box from this doc, restore the dump*. If you ever enable Hetzner's automatic backups (~€1/mo, 7 rotating slots — same 7 days, still honest), note it in §9. **Avoid manual snapshots** — they never rotate, so one holds a deleted account forever; delete any you take for a migration.

---

## Operating it

### Tune a rate limit (no rebuild)

The counters live in the JVM, so **restarting the backend clears every window** — the fastest way to unblock someone locked out right now. To change a limit, set the variable and restart the one container:

```bash
echo 'MATCH_DECISION_PERMITS=200' >> .env    # e.g. testers hitting the swipe limiter
$C up -d backend
```

Each limit has a `_PERMITS` and a `_WINDOW` (windows accept `30s`/`5m`/`1h`/`1d`; omitting one keeps the default):

| Limit | Variables | Default |
|---|---|---|
| Swiping the deck | `MATCH_DECISION_{PERMITS,WINDOW}` | 120 / 1m |
| Opening a lobby | `LOBBY_CREATE_{PERMITS,WINDOW}` | 20 / 1d |
| Asking to join a lobby | `LOBBY_JOIN_{PERMITS,WINDOW}` | 40 / 1h |
| Lobby chat | `LOBBY_MESSAGE_{PERMITS,WINDOW}` | 60 / 1m |
| Failed sign-ins | `AUTH_LOGIN_{PERMITS,WINDOW}` | 15 / 5m |
| Verification-code guesses | `AUTH_VERIFY_{PERMITS,WINDOW}` | 20 / 15m |
| Code emails | `AUTH_SEND_CODE_{PERMITS,WINDOW}` | 6 / 15m |
| Password reset | `AUTH_RESET_PASSWORD_{PERMITS,WINDOW}` | 20 / 15m |
| Sign-up/code/reset from one IP | `AUTH_IP_{PERMITS,WINDOW}` | 200 / 15m |

Raising a limit is safe; lowering one decides whom to turn away (`RateLimitBudgetsTest` fails the build if any is set below what a real person does). **Leave `AUTH_SEND_CODE_PERMITS` alone** — every permit sends a real email, and the person flooded isn't the one asking. `AUTH_IP_*` is keyed by address, so a tester behind a shared network is unblocked by `$C up -d backend` like the others; raise it (e.g. `AUTH_IP_PERMITS=100000`) before running the functional suite twice inside its window from one machine.

### Remove the seed accounts (once real users can fill a deck)

The `@bot.gamebuddy.invalid` profiles are fixtures. A fake profile a real person can match with is what dating/matching apps get investigated for, so delete them once real accounts exist. Both halves together (deleting the seed while `RETRAIN_INCLUDE_BOTS=true` just trains on whoever's left):

1. Set `RETRAIN_INCLUDE_BOTS=false` in `.env`.
2. Delete the seed with the ordered `deleteGamers` in `qa/functional/helpers/db.js` (**not** the README one-liner — it fails on 15 foreign keys, `QA_FINDINGS.md` #3).
3. `$C up -d model-retrain` and check the next night's run.

`HIDE_SEED_ACCOUNTS=true` keeps them out of decks meanwhile, but they're still reachable by direct id.

### Deploy an update

```bash
$C pull && $C up -d
```

> Apply any new migration from `GameBuddy-backend/src/main/resources/db/` **by hand first**, then start the new image. The backend runs `ddl-auto=validate` and refuses to start against a schema it disagrees with — which is the behaviour you want: it fails loudly instead of reshaping your database.

### Migrations applied on prod

The `schema-baseline.sql` was loaded on first init (2026-08-16). Everything below was applied
by hand with `$C exec -T postgres psql -U gamebuddy -d gamebuddy -f /dev/stdin < <file>` before
the image that needs it started. **Add a row when you apply one, not later.**

| File | Applied | Check |
|---|---|---|
| upgrade-2026-4 … 37 | before 2026-09-11 (pre-ledger) | — |
| upgrade-2026-38-promo-codes.sql | _pre-ledger_ | `\dt gamebuddy.promo_code*` → 3 tables |
| upgrade-2026-39-missions-and-badges.sql | _pre-ledger_ | `\d gamebuddy.gamer` has `mission_set_index` |
| upgrade-2026-40 … 44 | _pre-ledger_ | `\dt gamebuddy.gamer_auth_identity`; `review_prompt_shown_at` on `gamer` |
| upgrade-2026-45-report-cases.sql | _fill in_ | `\dt gamebuddy.moderation_case` exists; `\d gamebuddy.gamer` has `suspended_until` |

---

## Quick reference

```bash
cd ~/gamebuddy
C="docker compose -f docker-compose.yml -f docker-compose.prod.yml"

$C ps                                            # what's running
$C logs -f backend                               # follow the console
$C restart backend                               # restart one service
$C pull && $C up -d                              # deploy a new image
$C exec postgres psql -U gamebuddy -d gamebuddy  # a database shell
docker stats --no-stream                         # memory / CPU now
docker system prune -af --filter "until=168h"    # reclaim disk, weekly
```

**Debugging a user's bug report** → [docs/OPERATIONS.md](docs/OPERATIONS.md) (structured logs, trace ids, `jq` recipes).
