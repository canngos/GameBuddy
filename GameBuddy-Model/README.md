# GameBuddy — recommendation model

Ranks gamers by taste similarity so match-service can decide who to show next.

```bash
pip install -r requirements.txt                 # the service
pip install -r requirements-tools.txt           # plus training, export and tests

python -m gamebuddy_model generate --out ./data             # synthetic training population
python -m gamebuddy_model train    --data ./data --out ./artifacts
python -m gamebuddy_model evaluate --n 4000 --include-original
pytest tests
```

Against a live database, `export` replaces `generate` and everything downstream is
identical — see [Where the training data comes from](#where-the-training-data-comes-from).

The service loads `artifacts/recommender.pkl` at startup and serves `POST /predict`. It
also screens uploaded images — see [Image moderation](#image-moderation).

---

## Does it work?

That question had no answer before, so it is the first thing here. Every model is scored
by the same harness (`gamebuddy_model/evaluate.py`) against the same ground truth: mutual
matches formed in the **held-out half** of the swipe log.

The split is not a formality. The earlier version of this table scored every model against
the whole match graph, on the argument that no model trains on it. That was true of the
content model and false of the two that mattered: the popularity baseline ranks by like
count, the hybrid fits its desirability prior from the like log, and a mutual match *is*
two likes. Both were being scored on answers they had been shown, and popularity's lead
was substantially that leak. `split_exposures` now cuts the log in half; anything learned
from a like comes from the fitting half, and every model is scored on matches in the other.

Measured on 4000 synthetic gamers, 1500 evaluated:

| model | P@10 | R@20 | HitRate@10 | MAP@20 | coverage |
|---|---|---|---|---|---|
| random | 0.0028 | 0.0049 | 0.027 | 0.0012 | 1.00 |
| **original pipeline** | 0.0033 | 0.0067 | 0.033 | 0.0010 | 0.92 |
| popularity only | 0.0109 | 0.0204 | 0.099 | 0.0040 | **0.01** |
| rewrite, content only | 0.0069 | 0.0138 | 0.064 | 0.0026 | 1.00 |
| **rewrite, hybrid** | **0.0113** | **0.0198** | **0.108** | **0.0044** | **0.98** |
| *oracle (latent state)* | *0.0184* | *0.0449* | *0.161* | *0.0105* | *0.99* |

**3.4× the original, 4.0× random, and 61% of the way to a model that can read minds.**
Reproduce with `python -m gamebuddy_model evaluate --n 4000 --include-original`.

Read precision as a *ratio to random at the same population size*, not as an absolute.
Exposure per gamer is roughly constant, so as the population grows the relevant set stays
the same size while the candidate pool does not, and every model's precision falls for
arithmetic reasons that say nothing about the model.

Four of those rows exist to keep the result honest:

- **The original is reimplemented, not described.** `evaluate.OriginalPipeline` reproduces
  the notebook faithfully — the same label-encoded country, the same inverted PCA
  component count, the same double-joined games block. The comparison is measured.
- **Popularity is the baseline that matters.** Recommending whoever gets liked most beats
  a great deal of honest work, and a "recommender" that fails to beat it has learned who
  is popular rather than who is compatible.
- **Coverage is why popularity is not the answer.** It scores well on precision while
  showing the same 1% of profiles to everybody. For the other 99% that is not a slightly
  worse product, it is no product — they are never surfaced to anyone. The hybrid beats it
  on every accuracy metric *and* surfaces 96% of the population, and that column is the
  reason the desirability weight is set where it is.
- **The oracle is the ceiling.** It ranks by the true hidden latent state, which no model
  can see. At P@10 0.0183 it says the remaining headroom is real but bounded: matching is
  irreducibly noisy, because people decline for reasons no profile contains.

## What the original did, and what changed

| | original | now |
|---|---|---|
| features | age + label-encoded country + binary games/keywords | TF-IDF games + keywords only |
| similarity | Pearson correlation | cosine in SVD-reduced space |
| clustering | AgglomerativeClustering, no `predict` | MiniBatchKMeans |
| k | whichever the loop ended on | best silhouette |
| components | complement of the variance target | tuned on the match graph |
| new user | `KeyError` → HTTP 500 | cold-start endpoint |
| retraining | executed a notebook on an unauthenticated `GET` | offline batch job |
| auth | none | shared internal key |

The three that mattered most:

**Age and country dominated the similarity.** They sat unscaled among hundreds of 0/1
columns — an age near 22 and a label-encoded country near 37 against features that are 0 or
1. Pearson correlation is driven by covariance, so "similar taste" was mostly "similar age,
same country". Age is a safety constraint enforced as a hard filter by the backend; country
is a filter at most. Neither belongs in a taste vector. Label-encoding also told the
distance function that Turkey is nearer Sweden than Japan, because of the alphabet.

**Clustering could not serve a new user.** AgglomerativeClustering has no `predict`, so
placing one new gamer meant refitting everything — which is why retraining was wired to an
HTTP endpoint. It is also O(n³); that retrain stops completing in the low tens of
thousands of users. KMeans assigns a new gamer in microseconds.

**`GET /updateData` executed a Jupyter notebook in-process**, unauthenticated, with a 2000
second timeout. Anything that crawled the service could trigger a retrain, and GET is
specified as safe, so a browser prefetch was enough.

## The synthetic population

Generating training data for a matching model has one trap, and it is worth stating
because it is the reason a first attempt at this usually produces nothing:

> If matches are assigned by a rule over the visible profile — *A likes FPS, B likes FPS,
> so they match* — then the model either recovers the rule trivially or learns nothing,
> because the observed games **are** the rule. There is no hidden structure to find.

So matches here are not a function of the profile. Each gamer draws a hidden mixture over
14 taste archetypes (`catalogue.py`); their games and keywords are **sampled** from that
mixture, mixed with a popularity effect, and are therefore noisy, partial evidence of it.
Swipes are drawn from the **hidden** affinity plus per-gamer pickiness and desirability.
Recovering the hidden state from the sampled profile is the actual job, and because the
generator knows the true state, it can also state the ceiling.

### Two axes, because one is not how people work

A hidden mixture alone is not enough, and the way it fails is instructive. Affinity used to
be the plain cosine between two mixture vectors — but cosine knows nothing about what the
archetypes *mean*, so the only way to be compatible was to be nearly the same, and whatever
cross-genre matching survived was Dirichlet noise. Measured on the population that produced,
battle-royale and competitive-FPS players came out as the **coldest pair in the entire match
graph**, despite sharing Apex Legends and Warzone in the catalogue. The recommender reads
games, so it saw those two groups as close; the labels said they were not; and that
disagreement is a ceiling on any model's score.

Affinity is now three terms:

- **Taste** (0.58) — the mixture cosine *through* `catalogue.WARMTH`, a hand-written 14×14
  table of which genres get along, projected onto the PSD cone so it is a real kernel.
  Neighbouring tastes are genuinely warm, and the background floor of 0.08 means no pair is
  at zero.
- **Vibe** (0.32) — assortative on a three-axis temperament vector (`competitiveness`,
  `sociability`, `commitment`) that the archetype predicts but does not determine.
  Temperament varies *within* a genre: the tryhard and the chill player queue into the
  same shooter, and they should not be each other's best match.
- **Platform** (0.10) — binary: do you share one at all. Not a taste but a constraint, and
  the only one of the three that can be close to a no. Tuned by the ratio it produces
  rather than by feel, because a binary term goes a long way: at 0.15 gamers sharing a
  platform matched 11× as often as those who did not, which is a veto wearing a penalty's
  clothing and too harsh for a catalogue where Fortnite, Rocket League, Apex, Minecraft and
  Call of Duty are all crossplay. At 0.10 it is 4×, and cross-platform pairs still match 4%
  of the time.

Keywords are split to follow. The `chill`/`tryhard`, `voice chat`/`no mic`,
`long sessions`/`short sessions` tags are sampled from the vibe vector; `waifu collector`,
`emulator user`, `sim racer` and the rest still come from the mixture. That is what makes
the feature space's keyword weight a knob with something to tune — before the split,
keywords were a second noisy copy of the games signal.

The result is graded rather than tidy. Match rate by how warm the pair of archetypes is:

| pair | match rate |
|---|---|
| same archetype | 0.474 |
| warm cross-genre (0.52–0.75) | 0.213 |
| warm cross-genre (0.38–0.52) | 0.152 |
| mild cross-genre (0.22–0.38) | 0.125 |
| coldest cross-genre (0.08) | 0.076 |

**74% of all matches cross taste archetypes**, and even the coldest pairs match 7.6% of the
time — carried by the vibe axis, which is exactly right: a competitive shooter player and a
cosy life-sim player who are both chill, both solo, both short-session *should* sometimes
get along. What the table rules out is the two failure modes on either side, a graph where
everyone matches their own kind and a graph where cross-genre matching is uniform noise.
Both are asserted against in `tests/test_pipeline.py`.

Calibrated so the graph is neither empty nor saturated: like rate 0.24, 13% of shown pairs
become mutual, median 12 matches per gamer, 26% of matches same-archetype against a 7.1%
chance rate.

Games and titles are real. Countries are drawn **independently of taste** on purpose, so
the evaluation can demonstrate that country carries no compatibility signal rather than us
having to argue it.

Ages are young-skewed but not truncated: median 26, 87% under 35, with a right tail that
reaches into the sixties and beyond. A plain normal around the archetype means stopped the
population dead at about 50, which is a quiet decision that older gamers do not exist —
and the sort of thing only noticed when someone browses the seeded database and finds it
looks nothing like their users. Bounds match `MIN_AGE`/`MAX_AGE` in the app's
`validation.ts`.

### Platform is in the vector, and the first version was wrong to leave it out

The original argument was that the backend already applies platform as a hard filter, so
encoding it as similarity would count it twice. That is wrong on the facts. The filter is
a Gold entitlement and defaults to null — `FeedFilters.narrowing()` treats setting it as a
paid narrowing — so for a Basic account, and for any Gold account that has not opened the
filter sheet, platform never entered the ranking at all. It was not being counted twice; it
was not being counted.

It is also not a soft preference. Two gamers with identical libraries on different boxes
cannot play most of those games together, which is a firmer constraint than any amount of
shared genre is a recommendation.

Its feature weight is set to 1.0, and the sweep is the interesting part:

| platform weight | 0.0 | 0.5 | 1.0 | 1.4 |
|---|---|---|---|---|
| taste recovery | 0.206 | 0.208 | 0.210 | 0.158 |
| vibe recovery | 0.227 | 0.228 | 0.226 | 0.194 |
| platform recovery | 0.003 | 0.020 | 0.128 | 0.459 |

Precision keeps climbing past 1.0, and 1.0 is still the right answer — because past it the
gain comes from platform crowding out the other two. That is a bad trade *specifically
because* platform is perfectly observable: a `WHERE` clause knows it for free. Spending the
model's limited capacity on a fact a query already has, at the cost of the taste and
temperament signal that nothing else can recover, is spending it where it is worth least.

### Adults only

The product is 18+: the signup screen gates on it and the backend refuses a younger birth
date. The generator produces no one under 18, so a seeded profile is never one the product
is not allowed to have. `summarise()` reports the count and a test asserts it is zero.

### How much data

Decided by measurement, not by feel. `--n` defaults to **20,000**, from a scaling study
across 1k–50k reading the model's recovery of the latent state — a far lower-variance
number than precision@10 at these match densities:

| gamers | 1,000 | 2,500 | 5,000 | 10,000 | 20,000 | 50,000 |
|---|---|---|---|---|---|---|
| latent recovery | 0.203 | 0.245 | 0.287 | 0.296 | **0.303** | 0.308 |
| generate | 0.2s | 0.5s | 1.0s | 2.3s | **4.0s** | 11.3s |

It flattens between 10k and 20k. Twenty thousand gets 98% of what fifty thousand does, for
a third of the generation time and a quarter of the seed file, and re-running the whole
comparison under three different seeds moves each model's score by less than the gaps
between the models — so the ordering is a property of the design, not of `seed=20260801`.

### Bot accounts are training fixtures, not users

`generate` writes `data/seed.sql`. **It targets a separate `schtrain` schema by default.**

If these rows land in the live `schauth.gamer` table they become swipeable, and a real
person is shown a profile that will never answer — the app looks dead in exactly the way
seeding was meant to prevent, and a fake profile a real user can message is a fake profile
they can be deceived by. `--schema schauth` exists because seeding a local or staging
database is legitimate, but it is opt-in, and every account stays reversible:

- email domain `@bot.gamebuddy.invalid` — `.invalid` is reserved by RFC 2606, so it can
  never collide with a real signup or be mailed;
- `pwd` is not a valid bcrypt hash, so no one can log in as one;
- `DELETE FROM ... WHERE email LIKE '%@bot.gamebuddy.invalid'` removes every trace.

Showing bot profiles to real users is a product and legal decision that needs disclosure in
the app. It is not something a seed script should decide.

### Keeping the seed population without serving it

At launch the seed accounts should stop appearing in decks — they will never answer a
message — but they are worth keeping in the *training* set: with a thousand real users, a
feature space fitted on twenty-one thousand profiles is far steadier than one fitted on the
first thousand signups. `HIDE_SEED_ACCOUNTS=true` on the model service does exactly that
split. It is a restart, not a retrain, and it is reversible.

It has to happen inside the ranking, which is why it is not simply a filter on the
response. `/predict` returns the top 150 of the whole artefact, so when 95% of the artefact
is seed accounts, so is the top 150 — measured against the live database it was **150 out
of 150**. Discarding them afterwards leaves an empty deck rather than a short one, and the
few real users that did survive would be whoever cracked the global top 150 rather than the
best real matches.

The service says which mode it is in at every load, because both failure directions look
identical from outside — a feed that is wrong:

```
WARNING  Serving 20000 seed account(s) of 20001 as real candidates. Fine in development;
         set HIDE_SEED_ACCOUNTS=true before real users arrive.
ERROR    Only 1 gamer(s) can be recommended after hiding seed accounts. Decks will be
         empty or near-empty.
```

**When to stop training on them too.** The seed profiles pin the IDF weights, the SVD
components and the cluster centroids to the synthetic archetypes in `catalogue.py`. Early
that is useful regularisation; once real users outnumber the seed it becomes a drag, because
the model is then measuring real people against axes invented for fake ones. Do not guess
the crossover — train both ways and compare on held-out real matches, which is what
`evaluate.py` is for. The desirability prior is unaffected either way, since `export`
excludes seed interactions by default.

## Layout

```
gamebuddy_model/
  catalogue.py     14 taste archetypes, the warmth matrix, the vibe axes
  population.py    the latent generative model
  features.py      TF-IDF vectorisation (no age, no country, no platform)
  recommender.py   SVD + MiniBatchKMeans + cosine, and the desirability prior
  evaluate.py      metrics, baselines, the split, faithful replica of the original
  seed.py          SQL and CSV export of a synthetic population
  export.py        the same CSVs, read out of a live Postgres
  version.py       records the training environment in the artefact
GameBuddy-ModelApi/api/main.py   FastAPI service
tests/                           87 tests
```

## Where the training data comes from

Two sources, one format. `train` reads four CSVs and cannot tell which produced them,
which is the property that makes a result measured offline mean something in production.

```
# offline: tune and test, where the latent state is known and answers can be checked
python -m gamebuddy_model generate --n 20000 --out ./data
python -m gamebuddy_model evaluate --n 4000 --include-original

# production: build the artefact the app actually serves
python -m gamebuddy_model export --out ./data --database-url postgresql://...
python -m gamebuddy_model train  --data ./data --out ./artifacts
curl -X POST -H "X-Internal-Api-Key: $INTERNAL_API_KEY" .../admin/reload
```

`export` is what closes the loop, and its absence used to be a quiet correctness problem
rather than a missing feature. `similar_to` can only ever return ids that are **in the
artefact**, so whoever it was trained on is the entire candidate universe. An artefact
built only from synthetic gamers cannot recommend a real one however many sign up — new
accounts are served through cold start, which ranks them *against* the pool without adding
them *to* it. Bot accounts are excluded by default, since training on profiles nobody is
behind teaches the model about people who will never answer.

### The nightly retrain

`tools/retrain.py` runs that loop on a schedule — daily at midnight, in the users'
timezone. Daily is the right cadence because it is only needed to admit new gamers to the
candidate pool and refresh the desirability prior; profile *edits* are already handled the
same day by the staleness flag routing that gamer to cold start.

```bash
docker compose up -d model-retrain            # compose: a container that sleeps and wakes
kubectl apply -f k8s/model-retrain-cronjob.yaml   # kubernetes: a real CronJob
python tools/retrain.py --database-url ...    # or once, by hand
```

It exists as a script rather than three commands in a cron line because of what happens
when a step goes wrong at midnight, which is where the interesting failures are:

| failure | what it looks like without a guard | guard |
|---|---|---|
| export returns a fraction of the population | training succeeds; a valid artefact that knows almost nobody replaces one that knew everybody; every deck goes thin and nothing errors | `--min-gamers`, plus a floor at 70% of the currently-served population — applied only when the artefact in place recognises at least half the export, so a launching product's first real retrain is not read as a collapse against the synthetic artefact it is replacing |
| artefact is corrupt or ranks nothing | swapped in, feed dies | loaded and asked real questions — including a cold-start probe — before it goes near the serving path |
| service reloads mid-write | unpickles a truncated file | written to a temp name, then `os.replace`, which is atomic |
| a bad artefact passes anyway | retrain to recover | the previous seven are kept beside it; rollback is a rename |

The posture throughout is **leave the old artefact serving**. A model a day stale is a good
product; no model is an error page. Every check exits non-zero without touching what is
live, so the exit code is the thing worth alerting on.

One consequence worth stating: because the retrain reads the database, **the database is
the source of truth**, and the seeded population must be the whole population the artefact
was trained on. Seeding a sample of it means every export looks like a collapse and the
job refuses — correctly — every night forever. `tools/seed_local_gamers.py` seeds all
20,000 for that reason.

## Operating it

Training is a batch job. Run it on a schedule, then `POST /admin/reload` to swap the
artefact in without a restart. Nightly is ample — taste does not change hourly, and new
gamers are served by `/predict/cold-start` in the meantime.

`INTERNAL_API_KEY` must be set and must match the value match-service sends. If it is
unset the service refuses every request rather than defaulting to open: this endpoint
returns a list of gamer ids given a gamer id, so failing open would publish the social
graph.

## Image moderation

The app lets gamers upload their own avatar and it admits under-18s, so nothing an account
uploads reaches another account unscreened. That screening lives here rather than behind a
paid vision API, which would bill per image forever.

```
POST /moderate/image          multipart, field name "file"
X-Internal-Api-Key: ...

→ 200 {"verdict": "APPROVE" | "REVIEW" | "REJECT", "score": 0.0–1.0}
→ 400 the upload was not a decodable image
```

The model is `Falconsai/nsfw_image_detection` (ViT-base, Apache-2.0, ~350MB), baked into
the image at build time and loaded lazily on the first request — a deployment that never
moderates anything never pays for it, and `/predict` still starts in a second.

**Three outcomes, and the caller must honour all three.** Any single threshold is wrong in
one direction: set it low and ordinary photographs are refused, set it high and the thing
it exists to stop gets through. `REVIEW` is the middle band — stored where only its owner
can see it, published or destroyed by a human. A caller that treats `REVIEW` as approval
has removed the point of having it. Both thresholds are environment variables
(`NSFW_REJECT_THRESHOLD`, `NSFW_APPROVE_THRESHOLD`) because they are a moderation policy,
not a property of the model, and should be retuned against real traffic.

**A 400 is not a rejection.** "This is not an image" and "this is pornography" are
different answers; a client that conflates them tells an innocent user something untrue.

Bytes are posted rather than a URL on purpose. The service needs no object-storage
credentials, and a URL parameter would let anyone who can reach it make the service fetch
an arbitrary host from inside the private network.

### What it does not do

It answers one narrow question — does this look like pornography — probabilistically. It
does not recognise a minor, a photograph of someone who did not consent to being uploaded,
or a picture of a screen showing either. Those need a human, a report button and a
takedown path. **The classifier is a filter, not a moderation policy**, and it is not on
its own an answer to what the law requires of a service that hosts user images and admits
children.

## Performance

Measured against the running service and, for the model itself, in-process — the HTTP
round trip on Docker-for-Windows is ~15 ms of pure networking, enough to bury what is
being measured. `/health`, which does no model work at all, costs the same 15 ms.

| population | rank | rank + 10k exclusions | cold start | resident | train | artefact |
|---|---|---|---|---|---|---|
| 20,000 | 0.12 ms | 1.33 ms | 0.92 ms | 21 MB | 2.7 s | 9.7 MB |
| 50,000 | 0.31 ms | 2.11 ms | 0.97 ms | 54 MB | 4.2 s | 24 MB |
| 100,000 | 0.96 ms | 4.72 ms | 1.59 ms | 108 MB | 5.9 s | 48 MB |
| 200,000 | 2.54 ms | 9.25 ms | 3.90 ms | 217 MB | 14.7 s | 97 MB |

End to end over HTTP: **p50 9 ms, p95 31 ms, ~580 req/s sustained** from a single
container, falling to ~350 req/s once exclusion lists push the request past 200 KB.
Roughly 1.1 KB of memory per gamer, no leak across 8,000 requests, 1.5 s from container
start to serving, 25 ms to reload an artefact.

One deck refresh covers about fifty swipes, so 580 req/s is far more headroom than this
product needs; the exclusion list, not the ranking, is what makes a request expensive.

The handlers are `def` rather than `async def` on purpose. The work is CPU-bound numpy,
and as coroutines it ran on the event loop — one request with a large exclusion list
delayed every other request in flight, including the readiness probe. The image
moderation endpoint was the worst of them: a ViT forward pass is hundreds of milliseconds,
so a burst of avatar uploads could stall the feed for whoever was swiping. Sync handlers
go to FastAPI's threadpool and genuinely overlap.

## Known limits

- **Content-only, no collaborative filtering.** Nothing yet learns from *who liked whom*
  beyond the popularity prior. Matrix factorisation over the like graph is the obvious next
  step and should close part of the gap to the oracle — it needs real interaction data.
- **Tuned on synthetic data.** The hyperparameters are honest for this population. The
  procedure transfers to real data; the specific numbers should be re-derived once there is
  some, which is why the harness and `export` are first-class parts of the package. Rerun
  the keyword-weight and desirability sweeps on the first real export — the synthetic
  population's desirability is a single global per-person trait, which is a generous model
  of a thing that is partly taste-specific in reality, so the prior may deserve less weight
  on real data than it earns here.
- **The desirability prior is self-reinforcing.** Shown more → liked more → shown more. It
  is scaled by the candidate pool's own spread so it can only break ties, and weighted so
  96% of the population still gets surfaced; the backend adds a 10% exploration term on
  top. If it goes live, the number to watch is impression concentration, and the coverage
  column in `evaluate` is the offline version of that alarm.
- **The warmth matrix is a set of opinions.** `WARM_PAIRS` is hand-written — informed
  opinions about which genres share an audience, but not measured. The first real export
  can replace it: the empirical match rate between archetype pairs is directly observable
  once there are enough real matches to estimate it.

## License

MIT.
