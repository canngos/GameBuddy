"""Tests for the generator, the feature space and the recommender.

The emphasis is on the properties that would be expensive to get wrong: the age floor,
cold start, and the fact that the model beats chance. A recommender can be wrong without
being broken — it returns a plausible list of ids either way — so the tests that matter
are the ones that would fail if it stopped ranking and started guessing.

The population tests are unusually opinionated about *shape*, and deliberately. The
generator has more than one way to produce data that looks fine and teaches nothing: a
match graph where everybody matches their own archetype and nobody else, or one where
cross-genre matching is uniform noise, both pass a test that only checks the like rate is
sane. Those are the failure modes the structural assertions below exist to catch.
"""

from __future__ import annotations

import pathlib
import re

import numpy as np
import pytest

from gamebuddy_model.catalogue import (ALL_GAMES, ALL_KEYWORDS, ARCHETYPES, GENRE_KEYWORDS,
                                       VIBE_KEYWORDS, WARMTH)
from gamebuddy_model.evaluate import (RandomBaseline, RewrittenModel, evaluate,
                                      split_exposures)
from gamebuddy_model.features import fit_feature_space, transform
from gamebuddy_model.population import (MAXIMUM_AGE, MINIMUM_AGE, PLATFORM_IDS,
                                        PopulationGenerator, summarise)
from gamebuddy_model.recommender import train


@pytest.fixture(scope="module")
def population():
    return PopulationGenerator(n_gamers=600, seed=1234).generate()


@pytest.fixture(scope="module")
def big_population():
    """Large enough that the structural rates below are not just sampling noise."""
    return PopulationGenerator(n_gamers=2500, seed=1234).generate()


@pytest.fixture(scope="module")
def model(population):
    gamers = population.gamers
    return train(
        [g.user_id for g in gamers],
        [g.games for g in gamers],
        [g.keywords for g in gamers],
        [g.platforms for g in gamers],
    )


# -- population --------------------------------------------------------------


def test_population_is_adults_only(population):
    """The product is 18+ — the signup screen gates on it and the backend refuses a
    younger birth date — so a generated profile under 18 is one that could not exist,
    and seeding it into a live schema would be a profile the product is not allowed to
    have. Asserted on the data as well as enforced upstream."""
    assert summarise(population)["under_age"] == 0
    for gamer in population.gamers:
        assert gamer.age >= MINIMUM_AGE


def test_population_is_reproducible():
    a = PopulationGenerator(n_gamers=100, seed=7).generate()
    b = PopulationGenerator(n_gamers=100, seed=7).generate()
    assert [g.user_id for g in a.gamers] == [g.user_id for g in b.gamers]
    assert [g.games for g in a.gamers] == [g.games for g in b.gamers]
    assert np.array_equal(a.mutual_pairs, b.mutual_pairs)


def test_different_seeds_give_different_populations():
    a = PopulationGenerator(n_gamers=100, seed=7).generate()
    b = PopulationGenerator(n_gamers=100, seed=8).generate()
    assert [g.games for g in a.gamers] != [g.games for g in b.gamers]


def test_population_is_neither_empty_nor_saturated(population):
    """A population where nobody matches teaches nothing, and one where everybody matches
    teaches nothing either — both were failure modes while calibrating this."""
    stats = summarise(population)
    assert 0.15 < stats["like_rate"] < 0.40
    assert stats["median_matches_per_gamer"] >= 5
    assert stats["gamers_with_no_match"] < len(population.gamers) * 0.1


def test_most_matches_cross_archetype(big_population):
    """The failure mode this guards against is data that is too tidy.

    If competitive shooter players only ever matched other competitive shooter players,
    the recommender would score well by learning one rule and the product would be worse
    than useless — it would confidently refuse to introduce people who would get along.
    Same-archetype pairs should be over-represented against the 1-in-14 chance rate, and
    still be a minority of all matches.
    """
    same = summarise(big_population)["same_archetype"]
    assert 3 * (1 / len(ARCHETYPES)) < same < 0.45


def test_cross_archetype_matching_is_structured_not_noise(big_population):
    """And the failure mode this guards against is data that is merely random.

    Cross-genre matching that ignores what the genres are is not organic, it is noise —
    and it is what the generator produced before ``WARMTH`` existed, where battle-royale
    and competitive-FPS players came out as the *coldest* pair in the graph despite
    sharing two games in the catalogue. Warm archetype pairs must match meaningfully more
    often than cold ones, and cold pairs must still match sometimes.
    """
    stats = summarise(big_population)
    assert stats["warm_cold_lift"] > 1.8

    dominant = np.array([int(np.argmax(g.theta)) for g in big_population.gamers])
    left, right = big_population.exposed[:, 0], big_population.exposed[:, 1]
    cold = (dominant[left] != dominant[right]) & (WARMTH[dominant[left], dominant[right]] <= 0.10)
    assert big_population.mutual_mask[cold].mean() > 0.02, (
        "the coldest archetype pairs should still match sometimes — a floor of zero means "
        "the model can never learn to introduce anyone across taste"
    )


