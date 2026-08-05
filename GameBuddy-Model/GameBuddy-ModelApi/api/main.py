"""The recommendation service.

What changed, and why
---------------------
**Artefacts load once, at startup.** Every ``/predict`` call used to ``pickle.load`` three
frames from disk, then run a correlation over a whole cluster — so the cost of a swipe was
paid on the request path, per request, forever. They are loaded once into ``state`` now,
and a request is a dot product against a preloaded matrix.

**``GET /updateData`` is gone.** It executed a Jupyter notebook, in-process, with a 2000
second timeout, on an unauthenticated GET. Anything that crawls the service could start a
retrain; a handful of concurrent calls would exhaust memory; and GET is specified as safe,
so a browser prefetch was enough to trigger it. Training is a batch job now
(``python -m gamebuddy_model train``), and this service reloads the artefact it produces.

**The port is 8000.** It was 4567, which is the port auth-service binds — the two could
not run on one host, and the backend's configured URL pointed at neither.

**Requests are authenticated.** This service returns a list of user ids given a user id,
so unauthenticated access is an enumeration endpoint for the social graph. It now requires
the same ``X-Internal-Api-Key`` shared secret the Java services use between themselves, and
is only reachable inside the cluster.
"""

from __future__ import annotations

import hmac
import logging
import os
import pickle
import sys
import time
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

# The artefact is a pickled Recommender, so unpickling it imports gamebuddy_model. Python
# puts the *script's* directory on sys.path, not the working directory, so launching this
# as `python api/main.py` leaves the package invisible and startup dies with
# ModuleNotFoundError — including inside the container, where WORKDIR is the project root
# but the script sits one level down. Adding the project root explicitly makes the service
# start the same way however it is launched.
_PROJECT_ROOT = Path(__file__).resolve().parents[2]
if str(_PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(_PROJECT_ROOT))

import uvicorn
from fastapi import Depends, FastAPI, File, Header, HTTPException, UploadFile
from pydantic import BaseModel, Field

from gamebuddy_model.moderation import UnreadableImage, classifier

logger = logging.getLogger("gamebuddy.model")

ARTIFACT_PATH = Path(os.environ.get("MODEL_ARTIFACT", "./artifacts/recommender.pkl"))
INTERNAL_API_KEY = os.environ.get("INTERNAL_API_KEY", "")
MAX_RESULTS = 200

#: Ceiling on the exclusion list. A gamer who has decided on this many people has seen
#: more of the population than any ranking can meaningfully order, and an unbounded list
#: is a cheap way to make the service allocate on request.
MAX_EXCLUSIONS = 10_000


class _State:
    """Holds the loaded model. A tiny class rather than a global so a reload is atomic:
    the new model is fully built before it replaces the old one, and in-flight requests
    keep serving from the old object rather than seeing a half-populated global."""

    def __init__(self) -> None:
        self.model: Any | None = None
        self.loaded_at: float = 0.0

    def load(self) -> None:
        if not ARTIFACT_PATH.exists():
            logger.error("No model artefact at %s; /predict will return 503", ARTIFACT_PATH)
            return
        with ARTIFACT_PATH.open("rb") as fh:
            model = pickle.load(fh)
        self.model = model
        self.loaded_at = time.time()
        logger.info("Loaded model: %d gamers, k=%d", len(model.user_ids), model.report.chosen_k)

    def require(self) -> Any:
        if self.model is None:
            # 503, not 500: the service is fine, it just has nothing trained yet, and a
            # caller should retry rather than treat this as a bug.
            raise HTTPException(status_code=503, detail="No model loaded")
        return self.model


state = _State()


@asynccontextmanager
async def lifespan(_: FastAPI):
    state.load()
    yield


app = FastAPI(title="GameBuddy recommendations", lifespan=lifespan)


def require_internal_key(x_internal_api_key: str = Header(default="")) -> None:
    if not INTERNAL_API_KEY:
        # Refusing everything is the safe failure. Defaulting to open would mean a missing
        # environment variable silently publishes the social graph.
        raise HTTPException(status_code=503, detail="Service not configured")
    if not hmac.compare_digest(x_internal_api_key, INTERNAL_API_KEY):
        raise HTTPException(status_code=401, detail="Unauthorized")


class PredictResponse(BaseModel):
    # Snake case because the Java client maps these names explicitly.
    user_id: str
    sim_users: list[str]


