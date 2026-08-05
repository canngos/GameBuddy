"""Generates a synthetic gamer population with a recoverable latent structure.

Why not just assign matches by a rule
-------------------------------------
The obvious approach — "these two both like shooters, so match them" — produces data
nothing can learn from. The rule is written in terms of the observed games, so the
observed games *are* the answer; a model either reproduces the rule exactly or fails, and
either way you learn nothing about whether the model would work on real people.

Real taste is not like that. Two gamers get along because of an underlying disposition,
and the handful of games they happened to list is partial, noisy evidence of it. Someone
who loves competitive shooters might list VALORANT and Rocket League; someone with the
same disposition might list CS2 and Apex, sharing nothing. A recommender is useful
precisely when it can see through that.

So the generator works the way the world does:

1. every gamer draws a **mixture over hidden archetypes** — most lean strongly one way
   with a secondary interest, which is what makes people interesting to match;
2. their games and keywords are **sampled** from that mixture, mixed with a popularity
   bias, and truncated to the handful the product asks for. Two gamers with identical
   dispositions routinely end up with different libraries;
3. matches are drawn from the **hidden** affinity, never from the observed profile, and
   are filtered through exposure, pickiness and desirability so the graph looks like a
   real swipe log rather than a similarity matrix.

The evaluation then asks the only question that matters: given the noisy profile, can the
recommender recover who this gamer would actually have matched with?
"""

from __future__ import annotations

import hashlib
import uuid
from dataclasses import dataclass, field

import numpy as np

from .catalogue import ALL_GAMES, ARCHETYPES, COUNTRIES

# Minors are never matched with adults, so the population is generated the same way and
# the two bands are effectively separate markets.
MAJORITY_AGE = 18


@dataclass
class SyntheticGamer:
    user_id: str
    username: str
    email: str
    age: int
    country: str
    gender: str
    games: list[str]
    keywords: list[str]

    #: The mixture over archetypes this gamer was drawn from. Ground truth: the
    #: recommender never sees it, and the evaluation uses it to explain results.
    theta: np.ndarray = field(repr=False)

    #: How readily this gamer swipes yes. Real users differ enormously here.
    pickiness: float = 0.0

    #: How readily others swipe yes on them, independent of fit — the popularity
    #: effect every matching product has.
    desirability: float = 0.0

    @property
    def is_minor(self) -> bool:
        return self.age < MAJORITY_AGE


@dataclass
class Population:
    gamers: list[SyntheticGamer]

    #: (u, v) pairs where u swiped yes on v. Directed.
    likes: set[tuple[str, str]]

    #: (u, v) pairs where u swiped no on v. Directed.
    passes: set[tuple[str, str]]

    #: Unordered pairs where both sides said yes. This is the target to predict.
    mutual: set[frozenset[str]]

    #: Unordered pairs that were shown to each other at all. Needed to turn a like count
    #: into a like *rate*: someone liked 40 times out of 200 is not more desirable than
    #: someone liked 30 times out of 60.
    exposed_pairs: set[frozenset[str]] = field(default_factory=set)

    _match_index: dict[str, set[str]] | None = field(default=None, repr=False, compare=False)

    def matches_of(self, user_id: str) -> set[str]:
        if self._match_index is None:
            index: dict[str, set[str]] = {g.user_id: set() for g in self.gamers}
            for pair in self.mutual:
                a, b = tuple(pair)
                index[a].add(b)
                index[b].add(a)
            self._match_index = index
        return self._match_index.get(user_id, set())


