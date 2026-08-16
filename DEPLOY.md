# Deploying GameBuddy to a Hetzner CX23

2 vCPU, 4 GB, 40 GB NVMe, ~€3.99/mo. Sized in `QA_PERFORMANCE.md`: it carries 50,000 daily
active users with about 7.7× headroom, answering in 67 ms at p95.

Work through this in order. The parts that will bite you if skipped are marked **critical**,
and they are all in steps 2 and 3 — the firewall and the secrets. Everything else is
recoverable.

---

## 1. The server

**Provisioned 2026-08-16.** This section is now a record of what exists, not a shopping list.

| | |
| --- | --- |
| Name | `gamebuddy-prod` |
| Type | CX23 — 2 vCPU (Intel/AMD), 3819 MB RAM, 38 GB disk, 20 TB traffic |
| Location | **Falkenstein** (`fsn1`, eu-central) |
| Image | Ubuntu 24.04.4 LTS, x86_64 |
| IPv4 | `178.105.245.202` |
| SSH key | `~/.ssh/gamebuddy_hetzner` (ed25519, no passphrase, `SHA256:89kfi5Xp…`) |
| Cost | €7.52/mo incl. VAT (€6.89 server + €0.63 IPv4) |

**Why Falkenstein and not Helsinki.** The operator is in Finland, so Helsinki is the tempting
pick, but the users are not: Europe broadly, Turkey and the US. Germany is ~20–40 ms better to
Central/Western Europe and Turkey and ~20 ms better to the US, and loses only to the Nordics.
Germany is also the better-peered transit hub. Living somewhere is a reason to *test* from
there, not to host there. (CX23 is EU-only in any case — the US and Singapore sites offer CPX
and CCX, not CX, so they were never selectable.)

```bash
ssh -i ~/.ssh/gamebuddy_hetzner root@178.105.245.202
adduser gamebuddy && usermod -aG sudo gamebuddy
rsync --archive --chown=gamebuddy:gamebuddy ~/.ssh /home/gamebuddy/

# SSH: keys only.
sed -i 's/^#*PermitRootLogin.*/PermitRootLogin no/;s/^#*PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
systemctl restart ssh

# Unattended security updates — the cheapest security control there is.
apt update && apt install -y unattended-upgrades && dpkg-reconfigure -plow unattended-upgrades

# Docker.
curl -fsSL https://get.docker.com | sh
usermod -aG docker gamebuddy

# 40 GB disk and no swap by default. 2 GB of swap is the difference between a container
# being OOM-killed at a spike and being slow for ten seconds.
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
sysctl -w vm.swappiness=10 && echo 'vm.swappiness=10' >> /etc/sysctl.conf
```

## 2. The firewall — **critical**

**Use Hetzner's Cloud Firewall, not just `ufw`.** This is the single most important step on
this page.

Docker writes its own `iptables` rules in the `DOCKER` chain, and those are evaluated
*before* ufw's. A published container port is reachable from the internet even when
`ufw status` shows that port denied. People check ufw, see "deny", and believe they are
covered. Hetzner's firewall runs on their network, outside your VM, so Docker cannot bypass
it.

In the Hetzner console → Firewalls → apply to this server:

| Direction | Port | Source | Why |
| --------- | ---- | ------ | --- |
| inbound | 22/tcp | your IP, if it is static | SSH |
| inbound | 80/tcp | any | ACME challenge, and the redirect to 443 |
| inbound | 443/tcp + 443/udp | any | the API and the chat socket (udp for HTTP/3) |
| inbound | everything else | — | **denied** |

Then verify from your laptop, not from the server — this is the check that matters:

```bash
nmap -Pn -p 22,80,443,5432,6379,8000,8080,9200,5601 <ip>
```

`5432`, `6379`, `8000`, `8080`, `9200` and `5601` must all be `filtered` or `closed`. If
`5432` or `6379` is open you have an internet-facing database and an unauthenticated Redis;
fix it before going further. `docker-compose.prod.yml` already removes those port
publications, so this should hold — the scan confirms it rather than assuming it.

Also set ufw as a second layer:

```bash
ufw default deny incoming && ufw allow 22,80,443/tcp && ufw allow 443/udp && ufw --force enable
```

## 3. Secrets — **critical**

**Everything in `.env.example` is public — it is in this repository.** Generate fresh values
for every secret. A JWT secret from the example file means anyone who read the repo can mint
a token for any account.

