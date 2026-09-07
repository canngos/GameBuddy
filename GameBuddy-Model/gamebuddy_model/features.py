"""Turns a gamer profile into the vector the recommender compares.

Three decisions here, each of which was wrong in the original pipeline.

**Age and country are not in the vector.** They were, unscaled, sitting among hundreds of
0/1 columns — age around 22 and a label-encoded country around 37 against features that
are 0 or 1. Pearson correlation is driven by covariance, so those two entries dominated
it completely and "similarity" was mostly "similar age, same country" regardless of what
anyone played. Age is a *safety* constraint, enforced as a hard filter by the backend, and
country is a filter at most; neither belongs in a taste vector.

**Label encoding is gone.** Turning country into 0..N tells a distance function that
Turkey is nearer to Sweden than to Japan because of where they landed in the alphabet.

**Games are TF-IDF weighted, not raw counts.** Almost everyone lists Minecraft, so sharing
it says nearly nothing; sharing Disco Elysium says a great deal. Inverse document
frequency is exactly that intuition, and without it the popular titles drown out the
informative ones.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np
from scipy.sparse import csr_matrix, hstack
from sklearn.feature_extraction.text import TfidfTransformer
from sklearn.preprocessing import normalize


@dataclass
class FeatureSpace:
    """The fitted vocabulary and weighting. Must be reused at inference time."""

    game_index: dict[str, int]
    keyword_index: dict[str, int]
    game_tfidf: TfidfTransformer
    keyword_tfidf: TfidfTransformer

    #: Platform is in the vector, and the argument for leaving it out was wrong.
    #:
    #: The reasoning was that the backend already applies platform as a hard filter, so
    #: encoding it as similarity would count it twice. But that filter is a Gold
    #: entitlement and it defaults to null — `FeedFilters.narrowing()` treats setting it as
    #: a paid narrowing — so for a Basic account, and for any Gold account that has not
    #: touched the filter sheet, platform never enters the ranking at all. Which means it
    #: was not being counted twice; it was not being counted.
    #:
    #: And it is not a soft preference. Two gamers with identical libraries on different
    #: boxes cannot play most of those games together, which is a stronger constraint than
    #: any amount of shared genre is a recommendation.
    #:
    #: Defaulted so that an artefact pickled before this field existed still unpickles and
    #: serves, with the platform block simply absent.
    platform_index: dict[str, int] = field(default_factory=dict)
    platform_tfidf: TfidfTransformer | None = None

    #: Keywords describe how someone plays, games describe what they play, and the two
    #: are different axes rather than two views of one — see ``catalogue.VIBE_KEYWORDS``.
    #: That is exactly what this weight trades off, and the sweep shows it cleanly, as
    #: correlation between the model's similarity and each true latent component:
    #:
    #:     weight   0.5    1.0    1.5
    #:     taste    0.219  0.205  0.088
    #:     vibe     0.013  0.223  0.240
    #:     total    0.183  0.294  0.210
    #:
    #: At 0.5 the games dominate and the model is blind to temperament; at 1.5 the tags
    #: dominate and it has stopped tracking what people play. 1.0 is the only setting that
    #: recovers both, and it is the peak on the held-out match graph too. Worth noting the
    #: match-graph numbers alone could not have chosen this — the P@10 differences across
    #: this range sit inside the run-to-run spread, and it was the latent-recovery
    #: diagnostic that showed what each setting was actually giving up.
    keyword_weight: float = 1.0

    #: Platform gets its own weight because it behaves unlike the other two blocks: five
    #: values against a hundred games, so each one carries far more mass, and PC alone
    #: covers two thirds of the population.
    #:
    #: The sweep, against held-out matches and against how much of each latent component
    #: the model's similarity recovers:
    #:
    #:     weight      0.0    0.6    1.0    1.5    2.5
    #:     lift        6.62   6.23   7.38   8.54   7.69
    #:     r_taste     0.206  0.209  0.210  0.150  0.087
    #:     r_vibe      0.227  0.228  0.226  0.183  0.099
    #:     r_platform  0.003  0.027  0.128  0.501  0.726
    #:     on-taste    15/20  --     17/20  14/20  --
    #:
    #: Precision peaks at 1.5, and 1.0 is the right answer anyway. At 1.5 the extra
    #: accuracy is bought by letting platform crowd out the other two: taste recovery falls
    #: by a quarter and the cosy-life-sim query starts returning people who do not play
    #: cosy games. That is a bad trade specifically *because* platform is perfectly
    #: observable — the backend can filter on it exactly, with a WHERE clause, for free.
    #: Spending the model's limited capacity on a fact a query already knows, at the cost
    #: of the taste and temperament signal that nothing else can recover, is spending it in
    #: the one place it is worth least.
    #:
    #: At 1.0 every axis improves over having no platform block at all: more lift, more
    #: total recovery, and taste and vibe recovery untouched.
    platform_weight: float = 1.0

    @property
    def n_features(self) -> int:
        return len(self.game_index) + len(self.keyword_index) + len(self.platform_index)


def _one_hot(items_per_row: list[list[str]], index: dict[str, int]) -> csr_matrix:
    rows, cols = [], []
    for row, items in enumerate(items_per_row):
        for item in items:
            col = index.get(item)
            if col is not None:
                rows.append(row)
                cols.append(col)
    data = np.ones(len(rows), dtype=np.float64)
    return csr_matrix((data, (rows, cols)), shape=(len(items_per_row), len(index)))


def fit_feature_space(
    games_per_gamer: list[list[str]],
    keywords_per_gamer: list[list[str]],
    platforms_per_gamer: list[list[str]] | None = None,
    *,
    keyword_weight: float = 1.0,
    platform_weight: float = 1.0,
) -> tuple[FeatureSpace, csr_matrix]:
    """Fits the vocabulary and weighting over the three profile blocks.

    ``platforms_per_gamer`` is optional so a caller with only games and keywords still
    works — the block is then absent rather than empty, and the vector is what it was
    before platform existed.
    """
    platforms_per_gamer = platforms_per_gamer if platforms_per_gamer is not None else []

    game_index = {g: i for i, g in enumerate(sorted({g for row in games_per_gamer for g in row}))}
    keyword_index = {k: i for i, k in enumerate(sorted({k for row in keywords_per_gamer for k in row}))}
    platform_index = {p: i for i, p in enumerate(sorted({p for row in platforms_per_gamer for p in row}))}

    space = FeatureSpace(
        game_index=game_index,
        keyword_index=keyword_index,
        game_tfidf=TfidfTransformer(norm=None, smooth_idf=True).fit(
            _one_hot(games_per_gamer, game_index)),
        keyword_tfidf=TfidfTransformer(norm=None, smooth_idf=True).fit(
            _one_hot(keywords_per_gamer, keyword_index)),
        platform_index=platform_index,
        # IDF matters more here than anywhere else: two thirds of the population is on PC,
        # so sharing it says almost nothing, while both being on Switch is a real signal.
        # Without it every PC player would look alike.
        platform_tfidf=(
            TfidfTransformer(norm=None, smooth_idf=True).fit(
                _one_hot(platforms_per_gamer, platform_index))
            if platform_index else None
        ),
        keyword_weight=keyword_weight,
        platform_weight=platform_weight,
    )
    return space, transform(space, games_per_gamer, keywords_per_gamer, platforms_per_gamer)


def transform(
    space: FeatureSpace,
    games_per_gamer: list[list[str]],
    keywords_per_gamer: list[list[str]],
    platforms_per_gamer: list[list[str]] | None = None,
) -> csr_matrix:
    """Vectorises profiles against an already-fitted space.

    Unknown games, keywords and platforms are dropped rather than raising: the catalogue
    grows, and a gamer who has picked a title added after the last training run should
    still get recommendations from the rest of their profile.
    """
    games = space.game_tfidf.transform(_one_hot(games_per_gamer, space.game_index))
    keywords = space.keyword_tfidf.transform(_one_hot(keywords_per_gamer, space.keyword_index))
    blocks = [games, keywords * space.keyword_weight]

    if space.platform_tfidf is not None:
        # Missing platforms are an empty row, not an error. The app has required at least
        # one since onboarding shipped, but accounts created before that exist, and the
        # right answer for them is to rank on games and keywords rather than to fail.
        rows = platforms_per_gamer if platforms_per_gamer is not None else [[]] * len(games_per_gamer)
        platforms = space.platform_tfidf.transform(_one_hot(rows, space.platform_index))
        blocks.append(platforms * space.platform_weight)

    # L2 so that cosine similarity is a plain dot product, and so a gamer who listed
    # twelve games is not automatically "closer to everything" than one who listed three.
    return normalize(hstack(blocks, format="csr"), norm="l2", axis=1)