class PopulationGenerator:
    """Draws a population from the latent model described in the module docstring."""

    def __init__(
        self,
        n_gamers: int = 4000,
        *,
        seed: int = 20260801,
        minor_fraction: float = 0.18,
        # Dirichlet concentration. Below 1 the mixture is sparse — a gamer is mostly one
        # archetype with a secondary leaning, which is what real taste looks like. Raise
        # it and everyone becomes an identical blend of everything, and nothing is
        # learnable because nobody differs.
        mixture_concentration: float = 0.35,
        exposure_per_gamer: int = 80,
        # Multiplies latent affinity in the swipe model. Higher means taste dominates and
        # the problem becomes easy; lower means noise dominates and no model can do well.
        # 7.0 leaves a clear but far from deterministic signal.
        affinity_weight: float = 7.0,
        # Affinity at which a swipe is a coin flip before personal bias. Set above the
        # median so most pairs are a no and a yes has to be earned.
        # Calibrated so ~25% of swipes are yes and ~9% of surfaced pairs become mutual
        # matches, which is the range a real matching product operates in. Lower it and
        # the graph gets dense enough that guessing scores well; raise it and most gamers
        # have no matches to evaluate against.
        like_threshold: float = 0.62,
    ) -> None:
        self.n_gamers = n_gamers
        self.rng = np.random.default_rng(seed)
        self.minor_fraction = minor_fraction
        self.mixture_concentration = mixture_concentration
        self.exposure_per_gamer = exposure_per_gamer
        self.affinity_weight = affinity_weight
        self.like_threshold = like_threshold

        self.n_archetypes = len(ARCHETYPES)
        self.game_names = [name for name, _ in ALL_GAMES]
        self.game_index = {name: i for i, name in enumerate(self.game_names)}

        self._game_profiles = self._build_game_profiles()
        self._keyword_profiles, self.keyword_names = self._build_keyword_profiles()
        # A long tail: a few games are enormously popular and appear across archetypes,
        # which is what stops naive overlap counting from working.
        self._popularity = self.rng.pareto(1.6, size=len(self.game_names)) + 1.0
        self._popularity /= self._popularity.sum()

    # -- catalogue → probability matrices ---------------------------------

    def _build_game_profiles(self) -> np.ndarray:
        """P(game | archetype). Rows are archetypes."""
        profiles = np.full((self.n_archetypes, len(self.game_names)), 0.02)
        for a_idx, archetype in enumerate(ARCHETYPES):
            for name, _ in archetype.games:
                profiles[a_idx, self.game_index[name]] = 1.0
        return profiles / profiles.sum(axis=1, keepdims=True)

    def _build_keyword_profiles(self) -> tuple[np.ndarray, list[str]]:
        names = sorted({k for a in ARCHETYPES for k in a.keywords})
        index = {k: i for i, k in enumerate(names)}
        profiles = np.full((self.n_archetypes, len(names)), 0.03)
        for a_idx, archetype in enumerate(ARCHETYPES):
            for keyword in archetype.keywords:
                profiles[a_idx, index[keyword]] = 1.0
        return profiles / profiles.sum(axis=1, keepdims=True), names

    # -- gamers -----------------------------------------------------------

    def _draw_theta(self) -> np.ndarray:
        return self.rng.dirichlet(np.full(self.n_archetypes, self.mixture_concentration))

    def _draw_age(self, theta: np.ndarray, minor: bool) -> int:
        mu = float(sum(t * a.age_mu for t, a in zip(theta, ARCHETYPES)))
        sigma = float(sum(t * a.age_sigma for t, a in zip(theta, ARCHETYPES)))
        age = int(round(self.rng.normal(mu, sigma)))
        # Clamp into the requested band rather than resampling, so the age distribution
        # inside each band stays connected to the archetype mix.
        return int(np.clip(age, 13, 17)) if minor else int(np.clip(age, 18, 60))

    def _sample_games(self, theta: np.ndarray) -> list[str]:
        n = int(np.clip(self.rng.normal(6.5, 2.2), 3, 12))
        # 80% taste, 20% "everyone has played Minecraft".
        p = 0.8 * (theta @ self._game_profiles) + 0.2 * self._popularity
        p = p / p.sum()
        picked = self.rng.choice(len(self.game_names), size=n, replace=False, p=p)
        return [self.game_names[i] for i in picked]

    def _sample_keywords(self, theta: np.ndarray) -> list[str]:
        n = int(np.clip(self.rng.normal(6.0, 1.5), 5, 10))
        p = theta @ self._keyword_profiles
        p = p / p.sum()
        n = min(n, len(self.keyword_names))
        picked = self.rng.choice(len(self.keyword_names), size=n, replace=False, p=p)
        return [self.keyword_names[i] for i in picked]

    def _draw_country(self) -> str:
        names = [c for c, _ in COUNTRIES]
        weights = np.array([w for _, w in COUNTRIES])
        return str(self.rng.choice(names, p=weights / weights.sum()))

    def _make_gamer(self, ordinal: int) -> SyntheticGamer:
        theta = self._draw_theta()
        minor = self.rng.random() < self.minor_fraction
        dominant = ARCHETYPES[int(np.argmax(theta))].key

        # Deterministic id from the ordinal, so regenerating with the same seed produces
        # the same ids and the seed SQL stays diffable.
        digest = hashlib.sha1(f"gamebuddy-bot-{ordinal}".encode()).hexdigest()
        user_id = str(uuid.UUID(digest[:32]))
        username = f"{dominant.split('_')[0]}_{ordinal:05d}"

        return SyntheticGamer(
            user_id=user_id,
            username=username,
            email=f"bot{ordinal:05d}@bot.gamebuddy.invalid",
            age=self._draw_age(theta, minor),
            country=self._draw_country(),
            # Drawn independently of taste, and not used by the recommender. It is here
            # because the schema has the column, not because it predicts anything: this is
            # a friend-finding product, so filtering candidates by gender is not a feature
            # and encoding it as a similarity signal would be a liability.
            gender=str(self.rng.choice(["Male", "Female", "Other", "Prefer not to say"],
                                       p=[0.62, 0.30, 0.04, 0.04])),
            games=self._sample_games(theta),
            keywords=self._sample_keywords(theta),
            theta=theta,
            # Some gamers say yes to almost anyone, some to almost nobody.
            pickiness=float(self.rng.normal(0.0, 1.1)),
            desirability=float(self.rng.normal(0.0, 0.9)),
        )

    # -- swipes -----------------------------------------------------------

    @staticmethod
    def _affinity(a: SyntheticGamer, b: SyntheticGamer) -> float:
        """Cosine similarity of the hidden mixtures. The thing to be recovered."""
        num = float(a.theta @ b.theta)
        den = float(np.linalg.norm(a.theta) * np.linalg.norm(b.theta)) or 1.0
        return num / den

    def _p_like(self, u: SyntheticGamer, v: SyntheticGamer) -> float:
        """Probability that u swipes yes on v.

        Centred above the median affinity (~0.30 for these mixtures) so a typical pair is
        a likely no and taste has to do real work to earn a yes. Individual pickiness and
        desirability then add noise on top, which is what stops the recovered ranking from
        being a deterministic function of similarity.
        """
        z = (
            self.affinity_weight * (self._affinity(u, v) - self.like_threshold)
            + u.pickiness
            + v.desirability
        )
        return 1.0 / (1.0 + np.exp(-z))

    def _exposed_pairs(self, gamers: list[SyntheticGamer]) -> set[frozenset[str]]:
        """Which pairs get put in front of each other.

        Symmetric on purpose. A swipe log only contains pairs the product surfaced, and
        in a matching app the pair is the unit — both people get the chance to decide.
        Modelling exposure one-directionally instead makes a mutual match require the
        product to have independently surfaced the same pair twice, which at realistic
        exposure rates is a couple of percent, and the resulting match graph is driven by
        that coincidence rather than by taste.

        Exposure is mildly skewed towards desirable profiles — the popularity effect every
        matching product has — but stays close to random so the log is not simply a record
        of some earlier recommender's opinion.
        """
        by_band: dict[bool, list[SyntheticGamer]] = {True: [], False: []}
        for g in gamers:
            by_band[g.is_minor].append(g)

        pairs: set[frozenset[str]] = set()
        for band_members in by_band.values():
            if len(band_members) < 2:
                continue
            weights = np.array([np.exp(0.5 * g.desirability) for g in band_members])
            weights /= weights.sum()
            for g in band_members:
                k = min(self.exposure_per_gamer, len(band_members) - 1)
                picks = self.rng.choice(len(band_members), size=k, replace=False, p=weights)
                for i in picks:
                    other = band_members[i]
                    if other.user_id != g.user_id:
                        pairs.add(frozenset((g.user_id, other.user_id)))
        return pairs

    def generate(self) -> Population:
        gamers = [self._make_gamer(i) for i in range(self.n_gamers)]
        by_id = {g.user_id: g for g in gamers}

        likes: set[tuple[str, str]] = set()
        passes: set[tuple[str, str]] = set()

        exposed = self._exposed_pairs(gamers)
        for pair in exposed:
            a_id, b_id = tuple(pair)
            a, b = by_id[a_id], by_id[b_id]
            # Both sides decide independently; a match needs both to say yes.
            for u, v in ((a, b), (b, a)):
                if self.rng.random() < self._p_like(u, v):
                    likes.add((u.user_id, v.user_id))
                else:
                    passes.add((u.user_id, v.user_id))

        mutual = {frozenset((a, b)) for a, b in likes if (b, a) in likes}
        return Population(
            gamers=gamers, likes=likes, passes=passes, mutual=mutual, exposed_pairs=exposed
        )


def summarise(population: Population) -> dict[str, float]:
    """Sanity figures. A population with no mutual matches teaches nothing."""
    n = len(population.gamers)
    match_counts = [len(population.matches_of(g.user_id)) for g in population.gamers]
    minors = sum(1 for g in population.gamers if g.is_minor)

    cross_band = 0
    by_id = {g.user_id: g for g in population.gamers}
    for pair in population.mutual:
        a, b = tuple(pair)
        if by_id[a].is_minor != by_id[b].is_minor:
            cross_band += 1

    return {
        "gamers": n,
        "minors": minors,
        "likes": len(population.likes),
        "passes": len(population.passes),
        "mutual_matches": len(population.mutual),
        "like_rate": len(population.likes) / max(1, len(population.likes) + len(population.passes)),
        "mean_matches_per_gamer": float(np.mean(match_counts)),
        "median_matches_per_gamer": float(np.median(match_counts)),
        "gamers_with_no_match": sum(1 for c in match_counts if c == 0),
        # Must be zero: minors are never paired with adults, and the generator has to
        # respect the same rule the backend enforces.
        "cross_band_matches": cross_band,
    }
