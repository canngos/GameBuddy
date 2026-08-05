"""Tests for the HTTP surface.

Mostly authorisation and failure modes. The endpoint returns a list of gamer ids given a
gamer id, so the tests that matter most are the ones asserting it refuses to do that for
an unauthenticated caller.
"""

from __future__ import annotations

import importlib
import pickle
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from gamebuddy_model.population import PopulationGenerator
from gamebuddy_model.recommender import train

API_DIR = Path(__file__).resolve().parents[1] / "GameBuddy-ModelApi" / "api"
KEY = "test-internal-key"


@pytest.fixture(scope="module")
def artefact(tmp_path_factory):
    population = PopulationGenerator(n_gamers=300, seed=5).generate()
    gamers = population.gamers
    model = train(
        [g.user_id for g in gamers], [g.games for g in gamers], [g.keywords for g in gamers]
    )
    path = tmp_path_factory.mktemp("artifacts") / "recommender.pkl"
    with path.open("wb") as fh:
        pickle.dump(model, fh)
    return path, [g.user_id for g in gamers]


def _load_app(monkeypatch, artefact_path, key=KEY):
    monkeypatch.setenv("INTERNAL_API_KEY", key)
    monkeypatch.setenv("MODEL_ARTIFACT", str(artefact_path))
    sys.path.insert(0, str(API_DIR))
    module = importlib.import_module("main")
    return importlib.reload(module)


@pytest.fixture
def client(monkeypatch, artefact):
    path, _ = artefact
    module = _load_app(monkeypatch, path)
    with TestClient(module.app) as c:
        yield c


@pytest.fixture
def user_id(artefact):
    return artefact[1][0]


def test_health_needs_no_key(client):
    """Kubernetes probes it, so it must answer without credentials."""
    body = client.get("/health").json()
    assert body["status"] == "up"
    assert body["model_loaded"] is True


def test_predict_rejects_a_missing_key(client, user_id):
    assert client.post("/predict", json={"user_id": user_id}).status_code == 401


def test_predict_rejects_a_wrong_key(client, user_id):
    response = client.post("/predict", json={"user_id": user_id},
                           headers={"X-Internal-Api-Key": "wrong"})
    assert response.status_code == 401


def test_predict_returns_recommendations(client, user_id):
    response = client.post("/predict", json={"user_id": user_id, "limit": 5},
                           headers={"X-Internal-Api-Key": KEY})
    assert response.status_code == 200
    body = response.json()
    assert body["user_id"] == user_id
    assert len(body["sim_users"]) == 5
    assert user_id not in body["sim_users"]


def test_predict_honours_exclusions(client, user_id):
    """The fix for the dry feed: a second page must not repeat the first."""
    headers = {"X-Internal-Api-Key": KEY}
    first = client.post("/predict", json={"user_id": user_id, "limit": 10},
                        headers=headers).json()["sim_users"]
    second = client.post("/predict", json={"user_id": user_id, "limit": 10, "exclude": first},
                         headers=headers).json()["sim_users"]
    assert len(second) == 10
    assert not set(first) & set(second)


def test_exclusion_list_is_bounded(client, user_id):
    """An unbounded list is a cheap way to make the service allocate on request."""
    response = client.post(
        "/predict",
        json={"user_id": user_id, "exclude": [f"g{i}" for i in range(10_001)]},
        headers={"X-Internal-Api-Key": KEY},
    )
    assert response.status_code == 422


def test_unknown_user_is_empty_not_a_500(client):
    """The original raised KeyError here, which meant every gamer who signed up between
    retrains got a 500 on their first swipe."""
    response = client.post("/predict", json={"user_id": "nobody"},
                           headers={"X-Internal-Api-Key": KEY})
    assert response.status_code == 200
    assert response.json()["sim_users"] == []


def test_limit_is_bounded(client, user_id):
    """Without a ceiling, `limit` is a cheap way to make the service serialise the whole
    user base on every call."""
    response = client.post("/predict", json={"user_id": user_id, "limit": 100_000},
                           headers={"X-Internal-Api-Key": KEY})
    assert response.status_code == 422


def test_cold_start_serves_an_unknown_gamer(client):
    response = client.post(
        "/predict/cold-start",
        json={"user_id": "brand-new", "games": ["VALORANT", "Counter-Strike 2"],
              "keywords": ["competitive"], "limit": 10},
        headers={"X-Internal-Api-Key": KEY},
    )
    assert response.status_code == 200
    assert len(response.json()["sim_users"]) == 10


