"""Draws GameBuddy's badge icons — one per mission.

    python badges/generate.py

Same principle as cosmetics/generate.py and default-avatars/generate.py: the source of
truth is the description of each badge, not the pixels. Nobody has to open an editor to
change one, and the whole set can be regenerated after a palette change.

A badge is a hexagonal plate with a flat glyph on it. The shape is shared by all of
them and the glyph is what differs, because a wall of thirteen icons is read as a grid
of *silhouettes* first — if each one had its own outline the page would look like a
sticker album rather than a set. Steam does the same thing, and so does every game that
has ever shipped an achievement list.

The plate rim carries the tier (bronze, silver, gold) and the glyph carries the accent
colour, so the grid reads twice: what kind of thing this is, and how hard it was.

Locked badges are *not* drawn separately. The app dims and desaturates the same file,
which halves the assets to download and means a badge cannot look like a different
picture before and after you earn it.
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).parent / "icons"

#: Displayed at 64px in the grid and 96px in the detail sheet, so 256 is already well
#: past what anyone sees. Same reasoning as the frames: these are downloaded over mobile
#: data, thirteen at a time, to decorate a list.
SIZE = 256
SCALE = 4

CANVAS = SIZE * SCALE


def px(v: float) -> int:
    return int(round(v * SCALE))


def rgba(colour, alpha: float = 1.0):
    return (*colour, max(0, min(255, int(alpha * 255))))


def mix(a, b, t: float):
    """Blends two colours. Used for tints rather than alpha, so nothing shows through."""
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


# --- palette ---------------------------------------------------------------
# Deliberately the same one as cosmetics/generate.py. A badge sits on the same profile
# as a frame and a banner, and three separate palettes on one screen is how an app ends
# up looking assembled rather than designed.

BRAND = (255, 77, 103)
GOLD = (247, 191, 84)
SILVER = (198, 206, 220)
BRONZE = (198, 132, 84)
CYAN = (110, 230, 240)
MINT = (120, 235, 190)
VIOLET = (176, 130, 245)
EMBER = (255, 140, 60)
LIME = (168, 240, 110)
SKY = (120, 178, 255)
INK = (26, 27, 34)
WHITE = (255, 255, 255)

TIERS = {"bronze": BRONZE, "silver": SILVER, "gold": GOLD}


# =========================================================================
# The plate
# =========================================================================


def hexagon(cx: float, cy: float, r: float, rotation: float = -90.0):
    """Six points, first one at the top. Pointy-top, which reads as a crest."""
    return [
        (cx + r * math.cos(math.radians(rotation + 60 * i)),
         cy + r * math.sin(math.radians(rotation + 60 * i)))
        for i in range(6)
    ]


def vertical_gradient(width: int, height: int, top, bottom) -> Image.Image:
    """A one-pixel-wide gradient stretched out.

    Building it a row at a time and resizing is far faster than filling per pixel, and
    at this canvas size the difference is seconds per run.
    """
    strip = Image.new("RGB", (1, height))
    for y in range(height):
        strip.putpixel((0, y), mix(top, bottom, y / max(1, height - 1)))
    return strip.resize((width, height), Image.BILINEAR).convert("RGBA")


def plate(accent, tier: str) -> tuple[Image.Image, ImageDraw.ImageDraw]:
    """The hexagon every badge is drawn on.

    Returns the plate and a draw handle onto it. The glyph goes on top and is *not*
    clipped to the hexagon — a glyph that overflows the plate would be a design mistake
    rather than something to guard against, and clipping would hide it.
    """
    rim = TIERS[tier]
    c = CANVAS / 2
    r = CANVAS * 0.46

    mask = Image.new("L", (CANVAS, CANVAS), 0)
    ImageDraw.Draw(mask).polygon(hexagon(c, c, r), fill=255)

    # Lit from the top, like every physical medal. The tint is the accent rather than
    # plain grey so the plate belongs to its glyph instead of being a neutral tray.
    face = vertical_gradient(
        CANVAS, CANVAS, mix(INK, accent, 0.34), mix(INK, (0, 0, 0), 0.30)
    )
    face.putalpha(mask)

    d = ImageDraw.Draw(face)
    # Rim, then a hairline inside it. Two thin lines read as a machined edge where one
    # thick line just reads as a border.
    d.line(hexagon(c, c, r) + [hexagon(c, c, r)[0]], fill=rgba(rim, 0.95), width=px(3.5))
    inner = hexagon(c, c, r * 0.87)
    d.line(inner + [inner[0]], fill=rgba(rim, 0.30), width=px(1.5))

    # A soft pool of accent behind where the glyph lands, so the middle is not flat.
    pool = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    pr = r * 0.52
    ImageDraw.Draw(pool).ellipse(
        [c - pr, c - pr, c + pr, c + pr], fill=rgba(accent, 0.20)
    )
    pool = pool.filter(ImageFilter.GaussianBlur(px(14)))
    pool.putalpha(Image.composite(pool.getchannel("A"), Image.new("L", pool.size, 0), mask))
    face = Image.alpha_composite(face, pool)

    return face, ImageDraw.Draw(face)


def finish(image: Image.Image) -> Image.Image:
    return image.resize((SIZE, SIZE), Image.LANCZOS)


# =========================================================================
# Glyphs
# =========================================================================
#
# Every glyph takes the draw handle, a centre, a half-extent `s` and its colour, and
# draws inside a box of roughly 2s. Flat shapes only — no gradients, no highlights.
# At 64px on a phone anything subtler than a stroke is mud.

STROKE = 7.0  # nominal stroke width, in unscaled pixels


def circle(d, cx, cy, r, colour, alpha=1.0, width=None):
    box = [cx - r, cy - r, cx + r, cy + r]
    if width is None:
        d.ellipse(box, fill=rgba(colour, alpha))
    else:
        d.ellipse(box, outline=rgba(colour, alpha), width=px(width))


def stroke(d, a, b, colour, width=STROKE, alpha=1.0):
    d.line([a, b], fill=rgba(colour, alpha), width=px(width))
    # Pillow does not round line ends, and a chain of butt-ended segments has visible
    # notches at every joint. Capping each end with a disc is the cheapest fix.
    for x, y in (a, b):
        r = px(width) / 2
        d.ellipse([x - r, y - r, x + r, y + r], fill=rgba(colour, alpha))


def glyph_link(d, cx, cy, s, colour):
    """First Contact: two players, joined."""
    off = s * 0.62
    stroke(d, (cx - off, cy), (cx + off, cy), colour, STROKE * 0.8, 0.55)
    circle(d, cx - off, cy, s * 0.34, colour)
    circle(d, cx + off, cy, s * 0.34, colour)


def glyph_trio(d, cx, cy, s, colour):
    """Squad Forming: three, connected."""
    pts = [
        (cx, cy - s * 0.68),
        (cx - s * 0.62, cy + s * 0.48),
        (cx + s * 0.62, cy + s * 0.48),
    ]
    for i in range(3):
        stroke(d, pts[i], pts[(i + 1) % 3], colour, STROKE * 0.7, 0.5)
    for p in pts:
        circle(d, p[0], p[1], s * 0.29, colour)


def glyph_party(d, cx, cy, s, colour):
    """Full Party: a ring of ten around one."""
    for i in range(10):
        a = math.radians(-90 + i * 36)
        circle(d, cx + math.cos(a) * s * 0.78, cy + math.sin(a) * s * 0.78, s * 0.17, colour)
    circle(d, cx, cy, s * 0.30, colour, 0.9)
    circle(d, cx, cy, s * 0.50, colour, 0.35, width=STROKE * 0.4)


def glyph_heart(d, cx, cy, s, colour):
    """Friendly Person. The parametric heart — a polygon of it always looks wrong."""
    pts = []
    for i in range(121):
        t = 2 * math.pi * i / 120
        x = 16 * math.sin(t) ** 3
        y = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
        pts.append((cx + x * s / 15.5, cy + y * s / 15.5))
    d.polygon(pts, fill=rgba(colour, 1.0))


def glyph_rings(d, cx, cy, s, colour):
    """Inner Circle: the group closing around one person."""
    circle(d, cx, cy, s * 0.26, colour)
    circle(d, cx, cy, s * 0.55, colour, 0.75, width=STROKE * 0.55)
    circle(d, cx, cy, s * 0.88, colour, 0.4, width=STROKE * 0.45)
    for i in range(6):
        a = math.radians(i * 60)
        circle(d, cx + math.cos(a) * s * 0.88, cy + math.sin(a) * s * 0.88, s * 0.14, colour, 0.9)


def bubble(d, cx, cy, w, h, colour, alpha=1.0, tail=True, flip=False):
    d.rounded_rectangle(
        [cx - w, cy - h, cx + w, cy + h], radius=px(h * 0.55 / SCALE), fill=rgba(colour, alpha)
    )
    if tail:
        x = cx + w * 0.45 * (-1 if flip else 1)
        d.polygon(
            [
                (x - w * 0.20, cy + h * 0.7),
                (x + w * 0.20, cy + h * 0.7),
                (x + (w * 0.05 if flip else -w * 0.05), cy + h * 1.55),
            ],
            fill=rgba(colour, alpha),
        )


def glyph_bubble(d, cx, cy, s, colour):
    """Icebreaker: one message, with something in it."""
    bubble(d, cx, cy - s * 0.14, s * 0.86, s * 0.60, colour)
    for i in (-1, 0, 1):
        circle(d, cx + i * s * 0.34, cy - s * 0.14, s * 0.11, INK)


def glyph_bubbles(d, cx, cy, s, colour):
    """Never Offline: a conversation that does not stop."""
    bubble(d, cx + s * 0.22, cy - s * 0.42, s * 0.62, s * 0.42, colour, 0.45, flip=True)
    bubble(d, cx - s * 0.16, cy + s * 0.26, s * 0.72, s * 0.46, colour)
    for i in (-1, 0, 1):
        circle(d, cx - s * 0.16 + i * s * 0.30, cy + s * 0.26, s * 0.095, INK)


def shield_points(cx, cy, w, h):
    """A heraldic shield: square shoulders, sides falling away to a point."""
    return [
        (cx - w, cy - h),
        (cx + w, cy - h),
        (cx + w, cy - h * 0.10),
        (cx + w * 0.80, cy + h * 0.42),
        (cx, cy + h),
        (cx - w * 0.80, cy + h * 0.42),
        (cx - w, cy - h * 0.10),
    ]


def glyph_shield(d, cx, cy, s, colour):
    """Guild Member: a crest, because that is what a community is."""
    d.polygon(shield_points(cx, cy, s * 0.76, s * 0.90), fill=rgba(colour, 1.0))
    # A chevron knocked out of it, full width. Heraldry, and it survives being shrunk to
    # a 24px showcase chip where a finer device would close up.
    d.polygon(
        [
            (cx - s * 0.76, cy + s * 0.06),
            (cx, cy - s * 0.44),
            (cx + s * 0.76, cy + s * 0.06),
            (cx + s * 0.76, cy + s * 0.36),
            (cx, cy - s * 0.14),
            (cx - s * 0.76, cy + s * 0.36),
        ],
        fill=rgba(INK, 1.0),
    )


def glyph_quill(d, cx, cy, s, colour):
    """Say Something: a pen, over two lines of what it wrote.

    Drawn as a tapered body rather than a stroke with a triangle stuck on the end. The
    first attempt did the latter and read as an arrow — the triangle merged with the
    shaft and pointed, which is the one thing a pen must not look like.
    """
    stroke(d, (cx - s * 0.88, cy + s * 0.62), (cx + s * 0.22, cy + s * 0.62), colour, STROKE * 0.6, 0.4)
    stroke(d, (cx - s * 0.88, cy + s * 0.92), (cx - s * 0.24, cy + s * 0.92), colour, STROKE * 0.6, 0.4)

    # Along the pen: from the nib at the lower left up to the blunt end at the upper right.
    ux, uy = math.cos(math.radians(-45)), math.sin(math.radians(-45))
    nx, ny = -uy, ux  # across it
    tip = (cx - s * 0.66, cy + s * 0.42)

    def at(along, across=0.0):
        return (tip[0] + ux * along + nx * across, tip[1] + uy * along + ny * across)

    half = s * 0.19
    # Body.
    d.polygon(
        [at(s * 0.30, half), at(s * 1.62, half), at(s * 1.62, -half), at(s * 0.30, -half)],
        fill=rgba(colour, 1.0),
    )
    # Nib: the taper down to the point.
    d.polygon([at(s * 0.30, half), at(0), at(s * 0.30, -half)], fill=rgba(colour, 1.0))
    # The slit that makes it a nib rather than a wedge.
    d.line([at(s * 0.34, half * 0.95), at(s * 0.34, -half * 0.95)], fill=rgba(INK, 1.0), width=px(3))
    d.line([at(s * 0.05), at(s * 0.30)], fill=rgba(INK, 1.0), width=px(2.5))


def star_points(cx, cy, outer, inner, points=5, rotation=-90.0):
    return [
        (
            cx + (outer if i % 2 == 0 else inner) * math.cos(math.radians(rotation + i * 180 / points)),
            cy + (outer if i % 2 == 0 else inner) * math.sin(math.radians(rotation + i * 180 / points)),
        )
        for i in range(points * 2)
    ]


def glyph_laurel(d, cx, cy, s, colour):
    """Local Legend: a star between two laurels.

    Two arcs on separate boxes, pushed outwards. Drawn on one box they overlapped into a
    single lopsided curve, which is what the first pass produced.
    """
    for sign in (-1, 1):
        ox = cx + sign * s * 0.30
        rx, ry = s * 0.78, s * 0.98
        d.arc(
            [ox - rx, cy - ry, ox + rx, cy + ry],
            start=-60 if sign > 0 else 120,
            end=60 if sign > 0 else 240,
            fill=rgba(colour, 0.6),
            width=px(STROKE * 0.75),
        )
        # Leaves. Without them the two arcs read as a pair of parentheses.
        for a in (-40, -14, 14, 40):
            t = math.radians(a if sign > 0 else 180 - a)
            x, y = ox + rx * math.cos(t), cy + ry * math.sin(t)
            stroke(d, (x, y), (x + sign * s * 0.26, y - s * 0.16), colour, STROKE * 0.5, 0.6)
    d.polygon(star_points(cx, cy - s * 0.04, s * 0.68, s * 0.28), fill=rgba(colour, 1.0))


def glyph_kit(d, cx, cy, s, colour):
    """Fully Kitted: a filled-in profile, ticked off."""
    circle(d, cx - s * 0.12, cy - s * 0.42, s * 0.32, colour)
    # Shoulders: a half-disc, drawn as a pieslice so the flat edge is exact.
    d.pieslice(
        [cx - s * 0.86, cy - s * 0.28, cx + s * 0.62, cy + s * 1.20],
        start=180,
        end=360,
        fill=rgba(colour, 1.0),
    )
    # The tick sits over the shoulder and is knocked out of it, so it stays legible
    # whatever the accent is.
    stroke(d, (cx + s * 0.18, cy + s * 0.52), (cx + s * 0.46, cy + s * 0.80), INK, STROKE * 1.5)
    stroke(d, (cx + s * 0.46, cy + s * 0.80), (cx + s * 0.96, cy + s * 0.10), INK, STROKE * 1.5)
    stroke(d, (cx + s * 0.18, cy + s * 0.52), (cx + s * 0.46, cy + s * 0.80), colour, STROKE * 0.85)
    stroke(d, (cx + s * 0.46, cy + s * 0.80), (cx + s * 0.96, cy + s * 0.10), colour, STROKE * 0.85)


def glyph_coins(d, cx, cy, s, colour):
    """Rich in the Hood: a stack, seen from slightly above.

    The top coin gets a lighter face rather than a dark one. A dark inner ellipse was
    the first attempt and turned the whole thing into a tyre.
    """
    for i, alpha in enumerate((0.42, 0.66, 1.0)):
        y = cy + s * 0.54 - i * s * 0.42
        d.ellipse(
            [cx - s * 0.78, y - s * 0.26, cx + s * 0.78, y + s * 0.26], fill=rgba(colour, alpha)
        )
    top = cy + s * 0.54 - 2 * s * 0.42
    d.ellipse(
        [cx - s * 0.52, top - s * 0.15, cx + s * 0.52, top + s * 0.15],
        fill=rgba(mix(colour, WHITE, 0.5), 1.0),
    )


def glyph_drip(d, cx, cy, s, colour):
    """Drip Check: a banner with a framed avatar over it — the profile header itself."""
    d.rounded_rectangle(
        [cx - s * 1.05, cy - s * 0.86, cx + s * 1.05, cy - s * 0.06],
        radius=px(s * 0.18 / SCALE),
        fill=rgba(colour, 0.42),
    )
    circle(d, cx, cy + s * 0.16, s * 0.60, mix(INK, colour, 0.28), 1.0)
    circle(d, cx, cy + s * 0.16, s * 0.60, colour, 1.0, width=STROKE * 1.15)


# =========================================================================
# The catalogue
# =========================================================================
#
# Keys match Badge.code on the backend (badges/badge-<kebab>.png is the object key it
# stores). If a name changes here it changes there; there is no lookup that would let
# the two drift apart quietly, which is the point of naming them after the mission.

BADGES = [
    # code,               accent,  tier,     glyph
    ("first-contact",     CYAN,    "bronze", glyph_link),
    ("squad-forming",     CYAN,    "silver", glyph_trio),
    ("full-party",        SKY,     "gold",   glyph_party),
    ("friendly-person",   BRAND,   "bronze", glyph_heart),
    ("inner-circle",      BRAND,   "silver", glyph_rings),
    ("icebreaker",        MINT,    "bronze", glyph_bubble),
    ("never-offline",     MINT,    "gold",   glyph_bubbles),
    ("guild-member",      VIOLET,  "bronze", glyph_shield),
    ("say-something",     LIME,    "bronze", glyph_quill),
    ("local-legend",      LIME,    "gold",   glyph_laurel),
    ("fully-kitted",      SKY,     "bronze", glyph_kit),
    ("rich-in-the-hood",  GOLD,    "silver", glyph_coins),
    ("drip-check",        EMBER,   "silver", glyph_drip),
]


def build(accent, tier: str, glyph) -> Image.Image:
    image, d = plate(accent, tier)
    c = CANVAS / 2
    # The glyph is drawn in a brightened accent rather than the accent itself: against a
    # plate tinted with the same colour, the pure hue does not separate.
    glyph(d, c, c, CANVAS * 0.20, mix(accent, WHITE, 0.35))
    return finish(image)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for code, accent, tier, glyph in BADGES:
        path = OUT / f"badge-{code}.png"
        build(accent, tier, glyph).save(path, optimize=True)
        print(f"  badge-{code}.png  ({path.stat().st_size // 1024}KB, {tier})")
    contact_sheet()


def contact_sheet() -> None:
    """One sheet to eyeball the set as a set. Local only; never uploaded."""
    cols = 5
    rows = (len(BADGES) + cols - 1) // cols
    sheet = Image.new("RGB", (SIZE * cols, SIZE * rows), (32, 33, 40))
    for i, (code, *_) in enumerate(BADGES):
        icon = Image.open(OUT / f"badge-{code}.png").convert("RGBA")
        sheet.paste(icon, ((i % cols) * SIZE, (i // cols) * SIZE), icon)
    sheet.save(Path(__file__).parent / "contact-sheet.png")
    print("  contact-sheet.png")


if __name__ == "__main__":
    main()
