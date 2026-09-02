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

# The hard tier has no single rim colour, which is the point of it: where the other three
# rims name a metal, this one runs the whole palette around the hexagon. It reads as "not
# one of those" at thumbnail size, which is the only size that matters on the badge wall.
#
# The order is a loop — the last colour blends back into the first — so the animated plates
# below can rotate the phase and come back to where they started without a jump.
PRISM = [CYAN, MINT, LIME, GOLD, EMBER, BRAND, VIOLET, SKY]


def prism_at(t: float):
    """The prismatic ramp at position t, wrapping at 1.0."""
    t = t % 1.0
    span = 1.0 / len(PRISM)
    i = int(t / span)
    return mix(PRISM[i], PRISM[(i + 1) % len(PRISM)], (t - i * span) / span)


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


def plate(accent, tier: str, phase: float = 0.0) -> tuple[Image.Image, ImageDraw.ImageDraw]:
    """The hexagon every badge is drawn on.

    Returns the plate and a draw handle onto it. The glyph goes on top and is *not*
    clipped to the hexagon — a glyph that overflows the plate would be a design mistake
    rather than something to guard against, and clipping would hide it.

    `phase` only means anything for the prismatic tier, where it turns the rim's colours
    around the hexagon. Every other tier ignores it, so a still badge and frame zero of an
    animated one are the same picture.
    """
    rim = TIERS.get(tier, WHITE)
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
    if tier == "prismatic":
        # Each edge its own colour, subdivided so the change along an edge is a gradient
        # rather than six flat facets. Drawn edge-first and then capped, because Pillow
        # butts its line ends and a ring of butted segments has a notch at every corner.
        points = hexagon(c, c, r)
        steps = 9
        for i in range(len(points)):
            a = points[i]
            b = points[(i + 1) % len(points)]
            for k in range(steps):
                t0, t1 = k / steps, (k + 1) / steps
                p0 = (a[0] + (b[0] - a[0]) * t0, a[1] + (b[1] - a[1]) * t0)
                p1 = (a[0] + (b[0] - a[0]) * t1, a[1] + (b[1] - a[1]) * t1)
                colour = prism_at(phase + (i + t0) / len(points))
                d.line([p0, p1], fill=rgba(colour, 0.95), width=px(3.5))
        for i, point in enumerate(points):
            colour = prism_at(phase + i / len(points))
            rad = px(3.5) / 2
            d.ellipse(
                [point[0] - rad, point[1] - rad, point[0] + rad, point[1] + rad],
                fill=rgba(colour, 0.95),
            )
        inner = hexagon(c, c, r * 0.87)
        d.line(inner + [inner[0]], fill=rgba(WHITE, 0.22), width=px(1.5))
    else:
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


# --- added with the mission campaign and the hard tier ----------------------


def glyph_door(d, cx, cy, s, colour):
    """Signed Up: a way in, standing open."""
    w, h = s * 0.62, s * 1.05
    d.rounded_rectangle(
        [cx - w, cy - h, cx + w, cy + h], radius=px(10), outline=rgba(colour), width=px(STROKE)
    )
    circle(d, cx + w * 0.45, cy + s * 0.12, s * 0.11, colour)


def glyph_beacon(d, cx, cy, s, colour):
    """Host Mode: opening a room, and putting out the call.

    Not a flag on a pole, which is what this was first — `trailblazer` is a flag on a pole
    too, and at 82px two of those are one badge. A signal going out says "I opened this"
    without borrowing anybody else's outline.
    """
    circle(d, cx, cy + s * 0.22, s * 0.26, colour)
    for i, r in enumerate((0.52, 0.82)):
        d.arc(
            [cx - s * r, cy + s * 0.22 - s * r, cx + s * r, cy + s * 0.22 + s * r],
            start=200,
            end=340,
            fill=rgba(colour, 0.95 - i * 0.25),
            width=px(STROKE * 0.85),
        )
    stroke(d, (cx, cy + s * 0.48), (cx, cy + s * 0.95), colour, STROKE * 0.8)


