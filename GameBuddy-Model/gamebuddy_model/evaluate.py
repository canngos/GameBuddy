"""Offline evaluation, and the baselines worth comparing against.

Nothing in the original could answer "is this better than showing random profiles?", so
that is the first thing built here. Every claim about the rewrite is measured against the
same harness, including the claim that the original was broken — ``OriginalPipeline``
below reimplements it faithfully so the comparison is evidence rather than argument.

Protocol
--------
Ground truth is the mutual-match graph. Neither the original nor the rewrite trains on it —
both look only at profiles — so the whole graph is legitimately held out and no split is
needed. A model that *did* learn from the graph would need one, and the split helper is
here for that.

Only same-age-band candidates are scored, because the backend refuses a cross-band pairing
outright. Counting recommendations the product would never surface would flatter every
model equally and tell us nothing.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Callable, Protocol

import numpy as np
import pandas as pd

from .population import Population
from .recommender import train


class RankingModel(Protocol):
    """Anything that can rank candidates for a gamer."""

    def rank(self, user_id: str, top_n: int) -> list[str]: ...


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

    def as_row(self) -> dict[str, object]:
        return {
            "model": self.name,
            "P@10": round(self.precision_at_10, 4),
            "P@20": round(self.precision_at_20, 4),
            "R@20": round(self.recall_at_20, 4),
            "R@50": round(self.recall_at_50, 4),
            "HitRate@10": round(self.hit_rate_at_10, 4),
            "MAP@20": round(self.map_at_20, 4),
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
    *,
    name: str,
    min_matches: int = 3,
    max_users: int = 1500,
    seed: int = 20260801,
) -> Metrics:
    """Scores a model against the mutual-match graph.

    Gamers with very few matches are skipped: precision@10 for someone with one match is
    capped at 0.1 whatever the model does, so including them measures the population more
    than the model.
    """
    rng = np.random.default_rng(seed)
    band_of = {g.user_id: g.is_minor for g in population.gamers}

    eligible = [g.user_id for g in population.gamers if len(population.matches_of(g.user_id)) >= min_matches]
    if len(eligible) > max_users:
        eligible = [eligible[i] for i in rng.choice(len(eligible), size=max_users, replace=False)]

    p10, p20, r20, r50, hits10, ap20 = [], [], [], [], [], []

    for user_id in eligible:
        relevant = population.matches_of(user_id)
        if not relevant:
            continue

        ranked = [c for c in model.rank(user_id, 200) if band_of.get(c) == band_of[user_id]]

        p10.append(len(set(ranked[:10]) & relevant) / 10)
        p20.append(len(set(ranked[:20]) & relevant) / 20)
        r20.append(len(set(ranked[:20]) & relevant) / len(relevant))
        r50.append(len(set(ranked[:50]) & relevant) / len(relevant))
        hits10.append(1.0 if set(ranked[:10]) & relevant else 0.0)
        ap20.append(_average_precision(ranked, relevant, 20))

    return Metrics(
        name=name,
        precision_at_10=float(np.mean(p10)),
        precision_at_20=float(np.mean(p20)),
        recall_at_20=float(np.mean(r20)),
        recall_at_50=float(np.mean(r50)),
        hit_rate_at_10=float(np.mean(hits10)),
        map_at_20=float(np.mean(ap20)),
        n_evaluated=len(p10),
    )


# ---------------------------------------------------------------------------
# Baselines
# ---------------------------------------------------------------------------


class RandomBaseline:
    """The floor. Any model that cannot beat this is doing nothing."""

    def __init__(self, population: Population, seed: int = 7) -> None:
        self.ids = [g.user_id for g in population.gamers]
        self.rng = np.random.default_rng(seed)

    def rank(self, user_id: str, top_n: int) -> list[str]:
        picks = self.rng.choice(len(self.ids), size=min(top_n + 1, len(self.ids)), replace=False)
        return [self.ids[i] for i in picks if self.ids[i] != user_id][:top_n]


class PopularityBaseline:
    """Recommend whoever gets liked most.

    A stronger floor than random and a genuinely hard one to beat in matching products,
    because desirability is real. A recommender that only matches popularity has learned
    nothing about *taste* — it has learned who is popular.
    """

    def __init__(self, population: Population) -> None:
        counts: dict[str, int] = {g.user_id: 0 for g in population.gamers}
        for _, target in population.likes:
            counts[target] = counts.get(target, 0) + 1
        self.ordered = [uid for uid, _ in sorted(counts.items(), key=lambda kv: -kv[1])]

    def rank(self, user_id: str, top_n: int) -> list[str]:
        return [uid for uid in self.ordered if uid != user_id][:top_n]


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

    def __init__(self, population: Population, *, variance_target: float = 0.65) -> None:
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

    With ``use_desirability`` it also fits the popularity prior from the like log, which
    is the hybrid the product should actually ship: taste decides who is worth showing,
    desirability breaks the ties among them.
    """

    def __init__(self, population: Population, *, use_desirability: bool = False, **kwargs) -> None:
        gamers = population.gamers
        self.model = train(
            [g.user_id for g in gamers],
            [g.games for g in gamers],
            [g.keywords for g in gamers],
            **kwargs,
        )
        if use_desirability:
            exposures: dict[str, int] = {}
            for a, b in population.exposed_pairs:
                exposures[a] = exposures.get(a, 0) + 1
                exposures[b] = exposures.get(b, 0) + 1
            self.model.fit_desirability(population.likes, exposures)

    def rank(self, user_id: str, top_n: int) -> list[str]:
        return self.model.similar_to(user_id, top_n)


# ---------------------------------------------------------------------------


def compare(population: Population, builders: dict[str, Callable[[], RankingModel]]) -> pd.DataFrame:
    rows = []
    for name, build in builders.items():
        rows.append(evaluate(build(), population, name=name).as_row())
    return pd.DataFrame(rows).set_index("model")