```bash
sudo -u gamebuddy -i
git clone <your repo> gamebuddy && cd gamebuddy
cp .env.example .env && chmod 600 .env

# Generate and paste in:
openssl rand -base64 32   # JWT_SECRET
openssl rand -base64 32   # CHAT_ENCRYPTION_KEY
openssl rand -hex  32     # INTERNAL_API_KEY
openssl rand -base64 32   # REVENUECAT_WEBHOOK_TOKEN  (same value in the RevenueCat dashboard)
openssl rand -base64 24   # DB_PASSWORD
openssl rand -base64 24   # REDIS_PASSWORD
openssl rand -base64 24   # MODERATOR_PASSWORD
```

`CHAT_ENCRYPTION_KEY` deserves a separate thought: it decrypts every stored message. Losing
it makes the entire chat history unreadable, and there is no recovery. Keep a copy somewhere
that is not this server.

Then set the rest in `.env`:

```bash
DOMAIN=api.yourdomain.com
ACME_EMAIL=you@yourdomain.com
TZ=Europe/Helsinki            # your users' timezone, not the server's

MAIL_HOST=smtp-relay.brevo.com
MAIL_PORT=587
SMTP_EMAIL=...
SMTP_EMAIL_PWD=...
MAIL_FROM=noreply@yourdomain.com
# MAIL_MODE is deliberately unset. The default is smtp. Only the exact word "log" changes
# it, and in production that would let anyone reading the logs verify any address.

R2_ENDPOINT=https://<account>.r2.cloudflarestorage.com
R2_ACCESS_KEY_ID=...
R2_SECRET_ACCESS_KEY=...
R2_PUBLIC_URL=https://pub-....r2.dev
# Uploads must go to R2, not the local disk: 40 GB, user-supplied images, and losing the
# volume loses every avatar.

FIREBASE_SERVICE_ACCOUNT=./secret/firebase-service-account.json
MODERATOR_EMAIL=you@yourdomain.com

HIDE_SEED_ACCOUNTS=true       # do not serve fixtures to real users
RETRAIN_INCLUDE_BOTS=true     # until the seed is deleted — see step 8
```

Copy the Firebase key up separately; it must never be committed:

```bash
scp firebase-service-account.json gamebuddy@<ip>:~/gamebuddy/secret/
chmod 600 ~/gamebuddy/secret/firebase-service-account.json
```

## 4. DNS

The domain is **findgamebuddy.com**, with DNS on Cloudflare. Three records matter, and they
do not all want the same treatment:

| Record | Target | Cloudflare proxy |
| --- | --- | --- |
| `findgamebuddy.com`, `www` | Cloudflare Pages (the marketing site) | proxied — orange cloud |
| `api.findgamebuddy.com` | this server's IPv4 | **DNS only — grey cloud** |
| MX + TXT | Cloudflare Email Routing | — |

**The grey cloud on `api` is not a preference, and getting it wrong fails in two ways at
once.** Caddy issues its own certificate over ACME, and Cloudflare's proxy terminates TLS
itself and answers the HTTP-01 challenge path — so issuance never completes. It also sits in
front of the STOMP WebSocket that carries chat. Proxying `api` looks like a free upgrade and
breaks messaging.

Point `api.findgamebuddy.com` at the server's IPv4 **before** starting Caddy — the ACME
challenge is answered on port 80 and fails without it. Let's Encrypt allows five failures
per domain per week, which is easy to burn while a record is still propagating. To test
issuance safely, uncomment the staging `acme_ca` line in `deploy/Caddyfile` first.

```bash
dig +short api.findgamebuddy.com    # must return the server IP before you continue
```

The marketing site is not deployed here — it is static, and lives on Cloudflare Pages. See
`GameBuddy-Web/README.md`. Keeping it off this box is deliberate: the privacy policy and
terms have to stay reachable for the store listing even while this server is being
redeployed.

## 5. Build and start

**Do not build on the server.** Your images are ~7.5 GB (the model image alone is 2.78 GB,
and `model-retrain` is a second 2.94 GB copy) and Docker's build cache would eat the 40 GB
disk within a few rebuilds. Build in CI or on your laptop, push to a registry, pull here.

If you do build on the box for a first deploy, prune immediately afterwards:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml build
docker builder prune -af
```

Start it:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
```