def test_keywords_reveal_temperament_not_just_genre(big_population):
    """Keywords have to carry an axis games do not.

    They used to be sampled from the same mixture as the games, which made them a second
    noisy copy of the genre signal — and made the feature space's keyword weight a knob
    with nothing to tune. Two gamers with similar temperament should share tags at a
    visibly higher rate than two drawn at random, independently of what they play.
    """
    gamers = big_population.gamers
    vibe = np.array([g.vibe for g in gamers])
    tags = [set(g.keywords) & set(VIBE_KEYWORDS) for g in gamers]

    rng = np.random.default_rng(0)
    left, right = rng.integers(0, len(gamers), 20000), rng.integers(0, len(gamers), 20000)
    keep = left != right
    left, right = left[keep], right[keep]

    gap = np.linalg.norm(vibe[left] - vibe[right], axis=1)
    shared = np.array([len(tags[a] & tags[b]) for a, b in zip(left, right)], dtype=float)

    close, far = gap < np.quantile(gap, 0.25), gap > np.quantile(gap, 0.75)
    assert shared[close].mean() > 1.5 * shared[far].mean()


def test_profiles_are_drawn_from_the_catalogue(population):
    known_games = {name for name, _ in ALL_GAMES}
    for gamer in population.gamers:
        assert set(gamer.games) <= known_games
        assert set(gamer.keywords) <= set(ALL_KEYWORDS)
        assert len(gamer.games) == len(set(gamer.games)), "no duplicate games"


def test_profiles_satisfy_the_products_minimums(population):
    """The app refuses to submit onboarding below these, so a generated profile that
    breaks them is one the product would never have stored."""
    for gamer in population.gamers:
        assert len(gamer.games) >= 3
        assert len(gamer.keywords) >= 5
        assert 1 <= len(gamer.platforms) <= 3
        assert set(gamer.platforms) <= set(PLATFORM_IDS)
        assert len(gamer.platforms) == len(set(gamer.platforms))


def test_activity_is_heavy_tailed(population):
    """A log where everyone swiped the same amount makes the desirability prior's
    rate-versus-count handling and its rater weighting untestable by the data they exist
    to handle — they only matter when activity is wildly uneven, and it is."""
    exposure = np.array([g.exposure for g in population.gamers])
    assert exposure.max() > 5 * np.median(exposure)
    assert (exposure < 15).mean() > 0.08, "there should be a visible tail of lurkers"


def test_ages_are_within_the_products_range(population):
    """The bounds match ``MIN_AGE``/``MAX_AGE`` in ``GameBuddy-App/src/validation.ts`` —
    an age outside them is one the app would refuse to store. The *shape* inside those
    bounds is a separate question, asserted by ``test_age_has_a_realistic_right_tail``."""
    for gamer in population.gamers:
        assert MINIMUM_AGE <= gamer.age <= MAXIMUM_AGE


def test_matches_are_symmetric(population):
    for gamer in population.gamers:
        for other in population.matches_of(gamer.user_id):
            assert gamer.user_id in population.matches_of(other)


# -- features ----------------------------------------------------------------


def test_vectors_are_l2_normalised(population):
    gamers = population.gamers
    _, vectors = fit_feature_space([g.games for g in gamers], [g.keywords for g in gamers])
    norms = np.sqrt(np.asarray(vectors.multiply(vectors).sum(axis=1))).ravel()
    assert np.allclose(norms, 1.0, atol=1e-9)


def test_unknown_items_are_ignored_not_fatal(population):
    """The catalogue grows. A gamer who picked a title added after the last training run
    must still get recommendations from the rest of their profile."""
    gamers = population.gamers
    space, _ = fit_feature_space([g.games for g in gamers], [g.keywords for g in gamers])
    vector = transform(space, [["Some Game Released Tomorrow", gamers[0].games[0]]], [[]])
    assert vector.nnz > 0


def test_age_and_country_are_absent_from_the_feature_space(population):
    """The specific defect in the original: age and country dominated the similarity. If
    they reappear in the vocabulary, similarity stops being about taste.

    Platform is a deliberate exception and is asserted *present* below — it is a real
    constraint on whether two people can play together, unlike country, which the
    evaluation shows carries no compatibility signal.
    """
    gamers = population.gamers
    space, _ = fit_feature_space(
        [g.games for g in gamers], [g.keywords for g in gamers], [g.platforms for g in gamers]
    )
    vocabulary = set(space.game_index) | set(space.keyword_index) | set(space.platform_index)
    assert not vocabulary & {g.country for g in gamers}
    assert not vocabulary & {str(g.age) for g in gamers}


def test_platform_is_in_the_feature_space(population):
    gamers = population.gamers
    space, vectors = fit_feature_space(
        [g.games for g in gamers], [g.keywords for g in gamers], [g.platforms for g in gamers]
    )
    assert set(space.platform_index) <= set(PLATFORM_IDS)
    assert space.n_features == len(space.game_index) + len(space.keyword_index) + len(space.platform_index)
    assert vectors.shape[1] == space.n_features


