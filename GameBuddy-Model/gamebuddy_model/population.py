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
   with a secondary interest, which is what makes people interesting to match — and a
   **vibe vector** describing temperament, which the archetype predicts but does not
   determine;
2. their games are **sampled** from the mixture and their keywords from *both* the
   mixture and the vibe, then truncated to the handful the product asks for. Two gamers
   with identical dispositions routinely end up with different libraries;
3. matches are drawn from the **hidden** state, never from the observed profile, and are
   filtered through exposure, pickiness and desirability so the graph looks like a real
   swipe log rather than a similarity matrix.

Two people can get along
------------------------
Affinity used to be the plain cosine of the two mixture vectors. That has a consequence
worth stating plainly: cosine knows nothing about what the archetypes *mean*, so the only
way to be compatible was to be nearly the same, and the cross-genre matching that did
happen was Dirichlet noise. Measured on the population it produced, battle-royale and
competitive-FPS players were the single coldest pair in the match graph, despite sharing
Apex Legends and Warzone in the catalogue. The recommender reads games, so it saw those
two groups as close; the labels said they were not; and the disagreement is a ceiling on
any model's score.

Affinity is now two terms:

* **taste**, the mixture cosine *through* ``catalogue.WARMTH``, so neighbouring genres are
  genuinely warm and no pair is at zero;
* **vibe**, assortative on temperament, so a chill no-mic player and a tryhard ranked
  grinder are a poor match even playing the same game — and a chill shooter player and a
  chill cozy-sim player are a plausible one even sharing nothing.

Taste carries more weight than vibe, but not so much more that it decides alone. That is
the whole design: most people match inside their own taste neighbourhood, a good number
match across it on temperament, and a few match for no better reason than that they liked
the look of each other, which is what ``pickiness`` and ``desirability`` are for.

