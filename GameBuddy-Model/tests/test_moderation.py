"""Tests for image screening.

The classifier itself is not under test here — it is a third-party model, and asserting
that it scores a particular picture a particular way would be testing Hugging Face rather
than this repository, on a 350MB download, in CI.

What is under test is everything around it, which is where the failures that matter live:
the banding, the rejection of input that should never reach a decoder at all, and the
distinction between "not an image" and "not allowed". A stub stands in for the model so
the bands can be driven to their edges deliberately.
"""

from __future__ import annotations

import io

import pytest

from gamebuddy_model import moderation
from gamebuddy_model.moderation import Classifier, UnreadableImage, Verdict


def png_bytes(width: int = 64, height: int = 64) -> bytes:
    from PIL import Image

    buffer = io.BytesIO()
    Image.new("RGB", (width, height), (120, 90, 200)).save(buffer, format="PNG")
    return buffer.getvalue()


class StubPipeline:
    """Stands in for the transformers pipeline, returning a score we choose."""

    def __init__(self, nsfw: float, label: str = "nsfw") -> None:
        self.nsfw = nsfw
        self.label = label

    def __call__(self, _image):
        return [
            {"label": self.label, "score": self.nsfw},
            {"label": "normal", "score": 1.0 - self.nsfw},
        ]


def classifier_scoring(nsfw: float, label: str = "nsfw") -> Classifier:
    c = Classifier()
    c._pipeline = StubPipeline(nsfw, label)
    return c


@pytest.mark.parametrize(
    ("score", "expected"),
    [
        (0.00, Verdict.APPROVE),
        (0.20, Verdict.APPROVE),  # boundary is inclusive
        (0.21, Verdict.REVIEW),
        (0.84, Verdict.REVIEW),
        (0.85, Verdict.REJECT),  # boundary is inclusive
        (1.00, Verdict.REJECT),
    ],
)
def test_bands(score, expected):
    assert classifier_scoring(score).assess(png_bytes()).verdict is expected


def test_score_is_reported_not_just_the_verdict():
    """A moderator needs to know whether a REVIEW was borderline, and the thresholds
    cannot be retuned later without the numbers behind past decisions."""
    assert classifier_scoring(0.5).assess(png_bytes()).score == pytest.approx(0.5)


def test_unknown_label_falls_back_to_the_safe_side():
    """If the model's labels are ever renamed, the fallback must not read as 0.0 — that
    would approve everything, silently, which is the one failure mode this whole module
    exists to prevent."""
    assessment = classifier_scoring(0.9, label="explicit").assess(png_bytes())
    # "explicit" is unrecognised, so the score comes from 1 - normal = 1 - 0.1 = 0.9.
    assert assessment.score == pytest.approx(0.9)
    assert assessment.verdict is Verdict.REJECT


def test_garbage_is_not_a_rejection():
    """"Not an image" and "pornography" are different answers. A caller that conflates
    them tells an innocent user something untrue."""
    with pytest.raises(UnreadableImage):
        classifier_scoring(0.0).assess(b"this is not a picture")


def test_oversized_payload_is_refused_before_decoding():
    with pytest.raises(UnreadableImage, match="larger than"):
        classifier_scoring(0.0).assess(b"\x00" * (moderation.MAX_BYTES + 1))


def test_decompression_bomb_is_refused(monkeypatch):
    """A small PNG can declare an enormous canvas; decoding it is the cheapest denial of
    service there is against an upload endpoint. The dimensions are checked before any
    pixels are allocated."""
    monkeypatch.setattr(moderation, "MAX_PIXELS", 100)
    with pytest.raises(UnreadableImage, match="larger than allowed"):
        classifier_scoring(0.0).assess(png_bytes(64, 64))


def test_palette_images_are_converted(monkeypatch):
    """A PNG in palette mode reaches the feature extractor as one channel and fails deep
    inside it with an error that says nothing about the input."""
    from PIL import Image

    buffer = io.BytesIO()
    Image.new("P", (32, 32)).save(buffer, format="PNG")

    seen = {}

    class Recording(StubPipeline):
        def __call__(self, image):
            seen["mode"] = image.mode
            return super().__call__(image)

    c = Classifier()
    c._pipeline = Recording(0.0)
    c.assess(buffer.getvalue())
    assert seen["mode"] == "RGB"
