"""The recommender: cluster to narrow the field, then rank by cosine similarity.

The two-stage shape is inherited from the original and is a reasonable pattern — the
change is in how each stage is done.

Clustering uses **MiniBatchKMeans** rather than AgglomerativeClustering. Agglomerative has
no ``predict``: the only way to place a new gamer is to refit the entire model, which is
why the original retrained on every profile edit, and it is O(n^3) in time and O(n^2) in
memory so that retrain stops completing somewhere in the low tens of thousands of users.
KMeans assigns a new gamer to a cluster in microseconds, which makes cold start a
non-event and lets training happen on a schedule instead of on the write path.

The number of clusters is chosen by silhouette score. The original computed silhouette and
Davies-Bouldin for every candidate k and then used whichever model the loop happened to
leave behind.

Ranking uses **cosine similarity** on TF-IDF profiles rather than Pearson correlation on
raw columns. Pearson on sparse binary data answers a question nobody asked; cosine
measures overlap, which is the actual question.

Similarity is measured in the **SVD-reduced space**, not on the raw sparse vectors. Two
gamers who both like tactical shooters but happen to have listed no title in common score
zero on raw cosine — sparse overlap is a brittle way to ask "similar taste?". Reducing
first lets a shared latent factor carry the resemblance. Measured on synthetic gamers whose
true taste vector is known, reduced cosine recovers that vector at r=0.30 against r=0.17
for raw cosine.

The component count and keyword weight were tuned against the **match graph**, not against
the latent vector. That distinction matters: latent taste is unobservable in production,
whereas likes and matches are logged, so this is a tuning procedure that can actually be
re-run on real data as it arrives.
"""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass, field

import numpy as np
from scipy.sparse import csr_matrix
from sklearn.cluster import MiniBatchKMeans
from sklearn.decomposition import TruncatedSVD
from sklearn.metrics import silhouette_score

from .features import FeatureSpace, fit_feature_space, transform
from .version import BuildInfo


@dataclass
class TrainingReport:
    n_gamers: int
    n_features: int
    n_components: int
    explained_variance: float
    chosen_k: int
    silhouette_by_k: dict[int, float] = field(default_factory=dict)
    cluster_sizes: list[int] = field(default_factory=list)

    #: Which library versions built this artefact. Defaulted rather than required so an
    #: artefact pickled before this field existed still unpickles — it just reports the
    #: loading environment instead of the training one, which is the honest answer when
    #: the truth was never recorded.
    build: BuildInfo = field(default_factory=BuildInfo)