def glyph_spark(d, cx, cy, s, colour):
    """Standing Ovation: the four-point spark a super like already uses."""
    for dx, dy in ((0, -1), (1, 0), (0, 1), (-1, 0)):
        d.polygon(
            [
                (cx + dx * s, cy + dy * s),
                (cx + dy * s * 0.30, cy - dx * s * 0.30),
                (cx - dy * s * 0.30, cy + dx * s * 0.30),
            ],
            fill=rgba(colour),
        )
    circle(d, cx, cy, s * 0.22, colour)


def glyph_seats(d, cx, cy, s, colour):
    """Regular: back in the same room again."""
    for i, dx in enumerate((-0.62, 0.0, 0.62)):
        circle(d, cx + dx * s, cy - s * 0.32, s * 0.24, colour, 1.0 if i != 1 else 0.55)
    d.rounded_rectangle(
        [cx - s * 0.95, cy + s * 0.10, cx + s * 0.95, cy + s * 0.60],
        radius=px(8),
        fill=rgba(colour, 0.85),
    )


def glyph_table(d, cx, cy, s, colour):
    """War Room: a plan, and people around it."""
    d.polygon(
        [
            (cx, cy - s * 0.55),
            (cx + s * 0.95, cy),
            (cx, cy + s * 0.55),
            (cx - s * 0.95, cy),
        ],
        outline=rgba(colour),
        width=px(STROKE * 0.85),
    )
    for dx, dy in ((-0.52, -0.62), (0.52, -0.62), (0.0, 0.78)):
        circle(d, cx + dx * s, cy + dy * s, s * 0.18, colour)


def glyph_wallet(d, cx, cy, s, colour):
    """Big Spender: the purse, lighter than it was."""
    d.rounded_rectangle(
        [cx - s * 0.95, cy - s * 0.62, cx + s * 0.95, cy + s * 0.68],
        radius=px(10),
        outline=rgba(colour),
        width=px(STROKE * 0.9),
    )
    d.rounded_rectangle(
        [cx + s * 0.15, cy - s * 0.10, cx + s * 1.02, cy + s * 0.30],
        radius=px(7),
        fill=rgba(colour),
    )


def glyph_play(d, cx, cy, s, colour):
    """Sponsored: the triangle everybody already reads as a video."""
    circle(d, cx, cy, s * 0.92, colour, 1.0, width=STROKE * 0.85)
    d.polygon(
        [
            (cx - s * 0.26, cy - s * 0.42),
            (cx + s * 0.46, cy),
            (cx - s * 0.26, cy + s * 0.42),
        ],
        fill=rgba(colour),
    )


def glyph_calendar(d, cx, cy, s, colour):
    """Seven Days: a week, kept."""
    d.rounded_rectangle(
        [cx - s * 0.92, cy - s * 0.72, cx + s * 0.92, cy + s * 0.82],
        radius=px(9),
        outline=rgba(colour),
        width=px(STROKE * 0.8),
    )
    stroke(d, (cx - s * 0.92, cy - s * 0.30), (cx + s * 0.92, cy - s * 0.30), colour, STROKE * 0.6)
    for i in range(3):
        for j in range(2):
            circle(d, cx + (i - 1) * s * 0.52, cy + s * 0.10 + j * s * 0.42, s * 0.11, colour)


def glyph_chevrons(d, cx, cy, s, colour):
    """Centurion: rank, three deep."""
    for i, dy in enumerate((-0.62, 0.0, 0.62)):
        alpha = 1.0 - i * 0.18
        stroke(d, (cx - s * 0.72, cy + dy * s + s * 0.24), (cx, cy + dy * s - s * 0.18), colour, STROKE, alpha)
        stroke(d, (cx, cy + dy * s - s * 0.18), (cx + s * 0.72, cy + dy * s + s * 0.24), colour, STROKE, alpha)