def test_the_platform_block_is_optional_in_both_directions(population):
    """An artefact trained before platform existed must still serve, and a profile with no
    platforms must still rank. Onboarding has required at least one since it shipped, but
    accounts predating that exist, and the right answer for them is to rank on what they
    do have rather than to fail."""
    gamers = population.gamers[:200]

    without, vectors = fit_feature_space([g.games for g in gamers], [g.keywords for g in gamers])
    assert without.platform_index == {}
    assert without.platform_tfidf is None
    # Transforming with platforms against a space that has no platform block ignores them.
    assert transform(without, [gamers[0].games], [gamers[0].keywords], [["PC"]]).shape[1] == vectors.shape[1]

    with_platforms, _ = fit_feature_space(
        [g.games for g in gamers], [g.keywords for g in gamers], [g.platforms for g in gamers]
    )
    # And a gamer with no platforms is an empty block, not an error.
    empty = transform(with_platforms, [gamers[0].games], [gamers[0].keywords], [[]])
    assert empty.nnz > 0


def test_cross_platform_pairs_match_less_often(big_population):
    """Platform has to matter in the *data*, or the feature is noise.

    Adding a block to the vector that the labels are indifferent to would not help the
    model — it would dilute the taste signal and the sweep would correctly show it hurting.
    Two gamers who share no platform cannot play most games together, so they should match
    measurably less; not never, because crossplay exists and people make friends they never
    queue with.
    """
    owns = [set(g.platforms) for g in big_population.gamers]
    left, right = big_population.exposed[:, 0], big_population.exposed[:, 1]
    shared = np.array([bool(owns[a] & owns[b]) for a, b in zip(left, right)])

    matched = big_population.mutual_mask
    assert shared.mean() > 0.3, "the split must not be so lopsided that it says nothing"
    assert matched[shared].mean() > 1.4 * matched[~shared].mean()
    assert matched[~shared].mean() > 0.02, "cross-platform pairs should still match sometimes"


# -- recommender -------------------------------------------------------------


def test_unknown_user_returns_empty_not_error(model):
    assert model.similar_to("no-such-gamer") == []


def test_never_recommends_the_gamer_to_themselves(model, population):
    for gamer in population.gamers[:50]:
        assert gamer.user_id not in model.similar_to(gamer.user_id, 50)


def test_returns_no_duplicates(model, population):
    result = model.similar_to(population.gamers[0].user_id, 100)
    assert len(result) == len(set(result))


def test_respects_the_requested_limit(model, population):
    assert len(model.similar_to(population.gamers[0].user_id, 7)) == 7


def test_cold_start_serves_an_untrained_profile(model):
    """A gamer who finished onboarding after the last retrain still gets a feed."""
    archetype = ARCHETYPES[0]
    result = model.similar_to_profile(
        [name for name, _ in archetype.games[:4]], archetype.keywords[:3], top_n=10
    )
    assert len(result) == 10


def test_cold_start_with_an_empty_profile_is_empty_not_an_error(model):
    assert model.similar_to_profile([], [], top_n=10) == []


def test_cold_start_with_only_unknown_items_is_empty(model):
    assert model.similar_to_profile(["Not A Real Game"], ["not a real keyword"], 10) == []


# -- exclusions: the fix for the feed running dry ----------------------------


def test_excluded_gamers_are_never_returned(model, population):
    user_id = population.gamers[0].user_id
    first = model.similar_to(user_id, 20)
    result = model.similar_to(user_id, 20, exclude=first)
    assert not set(result) & set(first)


def test_exclusions_reveal_deeper_candidates(model, population):
    """The actual bug. Swiping through a page must surface the next page rather than
    returning the same people, because the ranking never changes on its own."""
    user_id = population.gamers[0].user_id
    seen: set[str] = set()
    for _ in range(8):
        page = model.similar_to(user_id, 20, exclude=seen)
        assert page, "the feed went empty while candidates remained"
        assert not set(page) & seen
        seen.update(page)
    assert len(seen) == 160


def test_exclusion_widens_beyond_the_cluster_when_it_empties(model, population):
    """A heavy swiper exhausts their own cluster. The ranking must then widen to the whole
    population instead of returning nothing."""
    user_id = population.gamers[0].user_id
    idx = model._index_of[user_id]
    cluster = [
        model.user_ids[i]
        for i in range(len(model.user_ids))
        if model.labels[i] == model.labels[idx]
    ]
    result = model.similar_to(user_id, 10, exclude=cluster)
    assert len(result) == 10
    assert not set(result) & set(cluster)


def test_exhausting_everyone_returns_empty_not_an_error(model):
    everyone = list(model.user_ids)
    assert model.similar_to(everyone[0], 10, exclude=everyone) == []


