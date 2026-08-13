#!/usr/bin/env python3
"""
Generate the app's four UI sound cues into `assets/sounds/`.

    python scripts/make-sounds.py

Standard library only — `wave`, `math`, `struct`. No numpy, no scipy, nothing to install,
which is the whole reason this is a script rather than four files someone downloaded. A
generated cue can be tuned by editing a number here and re-running; a downloaded one can
only be replaced.

**These are honest placeholders.** They are synthesised from sine partials with a plucked
envelope, which gets you something clean and inoffensive but not something designed. When
real audio is commissioned, drop the files in over the top with the same names — no call
site changes, because `src/ui/sound.ts` names them by intent, not by waveform.

Design rules the cues follow, and the reasons:

- **Short.** Nothing over 700ms. A UI sound that outlasts the animation it accompanies
  turns into a thing you wait for.
- **Quiet.** Peak amplitude 0.35, not 1.0. These play over whatever the user is already
  listening to (`interruptionMode: 'mixWithOthers'`), so they must sit under it.
- **Pentatonic, one key.** Every cue is built from C major pentatonic, so two landing
  together — a match and a message, which absolutely happens — cannot clash.
- **Rising for good news.** Match, purchase and badge all ascend. Only the message cue
  sits flat, because it is the one that fires most often and a fanfare per message is
  how an app gets muted.
"""

import math
import os
import struct
import wave

SAMPLE_RATE = 44100
PEAK = 0.35

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(os.path.dirname(HERE), "assets", "sounds")

# C major pentatonic, which is the scale that cannot make a wrong interval against itself.
C5, D5, E5, G5, A5, C6, D6, E6, G6 = (
    523.25, 587.33, 659.25, 783.99, 880.00, 1046.50, 1174.66, 1318.51, 1567.98,
)


def envelope(i: int, total: int, attack: float, decay: float) -> float:
    """
    A plucked shape: fast ramp in, exponential fall.

    The attack is not optional. Starting a sine at full amplitude puts a step
    discontinuity at sample zero, and that is heard as a click on every single play —
    the most common way a synthesised UI sound gives itself away.
    """
    t = i / SAMPLE_RATE
    total_s = total / SAMPLE_RATE

    if t < attack:
        rise = t / attack
    else:
        rise = 1.0

    fall = math.exp(-t / decay)

    # Force the tail to zero rather than leaving it at whatever the exponential reached,
    # for the same reason as the attack: a truncated waveform clicks.
    tail = min(1.0, (total_s - t) / 0.01) if total_s - t < 0.01 else 1.0

    return rise * fall * max(0.0, tail)


def tone(freq: float, duration: float, amp: float = 1.0, decay: float = 0.18) -> list:
    """One note: fundamental plus a quiet octave and fifth to stop it sounding like a test."""
    total = int(duration * SAMPLE_RATE)
    out = []

    for i in range(total):
        t = i / SAMPLE_RATE
        value = (
            math.sin(2 * math.pi * freq * t)
            + 0.25 * math.sin(2 * math.pi * freq * 2 * t)
            + 0.10 * math.sin(2 * math.pi * freq * 3 * t)
        ) / 1.35
        out.append(value * amp * envelope(i, total, attack=0.004, decay=decay))

    return out


def mix(*layers) -> list:
    """Sum layers of differing lengths, padding to the longest."""
    length = max(len(layer) for layer in layers)
    out = [0.0] * length

    for layer in layers:
        for i, value in enumerate(layer):
            out[i] += value

    return out


def at(offset: float, samples: list) -> list:
    """Delay a layer by `offset` seconds, so notes can overlap instead of only queueing."""
    return [0.0] * int(offset * SAMPLE_RATE) + samples


def write(name: str, samples: list) -> None:
    peak = max((abs(s) for s in samples), default=0.0) or 1.0
    scale = PEAK / peak

    path = os.path.join(OUT_DIR, name)
    with wave.open(path, "w") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SAMPLE_RATE)
        frames = b"".join(
            struct.pack("<h", int(max(-1.0, min(1.0, s * scale)) * 32767)) for s in samples
        )
        f.writeframes(frames)

    seconds = len(samples) / SAMPLE_RATE
    print(f"  {name:14} {seconds:.2f}s  {os.path.getsize(path) / 1024:.1f} KB")


def match() -> list:
    """
    The biggest moment in the product, so the only cue that gets four notes.

    Notes overlap rather than queue — each starts before the last has decayed — which is
    what makes it read as a chord arriving rather than as a scale being played.
    """
    return mix(
        tone(C5, 0.70, decay=0.30),
        at(0.075, tone(E5, 0.65, decay=0.28)),
        at(0.150, tone(G5, 0.60, decay=0.26)),
        at(0.225, tone(C6, 0.55, amp=0.9, decay=0.30)),
        # A high sparkle a beat after the run lands, which is the bit that sounds like
        # a celebration rather than a doorbell.
        at(0.330, tone(G6, 0.35, amp=0.35, decay=0.12)),
    )


def purchase() -> list:
    """Two notes, a fifth apart, close together. Brisk and done — you bought a thing."""
    return mix(
        tone(G5, 0.34, decay=0.11),
        at(0.085, tone(D6, 0.36, amp=0.85, decay=0.14)),
        at(0.085, tone(G6, 0.30, amp=0.25, decay=0.10)),
    )


def reward() -> list:
    """
    Coins arriving, and badges. Three rising notes, lighter than a match.

    Deliberately not the purchase cue reversed — spending and earning need to be tellable
    apart with the phone in a pocket, and a listener separates two rising runs by length
    far more reliably than by direction.
    """
    return mix(
        tone(E5, 0.30, amp=0.8, decay=0.10),
        at(0.070, tone(A5, 0.32, amp=0.85, decay=0.11)),
        at(0.140, tone(E6, 0.40, decay=0.16)),
    )


def message() -> list:
    """
    The one that fires most, so it is the shortest and the flattest.

    Two notes a whole tone apart and nearly simultaneous: enough to be a sound rather than
    a beep, not enough to be a tune. Anything more becomes unbearable by the fifth message.
    """
    return mix(
        tone(D6, 0.20, amp=0.7, decay=0.06),
        at(0.045, tone(A5, 0.22, amp=0.6, decay=0.07)),
    )


if __name__ == "__main__":
    os.makedirs(OUT_DIR, exist_ok=True)
    print(f"Writing cues to {OUT_DIR}")

    write("match.wav", match())
    write("purchase.wav", purchase())
    write("reward.wav", reward())
    write("message.wav", message())

    print("Done.")
