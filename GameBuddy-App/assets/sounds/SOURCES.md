# Sound provenance

The four cue WAVs were sourced 2026-08-25 from free-for-commercial-use libraries
(no attribution required by either license; this file is our own provenance record).
Each original was processed to the app's cue spec: silence-trimmed, length-capped,
converted to mono 16-bit 44.1 kHz WAV, peak-normalized to 0.35 (−9 dBFS), 60 ms fade-out.

| File | Original | Source | License | Processing |
|---|---|---|---|---|
| `match.wav` | "Level Up" | [Pixabay #191997](https://pixabay.com/sound-effects/film-special-effects-level-up-191997/) | [Pixabay Content License](https://pixabay.com/service/license-summary/) | trimmed 1.05 s → 0.85 s |
| `reward.wav` | "Collect Points" | [Pixabay #190037](https://pixabay.com/sound-effects/film-special-effects-collect-points-190037/) | Pixabay Content License | 0.50 s, untrimmed |
| `purchase.wav` | "Clinking coins" | [Mixkit sfx 1993](https://mixkit.co/free-sound-effects/coin/) ([direct file](https://assets.mixkit.co/active_storage/sfx/1993/1993-preview.mp3)) | [Mixkit Sound Effects Free License](https://mixkit.co/license/) | trimmed 0.72 s → 0.70 s |
| `message.wav` | "Bubble Pop" | [Pixabay #293342](https://pixabay.com/sound-effects/film-special-effects-bubble-pop-293342/) | Pixabay Content License | 0.18 s, untrimmed |

The previous synthesized placeholders can be regenerated with `scripts/make-sounds.py`
if these files are ever lost; the script's design rules (short, quiet, drop-in filenames)
still govern any future replacement.
