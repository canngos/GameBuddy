"""Tests for the generator, the feature space and the recommender.

The emphasis is on the properties that would be expensive to get wrong: the age-band
guarantee, cold start, and the fact that the model beats chance. A recommender can be
wrong without being broken — it returns a plausible list of ids either way — so the tests
that matter are the ones that would fail if it stopped ranking and started guessing.
"""

from __future__ import annotations

import numpy as np
import pytest

from gamebuddy_model.catalogue import ALL_GAMES, ALL_KEYWORDS, ARCHETYPES
from gamebuddy_model.evaluate import RandomBaseline, RewrittenModel, evaluate
from gamebuddy_model.features import fit_feature_space, transform
from gamebuddy_model.population import MAJORITY_AGE, PopulationGenerator, summarise
from gamebuddy_model.recommender import train


@pytest.fixture(scope="module")
def population():
    return PopulationGenerator(n_gamers=600, seed=1234).generate()


@pytest.fixture(scope="module")
def model(population):
    gamers = population.gamers
    return train(
        [g.user_id for g in gamers],
        [g.games for g in gamers],
        [g.keywords for g in gamers],
    )


# -- population --------------------------------------------------------------


def test_no_minor_adult_matches(population):
    """The safety property. If this ever fails the product ships a child-safety incident,
    so it is asserted on the generated data as well as enforced in the backend."""
    by_id = {g.user_id: g for g in population.gamers}
    for pair in population.mutual:
        a, b = (by_id[x] for x in pair)
        assert a.is_minor == b.is_minor


def test_no_minor_adult_exposure(population):
    """Stronger than the above: minors and adults are never even shown to each other, so
    a match is impossible rather than merely unlikely."""
    by_id = {g.user_id: g for g in population.gamers}
    for pair in population.exposed_pairs:
        a, b = (by_id[x] for x in pair)
        assert a.is_minor == b.is_minor


def test_population_is_reproducible():
    a = PopulationGenerator(n_gamers=100, seed=7).generate()
    b = PopulationGenerator(n_gamers=100, seed=7).generate()
    assert [g.user_id for g in a.gamers] == [g.user_id for g in b.gamers]
    assert [g.games for g in a.gamers] == [g.games for g in b.gamers]
    assert a.mutual == b.mutual


def test_different_seeds_give_different_populations():
    a = PopulationGenerator(n_gamers=100, seed=7).generate()
    b = PopulationGenerator(n_gamers=100, seed=8).generate()
    assert [g.games for g in a.gamers] != [g.games for g in b.gamers]


def test_population_is_neither_empty_nor_saturated(population):
    """A population where nobody matches teaches nothing, and one where everybody matches
    teaches nothing either — both were failure modes while calibrating this."""
    stats = summarise(population)
    assert 0.05 < stats["like_rate"] < 0.6
    assert stats["median_matches_per_gamer"] >= 3
    assert stats["gamers_with_no_match"] < len(population.gamers) * 0.1


def test_profiles_are_drawn_from_the_catalogue(population):
    known_games = {name for name, _ in ALL_GAMES}
    for gamer in population.gamers:
        assert set(gamer.games) <= known_games
        assert set(gamer.keywords) <= set(ALL_KEYWORDS)
        assert len(gamer.games) == len(set(gamer.games)), "no duplicate games"


def test_ages_respect_the_band(population):
    for gamer in population.gamers:
        assert 13 <= gamer.age <= 75
        assert gamer.is_minor == (gamer.age < MAJORITY_AGE)


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
    they reappear in the vocabulary, similarity stops being about taste."""
    gamers = population.gamers
    space, _ = fit_feature_space([g.games for g in gamers], [g.keywords for g in gamers])
    vocabulary = set(space.game_index) | set(space.keyword_index)
    assert not vocabulary & {g.country for g in gamers}
    assert not vocabulary & {str(g.age) for g in gamers}


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

    exposures: dict[str, int] = {}
    for pair in population.exposed_pairs:
        for uid in pair:
            exposures[uid] = exposures.get(uid, 0) + 1
    model.fit_desirability(population.likes, exposures)
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


def test_model_beats_random(population):
    """The claim the original could not make. Guarded loosely, because the point is to
    catch a regression to chance rather than to pin an exact score."""
    ranked = evaluate(RewrittenModel(population, use_desirability=True), population,
                      name="hybrid", min_matches=3, max_users=300)
    chance = evaluate(RandomBaseline(population), population,
                      name="random", min_matches=3, max_users=300)
    assert ranked.precision_at_10 > chance.precision_at_10 * 2
    assert ranked.map_at_20 > chance.map_at_20 * 2