def glyph_anvil(d, cx, cy, s, colour):
    """Iron Will: turning up, and turning up, and turning up."""
    # Wide top, narrow waist, wide base — an I-beam silhouette. The first version tapered
    # all the way down, which is a funnel.
    d.rounded_rectangle(
        [cx - s * 0.95, cy - s * 0.72, cx + s * 0.95, cy - s * 0.30],
        radius=px(6),
        fill=rgba(colour),
    )
    # The horn, which is what makes an I-beam an anvil.
    d.polygon(
        [
            (cx + s * 0.95, cy - s * 0.72),
            (cx + s * 1.28, cy - s * 0.54),
            (cx + s * 0.95, cy - s * 0.30),
        ],
        fill=rgba(colour),
    )
    d.rectangle(
        [cx - s * 0.34, cy - s * 0.30, cx + s * 0.34, cy + s * 0.36],
        fill=rgba(colour, 0.85),
    )
    d.rounded_rectangle(
        [cx - s * 0.80, cy + s * 0.36, cx + s * 0.80, cy + s * 0.78],
        radius=px(6),
        fill=rgba(colour),
    )


def glyph_magnet(d, cx, cy, s, colour):
    """Magnetic: fifty people said yes back."""
    # The poles have to be visibly fatter than the arc, or a U with two thin legs is a
    # pair of headphones — which is what the first version of this was.
    arm = s * 0.30
    d.arc(
        [cx - s * 0.80, cy - s * 0.78, cx + s * 0.80, cy + s * 0.62],
        start=180,
        end=360,
        fill=rgba(colour),
        width=px(STROKE * 2.2),
    )
    for dx in (-0.80, 0.80):
        d.rectangle(
            [cx + dx * s - arm * 0.55, cy - s * 0.08, cx + dx * s + arm * 0.55, cy + s * 0.58],
            fill=rgba(colour),
        )
        d.rectangle(
            [cx + dx * s - arm * 0.55, cy + s * 0.30, cx + dx * s + arm * 0.55, cy + s * 0.58],
            fill=rgba(mix(colour, INK, 0.45)),
        )


def glyph_chain(d, cx, cy, s, colour):
    """Unbroken: thirty days, and not one missed."""
    # Overlapping, and the second drawn over the first: two rings side by side read as
    # spectacles. A chain is only a chain where the links pass through each other.
    for dx in (-0.34, 0.34):
        d.rounded_rectangle(
            [cx + dx * s - s * 0.46, cy - s * 0.34, cx + dx * s + s * 0.46, cy + s * 0.34],
            radius=px(13),
            outline=rgba(colour),
            width=px(STROKE * 1.05),
        )
    # Clears a notch where the left link passes behind the right one, so the overlap reads
    # as depth rather than as a blob.
    d.rectangle(
        [cx - s * 0.02, cy - s * 0.16, cx + s * 0.10, cy + s * 0.16],
        fill=rgba(mix(INK, colour, 0.10)),
    )


def glyph_grid(d, cx, cy, s, colour):
    """Collector: the shelf, filled."""
    for i in range(3):
        for j in range(3):
            faded = (i + j) % 2 == 1
            d.rounded_rectangle(
                [
                    cx + (i - 1.5) * s * 0.62,
                    cy + (j - 1.5) * s * 0.62,
                    cx + (i - 0.6) * s * 0.62,
                    cy + (j - 0.6) * s * 0.62,
                ],
                radius=px(4),
                fill=rgba(colour, 0.55 if faded else 1.0),
            )


def glyph_flag(d, cx, cy, s, colour):
    """Trailblazer: first up the hill, and the whole campaign behind them."""
    stroke(d, (cx - s * 0.52, cy - s * 0.95), (cx - s * 0.52, cy + s * 0.92), colour)
    d.polygon(
        [
            (cx - s * 0.52, cy - s * 0.88),
            (cx + s * 0.88, cy - s * 0.52),
            (cx - s * 0.52, cy - s * 0.10),
        ],
        fill=rgba(colour),
    )