@dataclass
class Recommender:
    space: FeatureSpace
    svd: TruncatedSVD | None
    kmeans: MiniBatchKMeans
    user_ids: list[str]
    vectors: csr_matrix
    reduced: np.ndarray
    labels: np.ndarray
    report: TrainingReport

    #: Per-gamer log-odds of being liked on sight, learned from the like log; ``None``
    #: until there is one. See ``fit_desirability``.
    desirability: np.ndarray | None = None

    #: (n,) bool. True for rows that may be *learned from* but never *returned* — the
    #: synthetic seed accounts, once real users arrive.
    #:
    #: The distinction is the whole point. A seed profile still shapes the IDF weights, the
    #: SVD components, the cluster centroids and the population mean of the desirability
    #: prior; with a thousand real users, a feature space fitted on twenty-one thousand
    #: profiles is far steadier than one fitted on the first thousand signups. What it must
    #: not do is appear in anyone's deck, because it will never answer a message.
    #:
    #: This cannot be done by filtering the response instead. ``/predict`` returns the top
    #: 150 of the whole artefact, and when 95% of the artefact is seed accounts, so is the
    #: top 150 — measured on the live database it was 150 out of 150. Discarding them
    #: afterwards leaves an empty deck rather than a short one, and the few real users who
    #: did survive would be whoever happened to crack the global top 150 rather than the
    #: best real matches. The exclusion has to happen before the cut, which is here.
    hidden: np.ndarray | None = field(default=None, repr=False)

    #: Whether to act on ``hidden``. Serve-time rather than train-time so visibility is a
    #: restart and not a retrain, and so it is reversible the same way: during development
    #: the seed accounts *are* the product and must be served, at launch they must not be.
    #: The API sets this from ``HIDE_SEED_ACCOUNTS``.
    hide_seed_accounts: bool = False

    #: How much weight the desirability prior gets, in units of the candidate pool's own
    #: spread of taste scores — see ``_score``. Swept against the held-out match graph,
    #: reading three things together, because precision alone picks the wrong value:
    #:
    #:     weight     0.00   0.25   0.50   1.00   1.50   2.50
    #:     lift        3.8    4.6    6.1    6.8    6.8    6.9
    #:     coverage   0.998  0.987  0.890  0.510  0.332  0.207
    #:     on-taste   19/20  20/20  20/20  17/20  --     14/20
    #:
    #: ``on-taste`` is the qualitative check: ask the model for cosy life-sim players and
    #: count how many of the twenty it returns actually have a cosy game on their profile.
    #:
    #: 0.5 is the settled value. It takes 88% of the available accuracy while still
    #: surfacing nine gamers in ten and returning a deck that visibly matches what was
    #: asked for. Past 1.0 the last 12% of precision costs a third of the taste coherence
    #: and half the population — the ranking is converging on the popularity baseline,
    #: which scores respectably on precision while showing the same fifty profiles to
    #: everybody, and which is a dead product for the other nineteen thousand.
    #:
    #: The prior is meant to break ties among plausible candidates, not to pick them.
    #: Pushing popular profiles harder is also self-reinforcing in a live product: the
    #: more they are shown, the more liked they get, the more they are shown.
    desirability_weight: float = 0.5

    _index_of: dict[str, int] = field(default_factory=dict, repr=False)
    _unit: np.ndarray = field(default_factory=lambda: np.empty(0), repr=False)

    def __post_init__(self) -> None:
        self._index_of = {uid: i for i, uid in enumerate(self.user_ids)}
        # Pre-normalise once so every similarity is a plain dot product.
        norms = np.linalg.norm(self.reduced, axis=1, keepdims=True)
        self._unit = self.reduced / np.where(norms == 0, 1.0, norms)

    # -- inference --------------------------------------------------------

    def similar_to(
        self,
        user_id: str,
        top_n: int = 100,
        *,
        exclude: Iterable[str] | None = None,
        include: Iterable[str] | None = None,
        restrict_to_cluster: bool = True,
    ) -> list[str]:
        """Ranks other gamers by taste similarity, most similar first.

        ``exclude`` is everyone the caller has already decided on. It has to be applied
        *here* rather than by the caller filtering the response: this ranking is a
        deterministic function of profiles, so a gamer who swipes through the top 100 would
        otherwise be handed the same 100 on every subsequent request — and the next
        retrain, ranking from the same profiles, returns almost exactly the same list. The
        feed goes empty and stays empty. Excluding before the cut instead means the ranking
        keeps descending into candidates the gamer has not seen.

        ``include`` is the opposite question and answers a different caller: the whole set
        of gamers who satisfy a filter, which only the backend can evaluate because this
        service knows nothing about countries, activity or the like. See ``_candidates``
        for why it exists in this direction rather than as a longer exclusion list.

        Returns an empty list for an unknown gamer rather than raising. The original
        indexed straight into the pickled frame, so anyone who had completed onboarding
        since the last training run got a KeyError and the endpoint answered 500 — which
        is every new user, at exactly the moment the app needs to look alive.
        """
        idx = self._index_of.get(user_id)
        if idx is None:
            return []

        blocked = self._excluded(exclude, idx)
        candidates = self._candidates(
            self.labels[idx] if restrict_to_cluster else None,
            top_n,
            blocked,
            self._allowed(include),
        )
        return self._top(candidates, self._unit[idx], top_n)

    def similar_to_profile(
        self,
        games: list[str],
        keywords: list[str],
        top_n: int = 100,
        *,
        platforms: list[str] | None = None,
        exclude: Iterable[str] | None = None,
        include: Iterable[str] | None = None,
    ) -> list[str]:
        """Ranks for a gamer who is not in the trained model yet.

        This is the cold-start path: a gamer who has just finished onboarding can be
        served immediately from their profile alone, without waiting for a retrain.

        ``platforms`` is keyword-only and optional so an older caller that passes just
        games and keywords keeps working — it simply ranks without the platform block
        rather than failing. ``include`` behaves as it does in ``similar_to``, and matters
        just as much here: a brand-new Gold subscriber filtering their first deck is served
        by this path, not by the trained one.
        """
        vector = transform(self.space, [games], [keywords], [platforms or []])
        if vector.nnz == 0:
            return []

        reduced = self._reduce(vector)
        norm = float(np.linalg.norm(reduced)) or 1.0
        unit = (reduced / norm).ravel()

        cluster = int(self.kmeans.predict(reduced)[0])
        candidates = self._candidates(
            cluster, top_n, self._excluded(exclude, None), self._allowed(include)
        )
        return self._top(candidates, unit, top_n)

    # -- internals --------------------------------------------------------

    def _top(self, candidates: np.ndarray, unit: np.ndarray, top_n: int) -> list[str]:
        """Scores a candidate pool and returns the best ``top_n`` ids, best first."""
        if candidates.size == 0:
            return []

        scores = self._score(candidates, unit)
        # argpartition first: the pool is the whole population whenever a cluster was too
        # small to fill the page, and a full sort of it to take the first hundred is work
        # nobody asked for.
        if len(scores) > top_n:
            top = np.argpartition(-scores, top_n)[:top_n]
            order = top[np.argsort(-scores[top])]
        else:
            order = np.argsort(-scores)
        return [self.user_ids[int(candidates[i])] for i in order]

    def _excluded(self, exclude: Iterable[str] | None, idx: int | None) -> set[int]:
        """Maps ids to row indices, dropping ids the model has never seen.

        Unknown ids are ignored rather than rejected: the caller's exclusion list comes
        from its own database, which knows about gamers created since the last retrain.
        """
        blocked = {idx} if idx is not None else set()
        for user_id in exclude or ():
            row = self._index_of.get(user_id)
            if row is not None:
                blocked.add(row)
        return blocked

    def _allowed(self, include: Iterable[str] | None) -> set[int] | None:
        """Maps an inclusion list to row indices, or ``None`` for "no restriction".

        ``None`` and the empty set mean opposite things and must never be conflated:
        ``None`` is "the caller is not filtering", the empty set is "the caller filtered and
        nobody qualifies". Collapsing them would answer a filter that matches nobody with an
        unfiltered deck, which is a worse failure than the one this parameter exists to fix
        — the gamer asked for people online in their country and got a page of people who
        are neither.

        Ids this artefact has never seen are dropped, as ``_excluded`` also does, but the
        asymmetry is worth naming. An unknown id in ``exclude`` is harmless; an unknown id in
        ``include`` silently narrows the pool. That is still correct — nothing here can rank
        a row it does not have — but it means a stale artefact serves a *shorter* filtered
        deck rather than a wrong one, and the fix is a retrain, not a code change.
        """
        if include is None:
            return None
        allowed: set[int] = set()
        for user_id in include:
            row = self._index_of.get(user_id)
            if row is not None:
                allowed.add(row)
        return allowed

    def _candidates(
        self,
        cluster: int | None,
        top_n: int,
        excluded: set[int],
        allowed: set[int] | None = None,
    ) -> np.ndarray:
        """The pool to score, after every restriction that applies before the cut.

        ``allowed`` is the eligible set for a narrowed feed, and it is expressed this way
        round on purpose. The backend used to send the complement — everyone the filter ruled
        *out* — which grows with the population rather than with the answer, and above ten
        thousand entries the request was refused outright and the caller was told the
        recommender was unavailable.

        Both directions have a size ceiling. What makes this one right is *when* it is
        reached: an exclusion list is longest when the filter is most narrowing, which is
        exactly when the caller cannot recover by filtering the response instead. An
        inclusion list is longest when the filter barely narrows anything — and then the
        caller dropping it and post-filtering the ranking costs almost nothing, because
        almost everyone qualifies. So the degraded path is only ever taken in the case where
        degrading is harmless.
        """
        keep = np.ones(len(self.user_ids), dtype=bool)
        if excluded:
            keep[list(excluded)] = False
        if allowed is not None:
            eligible = np.zeros(len(self.user_ids), dtype=bool)
            if allowed:
                eligible[list(allowed)] = True
            keep &= eligible
        if self.hide_seed_accounts and self.hidden is not None:
            # Before the cut, not after: see the note on `hidden`.
            keep &= ~self.hidden

        everyone = np.flatnonzero(keep)
        if cluster is None:
            return everyone

        pool = np.flatnonzero(keep & (self.labels == cluster))
        # A cluster can be smaller than the page we want to fill — and once exclusions are
        # applied it shrinks further, which is exactly the state a heavy swiper ends up in.
        # Widening to the whole population beats returning three people or none.
        #
        # This rule carries the inclusion case rather than needing one of its own: an
        # eligible set is spread across every cluster, so the caller's own cluster
        # intersected with it is usually far too small to fill a page, and this widens past
        # it automatically. `everyone` is already masked by `allowed`, so widening never
        # reaches somebody the filter excluded — it only stops the *cluster* narrowing a
        # deck that the filter has already narrowed.
        return pool if len(pool) >= top_n else everyone

    def _score(self, candidates: np.ndarray, unit: np.ndarray) -> np.ndarray:
        """Taste similarity, nudged by the popularity prior.

        The nudge is scaled by the **spread of taste scores in this candidate pool**,
        which is what makes it a tie-breaker rather than a second opinion. The prior is
        standardised to unit variance, so adding it raw meant a fixed-size shove against a
        similarity whose spread varies from pool to pool — and where the pool was tightly
        clustered, that shove was large enough to reorder it completely. Asked for cosy
        life-sim players, the model returned a battle-royale player and an MMO player
        ahead of five gamers with Stardew Valley and Animal Crossing on their profiles,
        purely because they were more liked. That is not a tie being broken, and it is the
        difference between a feed that looks like it understood you and one that looks
        random.

        Scaling by the spread means the prior decides only when taste has no strong
        opinion. Where one candidate is a clearly better fit, no amount of popularity
        overturns them.
        """
        scores = self._unit[candidates] @ unit
        if self.desirability is not None:
            spread = float(scores.std())
            scores = scores + self.desirability_weight * spread * self.desirability[candidates]
        return scores

    def _reduce(self, vectors: csr_matrix) -> np.ndarray:
        return self.svd.transform(vectors) if self.svd is not None else np.asarray(vectors.todense())

    # -- post-training calibration ----------------------------------------

    def fit_desirability(
        self,
        likes: list[tuple[str, str]],
        exposures: dict[str, int] | None = None,
        *,
        rater_weighting: bool = True,
    ) -> None:
        """Learns how readily each gamer is liked, from the like log.

        Taste is not the only thing that decides a match: some profiles are simply liked
        more often, and a pure content model is blind to that. On the synthetic population
        a popularity-only ranker scores P@10 0.049 against 0.026 for content alone, which
        is the measurement that motivated this — the content model was leaving a real and
        independent signal on the table.

        ``exposures`` is how many times each gamer was *shown* to someone. Pass it if the
        impression log exists: liked 40 times out of 200 is worse than liked 30 out of 60,
        and without the denominator the score measures reach rather than appeal. Without
        it the like count is used directly, log-damped so that one very visible gamer does
        not set the scale for everyone else.

        ``rater_weighting`` damps each rater's influence by the square root of how much
        they rate. Counting every opinion equally lets a single hyperactive account —
        someone who sits and rejects a thousand profiles — outweigh fifty ordinary users
        and drag down the score of everyone they saw. Daily caps slow that down but do not
        fix it, because a capped mass-rater still accumulates thousands of ratings a month;
        the fix has to be here, in how the ratings are counted. Sublinear rather than flat
        per-rater normalisation, because a genuinely active user *is* more informative than
        an idle one, just not proportionally so.

        Smoothed toward the population mean so that a gamer seen five times does not get a
        confident score, and standardised so the weight above means the same thing across
        populations.
        """
        if not likes:
            self.desirability = None
            return

        weight_of = self._rater_weights(likes) if rater_weighting else {}

        liked: dict[str, float] = {}
        for rater, target in likes:
            liked[target] = liked.get(target, 0.0) + weight_of.get(rater, 1.0)

        if not liked:
            self.desirability = None
            return

        if exposures:
            prior = sum(liked.values()) / max(sum(exposures.values()), 1)
            # Pseudo-observations, so a gamer with almost no impressions sits near the mean
            # instead of at whatever their first few swipes happened to be.
            strength = 20.0
            scores = np.array(
                [
                    (liked.get(uid, 0.0) + strength * prior) / (exposures.get(uid, 0) + strength)
                    for uid in self.user_ids
                ]
            )
        else:
            scores = np.log1p(np.array([liked.get(uid, 0.0) for uid in self.user_ids], dtype=float))

        spread = float(scores.std())
        if spread == 0.0:
            # Everyone equally desirable: the prior has nothing to say, and applying it
            # would add a constant to every candidate. Better to leave it off than to
            # carry a term that silently does nothing.
            self.desirability = None
            return

        self.desirability = (scores - scores.mean()) / spread

    @staticmethod
    def _rater_weights(likes: list[tuple[str, str]]) -> dict[str, float]:
        """Per-rating weight for each rater, damped by how much they rate.

        A rater who issues ``n`` ratings has each one weighted ``1/sqrt(n)``, normalised so
        the median rater is unaffected. Their total influence therefore grows as
        ``sqrt(n)`` rather than ``n``: someone rating a hundred times still counts for more
        than someone rating four times, but ten times more activity buys about three times
        the influence rather than ten.
        """
        activity: dict[str, int] = {}
        for rater, _ in likes:
            activity[rater] = activity.get(rater, 0) + 1

        raw = {rater: 1.0 / np.sqrt(count) for rater, count in activity.items()}
        # Anchor on the median so the overall scale of the scores does not shift with how
        # busy the population happens to be.
        median = float(np.median(list(raw.values()))) or 1.0
        return {rater: weight / median for rater, weight in raw.items()}