def test_unknown_ids_in_the_exclusion_list_are_ignored(model, population):
    """The caller's exclusion list comes from its own database, which knows about gamers
    created since the last retrain."""
    user_id = population.gamers[0].user_id
    baseline = model.similar_to(user_id, 10)
    assert model.similar_to(user_id, 10, exclude=["ghost-1", "ghost-2"]) == baseline


def test_exclusion_preserves_ranking_order(model, population):
    """Removing someone from the middle must not reshuffle the rest."""
    user_id = population.gamers[0].user_id
    full = model.similar_to(user_id, 20)
    dropped = full[5]
    result = model.similar_to(user_id, 19, exclude=[dropped])
    assert result == [uid for uid in full if uid != dropped]


def test_cold_start_honours_exclusions(model):
    archetype = ARCHETYPES[0]
    games = [name for name, _ in archetype.games[:4]]
    first = model.similar_to_profile(games, archetype.keywords[:3], 10)
    second = model.similar_to_profile(games, archetype.keywords[:3], 10, exclude=first)
    assert not set(first) & set(second)


# -- inclusions: the fix for Gold's advanced filters -------------------------


def test_inclusion_restricts_the_pool(model, population):
    """Only the eligible set may be returned.

    The backend evaluates a filter — country, activity, game, platform — and hands over who
    matched. It used to hand over the complement instead, which grows with the population
    rather than with the answer and was refused outright above ten thousand entries.
    """
    user_id = population.gamers[0].user_id
    eligible = [g.user_id for g in population.gamers[1:40]]
    result = model.similar_to(user_id, 20, include=eligible)
    assert len(result) == 20
    assert set(result) <= set(eligible)


def test_inclusion_reaches_past_the_callers_own_cluster(model, population):
    """The one that would fail if the cluster restriction were left to bite.

    An eligible set is spread across every cluster — nothing about "online in Finland"
    correlates with taste — so intersecting it with the caller's own cluster leaves a
    handful of people, and the deck comes back mysteriously short while plenty of matching
    gamers sit there unshown. That is the *same* failure the filter was supposed to fix,
    reintroduced one layer down.
    """
    user_id = population.gamers[0].user_id
    idx = model._index_of[user_id]
    other_clusters = [
        model.user_ids[i]
        for i in range(len(model.user_ids))
        if model.labels[i] != model.labels[idx]
    ]
    result = model.similar_to(user_id, 20, include=other_clusters)
    assert len(result) == 20
    assert set(result) <= set(other_clusters)


def test_an_empty_inclusion_means_nobody(model, population):
    """Empty and absent are opposite answers.

    Empty is "the filter matched nobody" and must produce an empty deck. Reading it as
    "unfiltered" would show a gamer who asked for people online in their country a page of
    people who are neither — worse than showing nothing, because it looks like the filter
    was ignored.
    """
    user_id = population.gamers[0].user_id
    assert model.similar_to(user_id, 20, include=[]) == []
    assert len(model.similar_to(user_id, 20, include=None)) == 20


def test_inclusion_and_exclusion_compose(model, population):
    """A filtered feed still skips everyone already decided on."""
    user_id = population.gamers[0].user_id
    eligible = [g.user_id for g in population.gamers[1:40]]
    first = model.similar_to(user_id, 10, include=eligible)
    second = model.similar_to(user_id, 20, include=eligible, exclude=first)
    assert set(second) <= set(eligible)
    assert not set(first) & set(second)


def test_the_caller_is_never_returned_even_if_included(model, population):
    """The backend builds the eligible set from a database query, and the caller satisfies
    their own filter — they are in their own country, on their own platform. Nobody should
    be shown their own profile because of it."""
    user_id = population.gamers[0].user_id
    eligible = [g.user_id for g in population.gamers[:40]]
    assert user_id not in model.similar_to(user_id, 20, include=eligible)


def test_unknown_ids_in_the_inclusion_list_are_ignored(model, population):
    """Symmetric with the exclusion case, but the consequence differs and it is worth
    knowing which way: an unknown id in `exclude` changes nothing, while an unknown id in
    `include` narrows the pool. A stale artefact therefore serves a shorter filtered deck,
    never a wrong one — the fix is a retrain, not a code change."""
    user_id = population.gamers[0].user_id
    eligible = [g.user_id for g in population.gamers[1:40]]
    with_ghosts = model.similar_to(user_id, 20, include=[*eligible, "ghost-1", "ghost-2"])
    assert with_ghosts == model.similar_to(user_id, 20, include=eligible)


def test_inclusion_preserves_ranking_order(model, population):
    """Filtering decides who is ranked, not in what order — a filtered deck must be the
    unfiltered one with the ineligible people taken out."""
    user_id = population.gamers[0].user_id
    full = model.similar_to(user_id, 60)
    eligible = full[::2]
    result = model.similar_to(user_id, 30, include=eligible)
    assert result == eligible


def test_cold_start_honours_inclusions(model, population):
    """A Gold subscriber who signed up since the last retrain is served by this path."""
    archetype = ARCHETYPES[0]
    games = [name for name, _ in archetype.games[:4]]
    eligible = [g.user_id for g in population.gamers[:30]]
    result = model.similar_to_profile(
        games, archetype.keywords[:3], 20, include=eligible
    )
    assert result
    assert set(result) <= set(eligible)


