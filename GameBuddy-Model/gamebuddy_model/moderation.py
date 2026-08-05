"""Sexual-content screening for user-uploaded images.

Why this lives here
-------------------
The app lets gamers upload their own avatar, and it admits under-18s. Nothing an account
uploads can be shown to another account until something has looked at it. A paid vision
API would do this well; it would also bill per image forever, so the classifier is
self-hosted and runs in the service that already exists for the recommender.

What it is
----------
``Falconsai/nsfw_image_detection`` — a ViT-base fine-tune, two classes, ~350MB, Apache-2.0.
Small enough to run on CPU in the hundreds of milliseconds, which is fine for a queue that
processes one avatar per signup rather than a video stream.

What it is not
--------------
**A classifier is not a moderation policy.** This one answers one narrow question — does
this look like pornography — and it answers it probabilistically. It does not recognise a
minor, it does not recognise a photograph of someone who did not consent to being
uploaded, and it does not recognise the picture of a screen showing either. Those need a
human and a report button, which is why the middle band exists and why nothing here is
allowed to auto-approve its way past a person for long.

Three outcomes rather than two
------------------------------
Any single threshold is wrong in one direction. Set it low and ordinary photographs get
refused, which users experience as the feature being broken; set it high and the thing you
built it to stop gets through. Two thresholds give a middle band that is neither published
nor destroyed, and a human decides. The bands are configurable because the right numbers
depend on what the queue can absorb, not on anything intrinsic to the model.
"""

from __future__ import annotations

import io
import logging
import os
from dataclasses import dataclass
from enum import Enum

logger = logging.getLogger("gamebuddy.moderation")

MODEL_ID = os.environ.get("NSFW_MODEL_ID", "Falconsai/nsfw_image_detection")

#: At or above this, refuse outright.
REJECT_AT = float(os.environ.get("NSFW_REJECT_THRESHOLD", "0.85"))

#: At or below this, publish without a human.
APPROVE_AT = float(os.environ.get("NSFW_APPROVE_THRESHOLD", "0.20"))

#: Anything Pillow will decode into more pixels than this is refused before it is decoded.
#: A 64KB PNG can declare a 50,000x50,000 canvas and cost 10GB of RAM to open — a
#: decompression bomb, and the cheapest denial of service there is against an upload
#: endpoint. Pillow's own guard warns at ~89M pixels; this is far below anything a real
#: avatar needs.
MAX_PIXELS = 40_000_000

#: Refused before decoding. An avatar has no business being larger, and the limit bounds
#: what an unauthenticated-ish path can make the service allocate.
MAX_BYTES = 12 * 1024 * 1024


class Verdict(str, Enum):
    """What should happen to the image."""

    APPROVE = "APPROVE"
    REVIEW = "REVIEW"
    REJECT = "REJECT"


@dataclass(frozen=True)
class Assessment:
    verdict: Verdict
    #: P(sexual content), 0..1. Kept in the response so a moderator sees how close a
    #: REVIEW was to either edge, and so thresholds can be retuned against real traffic
    #: rather than guessed at twice.
    score: float


class UnreadableImage(Exception):
    """The bytes are not a decodable image, or are implausibly large.

    Distinct from a rejection: this is a malformed request, not a judgement about content,
    and the two should not reach the caller as the same thing.
    """


class Classifier:
    """Wraps the model. Loaded once, lazily, and shared across requests.

    Lazy because the service's main job is recommendations: a deployment with no image
    moderation configured should still start in a second and serve ``/predict``, not spend
    a minute pulling weights it may never use.
    """

    def __init__(self) -> None:
        self._pipeline = None

    def ready(self) -> bool:
        return self._pipeline is not None

    def load(self) -> None:
        if self._pipeline is not None:
            return

        # Imported here rather than at module scope so the recommender does not carry a
        # torch import on every startup, and so a deployment without the extra
        # dependencies fails when moderation is first used instead of at boot.
        from transformers import pipeline  # noqa: PLC0415

        logger.info("Loading NSFW classifier %s", MODEL_ID)
        self._pipeline = pipeline("image-classification", model=MODEL_ID, device=-1)
        logger.info("NSFW classifier ready")

    def assess(self, data: bytes) -> Assessment:
        if len(data) > MAX_BYTES:
            raise UnreadableImage(f"Image is larger than {MAX_BYTES} bytes")

        image = self._decode(data)
        self.load()

        scores = {row["label"].lower(): float(row["score"]) for row in self._pipeline(image)}
        # The model labels its classes "normal" and "nsfw". Read the unsafe class directly
        # and fall back to 1 - normal, so a future label rename degrades into a slightly
        # cautious answer rather than a silent zero that approves everything.
        nsfw = scores.get("nsfw")
        if nsfw is None:
            nsfw = 1.0 - scores.get("normal", 0.0)

        return Assessment(verdict=self._band(nsfw), score=nsfw)

    @staticmethod
    def _band(score: float) -> Verdict:
        if score >= REJECT_AT:
            return Verdict.REJECT
        if score <= APPROVE_AT:
            return Verdict.APPROVE
        return Verdict.REVIEW

    @staticmethod
    def _decode(data: bytes):
        from PIL import Image  # noqa: PLC0415

        try:
            image = Image.open(io.BytesIO(data))
            # verify() reads the header and checks integrity without decoding the pixels,
            # so a bomb is caught before it is allocated. It leaves the file unusable
            # afterwards, hence the reopen below — that is Pillow's documented behaviour,
            # not an oversight.
            image.verify()

            image = Image.open(io.BytesIO(data))
            width, height = image.size
            if width * height > MAX_PIXELS:
                raise UnreadableImage(f"Image is {width}x{height}, larger than allowed")

            # Converted to RGB because the model expects three channels, and because a
            # palette or CMYK image otherwise fails deep inside the feature extractor with
            # an error that says nothing about the input.
            return image.convert("RGB")
        except UnreadableImage:
            raise
        except Exception as exc:  # Pillow raises a wide range for malformed input.
            raise UnreadableImage(f"Not a readable image: {exc}") from exc


#: The process-wide instance. One model in memory, not one per request.
classifier = Classifier()
