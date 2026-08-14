# GameBuddy Security Assessment — Runbook

A repeatable two-persona security exercise for GameBuddy, run **before** the Hetzner deploy
(local) and **after** (live host). Two authorized personas attack the owner's own system; the
output is a single ranked report, `SECURITY_REVIEW.md` at the repo root.

## Personas (`.claude/agents/`)

| Persona | File | Role |
|---|---|---|
| White-hat auditor | `.claude/agents/security-whitehat.md` | Methodical review; verifies controls; ranks findings with fixes. Read-only, never modifies app code. |
| Black-hat red-teamer | `.claude/agents/security-blackhat.md` | Attacks like a real adversary; destructive/DoS in scope; chains weaknesses. |

Both carry the same **Rules of Engagement**: destructive/DoS testing is in scope against
GameBuddy's own stack; the only hard limits are (1) never attack third-party providers
(RevenueCat, Firebase, AdMob/Google, Cloudflare, Hetzner panel, R2), and (2) keep volumetric
network floods on the live host bounded and operator-approved. Discovered secrets are never
reproduced — referenced by location only.

## Rounds

- **Round 1 — LOCAL (before Hetzner):** static code review + live testing against the local
  `docker-compose` stack. Snapshot the DB first.
- **Round 2 — HETZNER (after deploy):** same live suite against the public host + external
  exposure scan + TLS/headers check + regression check that Round-1 findings are fixed.
  Requires the operator to supply the host address and explicit authorization.

## Bring up the local stack (Round 1, Phase B)

```bash
# From the repo root. Requires Docker Desktop running.
docker compose up -d --build          # postgres, model, redis, backend (+ dev log stack)
docker compose ps                     # confirm health
# Seed data is applied from db/seed-local.sql on first boot.
```

Base URL: `http://localhost:8080` (backend), `http://localhost:8000` (model API).
Import `documentation/GameBuddy.postman_collection.json` +
`documentation/GameBuddy-Local.postman_environment.json` for the full endpoint set.

## Snapshot / restore (mandatory before destructive tests)

```bash
# Snapshot
docker compose exec -T postgres pg_dump -U "$DB_USER" "$DB_NAME" > backups/pre-sectest-$(date +%Y%m%d-%H%M%S).dump
# Restore if a destructive test damages state
cat backups/<snapshot>.dump | docker compose exec -T postgres psql -U "$DB_USER" "$DB_NAME"
```

## Run a round

From Claude Code in this repo:

```
# Launch each persona (they read the code and hit the running stack):
> Use the security-whitehat agent to run Round 1 (local) against http://localhost:8080 and the model API on :8000.
> Use the security-blackhat agent to run Round 1 (local) against the same stack. Snapshot the DB first.
```

For **Round 2**, add the live host and the explicit go-ahead, e.g.:

```
> Use the security-blackhat agent to run Round 2 against https://api.<domain> — this is my server, authorized.
> Keep volumetric floods bounded; app-layer destructive tests are approved.
```

The orchestrator merges both personas' findings (plus the recon head-start) into
`SECURITY_REVIEW.md`: executive summary → findings table → per-finding detail → prioritized
go-live checklist → attested controls.

## Notes

- Local DB is synthetic seed data — full-exfiltration PoCs are legitimate here.
- If Docker is unavailable, Phase A (static) still runs; Phase B is marked pending.
- Never commit `SECURITY_REVIEW.md` findings that contain live secret values — reference by
  location only.