def train(
    user_ids: list[str],
    games_per_gamer: list[list[str]],
    keywords_per_gamer: list[list[str]],
    platforms_per_gamer: list[list[str]] | None = None,
    *,
    hidden: list[bool] | None = None,
    candidate_k: tuple[int, ...] = (4, 6, 8, 10, 12, 16, 20, 24),
    n_components: int | None = 16,
    variance_target: float = 0.80,
    max_components: int = 128,
    keyword_weight: float = 1.0,
    platform_weight: float = 1.0,
    random_state: int = 20260801,
    silhouette_sample: int = 3000,
) -> Recommender:
    """Fits the feature space, reduces it, and clusters it.

    ``n_components`` is the single most consequential knob here, and 16 is not a round
    number picked by feel: it is the peak of a sweep scored against the match graph, and
    the curve either side of it is shallow enough that anything from 12 to 20 performs
    about the same. Too few components and distinct tastes get folded together; too many
    and the noise in a six-game profile comes back. Pass ``None`` to fall back to choosing
    by explained variance, which is the more usual rule but performs worse here — variance
    keeps components that describe *how much gamers differ*, not *how they differ in ways
    that predict a match*.
    """
    space, vectors = fit_feature_space(
        games_per_gamer,
        keywords_per_gamer,
        platforms_per_gamer,
        keyword_weight=keyword_weight,
        platform_weight=platform_weight,
    )

    # TruncatedSVD rather than PCA: it works on sparse matrices without densifying them,
    # which for a few thousand gamers across several hundred games is the difference
    # between a small matrix and a large one.
    ceiling = min(max_components, vectors.shape[1] - 1, len(user_ids) - 1)
    svd: TruncatedSVD | None = None
    reduced: np.ndarray

    if ceiling >= 2:
        if n_components is not None:
            keep = max(2, min(n_components, ceiling))
        else:
            svd = TruncatedSVD(n_components=ceiling, random_state=random_state)
            svd.fit(vectors)
            # Keep the components needed to *reach* the variance target. The original
            # computed this correctly, printed it, and then passed the complement — the
            # count of components it did not need — to PCA.
            cumulative = np.cumsum(svd.explained_variance_ratio_)
            keep = max(2, min(int(np.searchsorted(cumulative, variance_target) + 1), ceiling))

        svd = TruncatedSVD(n_components=keep, random_state=random_state)
        reduced = svd.fit_transform(vectors)
        explained = float(np.sum(svd.explained_variance_ratio_))
    else:
        reduced = np.asarray(vectors.todense())
        explained = 1.0

    # Choose k by silhouette, and actually use the result.
    silhouette_by_k: dict[int, float] = {}
    best_k, best_score, best_model, best_labels = None, -1.0, None, None

    rng = np.random.default_rng(random_state)
    sample = (
        rng.choice(len(user_ids), size=silhouette_sample, replace=False)
        if len(user_ids) > silhouette_sample
        else np.arange(len(user_ids))
    )

    for k in candidate_k:
        if k >= len(user_ids):
            continue
        model = MiniBatchKMeans(n_clusters=k, random_state=random_state, n_init=5, batch_size=1024)
        labels = model.fit_predict(reduced)
        if len(set(labels[sample])) < 2:
            continue
        score = float(silhouette_score(reduced[sample], labels[sample]))
        silhouette_by_k[k] = score
        if score > best_score:
            best_k, best_score, best_model, best_labels = k, score, model, labels

    if best_model is None:
        # Too few gamers to cluster — a brand new deployment, or a test fixture. Falling
        # back to a single cluster keeps training a success rather than an exception: the
        # ranking still works, it just compares against everyone, which is the right
        # behaviour at this size anyway. Refusing to train would mean a new install has no
        # recommendations at all until it crosses some invisible threshold.
        best_k = 1
        best_model = MiniBatchKMeans(n_clusters=1, random_state=random_state, n_init=1)
        best_labels = best_model.fit_predict(reduced)

    report = TrainingReport(
        n_gamers=len(user_ids),
        n_features=space.n_features,
        n_components=reduced.shape[1],
        explained_variance=explained,
        chosen_k=int(best_k),
        silhouette_by_k=silhouette_by_k,
        cluster_sizes=np.bincount(best_labels, minlength=int(best_k)).tolist(),
    )

    return Recommender(
        space=space,
        svd=svd,
        kmeans=best_model,
        user_ids=list(user_ids),
        vectors=vectors,
        reduced=reduced,
        labels=best_labels,
        report=report,
        # Recorded whether or not it will be acted on, so that turning seed accounts
        # invisible is an environment variable and a restart rather than a retrain.
        hidden=np.asarray(hidden, dtype=bool) if hidden is not None else None,
    )
