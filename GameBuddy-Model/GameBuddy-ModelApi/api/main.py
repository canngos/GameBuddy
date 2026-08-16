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

#: Stop recommending the synthetic seed accounts, while still ranking with everything the
#: model learned from them.
#:
#: Off by default, because during development the seed accounts *are* the population and
#: turning them off would leave an empty feed. Turn it on when real users arrive — it
#: needs a restart, not a retrain, and it is reversible the same way.
HIDE_SEED_ACCOUNTS = os.environ.get("HIDE_SEED_ACCOUNTS", "").lower() in {"1", "true", "yes"}

#: Ceiling on the exclusion list. A gamer who has decided on this many people has seen
#: more of the population than any ranking can meaningfully order, and an unbounded list
#: is a cheap way to make the service allocate on request.
MAX_EXCLUSIONS = 10_000

#: Ceiling on the inclusion list — the eligible set for a narrowed feed.
#:
#: Larger than the exclusion cap because the two bite in opposite situations. An exclusion
#: list is longest when the filter is *most* narrowing, and the caller has no way to recover
#: from being refused: filtering the response instead reaches only the top N, which is the
#: bug the whole exclusion mechanism was built to avoid. An inclusion list is longest when
#: the filter barely narrows anything, and there the caller *can* recover — dropping the
#: filter and post-filtering the ranking costs almost nothing when almost everyone qualifies.
#:
#: So this bound is a guard against an absurd request rather than a design constraint, and
#: the backend treats crossing it as "stop filtering here" rather than as an error. Note the
#: history: three of the four Gold filters returned 503 in a 20,001-gamer database because
#: the backend was sending the complement and Pydantic refused it. Raising a cap was the
#: wrong fix; reversing the direction was the right one.
MAX_INCLUSIONS = 50_000


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
        model.hide_seed_accounts = HIDE_SEED_ACCOUNTS
        self.model = model
        self.loaded_at = time.time()
        logger.info("Loaded model: %d gamers, k=%d", len(model.user_ids), model.report.chosen_k)
        self._warn_on_version_skew(model)
        self._report_visibility(model)

    @staticmethod
    def _report_visibility(model: Any) -> None:
        """States how many gamers are actually recommendable, and objects if it is absurd.

        Silence here would be dangerous in both directions. Serving seed accounts to real
        users is the failure the marker exists to prevent; hiding them when they are the
        only population there is empties every deck. Both look identical from outside — a
        feed that is wrong — so the count goes in the log at every load.
        """
        hidden = getattr(model, "hidden", None)
        total = len(model.user_ids)

        if hidden is None:
            if HIDE_SEED_ACCOUNTS:
                logger.warning(
                    "HIDE_SEED_ACCOUNTS is set but this artefact records no seed markers; "
                    "it predates them. Retrain, or every seed account stays recommendable.")
            return

        seeded = int(hidden.sum())
        if not model.hide_seed_accounts:
            if seeded:
                logger.warning(
                    "Serving %d seed account(s) of %d as real candidates. Fine in "
                    "development; set HIDE_SEED_ACCOUNTS=true before real users arrive.",
                    seeded, total)
            return

        servable = total - seeded
        logger.info("Hiding %d seed account(s); %d gamer(s) recommendable", seeded, servable)
        if servable < 50:
            logger.error(
                "Only %d gamer(s) can be recommended after hiding seed accounts. Decks "
                "will be empty or near-empty. Either unset HIDE_SEED_ACCOUNTS or wait "
                "until there are real users to serve.", servable)

    @staticmethod
    def _warn_on_version_skew(model: Any) -> None:
        """Says so when the artefact was built by a different scikit-learn or numpy.

        Unpickling fitted estimators across versions is undefined behaviour, and it
        usually does not raise — it returns a model that loads, serves and ranks wrongly.
        Since every response is a plausible list of ids either way, nothing downstream can
        notice. Logged rather than fatal: a mismatch is usually harmless, and refusing to
        start takes the whole feed down over what is most often a patch release.
        """
        build = getattr(model.report, "build", None)
        if build is None:
            logger.warning(
                "Artefact records no build info — it predates version stamping. Retrain to "
                "get a version check on load."
            )
            return

        if mismatches := build.mismatches():
            logger.warning(
                "Artefact was trained with different library versions: %s. Unpickling "
                "scikit-learn across versions can silently change rankings — retrain in "
                "the pinned environment.",
                "; ".join(f"{name} {was} != {now}" for name, (was, now) in sorted(mismatches.items())),
            )
        else:
            logger.info("Artefact libraries match this environment (%s)", build.describe())

        if interpreter := build.interpreter_mismatch():
            # Expected: training runs outside the container. Stated, not alarmed about.
            logger.info("Artefact was trained on Python %s, serving on %s", *interpreter)

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

    #: The eligible set for a narrowed feed, or absent when the caller is not filtering.
    #:
    #: ``None`` and ``[]`` are different answers and both are meaningful: absent means "rank
    #: over everybody", empty means "the filter matched nobody" and must produce an empty
    #: deck rather than an unfiltered one.
    #:
    #: Optional with a default so deploy order between this service and the backend stays
    #: free — an older backend that sends only ``exclude`` keeps working unchanged.
    include: list[str] | None = Field(default=None, max_length=MAX_INCLUSIONS)

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
    # Optional so a caller that predates the platform block keeps working — it ranks on
    # games and keywords alone rather than rejecting the request. Deploy order between the
    # two services is then free.
    platforms: list[str] = Field(default_factory=list, max_length=16)
    exclude: list[str] = Field(default_factory=list, max_length=MAX_EXCLUSIONS)
    #: See PredictRequest.include. Needed on this path too: a Gold subscriber who signed up
    #: since the last retrain is ranked here, and their filters have to work on day one.
    include: list[str] | None = Field(default=None, max_length=MAX_INCLUSIONS)
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
def predict(request: PredictRequest) -> PredictResponse:
    """Ranks similar gamers, skipping everyone the caller has already decided on.

    Declared ``def`` rather than ``async def``, deliberately. FastAPI runs a sync handler
    in a threadpool and an async one directly on the event loop — and the work below is
    CPU-bound numpy, not I/O. As ``async def`` it blocked the loop for the whole ranking,
    which meant one request with a large exclusion list delayed every other request in
    flight, including ``/health``: the readiness probe was queueing behind ranking work.
    The compute is small (about 1 ms at twenty thousand gamers, 9 ms with ten thousand
    exclusions), so this was survivable rather than urgent, but it is the wrong shape and
    it grows with the population. In a threadpool the requests genuinely overlap, and
    numpy releases the GIL for the BLAS calls that dominate.

    An unknown ``user_id`` returns an empty list rather than raising. The original indexed
    straight into the pickled frame, so every gamer who had signed up since the last
    training run got a KeyError and a 500 — precisely the users for whom recommendations
    matter most. The caller should fall back to ``/predict/cold-start``.
    """
    model = state.require()
    return PredictResponse(
        user_id=request.user_id,
        sim_users=model.similar_to(
            request.user_id, request.limit, exclude=request.exclude, include=request.include
        ),
    )


