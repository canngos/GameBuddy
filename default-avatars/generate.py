"""Draws GameBuddy's default avatars.

Why a script and not twelve PNGs committed by hand
--------------------------------------------------
The art is generated, so the source of truth is the description of each character rather
than the pixels. Changing the palette, the size, or adding a thirteenth character is an
edit here and a re-run, not a round trip through an image editor — and anyone can see
exactly what produced the files sitting next to this one.

    python default-avatars/generate.py

Style
-----
Flat vector, no gradients or outlines, built from the same parts every time: a coloured
disc, shoulders, a head, hair, and one piece of gaming kit. That shared construction is
what makes twelve separate drawings read as one set. Everything is drawn at 4x and
downsampled, because Pillow does not anti-alias its shape primitives and the difference
between a jagged 512px circle and a clean one is entirely in that resample.

Draw order is load-bearing. Anything that hangs behind the head — a curtain of long hair,
a ponytail — goes down before the head; anything sitting on the crown goes after. The
first version of this file drew long hair as one rounded rectangle around the whole head,
which framed the chin as well as the temples and gave two characters a full beard.

The cast
--------
Varied skin tones, hair textures and styles across the set, rather than one "default"
character with a diversity exception bolted on the end. The files are numbered rather than
named after who they depict — nobody should have to pick "the black one" from a menu; they
pick avatar-07 because they like the cap.
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw

SIZE = 512
SCALE = 4  # supersampling factor; Pillow will not anti-alias shapes on its own
S = SIZE * SCALE

OUT = Path(__file__).parent

# Backgrounds. Muted, so the character reads first and white username text sits on top of
# these comfortably in the app's lists.
BG = {
    "rose": (255, 138, 155),
    "coral": (255, 154, 122),
    "amber": (247, 191, 106),
    "sage": (129, 199, 154),
    "teal": (99, 191, 190),
    "sky": (122, 176, 240),
    "indigo": (139, 148, 232),
    "violet": (183, 143, 227),
    "slate": (140, 160, 184),
    "sand": (222, 194, 160),
}

# Five tones, evenly spread rather than four pale ones and a token dark.
SKIN = {
    "porcelain": (255, 220, 196),
    "tan": (231, 180, 143),
    "olive": (198, 145, 106),
    "brown": (150, 100, 68),
    "deep": (94, 60, 44),
}

HAIR = {
    "black": (38, 34, 40),
    "dark": (62, 46, 40),
    "brown": (110, 72, 44),
    "auburn": (154, 74, 44),
    "blond": (222, 179, 96),
    "ash": (176, 176, 184),
    "pink": (240, 120, 170),
    "mint": (110, 214, 190),
    "blue": (86, 140, 226),
}

INK = (44, 40, 52)
WHITE = (255, 255, 255)


def px(v: float) -> int:
    return int(round(v * SCALE))


def shade(colour, factor):
    """Darken (<1) or lighten (>1) without leaving the 8-bit range."""
    return tuple(max(0, min(255, int(c * factor))) for c in colour)


class Avatar:
    """One character, drawn part by part onto a supersampled canvas."""

    # One set of proportions for the whole cast. Faces sitting at different heights are
    # what makes a set of avatars look like a set of accidents.
    CX = 256
    HEAD_Y = 206
    HEAD_W = 178
    HEAD_H = 196
    CHIN = HEAD_Y + HEAD_H / 2
    SHOULDER_Y = 348

    def __init__(self, bg, skin, hair):
        self.image = Image.new("RGB", (S, S), BG[bg])
        self.d = ImageDraw.Draw(self.image)
        self.skin = SKIN[skin]
        self.hair = HAIR[hair]

    def ellipse(self, cx, cy, w, h, fill):
        self.d.ellipse([px(cx - w / 2), px(cy - h / 2), px(cx + w / 2), px(cy + h / 2)], fill=fill)

    # -- body -------------------------------------------------------------

    def neck(self):
        # Short, and mostly hidden by the collar. An earlier version ran this from the
        # chin all the way down to the shoulders and every character was a giraffe.
        self.d.rounded_rectangle(
            [px(self.CX - 40), px(self.CHIN - 34), px(self.CX + 40), px(self.SHOULDER_Y + 24)],
            radius=px(18),
            fill=shade(self.skin, 0.86),
        )

    def shoulders(self, colour):
        self.d.rounded_rectangle(
            [px(self.CX - 208), px(self.SHOULDER_Y), px(self.CX + 208), px(660)],
            radius=px(126),
            fill=colour,
        )
        # A collar, so the head is not glued onto a slab.
        self.ellipse(self.CX, self.SHOULDER_Y + 16, 128, 56, shade(colour, 1.12))

    def head(self):
        # Ears first; the head covers their inner edge.
        self.ellipse(self.CX - self.HEAD_W / 2 - 2, self.HEAD_Y + 22, 36, 46, shade(self.skin, 0.92))
        self.ellipse(self.CX + self.HEAD_W / 2 + 2, self.HEAD_Y + 22, 36, 46, shade(self.skin, 0.92))
        self.ellipse(self.CX, self.HEAD_Y, self.HEAD_W, self.HEAD_H, self.skin)

    def face(self, blush=False):
        eye_y = self.HEAD_Y + 18
        for dx in (-34, 34):
            self.ellipse(self.CX + dx, eye_y, 19, 24, INK)
            self.ellipse(self.CX + dx + 5, eye_y - 6, 7, 7, WHITE)
        for dx in (-34, 34):
            self.d.rounded_rectangle(
                [px(self.CX + dx - 19), px(eye_y - 34), px(self.CX + dx + 19), px(eye_y - 25)],
                radius=px(5),
                fill=shade(self.hair, 0.85),
            )
        if blush:
            for dx in (-60, 60):
                self.ellipse(self.CX + dx, eye_y + 30, 34, 18, shade(self.skin, 0.87))
        self.d.arc(
            [px(self.CX - 28), px(eye_y + 24), px(self.CX + 28), px(eye_y + 60)],
            start=20, end=160, fill=INK, width=px(7),
        )

    # -- hair -------------------------------------------------------------

    def hair_short(self):
        self.d.pieslice(
            [px(self.CX - self.HEAD_W / 2 - 6), px(self.HEAD_Y - self.HEAD_H / 2 - 12),
             px(self.CX + self.HEAD_W / 2 + 6), px(self.HEAD_Y + self.HEAD_H / 2 - 40)],
            start=180, end=360, fill=self.hair,
        )

    def hair_buzz(self):
        self.d.pieslice(
            [px(self.CX - self.HEAD_W / 2 - 1), px(self.HEAD_Y - self.HEAD_H / 2 - 1),
             px(self.CX + self.HEAD_W / 2 + 1), px(self.HEAD_Y + self.HEAD_H / 2 - 76)],
            start=180, end=360, fill=self.hair,
        )

    def hair_coils(self):
        """Tight coils, built from overlapping discs around the crown."""
        for angle in range(184, 361, 16):
            a = math.radians(angle)
            cx = self.CX + math.cos(a) * (self.HEAD_W / 2 + 2)
            cy = self.HEAD_Y - 22 + math.sin(a) * (self.HEAD_H / 2 + 2)
            self.ellipse(cx, cy, 36, 36, self.hair)
        self.d.pieslice(
            [px(self.CX - self.HEAD_W / 2 - 4), px(self.HEAD_Y - self.HEAD_H / 2 - 14),
             px(self.CX + self.HEAD_W / 2 + 4), px(self.HEAD_Y + 4)],
            start=180, end=360, fill=self.hair,
        )

    def hair_long_back(self):
        """The curtain behind the head. Stops at the collar, never under the chin."""
        self.d.rounded_rectangle(
            [px(self.CX - self.HEAD_W / 2 - 30), px(self.HEAD_Y - self.HEAD_H / 2 - 10),
             px(self.CX + self.HEAD_W / 2 + 30), px(self.SHOULDER_Y + 30)],
            radius=px(92),
            fill=self.hair,
        )

    def hair_long_front(self):
        """Crown and two side locks, over the head, framing the face."""
        self.d.pieslice(
            [px(self.CX - self.HEAD_W / 2 - 8), px(self.HEAD_Y - self.HEAD_H / 2 - 12),
             px(self.CX + self.HEAD_W / 2 + 8), px(self.HEAD_Y + 16)],
            start=180, end=360, fill=self.hair,
        )
        for side in (-1, 1):
            cx = self.CX + side * (self.HEAD_W / 2 + 4)
            self.d.rounded_rectangle(
                [px(cx - 26), px(self.HEAD_Y - 48), px(cx + 26), px(self.CHIN - 6)],
                radius=px(26),
                fill=self.hair,
            )

    def hair_bun(self):
        self.ellipse(self.CX, self.HEAD_Y - self.HEAD_H / 2 - 26, 84, 84, self.hair)
        self.hair_short()

    def hair_ponytail_back(self):
        # Far enough out to clear a headset earcup, which sits at CX+118 and hid this
        # entirely the first time round.
        self.ellipse(self.CX + 152, self.HEAD_Y + 58, 84, 176, self.hair)
        self.ellipse(self.CX + 108, self.HEAD_Y - 34, 60, 60, self.hair)

    # -- gaming kit -------------------------------------------------------

    def headset(self, accent):
        """The prop that says what this app is for."""
        band_top = self.HEAD_Y - self.HEAD_H / 2 - 30
        self.d.arc(
            [px(self.CX - 128), px(band_top), px(self.CX + 128), px(band_top + 220)],
            start=180, end=360, fill=INK, width=px(22),
        )
        for dx in (-118, 118):
            self.d.rounded_rectangle(
                [px(self.CX + dx - 30), px(self.HEAD_Y - 14), px(self.CX + dx + 30), px(self.HEAD_Y + 76)],
                radius=px(26), fill=INK,
            )
            self.d.rounded_rectangle(
                [px(self.CX + dx - 16), px(self.HEAD_Y + 4), px(self.CX + dx + 16), px(self.HEAD_Y + 56)],
                radius=px(14), fill=accent,
            )
        # Boom mic on one side only; symmetrical would read as a stethoscope.
        self.d.rounded_rectangle(
            [px(self.CX - 132), px(self.HEAD_Y + 76), px(self.CX - 62), px(self.HEAD_Y + 94)],
            radius=px(9), fill=INK,
        )
        self.ellipse(self.CX - 58, self.HEAD_Y + 85, 28, 28, accent)

    def visor(self, accent):
        """Pushed up onto the forehead, not across the eyes.

        Over the eyes it looked right in isolation and wrong in a grid — three of twelve
        characters had no face at all. An avatar's job is to be a face.
        """
        y = self.HEAD_Y - 54
        self.d.rounded_rectangle(
            [px(self.CX - 96), px(y - 22), px(self.CX + 96), px(y + 24)],
            radius=px(22), fill=INK,
        )
        self.d.rounded_rectangle(
            [px(self.CX - 82), px(y - 10), px(self.CX + 82), px(y + 12)],
            radius=px(11), fill=accent,
        )

    def helmet(self, accent):
        self.d.pieslice(
            [px(self.CX - self.HEAD_W / 2 - 30), px(self.HEAD_Y - self.HEAD_H / 2 - 36),
             px(self.CX + self.HEAD_W / 2 + 30), px(self.HEAD_Y + 40)],
            start=180, end=360, fill=accent,
        )
        self.d.rounded_rectangle(
            [px(self.CX - self.HEAD_W / 2 - 34), px(self.HEAD_Y - 46),
             px(self.CX + self.HEAD_W / 2 + 34), px(self.HEAD_Y - 20)],
            radius=px(12), fill=shade(accent, 0.78),
        )
        # Chin strap, so the helmet sits on the head instead of hovering over it.
        self.d.line(
            [px(self.CX - self.HEAD_W / 2 - 14), px(self.HEAD_Y - 26),
             px(self.CX - self.HEAD_W / 2 + 12), px(self.CHIN - 16)],
            fill=shade(accent, 0.68), width=px(11),
        )

    def cap(self, accent):
        self.d.pieslice(
            [px(self.CX - self.HEAD_W / 2 - 12), px(self.HEAD_Y - self.HEAD_H / 2 - 26),
             px(self.CX + self.HEAD_W / 2 + 12), px(self.HEAD_Y - 4)],
            start=180, end=360, fill=accent,
        )
        self.d.rounded_rectangle(
            [px(self.CX - 10), px(self.HEAD_Y - 68), px(self.CX + 148), px(self.HEAD_Y - 42)],
            radius=px(13), fill=shade(accent, 0.8),
        )

    def beanie(self, accent):
        self.d.pieslice(
            [px(self.CX - self.HEAD_W / 2 - 14), px(self.HEAD_Y - self.HEAD_H / 2 - 34),
             px(self.CX + self.HEAD_W / 2 + 14), px(self.HEAD_Y - 6)],
            start=180, end=360, fill=accent,
        )
        self.d.rounded_rectangle(
            [px(self.CX - self.HEAD_W / 2 - 16), px(self.HEAD_Y - 64),
             px(self.CX + self.HEAD_W / 2 + 16), px(self.HEAD_Y - 26)],
            radius=px(17), fill=shade(accent, 0.85),
        )
        self.ellipse(self.CX, self.HEAD_Y - self.HEAD_H / 2 - 34, 46, 46, shade(accent, 1.18))

    # -- output -----------------------------------------------------------

    def finish(self) -> Image.Image:
        # Everything outside the disc is cut away, so the file is the circle the app
        # draws rather than a square that happens to contain one.
        mask = Image.new("L", (S, S), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, S - 1, S - 1], fill=255)
        out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        out.paste(self.image, (0, 0), mask)
        return out.resize((SIZE, SIZE), Image.LANCZOS)


def build(spec) -> Image.Image:
    """Assembles one character, back to front."""
    a = Avatar(spec["bg"], spec["skin"], spec["hair"])
    style = spec["hair_style"]

    if style == "long":
        a.hair_long_back()
    elif style == "ponytail":
        a.hair_ponytail_back()

    a.neck()
    a.shoulders(spec["shirt"])
    a.head()

    if style == "long":
        a.hair_long_front()
    elif style == "ponytail":
        a.hair_short()
    else:
        getattr(a, f"hair_{style}")()

    a.face(blush=spec.get("blush", False))

    if spec.get("kit"):
        getattr(a, spec["kit"])(spec["accent"])
    return a.finish()


# Twelve, because eight was the old count and the extra four are what let the set cover a
# real range of tones and styles rather than a token one of each.
CAST = [
    dict(bg="indigo", skin="tan",       hair="dark",   hair_style="short",    kit="headset", accent=(255, 77, 103),  shirt=(64, 72, 128)),
    dict(bg="rose",   skin="porcelain", hair="auburn", hair_style="long",     kit="headset", accent=(122, 214, 255), shirt=(196, 84, 108), blush=True),
    dict(bg="sage",   skin="deep",      hair="black",  hair_style="coils",    kit="headset", accent=(255, 191, 84),  shirt=(58, 122, 88)),
    dict(bg="amber",  skin="olive",     hair="black",  hair_style="buzz",     kit="helmet",  accent=(108, 122, 92),  shirt=(94, 88, 70)),
    dict(bg="teal",   skin="brown",     hair="black",  hair_style="bun",      kit="visor",   accent=(126, 245, 226), shirt=(38, 118, 122)),
    dict(bg="violet", skin="porcelain", hair="pink",   hair_style="ponytail", kit="headset", accent=(255, 138, 205), shirt=(126, 88, 168), blush=True),
    dict(bg="sky",    skin="deep",      hair="black",  hair_style="short",    kit="cap",     accent=(38, 62, 110),   shirt=(52, 96, 160)),
    dict(bg="coral",  skin="tan",       hair="blond",  hair_style="short",    kit="beanie",  accent=(232, 96, 84),   shirt=(190, 102, 82)),
    dict(bg="slate",  skin="brown",     hair="ash",    hair_style="coils",    kit="visor",   accent=(180, 196, 214), shirt=(84, 100, 122)),
    dict(bg="sand",   skin="olive",     hair="brown",  hair_style="long",     kit=None,      accent=None,            shirt=(178, 146, 112), blush=True),
    dict(bg="teal",   skin="porcelain", hair="mint",   hair_style="bun",      kit="headset", accent=(120, 255, 214), shirt=(44, 132, 130)),
    dict(bg="indigo", skin="brown",     hair="blue",   hair_style="buzz",     kit="visor",   accent=(126, 178, 255), shirt=(70, 78, 140)),
]


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    sheet = Image.new("RGBA", (SIZE * 6, SIZE * 2), (255, 255, 255, 0))

    for index, spec in enumerate(CAST, start=1):
        image = build(spec)
        name = f"avatar-{index:02d}.png"
        image.save(OUT / name)
        sheet.paste(image, (((index - 1) % 6) * SIZE, ((index - 1) // 6) * SIZE))
        print(f"  {name}")

    # A contact sheet, so the set can be judged as a set. Not uploaded anywhere; it
    # exists to be looked at whenever one of these changes.
    sheet.resize((SIZE * 3, SIZE), Image.LANCZOS).save(OUT / "contact-sheet.png")
    print(f"\n{len(CAST)} avatars written to {OUT}")


if __name__ == "__main__":
    main()