def test_hidden_seed_accounts_still_win_against_an_inclusion_list(population):
    """Two restrictions, and the stricter one has to hold.

    An eligible set is built by the backend, which does not know which rows the artefact
    marks as seed accounts. If a filter listing one made it recommendable, turning
    HIDE_SEED_ACCOUNTS on would stop working for exactly the paying users who filter.
    """
    gamers = population.gamers[:200]
    hidden = [i < 100 for i in range(len(gamers))]
    trained = train(
        [g.user_id for g in gamers],
        [g.games for g in gamers],
        [g.keywords for g in gamers],
        [g.platforms for g in gamers],
        hidden=hidden,
    )
    trained.hide_seed_accounts = True

    seeded_ids = [g.user_id for g in gamers[:100]]
    assert trained.similar_to(gamers[150].user_id, 20, include=seeded_ids) == []


def test_similar_profiles_rank_each_other_highly(model, population):
    """A gamer's nearest neighbour should share more of their profile than a random gamer
    does. This is the weakest possible statement of 'the ranking means something', and it
    is the one that fails loudly if the similarity is ever computed on the wrong axis."""
    gamers = {g.user_id: g for g in population.gamers}
    rng = np.random.default_rng(0)
    ids = list(gamers)

    top_overlap, random_overlap = [], []
    for gamer in population.gamers[:100]:
        nearest = model.similar_to(gamer.user_id, 1)
        if not nearest:
            continue
        own = set(gamer.games) | set(gamer.keywords)
        near = gamers[nearest[0]]
        other = gamers[ids[int(rng.integers(len(ids)))]]
        top_overlap.append(len(own & (set(near.games) | set(near.keywords))))
        random_overlap.append(len(own & (set(other.games) | set(other.keywords))))

    assert np.mean(top_overlap) > np.mean(random_overlap) * 2


def test_tiny_population_trains_instead_of_raising(population):
    """A brand new deployment has a handful of users and no k worth choosing. Training
    must still succeed — refusing would mean no recommendations at all until the install
    crossed an invisible threshold."""
    gamers = population.gamers[:3]
    tiny = train(
        [g.user_id for g in gamers], [g.games for g in gamers], [g.keywords for g in gamers]
    )
    assert tiny.report.chosen_k == 1
    assert len(tiny.similar_to(gamers[0].user_id, 5)) == 2


def test_training_report_is_populated(model):
    report = model.report
    assert report.n_gamers == 600
    assert report.chosen_k in report.silhouette_by_k
    # The chosen k must be the best k, not whichever the loop ended on — the original bug.
    assert report.silhouette_by_k[report.chosen_k] == max(report.silhouette_by_k.values())
    assert sum(report.cluster_sizes) == report.n_gamers


def test_desirability_prior_is_optional_and_reversible(population):
    gamers = population.gamers
    model = train(
        [g.user_id for g in gamers], [g.games for g in gamers], [g.keywords for g in gamers]
    )
    baseline = model.similar_to(gamers[0].user_id, 20)

    model.fit_desirability(population.likes(), population.exposure_counts())
    assert model.desirability is not None
    assert model.similar_to(gamers[0].user_id, 20) != baseline

    model.desirability = None
    assert model.similar_to(gamers[0].user_id, 20) == baseline


def test_desirability_with_no_likes_is_a_no_op(model):
    model.fit_desirability([])
    assert model.desirability is None


def test_desirability_uses_the_rate_not_the_count(population):
    """The denominator matters. A gamer liked 40 times out of 200 impressions is less
    desirable than one liked 30 times out of 60, and a prior that ignores exposure ranks
    by reach instead of appeal."""
    gamers = population.gamers[:3]
    model = train(
        [g.user_id for g in gamers], [g.games for g in gamers], [g.keywords for g in gamers]
    )
    wide, narrow, quiet = (g.user_id for g in gamers)
    likes = [("x", wide)] * 40 + [("x", narrow)] * 30 + [("x", quiet)] * 5
    model.fit_desirability(likes, {wide: 200, narrow: 60, quiet: 40})

    scores = dict(zip(model.user_ids, model.desirability))
    assert scores[narrow] > scores[wide]


def test_rater_weighting_blunts_a_mass_rater(population):
    """A single hyperactive account must not outweigh the rest of the population.

    Counting every opinion equally lets one user who sits and rates a thousand profiles
    decide who is desirable. Daily caps only slow that down — a capped mass-rater still
    accumulates thousands of ratings a month — so the fix has to be in how ratings are
    counted, not in how many are allowed.
    """
    gamers = population.gamers[:60]
    model = train(
        [g.user_id for g in gamers], [g.games for g in gamers], [g.keywords for g in gamers]
    )
    ids = [g.user_id for g in gamers]
    favourite, rival = ids[0], ids[1]

    # Fifty ordinary raters slightly prefer `rival`; one obsessive account likes only
    # `favourite`, over and over.
    likes = [(ids[i], rival) for i in range(2, 52)]
    likes += [("obsessive", favourite)] * 500

    model.fit_desirability(likes, rater_weighting=False)
    unweighted = dict(zip(model.user_ids, model.desirability))

    model.fit_desirability(likes, rater_weighting=True)
    weighted = dict(zip(model.user_ids, model.desirability))

    assert unweighted[favourite] > unweighted[rival], "unweighted, the mass-rater wins"
    assert weighted[rival] > weighted[favourite], "weighted, the fifty real users win"