@app.post("/predict/cold-start", response_model=PredictResponse,
          dependencies=[Depends(require_internal_key)])
def predict_cold_start(request: ColdStartRequest) -> PredictResponse:
    """Ranks for a gamer the model has not been trained on yet.

    Recommendations from the profile alone, so someone who finished onboarding a minute
    ago sees a populated feed instead of an empty one until the next nightly retrain.
    """
    model = state.require()
    return PredictResponse(
        user_id=request.user_id,
        sim_users=model.similar_to_profile(
            request.games, request.keywords, request.limit,
            platforms=request.platforms, exclude=request.exclude, include=request.include,
        ),
    )


@app.post("/moderate/image", response_model=ModerationResponse,
          dependencies=[Depends(require_internal_key)])
def moderate_image(file: UploadFile = File(...)) -> ModerationResponse:
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

    Sync, and of everything here this is the one that most needed to be. A ViT forward
    pass on CPU is hundreds of milliseconds; on the event loop that stalled *every*
    concurrent request for the duration, so a burst of avatar uploads could stop the feed
    for whoever happened to be swiping. ``file.file.read()`` rather than ``await
    file.read()`` because the handler is no longer a coroutine — same bytes, and the
    threadpool is the right place for the wait.
    """
    data = file.file.read()
    try:
        assessment = classifier.assess(data)
    except UnreadableImage as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    # The score is logged and the image is not. Whatever this endpoint was just handed,
    # the logs are the last place it should end up.
    logger.info("Moderated image: %s (%.3f)", assessment.verdict.value, assessment.score)
    return ModerationResponse(verdict=assessment.verdict.value, score=assessment.score)


@app.post("/admin/reload", dependencies=[Depends(require_internal_key)])
def reload_model() -> dict[str, object]:
    """Swaps in a newly trained artefact without a restart.

    POST, not GET, because it changes server state — and it only reads a file the training
    job has already written. It does not train.

    Sync for the same reason as the others: unpickling is blocking file I/O and CPU, and
    it grows with the population — 25 ms at twenty thousand gamers, and a 97 MB artefact
    at two hundred thousand. The retrain job calls this straight after swapping the file
    in, which is exactly when the service should still be answering.
    """
    state.load()
    return {"model_loaded": state.model is not None, "loaded_at": state.loaded_at}


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("PORT", "8000")))