def test_cold_start_requires_a_key(client):
    assert client.post("/predict/cold-start", json={"user_id": "x"}).status_code == 401


def test_reload_requires_a_key(client):
    assert client.post("/admin/reload").status_code == 401


def test_reload_swaps_the_artefact(client):
    response = client.post("/admin/reload", headers={"X-Internal-Api-Key": KEY})
    assert response.status_code == 200
    assert response.json()["model_loaded"] is True


def test_there_is_no_endpoint_that_executes_a_notebook(client):
    """Regression guard. /updateData ran a Jupyter notebook in-process on an
    unauthenticated GET; it must not come back."""
    assert client.get("/updateData").status_code == 404


def test_unconfigured_key_refuses_rather_than_opens(monkeypatch, artefact):
    """Failing closed. If INTERNAL_API_KEY is unset, a missing variable must not silently
    publish the social graph."""
    path, ids = artefact
    module = _load_app(monkeypatch, path, key="")
    with TestClient(module.app) as c:
        assert c.post("/predict", json={"user_id": ids[0]}).status_code == 503


def test_missing_artefact_is_503_not_500(monkeypatch, tmp_path):
    module = _load_app(monkeypatch, tmp_path / "does-not-exist.pkl")
    with TestClient(module.app) as c:
        assert c.get("/health").json()["model_loaded"] is False
        response = c.post("/predict", json={"user_id": "x"},
                          headers={"X-Internal-Api-Key": KEY})
        assert response.status_code == 503


# --- image moderation ------------------------------------------------------
#
# The classifier is stubbed. Downloading a 350MB model into CI to assert that Hugging
# Face scores a picture the way Hugging Face scores a picture would test the wrong
# repository; what matters here is that the endpoint refuses unauthenticated callers,
# and that "not an image" does not come back looking like "not allowed".


@pytest.fixture
def stub_classifier(monkeypatch):
    """Replaces the model with something that returns a score we choose."""
    from gamebuddy_model import moderation

    def scoring(nsfw: float):
        monkeypatch.setattr(
            moderation.classifier,
            "_pipeline",
            lambda _image: [{"label": "nsfw", "score": nsfw},
                            {"label": "normal", "score": 1.0 - nsfw}],
        )

    return scoring


def _png() -> bytes:
    import io as _io

    from PIL import Image

    buffer = _io.BytesIO()
    Image.new("RGB", (48, 48), (30, 160, 90)).save(buffer, format="PNG")
    return buffer.getvalue()


def test_moderation_requires_a_key(client):
    """Unauthenticated, this is a free image classifier for anyone who can reach it."""
    response = client.post("/moderate/image", files={"file": ("a.png", _png(), "image/png")})
    assert response.status_code == 401


def test_moderation_approves_a_clean_image(client, stub_classifier):
    stub_classifier(0.01)
    response = client.post(
        "/moderate/image",
        files={"file": ("a.png", _png(), "image/png")},
        headers={"X-Internal-Api-Key": KEY},
    )
    assert response.status_code == 200
    assert response.json()["verdict"] == "APPROVE"


def test_moderation_rejects_an_explicit_image(client, stub_classifier):
    stub_classifier(0.97)
    response = client.post(
        "/moderate/image",
        files={"file": ("a.png", _png(), "image/png")},
        headers={"X-Internal-Api-Key": KEY},
    )
    assert response.json()["verdict"] == "REJECT"


def test_moderation_sends_the_uncertain_middle_to_a_human(client, stub_classifier):
    stub_classifier(0.5)
    response = client.post(
        "/moderate/image",
        files={"file": ("a.png", _png(), "image/png")},
        headers={"X-Internal-Api-Key": KEY},
    )
    body = response.json()
    assert body["verdict"] == "REVIEW"
    assert body["score"] == pytest.approx(0.5)


def test_moderation_400s_on_something_that_is_not_an_image(client, stub_classifier):
    """A 400, not a REJECT. Telling a user their holiday photo was refused as sexual
    content when it was actually a corrupt upload is a bug with a reputational cost."""
    stub_classifier(0.0)
    response = client.post(
        "/moderate/image",
        files={"file": ("a.txt", b"definitely not a png", "image/png")},
        headers={"X-Internal-Api-Key": KEY},
    )
    assert response.status_code == 400