class PredictRequest(BaseModel):
    user_id: str = Field(min_length=1, max_length=64)

    #: Gamers the caller has already decided on. Sent in the body rather than the query
    #: string because it grows with every swipe and would blow the URL length limit.
    exclude: list[str] = Field(default_factory=list, max_length=MAX_EXCLUSIONS)

    limit: int = Field(default=100, ge=1, le=MAX_RESULTS)


class ModerationResponse(BaseModel):
    """The answer for one image.

    ``score`` rides along with the verdict on purpose: a moderator looking at a REVIEW
    wants to know whether it landed at 0.21 or 0.84, and the thresholds can only be
    retuned against real traffic if the numbers behind past decisions were kept.
    """

    verdict: str
    score: float


class ColdStartRequest(BaseModel):
    user_id: str = Field(min_length=1, max_length=64)
    games: list[str] = Field(default_factory=list, max_length=200)
    keywords: list[str] = Field(default_factory=list, max_length=200)
    exclude: list[str] = Field(default_factory=list, max_length=MAX_EXCLUSIONS)
    limit: int = Field(default=100, ge=1, le=MAX_RESULTS)


@app.get("/health")
async def health() -> dict[str, object]:
    """Liveness and readiness. Kubernetes needs somewhere to point a probe, and it must
    not require the API key or a trained model to answer."""
    return {
        "status": "up",
        "model_loaded": state.model is not None,
        "gamers": len(state.model.user_ids) if state.model else 0,
        "loaded_at": state.loaded_at,
        # Lazily loaded, so False here means "no image has been screened yet", not
        # "broken". It distinguishes a cold service from a misconfigured one.
        "moderation_loaded": classifier.ready(),
    }


@app.post("/predict", response_model=PredictResponse, dependencies=[Depends(require_internal_key)])
async def predict(request: PredictRequest) -> PredictResponse:
    """Ranks similar gamers, skipping everyone the caller has already decided on.

    An unknown ``user_id`` returns an empty list rather than raising. The original indexed
    straight into the pickled frame, so every gamer who had signed up since the last
    training run got a KeyError and a 500 — precisely the users for whom recommendations
    matter most. The caller should fall back to ``/predict/cold-start``.
    """
    model = state.require()
    return PredictResponse(
        user_id=request.user_id,
        sim_users=model.similar_to(request.user_id, request.limit, exclude=request.exclude),
    )


@app.post("/predict/cold-start", response_model=PredictResponse,
          dependencies=[Depends(require_internal_key)])
async def predict_cold_start(request: ColdStartRequest) -> PredictResponse:
    """Ranks for a gamer the model has not been trained on yet.

    Recommendations from the profile alone, so someone who finished onboarding a minute
    ago sees a populated feed instead of an empty one until the next nightly retrain.
    """
    model = state.require()
    return PredictResponse(
        user_id=request.user_id,
        sim_users=model.similar_to_profile(
            request.games, request.keywords, request.limit, exclude=request.exclude
        ),
    )


@app.post("/moderate/image", response_model=ModerationResponse,
          dependencies=[Depends(require_internal_key)])
async def moderate_image(file: UploadFile = File(...)) -> ModerationResponse:
    """Screens one image for sexual content.

    The bytes are posted rather than a URL, so this service needs no object-storage
    credentials and cannot be pointed at an arbitrary host by whoever can reach it — a
    URL parameter here would be a server-side request forgery hole into the private
    network the service sits in.

    Three outcomes, not two, and the caller is expected to honour all three: APPROVE
    publishes, REJECT refuses, REVIEW stores the image where only its owner can see it
    until a human decides. Treating REVIEW as approval defeats the point of having it.

    A 400 means the upload was not a decodable image. That is deliberately not a REJECT:
    "this is not an image" and "this is pornography" are different answers, and a client
    that conflates them will tell an innocent user something untrue.
    """
    data = await file.read()
    try:
        assessment = classifier.assess(data)
    except UnreadableImage as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    # The score is logged and the image is not. Whatever this endpoint was just handed,
    # the logs are the last place it should end up.
    logger.info("Moderated image: %s (%.3f)", assessment.verdict.value, assessment.score)
    return ModerationResponse(verdict=assessment.verdict.value, score=assessment.score)


@app.post("/admin/reload", dependencies=[Depends(require_internal_key)])
async def reload_model() -> dict[str, object]:
    """Swaps in a newly trained artefact without a restart.

    POST, not GET, because it changes server state — and it only reads a file the training
    job has already written. It does not train.
    """
    state.load()
    return {"model_loaded": state.model is not None, "loaded_at": state.loaded_at}


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("PORT", "8000")))