def test_rater_weighting_still_rewards_genuine_activity(population):
    """Damped, not flattened. An active user is more informative than an idle one — just
    not proportionally so, or there would be no point counting activity at all."""
    gamers = population.gamers[:10]
    model = train(
        [g.user_id for g in gamers], [g.games for g in gamers], [g.keywords for g in gamers]
    )
    weights = model._rater_weights([("busy", "x")] * 100 + [("quiet", "y")] * 4)

    assert weights["busy"] < weights["quiet"], "each individual rating counts for less"
    # Total influence grows sublinearly: 25x the ratings buys ~5x the influence.
    assert weights["busy"] * 100 > weights["quiet"] * 4


def test_desirability_falls_back_to_counts_without_exposures(population):
    """Impression logs may not exist yet. The prior should still do something rather than
    silently flatten to zero, which is what an unguarded rate calculation does when every
    denominator equals its numerator."""
    gamers = population.gamers[:3]
    model = train(
        [g.user_id for g in gamers], [g.games for g in gamers], [g.keywords for g in gamers]
    )
    popular, ignored = gamers[0].user_id, gamers[1].user_id
    model.fit_desirability([("x", popular)] * 30 + [("x", ignored)])

    assert model.desirability is not None
    scores = dict(zip(model.user_ids, model.desirability))
    assert scores[popular] > scores[ignored]


# -- end to end --------------------------------------------------------------


def test_model_beats_random(big_population):
    """The claim the original could not make. Guarded loosely, because the point is to
    catch a regression to chance rather than to pin an exact score."""
    split = split_exposures(big_population, seed=1234)
    ranked = evaluate(RewrittenModel(big_population, split, use_desirability=True),
                      big_population, split, name="hybrid", min_matches=3, max_users=400)
    chance = evaluate(RandomBaseline(big_population, split), big_population, split,
                      name="random", min_matches=3, max_users=400)
    assert ranked.precision_at_10 > chance.precision_at_10 * 2
    assert ranked.map_at_20 > chance.map_at_20 * 2


def test_the_split_actually_holds_data_back(big_population):
    """The leak this exists to close.

    The desirability prior and the popularity baseline are fitted from the like log, and
    mutual matches are made of those same likes — so scoring them against the whole graph
    was scoring them on answers they had been shown. Nothing a model may learn from can
    appear in what it is scored against.
    """
    split = split_exposures(big_population, ratio=0.5, seed=1234)

    fit_pairs = {frozenset(pair) for pair in split.fit_likes}
    holdout_pairs = {
        frozenset((uid, other))
        for uid, others in split.holdout_matches.items()
        for other in others
    }
    assert holdout_pairs, "the holdout must contain some matches to score against"
    assert not (fit_pairs & holdout_pairs), "a scored match was visible in the fitting log"


def test_the_model_serves_a_mix_not_a_monoculture(big_population):
    """A recommender that only ever returns the caller's own archetype would score
    respectably and make a dull product. What it serves should lean towards similar taste
    without being confined to it."""
    from gamebuddy_model.evaluate import structure_report

    split = split_exposures(big_population, seed=1234)
    served = structure_report(
        RewrittenModel(big_population, split, use_desirability=True),
        big_population,
        max_users=150,
    )
    chance = 1 / len(ARCHETYPES)
    assert chance < served["same_archetype"] < 0.6
    assert served["mean_warmth"] > 0.15, "cross-genre suggestions should favour warm pairs"


# -- catalogue ---------------------------------------------------------------


def test_warmth_is_a_usable_similarity_kernel():
    """Symmetric, unit-diagonal and positive semi-definite.

    The first two are obvious. The third is the one that bites: ``WARM_PAIRS`` is a
    hand-written table of opinions, and nothing stops those opinions from being
    geometrically impossible — "A is close to B, B is close to C, A is far from C" can be
    written down but cannot be embedded. A kernel with a negative eigenvalue lets a gamer
    be less similar to themselves than to a stranger, which surfaces as a cosine outside
    [-1, 1] and a like probability that makes no sense. ``_build_warmth`` projects onto the
    PSD cone to guarantee it; this test is what tells whoever edits the table next that
    the projection has started moving their numbers.
    """
    assert np.allclose(WARMTH, WARMTH.T)
    assert np.allclose(np.diag(WARMTH), 1.0)
    assert np.linalg.eigvalsh(WARMTH).min() >= -1e-9
    off_diagonal = WARMTH[~np.eye(len(ARCHETYPES), dtype=bool)]
    assert off_diagonal.min() > 0.0, "no archetype pair should be at zero warmth"
    assert off_diagonal.max() < 1.0


