# Operations — reading the logs without Kibana

The production box runs without Elasticsearch (it needs 2.9 GB and doesn't fit), so when a
user reports a bug you read the logs directly. That's less of a downgrade than it sounds:
the backend writes structured ECS JSON and stamps every line with a trace id and a user id.

This is the debugging playbook. For standing the server up in the first place, see
[DEPLOY.md](../DEPLOY.md).

---

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

