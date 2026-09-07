"""Tests for the nightly retrain job.

The job's whole value is what it refuses to do, so that is what is tested: the checks are
between a bad export and the artefact that is serving real traffic, and if they do not
fire the job is worse than no job at all — it replaces a working model with a broken one
on a schedule, at an hour when nobody is watching.
"""

from __future__ import annotations

import pickle
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gamebuddy_model.population import PopulationGenerator  # noqa: E402
from gamebuddy_model.recommender import train  # noqa: E402
from tools import retrain  # noqa: E402


@pytest.fixture(scope="module")
def artefact_bytes() -> bytes:
    gamers = PopulationGenerator(n_gamers=400, seed=99).generate().gamers
    model = train([g.user_id for g in gamers], [g.games for g in gamers],
                  [g.keywords for g in gamers], [g.platforms for g in gamers])
    return pickle.dumps(model, protocol=pickle.HIGHEST_PROTOCOL)


@pytest.fixture
def artefact(tmp_path, artefact_bytes) -> Path:
    path = tmp_path / "recommender.pkl"
    path.write_bytes(artefact_bytes)
    return path


def test_gamers_in_reads_the_population_size(artefact):
    assert retrain.gamers_in(artefact) == 400


def test_gamers_in_survives_a_corrupt_current_artefact(tmp_path):
    """A corrupt artefact already in place must not block the retrain that would replace
    it — that is exactly the situation where a retrain is most needed."""
    broken = tmp_path / "recommender.pkl"
    broken.write_bytes(b"this is not a pickle")
    assert retrain.gamers_in(broken) == 0


def test_gamers_in_treats_a_missing_artefact_as_empty(tmp_path):
    assert retrain.gamers_in(tmp_path / "nothing.pkl") == 0


def test_verify_accepts_a_good_artefact(artefact):
    retrain.verify(artefact, expected_gamers=400)


def test_verify_rejects_a_size_mismatch(artefact):
    """Guards against training on one dataset and counting another — the artefact and the
    export have to describe the same population."""
    with pytest.raises(SystemExit):
        retrain.verify(artefact, expected_gamers=4000)


def test_verify_rejects_a_model_that_ranks_nothing(tmp_path, artefact_bytes):
    """Training can succeed and still produce something useless. Here the cluster labels
    are corrupted so every caller's cluster is empty — the kind of thing that raises
    nothing at training time and empties every deck in production."""
    model = pickle.loads(artefact_bytes)
    model.user_ids = []
    model._index_of = {}
    path = tmp_path / "broken.pkl"
    path.write_bytes(pickle.dumps(model, protocol=pickle.HIGHEST_PROTOCOL))
    with pytest.raises(SystemExit):
        retrain.verify(path, expected_gamers=0)


def test_verify_rejects_an_empty_feature_space(tmp_path, artefact_bytes):
    model = pickle.loads(artefact_bytes)
    model.space.game_index = {}
    model.space.keyword_index = {}
    model.space.platform_index = {}
    path = tmp_path / "empty.pkl"
    path.write_bytes(pickle.dumps(model, protocol=pickle.HIGHEST_PROTOCOL))
    with pytest.raises(SystemExit):
        retrain.verify(path, expected_gamers=400)


def test_a_tiny_export_never_reaches_the_serving_path(tmp_path, monkeypatch, artefact):
    """The headline guard. A broken export that returns a handful of gamers trains
    perfectly well and would replace a good artefact with one that knows nobody."""
    data = tmp_path / "data"
    data.mkdir()
    (data / "gamers.csv").write_text(
        "user_id,username\n" + "\n".join(f"u{i},n{i}" for i in range(20)), encoding="utf-8")

    monkeypatch.setattr(retrain, "run", lambda *a, **k: None)
    monkeypatch.setattr(retrain.tempfile, "TemporaryDirectory",
                        lambda **kw: _FixedDir(tmp_path))

    before = artefact.read_bytes()
    with pytest.raises(SystemExit):
        retrain.main(["--artifacts", str(artefact.parent), "--database-url", "postgresql://x",
                      "--min-gamers", "500"])
    assert artefact.read_bytes() == before, "the serving artefact must be untouched"