def test_warmth_matches_the_table_it_was_written_from():
    """The PSD projection is allowed to nudge the hand-written numbers, not to rewrite
    them. If this fails, the table has become inconsistent enough that the projection is
    now making the decisions."""
    from gamebuddy_model.catalogue import ARCHETYPE_INDEX, WARM_PAIRS

    for (left, right), value in WARM_PAIRS.items():
        actual = WARMTH[ARCHETYPE_INDEX[left], ARCHETYPE_INDEX[right]]
        assert abs(actual - value) < 0.05, f"{left}/{right}: {value} became {actual:.3f}"


def test_every_keyword_is_either_genre_or_vibe():
    """The two sets partition the vocabulary. A keyword in neither would be unreachable
    from the vibe side and near-unreachable from the genre side; one in both would be
    double-counted."""
    assert set(GENRE_KEYWORDS).isdisjoint(VIBE_KEYWORDS)
    assert set(GENRE_KEYWORDS) | set(VIBE_KEYWORDS) == set(ALL_KEYWORDS)


def test_the_prior_breaks_ties_rather_than_overriding_taste(big_population):
    """The popularity prior must not outrank a visibly better fit.

    It is standardised to unit variance, so adding it raw applied a fixed-size shove to a
    similarity whose spread differs from pool to pool. Asked for cosy life-sim players the
    model returned a battle-royale and an MMO player ahead of five gamers with Stardew
    Valley and Animal Crossing on their profiles, purely because they were more liked.
    Scaling the nudge by the pool's own spread is what makes it a tie-break.
    """
    from gamebuddy_model.catalogue import GAMES

    gamers = big_population.gamers
    model = train([g.user_id for g in gamers], [g.games for g in gamers],
                  [g.keywords for g in gamers])
    split = split_exposures(big_population, seed=1234)
    model.fit_desirability(split.fit_likes, split.fit_exposures)

    cosy = {name for name, _ in GAMES["cozy_life_sim"]}
    plays = {g.user_id: set(g.games) for g in gamers}
    query = (["Stardew Valley", "Animal Crossing: New Horizons", "The Sims 4"],
             ["chill", "casual", "no mic", "short sessions", "decorator"])

    ranked = model.similar_to_profile(*query, 20)
    on_taste = sum(1 for uid in ranked if cosy & plays[uid])
    assert on_taste >= 15, (
        f"only {on_taste}/20 of the returned candidates play a cosy game — the prior is "
        "picking candidates rather than ordering them"
    )


def test_artefact_records_the_environment_that_built_it(model):
    """Unpickling fitted estimators across scikit-learn versions is undefined behaviour,
    and it does not raise — it returns a model that loads, serves, and ranks wrongly. The
    stamp is what lets the API say so at startup instead of it going unnoticed."""
    build = model.report.build
    assert {"python", "numpy", "scipy", "scikit-learn"} <= set(build.versions)
    assert not build.mismatches(), "freshly trained, so nothing should differ"

    build.versions["scikit-learn"] = "0.0.1-not-a-real-version"
    assert "scikit-learn" in build.mismatches()


def test_interpreter_skew_is_reported_separately_from_library_skew(model):
    """Training happens outside the serving container, so the Python version differing is
    the normal state. Folding it in with the library check would make the check fire on
    every boot, and a warning that always fires is one nobody reads."""
    build = model.report.build
    build.versions["python"] = "3.0.0"
    assert build.interpreter_mismatch() == ("3.0.0", __import__("platform").python_version())
    assert "python" not in build.mismatches()


def test_age_has_a_realistic_right_tail(big_population):
    """Young-skewed, as a social product is, but not truncated.

    A plain normal around the archetype means stopped the population dead at about 50.
    There are people in their sixties on Old School RuneScape, and generating none of them
    is a quiet decision that they do not exist — the kind of thing that only shows up when
    someone browses the seeded database and finds it looks nothing like their users.
    """
    ages = np.array([g.age for g in big_population.gamers])
    assert ages.min() >= MINIMUM_AGE and ages.max() <= MAXIMUM_AGE
    assert 22 <= np.median(ages) <= 30, "the bulk should still be a young social product"
    assert ((ages >= 18) & (ages <= 34)).mean() > 0.75
    assert (ages >= 40).mean() > 0.03, "there should be a real tail, not a cliff"


