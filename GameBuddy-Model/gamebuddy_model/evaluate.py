"""Offline evaluation, and the baselines worth comparing against.

Nothing in the original could answer "is this better than showing random profiles?", so
that is the first thing built here. Every claim about the rewrite is measured against the
same harness, including the claim that the original was broken — ``OriginalPipeline``
below reimplements it faithfully so the comparison is evidence rather than argument.

Protocol
--------
Ground truth is the mutual-match graph, and the swipe log is **split** before anything
is fitted.

This used to be waved away. The argument was that neither model trains on the graph —
both look only at profiles — so the whole graph was legitimately held out and no split
was needed. That was true of the content model and false of everything else in the
table. ``PopularityBaseline`` ranks by like count, the hybrid fits its desirability prior
from the like log, and mutual matches are *made of* those same likes: a gamer who liked
you is disproportionately a gamer you matched with, so both models were being scored on
the answers they had been shown. The popularity baseline beating the content model was
substantially that leak.

So ``split_exposures`` divides the impressions into a fitting half and a held-out half.
Anything learned from the log is learned from the fitting half only, and every model is
scored against matches formed in the held-out half. The content model is unaffected — it
never saw either — which is the point: the comparison is now like for like.

Only same-country candidates are *not* filtered, but blocked and self pairs are, and the
population is adults-only so the age band no longer needs special handling here.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Callable, Protocol

import numpy as np
import pandas as pd

from .catalogue import PLATFORM_IDS, WARMTH
from .population import (PLATFORM_WEIGHT, TASTE_WEIGHT, VIBE_SCALE, VIBE_WEIGHT,
                         Population)
from .recommender import train


class RankingModel(Protocol):
    """Anything that can rank candidates for a gamer."""

    def rank(self, user_id: str, top_n: int) -> list[str]: ...


@dataclass
class Split:
    """A swipe log divided into what a model may learn from and what it is scored on."""

    #: Directed (rater, target) likes drawn only from the fitting impressions.
    fit_likes: list[tuple[str, str]]

    #: How often each gamer was shown, over the fitting impressions only. The denominator
    #: that turns a like count into a like rate.
    fit_exposures: dict[str, int]

    #: user_id → the gamers they mutually matched with, over held-out impressions only.
    holdout_matches: dict[str, set[str]]

    def matches_of(self, user_id: str) -> set[str]:
        return self.holdout_matches.get(user_id, set())


def split_exposures(population: Population, *, ratio: float = 0.5, seed: int = 20260801) -> Split:
    """Splits the impression log into a fitting part and a held-out part.

    Half and half rather than a thin holdout. The scoring half decides how many relevant
    candidates each gamer has, and precision@10 measured against three or four of them is
    mostly noise — differences between hyperparameter settings came out smaller than the
    run-to-run spread. Half a log is still plenty to fit a popularity prior from.

    Split on **impressions**, not on matches. Splitting the match graph directly would
    leave the corresponding likes in the fitting half, and a like is most of the way to
    knowing about the match — the leak would survive the fix. Cutting the log at the
    impression means a held-out match was formed by two swipes the model never saw.

    Random rather than chronological because the generator has no clock. On real exported
    data a time-ordered cut is the honest one, since it also captures drift; the shape of
    the function is the same.
    """
    rng = np.random.default_rng(seed)
    ids = population.user_ids

    is_fit = rng.random(len(population.exposed)) < ratio
    left, right = population.exposed[:, 0], population.exposed[:, 1]

    forward = population.liked_forward & is_fit
    backward = population.liked_backward & is_fit
    fit_likes = [(ids[a], ids[b]) for a, b in zip(left[forward], right[forward])]
    fit_likes += [(ids[b], ids[a]) for a, b in zip(left[backward], right[backward])]

    counts = np.bincount(population.exposed[is_fit].ravel(), minlength=len(ids))
    fit_exposures = {uid: int(c) for uid, c in zip(ids, counts)}

    holdout: dict[str, set[str]] = {uid: set() for uid in ids}
    for a, b in population.exposed[population.mutual_mask & ~is_fit]:
        holdout[ids[a]].add(ids[b])
        holdout[ids[b]].add(ids[a])

    return Split(fit_likes=fit_likes, fit_exposures=fit_exposures, holdout_matches=holdout)


@dataclass
class Metrics:
    name: str
    precision_at_10: float
    precision_at_20: float
    recall_at_20: float
    recall_at_50: float
    hit_rate_at_10: float
    map_at_20: float
    n_evaluated: int

    #: Share of the population that appears in anyone's top 50. A model can score well on
    #: precision while showing the same few hundred profiles to everybody, which is a
    #: dead product for everyone else; this is the number that catches it.
    coverage: float = 0.0

    def as_row(self) -> dict[str, object]:
        return {
            "model": self.name,
            "P@10": round(self.precision_at_10, 4),
            "P@20": round(self.precision_at_20, 4),
            "R@20": round(self.recall_at_20, 4),
            "R@50": round(self.recall_at_50, 4),
            "HitRate@10": round(self.hit_rate_at_10, 4),
            "MAP@20": round(self.map_at_20, 4),
            "coverage": round(self.coverage, 4),
            "users": self.n_evaluated,
        }


def _average_precision(ranked: list[str], relevant: set[str], k: int) -> float:
    hits, score = 0, 0.0
    for i, candidate in enumerate(ranked[:k], start=1):
        if candidate in relevant:
            hits += 1
            score += hits / i
    return score / min(len(relevant), k) if relevant else 0.0


def evaluate(
    model: RankingModel,
    population: Population,
    split: Split,
    *,
    name: str,
    min_matches: int = 3,
    max_users: int = 1500,
    seed: int = 20260801,
) -> Metrics:
    """Scores a model against the held-out half of the match graph.

    Gamers with very few held-out matches are skipped: precision@10 for someone with one
    match is capped at 0.1 whatever the model does, so including them measures the
    population more than the model.
    """
    rng = np.random.default_rng(seed)

    eligible = [uid for uid in population.user_ids if len(split.matches_of(uid)) >= min_matches]
    if len(eligible) > max_users:
        eligible = [eligible[i] for i in rng.choice(len(eligible), size=max_users, replace=False)]

    p10, p20, r20, r50, hits10, ap20 = [], [], [], [], [], []
    surfaced: set[str] = set()

    for user_id in eligible:
        relevant = split.matches_of(user_id)
        ranked = model.rank(user_id, 200)
        surfaced.update(ranked[:50])

        p10.append(len(set(ranked[:10]) & relevant) / 10)
        p20.append(len(set(ranked[:20]) & relevant) / 20)
        r20.append(len(set(ranked[:20]) & relevant) / len(relevant))
        r50.append(len(set(ranked[:50]) & relevant) / len(relevant))
        hits10.append(1.0 if set(ranked[:10]) & relevant else 0.0)
        ap20.append(_average_precision(ranked, relevant, 20))

    if not p10:
        return Metrics(name, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0)

    return Metrics(
        name=name,
        precision_at_10=float(np.mean(p10)),
        precision_at_20=float(np.mean(p20)),
        recall_at_20=float(np.mean(r20)),
        recall_at_50=float(np.mean(r50)),
        hit_rate_at_10=float(np.mean(hits10)),
        map_at_20=float(np.mean(ap20)),
        n_evaluated=len(p10),
        coverage=len(surfaced) / max(1, len(population.gamers)),
    )


# ---------------------------------------------------------------------------
# Baselines
# ---------------------------------------------------------------------------


class RandomBaseline:
    """The floor. Any model that cannot beat this is doing nothing."""

    def __init__(self, population: Population, split: Split | None = None, seed: int = 7) -> None:
        self.ids = population.user_ids
        self.rng = np.random.default_rng(seed)

    def rank(self, user_id: str, top_n: int) -> list[str]:
        picks = self.rng.choice(len(self.ids), size=min(top_n + 1, len(self.ids)), replace=False)
        return [self.ids[i] for i in picks if self.ids[i] != user_id][:top_n]


class PopularityBaseline:
    """Recommend whoever gets liked most.

    A stronger floor than random and a genuinely hard one to beat in matching products,
    because desirability is real. A recommender that only matches popularity has learned
    nothing about *taste* — it has learned who is popular.

    Ranks by like *rate* over the fitting split, not like count over everything. Counting
    likes rewards whoever was shown most, and reading them from the whole log is scoring
    the model on its own answers.
    """

    def __init__(self, population: Population, split: Split) -> None:
        liked: dict[str, int] = {}
        for _, target in split.fit_likes:
            liked[target] = liked.get(target, 0) + 1

        prior = sum(liked.values()) / max(sum(split.fit_exposures.values()), 1)
        strength = 20.0
        rates = {
            uid: (liked.get(uid, 0) + strength * prior) / (split.fit_exposures.get(uid, 0) + strength)
            for uid in population.user_ids
        }
        self.ordered = [uid for uid, _ in sorted(rates.items(), key=lambda kv: -kv[1])]

    def rank(self, user_id: str, top_n: int) -> list[str]:
        return [uid for uid in self.ordered if uid != user_id][:top_n]


class OracleBaseline:
    """Ranks by the true latent affinity. The ceiling, not a shippable model.

    Reads ``theta`` and ``vibe`` — the hidden state the recommender never sees — and
    scores exactly the quantity the swipe model uses. It cannot reach 1.0, because a
    match also needs the pair to have been surfaced and both sides to have said yes
    through their own pickiness and desirability; what it measures is how much of the
    remaining gap is *recoverable in principle* rather than a failure of the model. If the
    rewrite sits near the oracle, better features are the wrong place to spend effort.
    """

    def __init__(self, population: Population, split: Split | None = None) -> None:
        self.ids = population.user_ids
        theta = np.array([g.theta for g in population.gamers])
        self.vibe = np.array([g.vibe for g in population.gamers])
        self._index = {uid: i for i, uid in enumerate(self.ids)}

        self._warmed = theta @ WARMTH
        self._theta = theta
        self._norms = np.sqrt(np.maximum(np.einsum("ij,ij->i", self._warmed, theta), 1e-12))

        at = {name: i for i, name in enumerate(PLATFORM_IDS)}
        self._platforms = np.zeros((len(self.ids), len(PLATFORM_IDS)), dtype=bool)
        for row, gamer in enumerate(population.gamers):
            for name in gamer.platforms:
                self._platforms[row, at[name]] = True

    def rank(self, user_id: str, top_n: int) -> list[str]:
        idx = self._index[user_id]
        taste = (self._warmed @ self._theta[idx]) / (self._norms * self._norms[idx])
        gap = self.vibe - self.vibe[idx]
        vibe_sim = np.exp(-np.einsum("ij,ij->i", gap, gap) / (2.0 * VIBE_SCALE**2))
        shared_platform = (self._platforms & self._platforms[idx]).any(axis=1)

        scores = (TASTE_WEIGHT * taste + VIBE_WEIGHT * vibe_sim
                  + PLATFORM_WEIGHT * shared_platform)
        scores[idx] = -np.inf
        order = np.argpartition(-scores, min(top_n, len(scores) - 1))[:top_n]
        return [self.ids[i] for i in order[np.argsort(-scores[order])]]


class OriginalPipeline:
    """A faithful reimplementation of the original notebook and /predict.

    Reproduced so the comparison is measured rather than asserted. Every quirk below is
    deliberate and matches the original:

    * ``age`` and ``country`` stay in the frame, unscaled, alongside the 0/1 columns;
    * ``country`` is label-encoded into 0..N;
    * the PCA component count is the complement of the one needed to reach the variance
      target — the bug in the notebook;
    * the clustering loop keeps whichever model it ended on rather than the best;
    * the games block is joined twice, so games carry double weight;
    * ranking is Pearson correlation over the resulting rows.
    """

    def __init__(
        self, population: Population, split: Split | None = None, *, variance_target: float = 0.65
    ) -> None:
        from sklearn.cluster import AgglomerativeClustering
        from sklearn.decomposition import PCA
        from sklearn.preprocessing import LabelEncoder, MinMaxScaler

        gamers = population.gamers
        self.user_ids = [g.user_id for g in gamers]

        games = pd.Series([", ".join(g.games) for g in gamers], index=self.user_ids)
        keywords = pd.Series([", ".join(g.keywords) for g in gamers], index=self.user_ids)
        self.games_encoded = games.str.get_dummies(sep=", ")
        keywords_encoded = keywords.str.get_dummies(sep=", ")

        df = pd.DataFrame(
            {"age": [g.age for g in gamers], "country": [g.country for g in gamers]},
            index=self.user_ids,
        )
        df["country"] = LabelEncoder().fit_transform(df["country"])
        df = df.join(self.games_encoded).join(keywords_encoded)

        # Scaled frame drops age and country — so the clustering never sees them, while
        # the frame that gets pickled and ranked keeps them raw.
        scaled_source = df.drop(["country", "age"], axis=1)
        scaled = pd.DataFrame(
            MinMaxScaler().fit_transform(scaled_source),
            columns=scaled_source.columns,
        )

        pca = PCA()
        pca.fit(scaled)
        cumulative = pca.explained_variance_ratio_.cumsum()
        n_over = len(cumulative[cumulative >= variance_target])  # the tail, not the head
        n_over = max(2, min(n_over, min(scaled.shape) - 1))
        reduced = PCA(n_components=n_over).fit_transform(scaled)

        hac = None
        for n in range(2, 20, 2):
            hac = AgglomerativeClustering(n_clusters=n)
            hac.fit(reduced)
        # `hac` is now n=18 because 18 is last, not because 18 was best.

        df.insert(0, "Cluster Score", hac.labels_)
        self.df = df

    def rank(self, user_id: str, top_n: int) -> list[str]:
        cluster = self.df.at[user_id, "Cluster Score"]
        group = self.df[self.df["Cluster Score"] == cluster].drop("Cluster Score", axis=1)
        # The double join that doubles the weight of games.
        group = group.join(self.games_encoded, how="inner", lsuffix="_left", rsuffix="_right")
        if len(group) < 2:
            return []
        correlations = group.T.corr()
        ordered = correlations[[user_id]].sort_values(by=[user_id], axis=0, ascending=False)
        return [uid for uid in ordered.index.tolist() if uid != user_id][:top_n]


class RewrittenModel:
    """The rewrite, wrapped for the harness.

    With ``use_desirability`` it also fits the popularity prior, which is the hybrid the
    product should actually ship: taste decides who is worth showing, desirability breaks
    the ties among them. The prior is fitted from the **fitting split only** — see the
    module docstring for why that matters more than it sounds.
    """

    def __init__(
        self, population: Population, split: Split, *, use_desirability: bool = False, **kwargs
    ) -> None:
        gamers = population.gamers
        self.model = train(
            [g.user_id for g in gamers],
            [g.games for g in gamers],
            [g.keywords for g in gamers],
            [g.platforms for g in gamers],
            **kwargs,
        )
        if use_desirability:
            self.model.fit_desirability(split.fit_likes, split.fit_exposures)

    def rank(self, user_id: str, top_n: int) -> list[str]:
        return self.model.similar_to(user_id, top_n)


# ---------------------------------------------------------------------------


def default_builders(
    population: Population, split: Split, *, include_original: bool = False
) -> dict[str, Callable[[], RankingModel]]:
    builders: dict[str, Callable[[], RankingModel]] = {
        "random": lambda: RandomBaseline(population, split),
        "popularity": lambda: PopularityBaseline(population, split),
        "rewrite: content only": lambda: RewrittenModel(population, split),
        "rewrite: hybrid": lambda: RewrittenModel(population, split, use_desirability=True),
        "oracle (latent state)": lambda: OracleBaseline(population, split),
    }
    if include_original:
        builders["original (as-is)"] = lambda: OriginalPipeline(population, split)
    return builders


def compare(
    population: Population,
    builders: dict[str, Callable[[], RankingModel]],
    split: Split | None = None,
) -> pd.DataFrame:
    split = split or split_exposures(population)
    rows = [evaluate(build(), population, split, name=name).as_row() for name, build in builders.items()]
    return pd.DataFrame(rows).set_index("model")


def latent_recovery(
    model, population: Population, *, n_pairs: int = 80_000, seed: int = 20260801
) -> dict[str, float]:
    """How much of the hidden state the model's similarity actually recovers.

    Takes a fitted ``Recommender`` and correlates the similarity it computes against the
    true latent affinity, and against each of its two components separately.

    Worth more than it looks. Precision@10 is measured against a handful of held-out
    matches per gamer and is noisy enough that a real difference between two settings can
    hide inside the run-to-run spread — the keyword-weight sweep looked flat on P@10 while
    this showed 0.5 recovering taste and nothing else, 2.0 recovering vibe and nothing
    else, and 1.0 recovering both. It is a diagnostic and not a target: latent state is
    unobservable in production, so nothing can be tuned on it once real data arrives. Use
    it to understand *why* a setting wins, then confirm on the match graph.
    """
    rng = np.random.default_rng(seed)
    gamers = population.gamers
    theta = np.array([g.theta for g in gamers])
    vibe = np.array([g.vibe for g in gamers])

    at = {name: i for i, name in enumerate(PLATFORM_IDS)}
    owns = np.zeros((len(gamers), len(PLATFORM_IDS)), dtype=bool)
    for row, gamer in enumerate(gamers):
        for name in gamer.platforms:
            owns[row, at[name]] = True

    warmed = theta @ WARMTH
    norms = np.sqrt(np.maximum(np.einsum("ij,ij->i", warmed, theta), 1e-12))

    left = rng.integers(0, len(gamers), n_pairs)
    right = rng.integers(0, len(gamers), n_pairs)
    keep = left != right
    left, right = left[keep], right[keep]

    taste = np.einsum("ij,ij->i", warmed[left], theta[right]) / (norms[left] * norms[right])
    gap = vibe[left] - vibe[right]
    vibe_sim = np.exp(-np.einsum("ij,ij->i", gap, gap) / (2.0 * VIBE_SCALE**2))
    shared_platform = (owns[left] & owns[right]).any(axis=1).astype(float)
    true = TASTE_WEIGHT * taste + VIBE_WEIGHT * vibe_sim + PLATFORM_WEIGHT * shared_platform

    unit = model._unit
    predicted = np.einsum("ij,ij->i", unit[left], unit[right])

    return {
        "r_total": float(np.corrcoef(predicted, true)[0, 1]),
        "r_taste": float(np.corrcoef(predicted, taste)[0, 1]),
        "r_vibe": float(np.corrcoef(predicted, vibe_sim)[0, 1]),
        "r_platform": float(np.corrcoef(predicted, shared_platform)[0, 1]),
    }


def structure_report(model: RankingModel, population: Population, *, max_users: int = 500,
                     seed: int = 20260801) -> dict[str, float]:
    """What *kind* of candidate the model actually serves.

    The ranking metrics say whether the recommendations are right. These say whether they
    are interesting: a model that only ever pairs people with their own archetype would
    score respectably and make a boring product, and one whose cross-genre suggestions are
    spread evenly over every other archetype has not learned that some genres are closer
    than others — it is just adding noise. Compare against ``summarise``'s figures for the
    population itself.
    """
    rng = np.random.default_rng(seed)
    dominant = {g.user_id: int(np.argmax(g.theta)) for g in population.gamers}
    vibe = {g.user_id: g.vibe for g in population.gamers}

    ids = population.user_ids
    sample = [ids[i] for i in rng.choice(len(ids), size=min(max_users, len(ids)), replace=False)]

    same, warmths, vibe_gaps = 0, [], []
    total = 0
    for user_id in sample:
        for candidate in model.rank(user_id, 20):
            total += 1
            if dominant[candidate] == dominant[user_id]:
                same += 1
            warmths.append(WARMTH[dominant[user_id], dominant[candidate]])
            gap = vibe[user_id] - vibe[candidate]
            vibe_gaps.append(float(np.exp(-gap @ gap / (2.0 * VIBE_SCALE**2))))

    if not total:
        return {"same_archetype": 0.0, "mean_warmth": 0.0, "mean_vibe_similarity": 0.0}
    return {
        "same_archetype": same / total,
        "mean_warmth": float(np.mean(warmths)),
        "mean_vibe_similarity": float(np.mean(vibe_gaps)),
    }
