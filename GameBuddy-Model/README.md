# GameBuddy — recommendation model

Ranks gamers by taste similarity so match-service can decide who to show next.

```bash
pip install -r requirements.txt

python -m gamebuddy_model generate --n 4000 --out ./data   # synthetic training population
python -m gamebuddy_model train    --data ./data --out ./artifacts
python -m gamebuddy_model evaluate --n 2000 --include-original
pytest tests
```

The service loads `artifacts/recommender.pkl` at startup and serves `POST /predict`. It
also screens uploaded images — see [Image moderation](#image-moderation).

---

## Does it work?

That question had no answer before, so it is the first thing here. Every model is scored
by the same harness (`gamebuddy_model/evaluate.py`) against the same ground truth: the
mutual-match graph. Nothing trains on that graph, so all of it is held out.

Measured on 2000 synthetic gamers, 1500 evaluated:

| model | P@10 | R@20 | HitRate@10 | MAP@20 |
|---|---|---|---|---|
| random | 0.013 | 0.019 | 0.107 | 0.004 |
| **original pipeline** | 0.021 | 0.027 | 0.173 | 0.008 |
| popularity only | 0.046 | 0.069 | 0.340 | 0.021 |
| rewrite, content only | 0.036 | 0.053 | 0.258 | 0.014 |
| **rewrite, hybrid** | **0.055** | **0.082** | **0.399** | **0.022** |
| *oracle (true taste vector)* | *0.090* | *0.144* | *0.545* | *0.044* |

**2.6× the original, and 20% above a popularity-only ranker.** Reproduce with
`python -m gamebuddy_model evaluate --include-original`.

Three of those rows exist to keep the result honest:

- **The original is reimplemented, not described.** `evaluate.OriginalPipeline` reproduces
  the notebook faithfully — the same label-encoded country, the same inverted PCA
  component count, the same double-joined games block. The comparison is measured.
- **Popularity is the baseline that matters.** Recommending whoever gets liked most beats
  a great deal of honest work, and a "recommender" that fails to beat it has learned who
  is popular rather than who is compatible. Content-only *loses* to it — which is not a
  failure, it is the measurement that showed desirability is a real and separate signal,
  and led directly to the hybrid.
- **The oracle is the ceiling.** It ranks by the true hidden taste vector, which no model
  can see. At P@10 0.090 it says the remaining headroom is real but bounded: matching is
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
Recovering the hidden vector from the sampled profile is the actual job, and because the
generator knows the true vector, it can also state the ceiling.

Calibrated so the graph is neither empty nor saturated: like rate 0.24, ~9.5% of shown
pairs become mutual, median 11 matches per gamer.

Games and titles are real. Countries are drawn **independently of taste** on purpose, so
the evaluation can demonstrate that country carries no compatibility signal rather than us
having to argue it.

### Minors and adults never meet

Enforced in the generator, not just downstream: minors and adults are never shown to each
other, so a cross-band match is impossible rather than merely improbable. Asserted by
`summarise()`, by two tests, and re-checked in SQL after seeding. The evaluation also only
scores same-band candidates, since the backend refuses a cross-band pairing outright.

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

## Layout

```
gamebuddy_model/
  catalogue.py     14 taste archetypes over real game titles
  population.py    the latent generative model
  features.py      TF-IDF vectorisation (no age, no country)
  recommender.py   SVD + MiniBatchKMeans + cosine, and the desirability prior
  evaluate.py      metrics, baselines, faithful replica of the original
  seed.py          SQL and CSV export
GameBuddy-ModelApi/api/main.py   FastAPI service
tests/                            39 tests
```

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

## Known limits

- **Content-only, no collaborative filtering.** Nothing yet learns from *who liked whom*
  beyond the popularity prior. Matrix factorisation over the like graph is the obvious next
  step and should close part of the gap to the oracle — it needs real interaction data.
- **Tuned on synthetic data.** The hyperparameters are honest for this population. The
  procedure transfers to real data; the specific numbers should be re-derived once there is
  some, which is why the harness is a first-class part of the package.
- **The desirability prior is self-reinforcing.** Shown more → liked more → shown more. It
  is deliberately weighted low (0.15), and if it ever goes live it needs an exploration
  term and monitoring of how concentrated impressions become.

## License

MIT.