def test_catalogue_names_match_the_database_seed():
    """The model's game names must be exactly the product's game names.

    The model is trained on names, and names are what crosses every boundary: the local
    seeder resolves ``gamer_games_join`` by joining on them, and the backend's cold-start
    path sends the names off a live profile to ``/predict/cold-start``. Neither fails loudly
    when they disagree — the seeder's INSERT...SELECT matches no row and inserts nothing,
    and ``transform`` drops vocabulary it does not recognise. The result is a seeded gamer
    quietly missing a fifth of their library and a real user whose favourite game
    contributes nothing to their recommendations.

    That is not hypothetical: 17 of the 100 titles had drifted apart this way — the
    catalogue said "Baldur's Gate 3" and "VALORANT" where the database said "Baldur's Gate
    III" and "Valorant". Everything looked fine from either side alone.
    """
    seed_sql = (pathlib.Path(__file__).resolve().parents[2]
                / "GameBuddy-backend/src/main/resources/db/seed-local.sql")
    if not seed_sql.exists():
        pytest.skip("backend checkout not present")

    text = seed_sql.read_text(encoding="utf-8")
    # Rows are `('<uuid>', '<name>', '<category>', ...)`; take the name that follows an id.
    seeded_games = {
        name.replace("''", "'")
        for _, name in re.findall(r"\(\s*'([0-9a-f-]{36})',\s*'((?:[^']|'')*)',", text)
    }

    catalogue = {name for name, _ in ALL_GAMES}
    missing = catalogue - seeded_games
    assert not missing, (
        f"{len(missing)} catalogue titles are not in seed-local.sql, so they will silently "
        f"vanish from seeded profiles and from cold-start ranking: {sorted(missing)}"
    )


# -- seed-account visibility -------------------------------------------------


def test_seed_accounts_are_learned_from_but_not_served(population):
    """The whole point of keeping the seed population after launch.

    Hidden rows must still shape the feature space — that is why they are kept, since a
    vector space fitted on twenty thousand profiles is steadier than one fitted on the
    first thousand signups — while never appearing in anyone's deck, because they will
    never answer a message.
    """
    gamers = population.gamers[:400]
    ids = [g.user_id for g in gamers]
    # First 300 are seed accounts, last 100 are "real".
    hidden = [i < 300 for i in range(len(gamers))]

    model = train(ids, [g.games for g in gamers], [g.keywords for g in gamers],
                  [g.platforms for g in gamers], hidden=hidden)

    # Learned from: every profile contributed to the vocabulary and the vectors.
    assert model.vectors.shape[0] == 400
    assert len(model.user_ids) == 400

    real = ids[300:]
    model.hide_seed_accounts = True
    for uid in real[:10]:
        returned = model.similar_to(uid, 100)
        assert returned, "a real gamer must still get candidates"
        assert set(returned) <= set(real), "a hidden account was recommended"


def test_hiding_is_off_until_asked(population):
    """Default off: during development the seed accounts are the population, and defaulting
    to hidden would empty the feed of anyone who upgraded without reading a changelog."""
    gamers = population.gamers[:200]
    ids = [g.user_id for g in gamers]
    model = train(ids, [g.games for g in gamers], [g.keywords for g in gamers],
                  [g.platforms for g in gamers], hidden=[True] * 200)
    assert model.hide_seed_accounts is False
    assert model.similar_to(ids[0], 20), "nothing should be hidden by default"


def test_cold_start_also_respects_hiding(population):
    """Cold start is the path every new signup takes until the next retrain, so a leak
    here would show seed accounts to precisely the users forming a first impression."""
    gamers = population.gamers[:400]
    ids = [g.user_id for g in gamers]
    hidden = [i < 300 for i in range(len(gamers))]
    model = train(ids, [g.games for g in gamers], [g.keywords for g in gamers],
                  [g.platforms for g in gamers], hidden=hidden)
    model.hide_seed_accounts = True

    newcomer = gamers[350]
    returned = model.similar_to_profile(newcomer.games, newcomer.keywords, 100,
                                        platforms=newcomer.platforms)
    assert returned
    assert set(returned) <= set(ids[300:])


def test_an_artefact_without_markers_still_serves(population):
    """Artefacts trained before the marker existed must keep working rather than refusing
    to return anyone — an upgrade should not empty the feed."""
    gamers = population.gamers[:200]
    ids = [g.user_id for g in gamers]
    model = train(ids, [g.games for g in gamers], [g.keywords for g in gamers],
                  [g.platforms for g in gamers])
    assert model.hidden is None
    model.hide_seed_accounts = True
    assert model.similar_to(ids[0], 20)


def test_the_seed_marker_has_one_definition():
    """Asked by the exporter, the trainer and the cleanup SQL. If they disagree, accounts
    get trained on but never served, or served but never trained, and nothing says so."""
    from gamebuddy_model.seed import BOT_EMAIL_DOMAIN, is_seed_account

    assert is_seed_account(f"bot00001@{BOT_EMAIL_DOMAIN}")
    assert is_seed_account(f"BOT00001@{BOT_EMAIL_DOMAIN.upper()}"), "must be case-insensitive"
    assert not is_seed_account("a.real.person@gmail.com")
    assert not is_seed_account("")
    assert not is_seed_account(None)
    # Must not match a lookalike domain a real user could register.
    assert not is_seed_account("someone@notbot.gamebuddy.invalid.example.com")