def glyph_crown(d, cx, cy, s, colour):
    """Completionist: every other badge in the game."""
    d.polygon(
        [
            (cx - s * 0.95, cy + s * 0.42),
            (cx - s * 0.72, cy - s * 0.55),
            (cx - s * 0.30, cy + s * 0.02),
            (cx, cy - s * 0.78),
            (cx + s * 0.30, cy + s * 0.02),
            (cx + s * 0.72, cy - s * 0.55),
            (cx + s * 0.95, cy + s * 0.42),
        ],
        fill=rgba(colour),
    )
    stroke(d, (cx - s * 0.95, cy + s * 0.72), (cx + s * 0.95, cy + s * 0.72), colour)



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

    # --- added with the mission campaign -----------------------------------
    ("first-lobby",       VIOLET,  "bronze", glyph_door),
    ("host-mode",         VIOLET,  "bronze", glyph_beacon),
    ("standing-ovation",  BRAND,   "bronze", glyph_spark),
    ("lobby-regular",     VIOLET,  "silver", glyph_seats),
    ("war-room",          SKY,     "silver", glyph_table),
    ("big-spender",       GOLD,    "silver", glyph_wallet),
    ("sponsored",         LIME,    "silver", glyph_play),
    ("week-one",          EMBER,   "gold",   glyph_calendar),

    # --- the hard tier -----------------------------------------------------
    # No rim colour of their own: prismatic runs the whole palette round the hexagon, so
    # the accent here only tints the plate and the glyph.
    ("centurion",         MINT,    "prismatic", glyph_chevrons),
    ("iron-will",         SILVER,  "prismatic", glyph_anvil),
    ("magnetic",          BRAND,   "prismatic", glyph_magnet),
    ("unbroken",          CYAN,    "prismatic", glyph_chain),
    ("collector",         GOLD,    "prismatic", glyph_grid),
    ("trailblazer",       LIME,    "prismatic", glyph_flag),
    ("completionist",     VIOLET,  "prismatic", glyph_crown),
]

# The two whose artwork actually moves.
#
# Not every prismatic badge: the app draws a turning aura over all seven for nothing, and
# animated WebP is the most expensive thing it decodes — three columns of them on the badge
# wall would be a real cost for a difference the aura already makes. These two are the only
# ones that cannot be earned until everything else has been, so they are where the extra is
# worth spending. Must match `Badge.isAnimated()` on the backend.
ANIMATED = {"trailblazer", "completionist"}

# The same encoder settings cosmetics/generate.py uses for animated frames, and for the
# same reasons — see the note there about lossy-with-alpha beating lossless on thin bright
# arcs over transparency.
#
# The frame count and rate are its own, though. A frame's ring is a fast effect; a badge's
# rim is meant to read as slowly turning light, so this is a 2.7-second loop rather than a
# 1.1-second one. Fewer frames is also what brings the file under budget — at 22 frames
# these came out at 160KB each, which is a lot to download to decorate an 82px tile, and
# the hue shifts so gradually that nothing is lost by dropping six of them.
FPS = 6
LOOP_FRAMES = 16


def build(accent, tier: str, glyph, phase: float = 0.0) -> Image.Image:
    image, d = plate(accent, tier, phase)
    c = CANVAS / 2
    # The glyph is drawn in a brightened accent rather than the accent itself: against a
    # plate tinted with the same colour, the pure hue does not separate.
    glyph(d, c, c, CANVAS * 0.20, mix(accent, WHITE, 0.35))
    return finish(image)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for code, accent, tier, glyph in BADGES:
        if code in ANIMATED:
            # One full turn of the rim over the loop, so the last frame leads back into the
            # first and there is no jump at the seam.
            frames = [
                build(accent, tier, glyph, phase=i / LOOP_FRAMES) for i in range(LOOP_FRAMES)
            ]
            path = OUT / f"badge-{code}.webp"
            frames[0].save(
                path,
                format="WEBP",
                save_all=True,
                append_images=frames[1:],
                duration=int(1000 / FPS),
                loop=0,
                quality=82,
                method=6,
                disposal=2,
            )
            size = path.stat().st_size
            print(f"  badge-{code}.webp  ({size // 1024}KB, {tier}, animated)")
            # Roughly a third of a frame's budget. A badge is a quarter of a frame's area
            # and there are three of them across the wall at once.
            if size > 140_000:
                print(f"    WARNING: {size // 1024}KB is large for a badge tile")
        else:
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
        suffix = "webp" if code in ANIMATED else "png"
        icon = Image.open(OUT / f"badge-{code}.{suffix}").convert("RGBA")
        sheet.paste(icon, ((i % cols) * SIZE, (i // cols) * SIZE), icon)
    sheet.save(Path(__file__).parent / "contact-sheet.png")
    print("  contact-sheet.png")


if __name__ == "__main__":
    main()