The evaluation then asks the only question that matters: given the noisy profile, can the
recommender recover who this gamer would actually have matched with?
"""

from __future__ import annotations

import hashlib
import uuid
from collections.abc import Iterator
from dataclasses import dataclass, field

import numpy as np

from .catalogue import (ALL_GAMES, ARCHETYPES, COUNTRIES, GENRE_KEYWORDS, PLATFORM_IDS,
                        VIBE_KEYWORDS, WARMTH)

#: The product is 18+ — ``register.tsx`` gates on it and the backend refuses a younger
#: birth date — so the population is adults only. Kept as a named constant because the
#: age floor is a product rule rather than a modelling choice.
MINIMUM_AGE = 18

#: Matches ``MAX_AGE`` in ``GameBuddy-App/src/validation.ts``. It is a clamp of last
#: resort, not a shaping parameter — the archetype age distributions top out around 60 on
#: their own, so raising this from 60 to 99 changes a handful of profiles rather than the
#: shape of the population. It is here so the generator cannot produce an age the app
#: would refuse, and so the limit lives in one place conceptually rather than being
#: rediscovered from whatever the sampler happened to do.
MAXIMUM_AGE = 99

#: How the swipe decision splits between taste, temperament and whether the two of you
#: can actually play together. Taste leads; vibe is heavy enough to carry a cross-genre
#: pair on its own. See ``_affinity_of_pairs``.
#:
#: Platform is the smallest of the three and it was tuned by the ratio it produces rather
#: than by feel, because the term is binary and so a small weight goes a long way. At 0.15
#: gamers sharing a platform matched 11x as often as those who did not, which is a veto
#: wearing a penalty's clothing — and too harsh for a catalogue where Fortnite, Rocket
#: League, Apex, Minecraft and Call of Duty are all crossplay. At 0.10 the ratio is 4x:
#: a strong steer, with cross-platform pairs still matching 4% of the time.
TASTE_WEIGHT = 0.58
VIBE_WEIGHT = 0.32
PLATFORM_WEIGHT = 0.10

#: Scale of the vibe kernel. Two independent standard vibe vectors sit about
#: ``sqrt(6)`` apart, which at this width scores ~0.47 — so an average stranger is
#: neither a vibe match nor a vibe mismatch, and the tails do the work.
VIBE_SCALE = 2.0

#: How sharply the vibe vector decides which temperament tags a gamer lists, and what
#: share of their tag budget goes to temperament rather than genre.
#:
#: These two matter more than they look. Vibe drives a real share of the matches, but the
#: recommender only ever sees the tags — so if the tags do not *reveal* the vibe, those
#: matches are unlearnable noise and the population is harder without being richer. At the
#: first values tried (temperature 0.8, half the budget) the cosine between two gamers'
#: keyword vectors correlated 0.04 with their true vibe similarity, which is nothing.
#: Sharpening to 0.35 takes that to about 0.2: someone who ticks "no mic", "solo player"
#: and "short sessions" has genuinely told you their temperament, which is how profile
#: tags work in practice.
VIBE_KEYWORD_TEMPERATURE = 0.35
VIBE_KEYWORD_SHARE = 0.55


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
    platforms: list[str]

    #: The mixture over archetypes this gamer was drawn from. Ground truth: the
    #: recommender never sees it, and the evaluation uses it to explain results.
    theta: np.ndarray = field(repr=False)

    #: Temperament on ``catalogue.VIBE_AXES``. Also ground truth, and the axis a
    #: games-only model is blind to.
    vibe: np.ndarray = field(repr=False)

    #: How readily this gamer swipes yes. Real users differ enormously here.
    pickiness: float = 0.0

    #: How readily others swipe yes on them, independent of fit — the popularity
    #: effect every matching product has.
    desirability: float = 0.0

    #: How many profiles this gamer gets shown. Heavy-tailed: most of a swipe log is
    #: written by a minority of accounts, and a prior fitted as if everyone swiped
    #: equally would be measuring reach rather than appeal.
    exposure: int = 0

    #: Minutes since last seen, for the product's "online now" filter (15 minutes).
    last_active_minutes_ago: int = 0


@dataclass
class Population:
    """A population and the swipe log it produced.

    Stored as index arrays rather than sets of id tuples. At twenty thousand gamers the
    log runs to well over a million impressions, and a ``set`` of string pairs for each
    of likes, passes and matches costs several hundred megabytes to hold something that
    fits in a few tens of megabytes of ``int32``. The id-shaped views below are built on
    demand, and the ones only needed for export are generators so they never all exist at
    once.
    """

    gamers: list[SyntheticGamer]

    #: (M, 2) int32. Every pair the product put in front of each other, ``i < j``.
    #: Symmetric on purpose: in a matching app the pair is the unit — both people get the
    #: chance to decide. Modelling exposure one-directionally instead makes a mutual match
    #: require the product to have independently surfaced the same pair twice, which at
    #: realistic exposure rates is a couple of percent, and the resulting match graph is
    #: driven by that coincidence rather than by taste.
    exposed: np.ndarray

    #: (M,) bool. Did the left side of the pair swipe yes on the right.
    liked_forward: np.ndarray

    #: (M,) bool. Did the right side swipe yes on the left.
    liked_backward: np.ndarray

    _match_index: dict[str, set[str]] | None = field(default=None, repr=False, compare=False)
    _ids: list[str] | None = field(default=None, repr=False, compare=False)

    # -- views -------------------------------------------------------------

    @property
    def user_ids(self) -> list[str]:
        if self._ids is None:
            self._ids = [g.user_id for g in self.gamers]
        return self._ids

    @property
    def mutual_mask(self) -> np.ndarray:
        """(M,) bool — the pairs where both sides said yes. The target to predict."""
        return self.liked_forward & self.liked_backward

    @property
    def mutual_pairs(self) -> np.ndarray:
        """(K, 2) int32 of mutually-matched index pairs."""
        return self.exposed[self.mutual_mask]

    def matches_of(self, user_id: str) -> set[str]:
        if self._match_index is None:
            ids = self.user_ids
            index: dict[str, set[str]] = {uid: set() for uid in ids}
            for left, right in self.mutual_pairs:
                index[ids[left]].add(ids[right])
                index[ids[right]].add(ids[left])
            self._match_index = index
        return self._match_index.get(user_id, set())

    def likes(self) -> list[tuple[str, str]]:
        """Directed (rater, target) pairs where the rater swiped yes.

        A list rather than a generator because ``fit_desirability`` needs two passes over
        it — once to count each rater's activity, once to accumulate.
        """
        ids = self.user_ids
        left, right = self.exposed[:, 0], self.exposed[:, 1]
        out = [(ids[a], ids[b]) for a, b in zip(left[self.liked_forward], right[self.liked_forward])]
        out += [(ids[b], ids[a]) for a, b in zip(left[self.liked_backward], right[self.liked_backward])]
        return out

    def interactions(self) -> Iterator[tuple[str, str, str]]:
        """(rater, target, LIKE|PASS) for every impression, for export.

        A generator: at twenty thousand gamers this is a few million rows, and the
        exporter writes them straight out rather than holding them.
        """
        ids = self.user_ids
        for (left, right), forward, backward in zip(
            self.exposed, self.liked_forward, self.liked_backward
        ):
            yield ids[left], ids[right], "LIKE" if forward else "PASS"
            yield ids[right], ids[left], "LIKE" if backward else "PASS"

    def exposure_counts(self) -> dict[str, int]:
        """How many times each gamer was *shown* to someone.

        The denominator that turns a like count into a like rate. Someone liked 40 times
        out of 200 is not more desirable than someone liked 30 times out of 60.
        """
        ids = self.user_ids
        counts = np.bincount(self.exposed.ravel(), minlength=len(ids))
        return {uid: int(count) for uid, count in zip(ids, counts)}


class PopulationGenerator:
    """Draws a population from the latent model described in the module docstring."""

    def __init__(
        self,
        n_gamers: int = 4000,
        *,
        seed: int = 20260801,
        # Dirichlet concentration. Below 1 the mixture is sparse — a gamer is mostly one
        # archetype with a secondary leaning, which is what real taste looks like. Raise
        # it and everyone becomes an identical blend of everything, and nothing is
        # learnable because nobody differs. Lowered from 0.35 alongside the warmth floor:
        # a denser mixture overlaps every archetype a little, which flattens the
        # distinction between a warm cross-genre pair and a cold one.
        mixture_concentration: float = 0.25,
        # How strongly the archetype mixture predicts temperament. At 1.0 vibe is a
        # deterministic function of taste and keywords go back to being a second copy of
        # the games signal; at 0.0 nobody's tags relate to what they play. 0.6 leaves
        # keywords genuinely informative about *both*.
        vibe_from_archetype: float = 0.6,
        # Median impressions for an ordinary gamer, before the lognormal spread.
        median_exposure: float = 60.0,
        # Fraction who sign up, swipe a handful of times and stop. Every product has
        # them, and they are why the desirability prior needs smoothing.
        lurker_fraction: float = 0.15,
        # Fraction whose age is drawn from the right tail rather than the archetype's
        # normal. Puts a realistic minority of thirty-somethings-and-up into a population
        # that otherwise stops around 50. See _draw_age.
        older_fraction: float = 0.12,
        # Spread of the personal biases. These are the knob that separates the *like*
        # rate from the *match* rate: affinity is shared by both sides of a pair, so on
        # its own it makes the two decisions move together and almost every like becomes
        # mutual. Pickiness and desirability are per-person, so they pull the two
        # directions apart — which is why a real product has far more one-sided likes
        # than matches.
        pickiness_sd: float = 2.2,
        desirability_sd: float = 0.9,
        # Multiplies latent affinity in the swipe model. Higher means taste dominates and
        # the problem becomes easy; lower means noise dominates and no model can do well.
        # Swept jointly with like_threshold along the curve that holds the like rate at
        # 0.24: raising it past ~32 stops buying any extra warm-versus-cold separation and
        # only makes the two sides of a pair agree more, which inflates the match rate
        # without making the graph more learnable.
        affinity_weight: float = 32.0,
        # Affinity at which a swipe is a coin flip before personal bias. Set above the
        # median (~0.64 for these mixtures) so most pairs are a no and a yes has to be
        # earned. Calibrated together with affinity_weight and pickiness_sd so ~24% of
        # swipes are yes and a median gamer comes away with ~12 matches, which is the
        # range a real matching product operates in. Lower it and the graph gets dense
        # enough that guessing scores well; raise it and most gamers have no matches to
        # evaluate against.
        like_threshold: float = 0.745,
    ) -> None:
        self.n_gamers = n_gamers
        self.rng = np.random.default_rng(seed)
        self.mixture_concentration = mixture_concentration
        self.vibe_from_archetype = vibe_from_archetype
        self.median_exposure = median_exposure
        self.lurker_fraction = lurker_fraction
        self.older_fraction = older_fraction
        self.pickiness_sd = pickiness_sd
        self.desirability_sd = desirability_sd
        self.affinity_weight = affinity_weight
        self.like_threshold = like_threshold

        self.n_archetypes = len(ARCHETYPES)
        self.game_names = [name for name, _ in ALL_GAMES]
        self.game_index = {name: i for i, name in enumerate(self.game_names)}

        self._game_profiles = self._build_game_profiles()
        self._keyword_profiles, self.keyword_names = self._build_keyword_profiles()
        self._vibe_profiles = self._build_vibe_keyword_profiles()
        self._archetype_vibe = np.array([a.vibe for a in ARCHETYPES], dtype=float)
        self._platform_profiles = np.array([a.platforms for a in ARCHETYPES], dtype=float)
        self._platform_profiles /= self._platform_profiles.sum(axis=1, keepdims=True)
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
        """P(keyword | archetype), over the genre keywords only.

        Vibe keywords keep only the background floor here: they are reachable from the
        taste side, so a gamer whose vibe pulls the other way can still occasionally list
        one, but their mass comes from ``_build_vibe_keyword_profiles``.
        """
        names = self.keyword_vocabulary()
        index = {k: i for i, k in enumerate(names)}
        profiles = np.full((self.n_archetypes, len(names)), 0.03)
        for a_idx, archetype in enumerate(ARCHETYPES):
            for keyword in archetype.keywords:
                if keyword in GENRE_KEYWORDS:
                    profiles[a_idx, index[keyword]] = 1.0
        return profiles / profiles.sum(axis=1, keepdims=True), names

    def _build_vibe_keyword_profiles(self) -> np.ndarray:
        """(n_keywords, 3) loadings, aligned to the keyword vocabulary.

        Genre keywords load zero on every axis, so their vibe-side probability is flat and
        the vibe distribution puts its mass on the temperament tags.
        """
        loadings = np.zeros((len(self.keyword_names), 3))
        for keyword, loading in VIBE_KEYWORDS.items():
            loadings[self.keyword_names.index(keyword)] = loading
        return loadings

    @staticmethod
    def keyword_vocabulary() -> list[str]:
        return sorted({k for a in ARCHETYPES for k in a.keywords})

    # -- gamers -----------------------------------------------------------

    def _draw_theta(self) -> np.ndarray:
        return self.rng.dirichlet(np.full(self.n_archetypes, self.mixture_concentration))

    def _draw_vibe(self, theta: np.ndarray) -> np.ndarray:
        """Temperament: partly implied by taste, partly the gamer's own.

        The two terms are weighted so the result keeps unit variance whatever
        ``vibe_from_archetype`` is set to, which matters because ``VIBE_SCALE`` and
        ``like_threshold`` are calibrated against that scale.
        """
        alpha = self.vibe_from_archetype
        implied = theta @ self._archetype_vibe
        own = self.rng.normal(0.0, 1.0, size=3)
        return alpha * implied + np.sqrt(1.0 - alpha**2) * own

    def _draw_age(self, theta: np.ndarray) -> int:
        """Age from the archetype mix, with a right tail.

        A plain normal around the archetype means produced a population that stopped dead
        at about 50, which is not what gaming looks like — there are people in their
        sixties on Old School RuneScape and Age of Empires, and a matching product that
        generates none of them has quietly decided they do not exist. The bulk of a social
        app's population genuinely is 18–35, so the fix is a skew and not a wider spread:
        the median stays put and a minority stretches out behind it.
        """
        mu = float(theta @ np.array([a.age_mu for a in ARCHETYPES]))
        sigma = float(theta @ np.array([a.age_sigma for a in ARCHETYPES]))
        age = self.rng.normal(mu, sigma)
        if self.rng.random() < self.older_fraction:
            age += self.rng.exponential(10.0)
        return int(np.clip(round(age), MINIMUM_AGE, MAXIMUM_AGE))

    def _sample_games(self, theta: np.ndarray) -> list[str]:
        n = int(np.clip(self.rng.normal(6.5, 2.2), 3, 12))
        # 80% taste, 20% "everyone has played Minecraft".
        p = 0.8 * (theta @ self._game_profiles) + 0.2 * self._popularity
        p = p / p.sum()
        picked = self.rng.choice(len(self.game_names), size=n, replace=False, p=p)
        return [self.game_names[i] for i in picked]

    def _sample_keywords(self, theta: np.ndarray, vibe: np.ndarray) -> list[str]:
        """Half what you play, half how you play.

        An even mixture of the two sources rather than a sequence of separate draws, so
        the count the product asks for (5–10) is spent on whichever tags this particular
        gamer's taste and temperament actually imply, instead of being split by quota.
        """
        n = int(np.clip(self.rng.normal(6.0, 1.5), 5, 10))

        genre = theta @ self._keyword_profiles
        # Softmax over the vibe loadings. The temperature keeps this peaked enough to be
        # informative without making the tags a lookup table for the vibe vector.
        vibe_logits = (self._vibe_profiles @ vibe) / VIBE_KEYWORD_TEMPERATURE
        vibe_p = np.exp(vibe_logits - vibe_logits.max())
        vibe_p /= vibe_p.sum()

        p = (1.0 - VIBE_KEYWORD_SHARE) * (genre / genre.sum()) + VIBE_KEYWORD_SHARE * vibe_p
        p /= p.sum()

        n = min(n, len(self.keyword_names))
        picked = self.rng.choice(len(self.keyword_names), size=n, replace=False, p=p)
        return [self.keyword_names[i] for i in picked]

    def _sample_platforms(self, theta: np.ndarray) -> list[str]:
        """One to three, weighted by what this taste is usually played on.

        Generated because the product collects it and filters on it, not because it
        predicts compatibility. It stays out of the taste vector for the same reason
        country does: the backend already applies it as a hard filter, and encoding it as
        a similarity signal would count it twice.
        """
        n = int(self.rng.choice([1, 2, 3], p=[0.55, 0.32, 0.13]))
        p = theta @ self._platform_profiles
        p = p / p.sum()
        picked = self.rng.choice(len(PLATFORM_IDS), size=n, replace=False, p=p)
        return [PLATFORM_IDS[i] for i in picked]

    def _draw_country(self) -> str:
        names = [c for c, _ in COUNTRIES]
        weights = np.array([w for _, w in COUNTRIES])
        return str(self.rng.choice(names, p=weights / weights.sum()))

    def _draw_exposure(self) -> int:
        """How many profiles this gamer gets shown.

        Lognormal for the ordinary case, with an explicit lurker class underneath. The
        shape matters more than the numbers: a log where everyone has exactly the same
        exposure makes the rate-versus-count distinction in ``fit_desirability`` vacuous
        and the ``1/sqrt(n)`` rater weighting a no-op, so neither would be tested by the
        data they exist to handle.
        """
        if self.rng.random() < self.lurker_fraction:
            return int(self.rng.integers(3, 13))
        return int(np.clip(self.rng.lognormal(np.log(self.median_exposure), 0.8), 5, 600))

    def _draw_last_active(self) -> int:
        """Minutes since last seen.

        Heavy-tailed, with roughly a tenth of the population inside the product's
        15-minute "online now" window at any moment.
        """
        if self.rng.random() < 0.10:
            return int(self.rng.integers(0, 15))
        return int(np.clip(self.rng.lognormal(np.log(600), 2.2), 15, 60 * 24 * 90))

    def _make_gamer(self, ordinal: int) -> SyntheticGamer:
        theta = self._draw_theta()
        vibe = self._draw_vibe(theta)
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
            age=self._draw_age(theta),
            country=self._draw_country(),
            # Drawn independently of taste, and not used by the recommender. It is here
            # because the schema has the column, not because it predicts anything: this is
            # a friend-finding product, so filtering candidates by gender is not a feature
            # and encoding it as a similarity signal would be a liability.
            gender=str(self.rng.choice(["Male", "Female", "Other", "Prefer not to say"],
                                       p=[0.62, 0.30, 0.04, 0.04])),
            games=self._sample_games(theta),
            keywords=self._sample_keywords(theta, vibe),
            platforms=self._sample_platforms(theta),
            theta=theta,
            vibe=vibe,
            # Some gamers say yes to almost anyone, some to almost nobody.
            pickiness=float(self.rng.normal(0.0, self.pickiness_sd)),
            desirability=float(self.rng.normal(0.0, self.desirability_sd)),
            exposure=self._draw_exposure(),
            last_active_minutes_ago=self._draw_last_active(),
        )

    # -- swipes -----------------------------------------------------------

    def _affinity_of_pairs(
        self, pairs: np.ndarray, theta: np.ndarray, vibe: np.ndarray, platforms: np.ndarray
    ) -> np.ndarray:
        """Latent affinity for every exposed pair, in one pass.

        Taste is the mixture cosine *through the warmth kernel*: ``θu·W·θv`` normalised by
        each side's own warmth-weighted norm. Because ``W`` is positive semi-definite the
        result is a genuine cosine in the space ``W`` induces, so it stays inside [0, 1]
        and a gamer is maximally similar to themselves.

        Vibe is a Gaussian kernel on the distance between temperaments — assortative,
        because on a friend-finding product it is: the tryhard wants the tryhard, and the
        person who plays two short sessions a week does not want the raid leader.

        Platform is **binary**: do you share a platform at all. It is not a taste, it is a
        constraint — two people with identical libraries who own different boxes cannot
        play most of those games together, and no amount of shared taste fixes it. Graded
        overlap would say that sharing two platforms is twice as good as sharing one, and
        it is not; the thing that matters is whether the number is zero. Not a hard veto
        either, because crossplay exists and people make friends they never queue with, so
        a pair with nothing in common here is penalised rather than forbidden.

        Vectorised over pairs and chunked. The loop this replaces evaluated one pair at a
        time in Python, which is a couple of minutes at four thousand gamers and does not
        finish in a sitting at twenty thousand.
        """
        warmed = theta @ WARMTH                                   # (N, A)
        self_affinity = np.einsum("ij,ij->i", warmed, theta)      # (N,)
        norms = np.sqrt(np.maximum(self_affinity, 1e-12))

        out = np.empty(len(pairs), dtype=float)
        # Chunked so the gathered (chunk, A) and (chunk, 3) temporaries stay small
        # regardless of how many pairs the population produced.
        for start in range(0, len(pairs), 250_000):
            block = pairs[start:start + 250_000]
            left, right = block[:, 0], block[:, 1]

            taste = np.einsum("ij,ij->i", warmed[left], theta[right])
            taste /= norms[left] * norms[right]

            gap = vibe[left] - vibe[right]
            vibe_sim = np.exp(-np.einsum("ij,ij->i", gap, gap) / (2.0 * VIBE_SCALE**2))

            shared_platform = (platforms[left] & platforms[right]).any(axis=1)

            out[start:start + 250_000] = (
                TASTE_WEIGHT * taste
                + VIBE_WEIGHT * vibe_sim
                + PLATFORM_WEIGHT * shared_platform
            )
        return out

    def _exposed_pairs(self, gamers: list[SyntheticGamer]) -> np.ndarray:
        """Which pairs get put in front of each other, as an (M, 2) index array.

        Each gamer draws their own number of impressions from ``exposure``. Partners are
        drawn with replacement and the pairs are then deduplicated: sampling without
        replacement per gamer would mean an O(n) pass for every one of them, which is the
        single slowest thing in the generator at scale, and the duplicates it avoids are a
        fraction of a percent of draws.

        Exposure is mildly skewed towards desirable profiles — the popularity effect every
        matching product has — but stays close to random so the log is not simply a record
        of some earlier recommender's opinion.
        """
        n = len(gamers)
        if n < 2:
            return np.empty((0, 2), dtype=np.int32)

        weights = np.exp(0.5 * np.array([g.desirability for g in gamers]))
        weights /= weights.sum()

        counts = np.minimum(np.array([g.exposure for g in gamers]), n - 1)
        total = int(counts.sum())
        if total == 0:
            return np.empty((0, 2), dtype=np.int32)

        left = np.repeat(np.arange(n, dtype=np.int64), counts)
        right = self.rng.choice(n, size=total, replace=True, p=weights)

        keep = left != right
        left, right = left[keep], right[keep]

        # Canonical order, then dedupe on a single integer key — much cheaper than
        # np.unique over a two-column array at this size.
        low = np.minimum(left, right)
        high = np.maximum(left, right)
        _, first = np.unique(low * n + high, return_index=True)
        return np.stack([low[first], high[first]], axis=1).astype(np.int32)

    def generate(self) -> Population:
        gamers = [self._make_gamer(i) for i in range(self.n_gamers)]

        exposed = self._exposed_pairs(gamers)
        if len(exposed) == 0:
            empty = np.zeros(0, dtype=bool)
            return Population(gamers=gamers, exposed=exposed, liked_forward=empty,
                              liked_backward=empty)

        theta = np.array([g.theta for g in gamers])
        vibe = np.array([g.vibe for g in gamers])
        pickiness = np.array([g.pickiness for g in gamers])
        desirability = np.array([g.desirability for g in gamers])

        platform_at = {name: i for i, name in enumerate(PLATFORM_IDS)}
        platforms = np.zeros((len(gamers), len(PLATFORM_IDS)), dtype=bool)
        for row, gamer in enumerate(gamers):
            for name in gamer.platforms:
                platforms[row, platform_at[name]] = True

        affinity = self._affinity_of_pairs(exposed, theta, vibe, platforms)
        left, right = exposed[:, 0], exposed[:, 1]
        earned = self.affinity_weight * (affinity - self.like_threshold)

        # Both sides decide independently; a match needs both to say yes. The asymmetry is
        # in who is doing the judging: the rater's pickiness and the target's desirability.
        forward = 1.0 / (1.0 + np.exp(-(earned + pickiness[left] + desirability[right])))
        backward = 1.0 / (1.0 + np.exp(-(earned + pickiness[right] + desirability[left])))

        return Population(
            gamers=gamers,
            exposed=exposed,
            liked_forward=self.rng.random(len(exposed)) < forward,
            liked_backward=self.rng.random(len(exposed)) < backward,
        )


def summarise(population: Population) -> dict[str, float]:
    """Sanity figures. A population with no mutual matches teaches nothing.

    The structural figures at the bottom are the ones worth watching. ``same_archetype``
    near 1.0 means the generator has collapsed into "everyone matches their own kind" and
    the data is too tidy to be useful; near chance (1/14) means affinity has stopped
    carrying taste at all. ``warm_cold_lift`` is the ratio of the match rate among warm
    archetype pairs to the rate among the coldest ones — the number that says whether
    cross-genre matching is *structured* or merely noise. It was 1.0 by construction
    before ``WARMTH`` existed.
    """
    n = len(population.gamers)
    match_counts = [len(population.matches_of(g.user_id)) for g in population.gamers]

    n_exposed = len(population.exposed)
    n_likes = int(population.liked_forward.sum() + population.liked_backward.sum())
    n_swipes = 2 * n_exposed
    mutual = population.mutual_mask

    dominant = np.array([int(np.argmax(g.theta)) for g in population.gamers])
    left_arch = dominant[population.exposed[:, 0]]
    right_arch = dominant[population.exposed[:, 1]]

    same = left_arch == right_arch
    warmth_of_pair = WARMTH[left_arch, right_arch]
    warm = (~same) & (warmth_of_pair >= 0.40)
    cold = (~same) & (warmth_of_pair <= 0.15)

    def rate(mask: np.ndarray) -> float:
        shown = int(mask.sum())
        return float(mutual[mask].sum() / shown) if shown else 0.0

    warm_rate, cold_rate = rate(warm), rate(cold)

    return {
        "gamers": n,
        "exposed_pairs": n_exposed,
        "likes": n_likes,
        "passes": n_swipes - n_likes,
        "mutual_matches": int(mutual.sum()),
        "like_rate": n_likes / max(1, n_swipes),
        "mutual_rate": float(mutual.sum() / max(1, n_exposed)),
        "mean_matches_per_gamer": float(np.mean(match_counts)),
        "median_matches_per_gamer": float(np.median(match_counts)),
        "gamers_with_no_match": sum(1 for c in match_counts if c == 0),
        # Must be zero: the product is 18+ and the generator has to respect the same rule
        # the backend enforces.
        "under_age": sum(1 for g in population.gamers if g.age < MINIMUM_AGE),
        "same_archetype": float(mutual[same].sum() / max(1, int(mutual.sum()))),
        "warm_cold_lift": (warm_rate / cold_rate) if cold_rate else 0.0,
    }