Six containers: `caddy`, `backend`, `postgres`, `redis`, `model`, `model-retrain`.
Elasticsearch, Kibana and Filebeat are behind a `logs` profile and will not start — that is
deliberate, they need 2.9 GB and do not fit.

## 6. Verify

```bash
curl -sI https://api.yourdomain.com/actuator/health        # 200, and a valid certificate
curl -s  https://api.yourdomain.com/actuator/health        # {"status":"UP"}

# These must all fail — the API docs are disabled and actuator is closed at the proxy.
curl -so /dev/null -w '%{http_code}\n' https://api.yourdomain.com/swagger-ui/index.html   # 404
curl -so /dev/null -w '%{http_code}\n' https://api.yourdomain.com/api-docs                # 404
curl -so /dev/null -w '%{http_code}\n' https://api.yourdomain.com/actuator/env            # 404

# HTTP redirects to HTTPS.
curl -sI http://api.yourdomain.com/actuator/health | head -1                              # 308

# And from your laptop again, the scan from step 2.
nmap -Pn -p 5432,6379,8000,8080,9200 <ip>                  # all filtered/closed
```

Then run the functional suite against production once, read-only parts aside — it creates
accounts, so use it before you have real users:

```bash
GB_BASE_URL=https://api.yourdomain.com node qa/run-functional.js
```

## 7. Backups

The database is small (91 MB with the full seed) so this is cheap and there is no excuse.

```bash
# ~/backup.sh
set -euo pipefail
cd /home/gamebuddy/gamebuddy
STAMP=$(date +%F-%H%M)
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T postgres \
  pg_dump -U gamebuddy -d gamebuddy --format=custom \
  > /home/gamebuddy/backups/gamebuddy-$STAMP.dump
# Off the box: a backup on the same disk is not a backup.
rclone copy /home/gamebuddy/backups/gamebuddy-$STAMP.dump r2:gamebuddy-backups/
find /home/gamebuddy/backups -name '*.dump' -mtime +7 -delete
```

```bash
mkdir -p ~/backups && chmod +x ~/backup.sh
crontab -e   # 30 3 * * *  /home/gamebuddy/backup.sh >> /home/gamebuddy/backup.log 2>&1
```

**Restore-test it once**, now, while nothing is at stake. A backup you have never restored
is a hypothesis. Also enable Hetzner's automatic snapshots (20% of the server price, ~€0.80/mo)
— they cover the whole disk, not just the database.

## 8. After real users arrive

The seeded `@bot.gamebuddy.invalid` accounts are fixtures, not users. A fake profile a real
person can swipe on and message is what matching apps get investigated for, so remove them
once real accounts can fill a deck. Do both halves together — deleting the seed while
`RETRAIN_INCLUDE_BOTS=true` just trains on whoever is left:

1. Set `RETRAIN_INCLUDE_BOTS=false` in `.env`.
2. Delete the seed. **Not** the one-liner in the README — it fails on fifteen foreign key
   constraints (see `QA_FINDINGS.md` #3). Use the working ordered version in
   `qa/functional/helpers/db.js` (`deleteGamers`) or `qa/reset-fixtures.js`.
3. `docker compose ... up -d model-retrain` and check the next night's run.

`HIDE_SEED_ACCOUNTS=true` is already set, so they are not being recommended in the
meantime — but they are still in the database and still reachable by direct id.

---

# Reading the logs without Kibana

You dropped Elasticsearch, so the question is fair: a user reports a bug — how do you find
it? The answer is better than you might expect, because the backend already writes
structured ECS JSON and **stamps every line with a trace id and the user id**.

## The one thing to do first

The API returns `X-Request-Id` on **every** response — verified:

```
$ curl -sI https://api.yourdomain.com/actuator/health | grep -i x-request-id
X-Request-Id: 51707e98-3cca-4933-a593-96d2eff5b957
```

It also *accepts* one you send. `GameBuddy-App/src/api/client.ts` currently neither sends
nor surfaces it. **Show that id in the app's error screen** ("something went wrong —
reference 51707e98"). Then a bug report arrives with the exact key to every log line that
request produced, and the investigation below takes ten seconds instead of ten minutes.

That is a one-line change in the app and by far the highest-value thing on this page.

## The commands

Install `jq` **on the server, not in the container** — the backend image does not ship it,
which is why every command below `cat`s the file out of the container and filters on the
host:

```bash
sudo apt install -y jq
```

The log file lives in the `backend-logs` volume. Verified against a real file: 422 lines,
all valid JSON, 333 carrying a trace id and 261 carrying a user id.

```bash
cd ~/gamebuddy
alias gblog='docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T backend cat /app/logs/gamebuddy.json'
```

**A real log line looks like this** — every field below is one you can filter on:

```json
{"@timestamp":"2026-08-13T22:33:07.468Z","log":{"level":"INFO","logger":"gamebuddy.access"},
 "message":"GET /messages/get/inbox -> 200 (10 ms)",
 "trace":{"id":"a1eb9f00-6c0b-4fbd-9aad-451ec9eb4bbe"},
 "url":{"path":"/messages/get/inbox"},"client":{"ip":"172.18.0.1"},
 "http":{"request":{"method":"GET"},"response":{"status_code":200}},
 "user":{"id":"8dd8b6ed-077d-45fd-b1c4-d7924d40d3fa"},"event":{"duration_ms":10}}
```

**The user gave you a reference id** — everything that request did, in order:

```bash
gblog | jq -c 'select(.trace.id=="a1eb9f00-...") | {t:.["@timestamp"], lvl:.log.level, msg:.message, err:.error.type}'
```

**The user gave you their email** — find their id, then their last hour:

```bash
UID=$(docker compose ... exec -T postgres psql -U gamebuddy -d gamebuddy -tA \
      -c "select user_id from gamebuddy.gamer where lower(email)=lower('them@example.com');")

gblog | jq -c --arg u "$UID" 'select(.user.id==$u) |
  {t:.["@timestamp"], m:.http.request.method, p:.url.path, s:.http.response.status_code, msg:.message}' | tail -50
```

**Every error today, grouped by what it was:**

```bash
gblog | jq -r 'select(.log.level=="ERROR") | .error.type // .message' | sort | uniq -c | sort -rn
```

**Every failed request, newest last** — the access log is at `WARN` in production, so
non-2xx responses are exactly what it contains:

```bash
gblog | jq -c 'select(.log.logger=="gamebuddy.access") |
  {t:.["@timestamp"], s:.http.response.status_code, p:.url.path, u:.user.id, ms:.event.duration_ms}'
```

**A full stack trace for one error:**

```bash
gblog | jq -r 'select(.trace.id=="a1eb9f00-...") | select(.error) | .error.stack_trace'
```

**The slowest requests** (`event.duration_ms` is on every access line):

```bash
gblog | jq -c 'select(.event.duration_ms > 1000) | {p:.url.path, ms:.event.duration_ms, u:.user.id}' | tail -30
```

**Slow SQL** — `log_min_duration_statement=1000` is set in the prod compose:

```bash
docker compose ... logs postgres | grep "duration:"
```

## When that is not enough

The access log runs at `WARN` in production so only failures are recorded. To watch
everything while you reproduce a bug:

```bash
# In .env: ACCESS_LOG_LEVEL=INFO and LOG_LEVEL=DEBUG
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d backend
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f backend
# Put both back afterwards. DEBUG at any real traffic will rotate the log in hours.
```

## What you give up, honestly

Grep and `jq` are fine for "this user, this request, what happened". They are poor at "how
often has this happened this week" and "did this start after Tuesday's deploy", because the
file is capped at 200 MB and rotates. If that starts to matter, do not put Elasticsearch back
on this box — ship the same ECS JSON somewhere hosted. Keep Filebeat (170 MB, fits fine) and
point its output at a free tier: Grafana Cloud Loki, Axiom, or Better Stack all take this
format and all have free tiers that will comfortably hold a 50,000-DAU app's logs.

That gets the searchable history back for €0 and 170 MB instead of €2.50/mo and 2.9 GB.

---

## Quick reference

```bash
cd ~/gamebuddy
C="docker compose -f docker-compose.yml -f docker-compose.prod.yml"

$C ps                      # what is running
$C logs -f backend         # follow the console
$C restart backend         # restart one service
$C pull && $C up -d        # deploy a new image
$C exec postgres psql -U gamebuddy -d gamebuddy
docker stats --no-stream   # memory and CPU right now
docker system prune -af --filter "until=168h"   # reclaim disk, weekly
```

**Deploying an update:** apply any new migration from
`GameBuddy-backend/src/main/resources/db/` by hand *first*, then start the new image. The
backend runs `ddl-auto=validate` and will refuse to start against a schema it disagrees
with, which is the behaviour you want — it fails loudly instead of reshaping your database.
