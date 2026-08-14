# Load test results and Hetzner sizing — GameBuddy

Measured 2026-08-14 against an isolated stack (`gamebuddy-perf`) holding the real 20,002-gamer
population and the real trained artefact. Load generated with k6 pinned to cores the system
under test did not have, so the two never competed for CPU.

---

## The recommendation

**Buy CX23 (2 vCPU, 4 GB, ~€3.99/mo).** It carries 50,000 DAU with roughly **7.7× headroom**
and answers in 67 ms at p95. CX33 doubles the ceiling for €2.50 more, and the one reason to
pay it is the log stack — see [memory](#memory-is-what-actually-decides-this).

The honest summary is that **50,000 DAU is not a demanding workload for this application**,
and the plan choice is not close.

| | CAX11 | CX23 | CX33 |
| --- | --- | --- | --- |
| Spec | 2 vCPU Ampere **ARM64**, 4 GB | 2 vCPU Intel, 4 GB | 4 vCPU, 8 GB |
| Price | ~€3.79 | ~€3.99 | ~€6.49 |
| 50k DAU | not measured (see below) | **PASS**, p95 67 ms | **PASS**, p95 65 ms |
| Ceiling | — | **16 sessions/s ≈ 208 req/s** | **36 sessions/s ≈ 466 req/s** |
| Equivalent DAU at ceiling | — | ~384,000 | ~864,000 |
| Headroom over 50k DAU | — | 7.7× | 17.3× |
| App fits in RAM | yes (~1.8 GB) | yes (~1.8 GB) | yes |
| App **+ log stack** fits | **no** | **no** | yes (~4.7 GB) |

**CAX11 was not benchmarked, and no number here should be presented as if it were.** Its
caps are identical to CX23's (2 cores, 4 GB) so an x86 run would just reproduce the CX23
column while hiding the only thing that differs — Ampere Altra core speed. What *was*
checked is the blocking risk: PyTorch's CPU wheel index ships `manylinux_2_28_aarch64`
builds for cp312 (2.7.1 through 2.9.1), so the model image will build on ARM. If the €0.20
saving matters, rent a CAX11 for an hour (about one euro cent) and run
`qa/perf/run-plan.ps1` against it.

---

## What the numbers are

The workload model, so the arithmetic can be argued with:

```
DAU × 15% peak-hour concentration × (6 min session ÷ 60 min) = concurrent sessions
```

| DAU | peak hour | concurrent | offered load |
| --- | --------- | ---------- | ------------ |
| 1,000 | 150 | 15 | 0.5 req/s |
| 10,000 | 1,500 | 150 | 5.4 req/s |
| 50,000 | 7,500 | **750** | **27 req/s** |

27 req/s is the number that makes the answer easy. A session is 13 requests — open the app,
one feed, six swipes, five tab reads — and 50,000 people doing that across a day, with the
usual evening peak, is not much traffic. **The assumption most worth challenging is the
15%**: a product with one sharp evening peak can be 25–30%, which doubles the requirement
and still leaves CX23 with 4× headroom.

### The ladder

| Plan | DAU | offered | achieved | errors | p95 | feed p95 | verdict |
| ---- | --- | ------- | -------- | ------ | --- | -------- | ------- |
| cx23 | 1,000 | 0.5 req/s | 0.8 | 0.00% | 68 ms | 77 ms | PASS |
| cx23 | 10,000 | 5.4 req/s | 5.8 | 0.00% | 71 ms | 85 ms | PASS |
| cx23 | 50,000 | 27.1 req/s | 27.2 | 0.00% | **67 ms** | 89 ms | **PASS** |
| cx33 | 1,000 | 0.5 req/s | 0.7 | 0.00% | 72 ms | 97 ms | PASS |
| cx33 | 10,000 | 5.4 req/s | 5.7 | 0.00% | 67 ms | 80 ms | PASS |
| cx33 | 50,000 | 27.1 req/s | 27.4 | 0.00% | **65 ms** | 77 ms | **PASS** |

### The ceiling

Rates pushed until the system stopped meeting p95 < 500 ms and errors < 1%.

| Plan | sessions/s | req/s | errors | dropped | p95 | verdict |
| ---- | ---------- | ----- | ------ | ------- | --- | ------- |
| cx23 | 12 | 156.5 | 0.00% | 0 | 58 ms | pass |
| cx23 | **16** | **207.6** | 0.00% | 0 | **58 ms** | **last pass** |
| cx23 | 20 | 211.4 | 0.02% | 54 | 2717 ms | FAIL |
| cx23 | 24 | 225.6 | 0.03% | 128 | 3267 ms | FAIL |
| cx33 | 32 | 416.6 | 0.00% | 0 | 58 ms | pass |
| cx33 | **36** | **466.3** | 0.00% | 0 | **69 ms** | **last pass** |
| cx33 | 40 | 455.5 | 0.01% | 0 | 1801 ms | FAIL |
| cx33 | 48 | 501.9 | 0.00% | 125 | 2493 ms | FAIL |
| cx33 | 96 | 353.3 | 0.02% | 1165 | 11124 ms | FAIL |
| cx33 | 160 | 164.9 | 0.00% | 2003 | 28640 ms | FAIL |

The ceiling scales almost exactly with cores — 2 vCPU carries 208 req/s, 4 vCPU carries 466 —
which is what you expect when the constraint is CPU rather than a pool or a lock.

### How it fails, which matters more than where

The cliff is sharp and the failure mode is bad. On CX33, 36 sessions/s answers in 69 ms and
40 answers in 1801 ms — a 26× latency jump for an 11% load increase. Past that, **throughput
goes backwards**: 502 req/s at 48 sessions/s, 353 at 96, 203 at 128, 165 at 160. That is
congestion collapse — the system spends its capacity on work whose clients have already
given up.

Crucially, **errors stayed near zero throughout**. Nothing returns 429 or 503; requests just
queue, out to 28 seconds. There is no load shedding, so an overloaded GameBuddy does not
degrade — it stops, silently, while reporting success.

---

## The bottleneck is Postgres, and specifically the feed

Sampled at the CX23 knee (208 req/s, 2 vCPU = 200% total):

| container | mean CPU | share of busy CPU | memory |
| --------- | -------- | ----------------- | ------ |
| **postgres** | **114.4%** | **66%** | 269 MB |
| backend | 52.7% | 30% | 760 MB |
| model | 5.5% | 3% | 380 MB |
| redis | 0.4% | <1% | 7 MB |

The recommender — the component everyone suspects — is **3% of the load**. Two of the four
hypotheses this test was designed around are disproved:

- **The Hikari pool is not the constraint.** It is capped at 20 and `getRecommendations`
  holds a connection across the HTTP call to the model, which looked like the obvious
  ceiling. Active backends fluctuated between 1 and 16 and never pinned at 20; blocked
  backends were 0 essentially always.
- **The model is not the constraint.** One uvicorn worker at 5.5% CPU, and it never became
  interesting at any rate tested.

### 264 SQL statements per feed request

Measured with `pg_stat_statements`, ten requests, reset in between:

| calls per request | total ms | statement |
| ----------------- | -------- | --------- |
| **50** | **124.8** | select … from `gamer_games_join` … where gamer_id = $1 |
| **50** | 60.1 | select … from `gamer_keywords_join` … where gamer_id = $1 |
| **50** | 8.0 | select … from `gamer_platform` where user_id = $1 |
| 50 | 11.9 | select … from `recommendation_impression` … |
| 50 | 12.2 | insert into `recommendation_impression` … |
| 5 | 0.2 | select … from `blocked_friends` … |
| | | **264 statements total per feed request** |

This is a textbook N+1. Fifty candidates are returned and each one's games, keywords and
platforms are loaded with its own query — 150 statements where three would do.
`application.yml` sets `hibernate.default_batch_fetch_size: 50`, which should be preventing
exactly this, and on this evidence it is not taking effect for these three collections.
That is the first thing to look at.

A second, independent 100 statements per request come from impression recording: a SELECT
and an INSERT per candidate. It runs after commit on `applicationTaskExecutor`, so it does
not add latency to the feed — but it is 38% of the database work the feed causes, spent on
analytics.

### What to fix first

In order of return:

1. **Batch or join-fetch the candidate collections.** An entity graph, a `join fetch`, or a
   DTO projection over games/keywords/platforms. `GamerDto` only needs game names and icons
   and keyword names, so a projection avoids materialising the entities at all. Removes
   ~150 of 264 statements.
2. **Batch the impression writes.** One multi-row INSERT per page instead of 50 SELECT +
   INSERT pairs. Removes ~100 more.
3. **Add load shedding.** A bounded request queue or a concurrency limit on the feed, so
   that past the knee the API returns 503 quickly instead of queueing for 28 seconds. This
   is what turns congestion collapse into graceful degradation, and it matters more than
   raising the ceiling.

Items 1 and 2 together would cut the feed's database work by roughly 95%. Since Postgres is
66% of CPU and the feed is the dominant query source, the ceiling should move a long way —
plausibly several times — on the same hardware. **None of this is needed for 50,000 DAU.**
It is what to do before 300,000, or if you want CAX11 to be comfortable.

---

## Memory is what actually decides this

Every candidate plan has 4–8 GB, so memory — not CPU — is the real differentiator.

Measured, under load:

| component | idle | under load | note |
| --------- | ---- | ---------- | ---- |
| backend (JVM) | 688 MB | **1.18 GB** | `MaxRAMPercentage=75`, grows with load |
| postgres | 243 MB | 470 MB | |
| model | 138 MB | **380 MB** | after the NSFW classifier loads |
| redis | 7 MB | 14 MB | |
| **application total** | ~1.1 GB | **~1.8 GB** | |
| elasticsearch | 1.75 GB | — | |
| kibana | 1.02 GB | — | |
| filebeat | 170 MB | — | |
| **log stack total** | **~2.9 GB** | — | more than the application |

So:

- **CX23 / CAX11 (4 GB): the application fits** at ~1.8 GB with ~2 GB left for the OS and
  Postgres page cache. **The log stack does not.** Elasticsearch alone is more than the
  remaining memory.
- **CX33 (8 GB): both fit**, at ~4.7 GB, with headroom.

If you want Kibana, that is the argument for CX33 — not throughput. The alternative on a
4 GB box is to ship logs off the machine (a hosted Elastic/Loki/Grafana Cloud free tier) and
keep only Filebeat, which costs 170 MB.

Two smaller memory notes:

- **The NSFW classifier is lazily loaded and costs 242 MB** (138 → 380 MB) on first use. The
  first avatar upload after every restart also **takes 4.5 seconds** while the ViT loads. On
  a 4 GB box that 242 MB is real; consider pre-warming at startup so a user never pays the
  4.5 s.
- **750 concurrent STOMP sockets cost 67 MB** (688 → 755 MB), about 90 KB each, at 100%
  handshake success and 0.4% CPU while held. Sockets are cheap; 50,000 DAU worth of them is
  a rounding error. The in-memory SimpleBroker handled all 750 without complaint.

---

## Operational findings

**1. `/actuator/health` degrades with the application — a probe timeout will amputate a
healthy instance.** It shares the Tomcat request threads, so it queues behind the load. At
28 sessions/s on CX23 it stayed HTTP 200 but took a median of 205 ms and **p95 of 3836 ms,
max 4147 ms**; during the CX33 overload it exceeded a 5-second timeout entirely.

Compose's own healthcheck (`timeout: 5s`, `retries: 12`) tolerates this. A Kubernetes
liveness probe or a load balancer with a 2-second timeout would not: it would mark a slow
instance dead and remove it, shifting its load onto the remaining instances and taking them
down the same way. If GameBuddy ever runs behind a load balancer, give the probe a generous
timeout, or serve health from outside the request thread pool.

**2. Nothing sheds load.** See the failure mode above. There is no rate limit, queue bound
or circuit breaker between the API and a queue 28 seconds deep.

**3. The rate limiters are per-instance.** `MatchRateLimitConfig` (30 decisions/min) and
`AuthRateLimitConfig` (10/3/10 per 15 min) hold their state in process memory. A second
backend behind a load balancer doubles every limit. Not urgent at one instance — and one
instance is clearly enough — but it is a correctness cliff waiting at the moment you scale
out, which is exactly when nobody is looking at rate limits.

---

## What this test did not cover

Stated so the confidence is calibrated:

- **CAX11 / ARM64 performance.** Build feasibility confirmed; speed not measured.
- **The log stack under load.** Excluded deliberately — it does not fit on the small plans,
  and co-locating it here would have measured a configuration nobody can deploy.
- **Sustained soak.** Runs were 45–90 seconds. Backend memory grew from 688 MB to 1.18 GB
  under load and settled; that is normal JVM heap expansion, but a leak would need an hour
  to show. Worth one 60-minute run at 50k DAU before launch.
- **Scheduled jobs colliding with traffic.** The nightly retrain (00:00), impression purge
  (03:30) and outbox poller all run against the same database. At 3 a.m. against 27 req/s
  this is very unlikely to matter, but it is untested.
- **Real network latency.** Generator and system were on the same Docker network. Add
  20–50 ms of internet RTT to every number for what a phone actually experiences; it does
  not change the capacity conclusion.
- **The disk.** Hetzner NVMe versus this laptop's SSD. The working set is small and largely
  cached, so this is unlikely to bind.

---

## Reproducing this

```powershell
# One-time: isolated stack with the real population
docker compose stop
docker compose -p gamebuddy-perf -f docker-compose.yml -f qa/perf/docker-compose.perf.yml `
  -f qa/perf/plan.override.yml up -d postgres
docker compose -p gamebuddy-perf exec -T postgres psql -U gamebuddy -d gamebuddy < qa/perf/perf-seed.sql
node qa/perf/mint-tokens.js --count 20000 --project gamebuddy-perf

# Per plan: ladder + breakpoint sweep
cd qa/perf
.\run-plan.ps1 -Plan cx23
.\run-plan.ps1 -Plan cx33

# Tables
node qa/perf/report.js
```

`qa/perf/plan.js` holds the specs and caps; edit it there if Hetzner's line-up changes.
Raw results are in `qa/perf/results/`.