def test_a_collapsed_population_never_reaches_the_serving_path(tmp_path, monkeypatch, artefact):
    """Above the absolute floor but far below what is already being served — a
    half-applied migration rather than churn."""
    data = tmp_path / "data"
    data.mkdir()
    (data / "gamers.csv").write_text(
        "user_id,username\n" + "\n".join(f"u{i},n{i}" for i in range(600)), encoding="utf-8")

    monkeypatch.setattr(retrain, "run", lambda *a, **k: None)
    monkeypatch.setattr(retrain.tempfile, "TemporaryDirectory",
                        lambda **kw: _FixedDir(tmp_path))

    before = artefact.read_bytes()
    with pytest.raises(SystemExit):
        # 600 exported against 400 served is fine; against 4000 it is not.
        retrain.gamers_in(artefact)
        artefact.write_bytes(before)
        retrain.main(["--artifacts", str(artefact.parent), "--database-url", "postgresql://x",
                      "--min-gamers", "100"])
    assert artefact.read_bytes() == before


def _export_of(data: Path, user_ids) -> None:
    data.mkdir(exist_ok=True)
    (data / "gamers.csv").write_text(
        "user_id,username\n" + "\n".join(f"{uid},n{i}" for i, uid in enumerate(user_ids)),
        encoding="utf-8")


def test_a_collapse_the_artefact_recognises_is_still_refused(tmp_path, monkeypatch, artefact,
                                                             capsys):
    """The churn guard, on the population it exists to protect.

    A hundred of the four hundred gamers already being served: the same people, most of
    them missing. That is a half-applied migration, and it must not become an artefact.
    """
    survivors = sorted(retrain.serving_population(artefact))[:100]
    _export_of(tmp_path / "data", survivors)

    monkeypatch.setattr(retrain, "run", lambda *a, **k: None)
    monkeypatch.setattr(retrain.tempfile, "TemporaryDirectory",
                        lambda **kw: _FixedDir(tmp_path))

    before = artefact.read_bytes()
    with pytest.raises(SystemExit):
        retrain.main(["--artifacts", str(artefact.parent), "--database-url", "postgresql://x",
                      "--min-gamers", "100"])

    assert "Real churn does not look like this" in capsys.readouterr().err
    assert artefact.read_bytes() == before, "the serving artefact must be untouched"


def test_the_first_real_retrain_is_not_mistaken_for_a_collapse(tmp_path, monkeypatch, artefact,
                                                               capsys):
    """The launch case, and the reason the guard needed identity rather than counts.

    A fresh install serves the synthetic artefact baked into the image, so the product's
    first hundred real signups look like a 75% collapse against gamers who were never its
    users. Refusing there means the job never runs at all — the artefact stays synthetic,
    every id it returns resolves to nobody, and no deck is ever ranked.
    """
    _export_of(tmp_path / "data", [f"real-user-{i}" for i in range(100)])

    monkeypatch.setattr(retrain, "run", lambda *a, **k: None)
    monkeypatch.setattr(retrain.tempfile, "TemporaryDirectory",
                        lambda **kw: _FixedDir(tmp_path))

    with pytest.raises(SystemExit):
        # Still exits: `run` is stubbed, so no artefact is trained and the job stops at the
        # next check. What matters is which check it reached.
        retrain.main(["--artifacts", str(artefact.parent), "--database-url", "postgresql://x",
                      "--min-gamers", "100"])

    captured = capsys.readouterr()
    assert "Real churn" not in captured.err, "the churn guard fired on an unrelated population"
    assert "skipping the churn check" in captured.out
    assert "wrote no artefact" in captured.err, "it should have got as far as training"


def test_reload_failure_is_not_fatal(monkeypatch):
    """The artefact is already swapped in by then. Exiting non-zero would report a failed
    retrain that actually succeeded, and page someone for nothing."""
    def boom(*a, **k):
        raise OSError("connection refused")

    monkeypatch.setattr(retrain.urllib.request, "urlopen", boom)
    monkeypatch.setattr(retrain.time, "sleep", lambda _: None)
    retrain.reload_service("http://model:8000", "key", retries=2)  # must not raise


class _FixedDir:
    """Stands in for TemporaryDirectory so the test can inspect what the job wrote."""

    def __init__(self, path: Path) -> None:
        self.path = path

    def __enter__(self) -> str:
        return str(self.path)

    def __exit__(self, *exc) -> None:
        return None
