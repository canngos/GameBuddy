"""Draws GameBuddy's cosmetics: avatar frames and profile banners.

    python cosmetics/generate.py

Same reasoning as default-avatars/generate.py — the source of truth is the description of
each item, not the pixels — but the output is a different shape:

**Frames** are rings with a transparent middle, composited over the avatar by the app. The
hole has to be genuinely transparent rather than filled with the background colour, or the
frame only works on one screen. Animated ones are animated WebP: the one format that
animates on both Android and iOS, where GIF and APNG each miss one.

**Banners** are wide, opaque, and deliberately have no focal point. They sit behind a
profile header with a name and an avatar on top of them, so anything eye-catching in the
middle competes with the person whose profile it is. Abstract texture, dark enough for
white text, interesting at the edges.

Both are drawn at 4x and downsampled — Pillow does not anti-alias its shape primitives,
and on a ring made of arcs that is the difference between jewellery and a sawblade.
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).parent
FRAMES_DIR = OUT / "frames"
BANNERS_DIR = OUT / "banners"

# Frames render at 96px at the very largest (the profile card), so 256 is already 2.6x
# the pixels anyone sees. It was 384, which looked identical on device and cost four
# times the bytes — and these are downloaded over mobile data, several at a time, to
# decorate a picture the size of a thumbnail.
FRAME_SIZE = 256
BANNER_W, BANNER_H = 1200, 400
SCALE = 4

#: Animated frames run at 20fps for a second and a bit. Long enough not to look like a
#: twitch, short enough that the loop is not obvious — and every frame is bytes, so this
#: is the main lever on file size after the canvas.
FPS = 20
LOOP_FRAMES = 22


def px(v: float) -> int:
    return int(round(v * SCALE))


def canvas(size: int) -> tuple[Image.Image, ImageDraw.ImageDraw]:
    image = Image.new("RGBA", (size * SCALE, size * SCALE), (0, 0, 0, 0))
    return image, ImageDraw.Draw(image)


def rgba(colour, alpha: float):
    return (*colour, max(0, min(255, int(alpha * 255))))


# --- palette ---------------------------------------------------------------

BRAND = (255, 77, 103)
GOLD = (247, 191, 84)
SILVER = (198, 206, 220)
BRONZE = (198, 132, 84)
CYAN = (110, 230, 240)
MINT = (120, 235, 190)
VIOLET = (176, 130, 245)
EMBER = (255, 140, 60)
LIME = (168, 240, 110)
INK = (26, 27, 34)


# =========================================================================
# Frames
# =========================================================================
#
# Geometry is shared so every frame sits at the same radius. A set where one ring hugs
# the avatar and the next floats away from it reads as a mistake rather than a choice.

#: Proportions, expressed against FRAME_SIZE so changing the canvas changes nothing else.
#: The app composites the avatar at HOLE_R * 2 / FRAME_SIZE of the frame's width — see
#: FramedAvatar — so these two numbers are a contract with the client, not just drawing.
RING_R = FRAME_SIZE * 0.437   # centreline radius of the ring
HOLE_R = FRAME_SIZE * 0.390   # everything inside this stays transparent


def ring(d, radius, width, colour, alpha=1.0, start=0, end=360):
    """An arc of the main ring, centred on the canvas."""
    c = FRAME_SIZE / 2
    box = [px(c - radius), px(c - radius), px(c + radius), px(c + radius)]
    d.arc(box, start=start, end=end, fill=rgba(colour, alpha), width=px(width))


def dot(d, angle_deg, radius, size, colour, alpha=1.0):
    c = FRAME_SIZE / 2
    a = math.radians(angle_deg)
    x, y = c + math.cos(a) * radius, c + math.sin(a) * radius
    d.ellipse(
        [px(x - size / 2), px(y - size / 2), px(x + size / 2), px(y + size / 2)],
        fill=rgba(colour, alpha),
    )


def finish(image: Image.Image) -> Image.Image:
    """Down-samples and punches the middle out.

    The hole is cleared last rather than simply never drawn: several of these are built
    from blurs and glows that bleed inwards, and clipping afterwards is the only way to
    keep the avatar underneath completely unobstructed.
    """
    out = image.resize((FRAME_SIZE, FRAME_SIZE), Image.LANCZOS)

    hole = Image.new("L", (FRAME_SIZE, FRAME_SIZE), 255)
    c = FRAME_SIZE / 2
    ImageDraw.Draw(hole).ellipse([c - HOLE_R, c - HOLE_R, c + HOLE_R, c + HOLE_R], fill=0)

    alpha = out.getchannel("A")
    out.putalpha(Image.composite(alpha, Image.new("L", out.size, 0), hole))
    return out


def glow(image: Image.Image, radius: float = 6) -> Image.Image:
    """A soft copy composited under the sharp original, for anything that should shine."""
    blurred = image.filter(ImageFilter.GaussianBlur(px(radius)))
    return Image.alpha_composite(blurred, image)


# -- static frames ----------------------------------------------------------


def frame_plain(colour, width=11):
    """One clean band. The free frame: it has to look deliberate, not unfinished."""
    image, d = canvas(FRAME_SIZE)
    ring(d, RING_R, width, colour)
    return finish(image)


def frame_double(outer, inner):
    image, d = canvas(FRAME_SIZE)
    ring(d, RING_R + 8, 7, outer)
    ring(d, RING_R - 10, 4, inner, 0.85)
    return finish(image)


def frame_studs(colour, accent, count=12):
    """A band with rivets — reads as armour at avatar size."""
    image, d = canvas(FRAME_SIZE)
    ring(d, RING_R, 13, colour)
    for i in range(count):
        dot(d, i * (360 / count), RING_R, 11, accent)
    return finish(image)


def frame_notched(colour, count=8):
    """A ring broken into segments, like a targeting reticle."""
    image, d = canvas(FRAME_SIZE)
    span = 360 / count
    for i in range(count):
        start = i * span + 6
        ring(d, RING_R, 12, colour, 1.0, start, start + span - 12)
    return finish(image)


# -- animated frames --------------------------------------------------------
#
# Each returns a list of frames. Every one loops seamlessly: the last frame leads back
# into the first, so nothing jumps.


def anim_orbit(colour, accent, dots=3):
    """A quiet base ring with points travelling around it."""
    out = []
    for f in range(LOOP_FRAMES):
        turn = 360 * f / LOOP_FRAMES
        image, d = canvas(FRAME_SIZE)
        ring(d, RING_R, 8, colour, 0.55)
        for i in range(dots):
            angle = turn + i * (360 / dots)
            # A short trail, so the dot reads as moving rather than teleporting.
            for t in range(5):
                dot(d, angle - t * 5, RING_R, 15 - t * 2, accent, 0.85 - t * 0.16)
            dot(d, angle, RING_R, 16, accent)
        out.append(finish(glow(image, 5)))
    return out


def anim_sweep(colour, accent):
    """A band of light running around the ring."""
    out = []
    for f in range(LOOP_FRAMES):
        turn = 360 * f / LOOP_FRAMES
        image, d = canvas(FRAME_SIZE)
        ring(d, RING_R, 10, colour, 0.45)
        # Built from short arcs of rising alpha rather than one arc, because a gradient
        # along an arc is not something the drawing primitives can express.
        for i in range(26):
            ring(d, RING_R, 10, accent, (i / 26) ** 2, turn + i * 4, turn + i * 4 + 5)
        out.append(finish(glow(image, 6)))
    return out


def anim_pulse(colour):
    """Breathing. The slowest of the set, for people who do not want to be shouted at."""
    out = []
    for f in range(LOOP_FRAMES):
        phase = math.sin(2 * math.pi * f / LOOP_FRAMES)
        image, d = canvas(FRAME_SIZE)
        ring(d, RING_R, 9 + phase * 2.5, colour, 0.75 + phase * 0.25)
        ring(d, RING_R + 13 + phase * 4, 3, colour, 0.3 + phase * 0.22)
        out.append(finish(glow(image, 7)))
    return out


def anim_spin_segments(colour, accent, count=6):
    """Two counter-rotating sets of segments."""
    out = []
    for f in range(LOOP_FRAMES):
        turn = 360 * f / LOOP_FRAMES
        image, d = canvas(FRAME_SIZE)
        span = 360 / count
        for i in range(count):
            a = turn + i * span
            ring(d, RING_R + 7, 6, colour, 0.9, a, a + span * 0.55)
            b = -turn + i * span
            ring(d, RING_R - 8, 5, accent, 0.8, b, b + span * 0.45)
        out.append(finish(glow(image, 4)))
    return out


def anim_ember(colour, accent, count=14):
    """Flecks rising and fading, like sparks off a fire."""
    out = []
    for f in range(LOOP_FRAMES):
        image, d = canvas(FRAME_SIZE)
        ring(d, RING_R, 9, colour, 0.8)
        for i in range(count):
            # Deterministic scatter: each fleck has its own phase, so they do not pulse
            # in unison, and the whole thing still loops exactly.
            phase = ((f / LOOP_FRAMES) + i / count) % 1.0
            angle = i * (360 / count) + phase * 26
            radius = RING_R + phase * 22
            dot(d, angle, radius, 9 * (1 - phase) + 3, accent, (1 - phase) * 0.9)
        out.append(finish(glow(image, 5)))
    return out


# =========================================================================
# Banners
# =========================================================================


def banner_canvas(top, bottom):
    """A vertical gradient base, dark enough that white text sits on it safely."""
    image = Image.new("RGBA", (BANNER_W * SCALE, BANNER_H * SCALE))
    d = ImageDraw.Draw(image)
    for y in range(BANNER_H * SCALE):
        t = y / (BANNER_H * SCALE)
        d.line(
            [(0, y), (BANNER_W * SCALE, y)],
            fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,),
        )
    return image, d


def banner_finish(image):
    return image.resize((BANNER_W, BANNER_H), Image.LANCZOS).convert("RGB")


def banner_grid(top, bottom, line, horizon=0.52):
    """Synthwave: a perspective grid running to a horizon."""
    image, d = banner_canvas(top, bottom)
    w, h = BANNER_W * SCALE, BANNER_H * SCALE
    hy = int(h * horizon)

    # Verticals converging on the vanishing point.
    for i in range(-24, 25):
        x = w / 2 + i * (w / 26)
        d.line([(x, h), (w / 2, hy)], fill=rgba(line, 0.5), width=px(0.7))
    # Horizontals, spaced so they crowd towards the horizon.
    for i in range(1, 18):
        y = hy + (h - hy) * (i / 18) ** 2.1
        d.line([(0, y), (w, y)], fill=rgba(line, 0.45), width=px(0.7))

    d.line([(0, hy), (w, hy)], fill=rgba(line, 0.9), width=px(2))
    return banner_finish(image)


def banner_hexes(top, bottom, line):
    """A honeycomb mesh, fading out towards the middle so a name stays readable."""
    image, d = banner_canvas(top, bottom)
    w, h = BANNER_W * SCALE, BANNER_H * SCALE
    r = px(34)
    dx, dy = r * 1.5, r * math.sqrt(3)

    x = 0.0
    col = 0
    while x < w + r:
        y = (dy / 2) if col % 2 else 0.0
        while y < h + r:
            # Alpha falls off towards the centre of the banner, which is where the
            # avatar and the username land.
            centre = abs(x - w / 2) / (w / 2)
            points = [
                (x + r * math.cos(math.radians(60 * k)), y + r * math.sin(math.radians(60 * k)))
                for k in range(6)
            ]
            d.polygon(points, outline=rgba(line, 0.14 + centre * 0.4), width=px(1))
            y += dy
        x += dx
        col += 1
    return banner_finish(image)


def banner_circuit(top, bottom, line, accent):
    """Traces and pads, like the back of a console."""
    image, d = banner_canvas(top, bottom)
    w, h = BANNER_W * SCALE, BANNER_H * SCALE

    # A deterministic walk: reproducible, and it wanders rather than looking generated.
    seed = 20260804
    for run in range(26):
        seed = (seed * 1103515245 + 12345) % (2**31)
        x = (seed % w)
        seed = (seed * 1103515245 + 12345) % (2**31)
        y = (seed % h)
        for _ in range(7):
            seed = (seed * 1103515245 + 12345) % (2**31)
            length = px(30) + seed % px(90)
            horizontal = (seed >> 5) % 2 == 0
            nx = x + (length if (seed >> 3) % 2 else -length) if horizontal else x
            ny = y if horizontal else y + (length if (seed >> 3) % 2 else -length)
            d.line([(x, y), (nx, ny)], fill=rgba(line, 0.5), width=px(1.6))
            x, y = max(0, min(w, nx)), max(0, min(h, ny))
        d.ellipse([x - px(5), y - px(5), x + px(5), y + px(5)], fill=rgba(accent, 0.85))
    return banner_finish(image)


def banner_scanlines(top, bottom, accent):
    """CRT: a bright rolling band over hard scanlines.

    The first attempt was a faint bloom behind evenly spaced lines and came out almost
    featureless — at banner size it read as a flat dark rectangle. What makes a CRT
    recognisable is the *roll*: one bright horizontal band, and scanlines dark enough to
    actually see.
    """
    image, d = banner_canvas(top, bottom)
    w, h = BANNER_W * SCALE, BANNER_H * SCALE

    bloom = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bd = ImageDraw.Draw(bloom)
    # The rolling band, low in the frame so a name in the middle stays clear.
    band = h * 0.72
    for i in range(40):
        t = i / 40
        bd.line([(0, band - h * 0.16 * t), (w, band - h * 0.16 * t)],
                fill=rgba(accent, 0.5 * (1 - t)), width=px(7))
    # A second, weaker one for depth.
    for i in range(24):
        t = i / 24
        bd.line([(0, h * 0.22 + h * 0.1 * t), (w, h * 0.22 + h * 0.1 * t)],
                fill=rgba(accent, 0.22 * (1 - t)), width=px(6))
    image = Image.alpha_composite(image, bloom.filter(ImageFilter.GaussianBlur(px(11))))

    d = ImageDraw.Draw(image)
    for y in range(0, h, px(5)):
        d.line([(0, y), (w, y)], fill=(0, 0, 0, 96), width=px(2.2))
    return banner_finish(image)


def banner_starfield(top, bottom, accent, other):
    """Deep space: stars, a drift of dust, and one bright body off to the side."""
    image, _ = banner_canvas(top, bottom)
    w, h = BANNER_W * SCALE, BANNER_H * SCALE

    # A nebula wash first, blurred hard so it is texture rather than shape.
    wash = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    wd = ImageDraw.Draw(wash)
    seed = 424242
    for i in range(14):
        seed = (seed * 1103515245 + 12345) % (2**31)
        x = seed % w
        seed = (seed * 1103515245 + 12345) % (2**31)
        y = seed % h
        seed = (seed * 1103515245 + 12345) % (2**31)
        r = px(60) + seed % px(150)
        wd.ellipse([x - r, y - r, x + r, y + r],
                   fill=rgba(accent if i % 2 else other, 0.16))
    image = Image.alpha_composite(image, wash.filter(ImageFilter.GaussianBlur(px(40))))

    d = ImageDraw.Draw(image)
    seed = 20260805
    for _ in range(220):
        seed = (seed * 1103515245 + 12345) % (2**31)
        x = seed % w
        seed = (seed * 1103515245 + 12345) % (2**31)
        y = seed % h
        seed = (seed * 1103515245 + 12345) % (2**31)
        r = px(0.7) + (seed % 100) / 100 * px(2.2)
        brightness = 0.35 + (seed % 65) / 100
        d.ellipse([x - r, y - r, x + r, y + r], fill=rgba((255, 255, 255), brightness))
    return banner_finish(image)


def banner_bokeh(top, bottom, accent, other):
    """Soft discs of light. The quietest one, and the one most faces sit well on."""
    image, _ = banner_canvas(top, bottom)
    w, h = BANNER_W * SCALE, BANNER_H * SCALE

    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    seed = 987654321
    for i in range(30):
        seed = (seed * 1103515245 + 12345) % (2**31)
        x = seed % w
        seed = (seed * 1103515245 + 12345) % (2**31)
        y = seed % h
        seed = (seed * 1103515245 + 12345) % (2**31)
        r = px(12) + seed % px(52)
        colour = accent if i % 3 else other
        ld.ellipse([x - r, y - r, x + r, y + r], fill=rgba(colour, 0.30))
    image = Image.alpha_composite(image, layer.filter(ImageFilter.GaussianBlur(px(7))))
    return banner_finish(image)


def banner_arena(top, bottom, line, accent):
    """Concentric arcs sweeping off one corner, like a HUD."""
    image, d = banner_canvas(top, bottom)
    w, h = BANNER_W * SCALE, BANNER_H * SCALE
    cx, cy = w * 0.12, h * 1.05

    for i in range(11):
        r = px(70) + i * px(52)
        d.arc([cx - r, cy - r, cx + r, cy + r],
              start=250, end=350, fill=rgba(line, 0.42 - i * 0.02), width=px(2.4))
    for i in range(4):
        r = px(96) + i * px(150)
        d.arc([cx - r, cy - r, cx + r, cy + r],
              start=268, end=316, fill=rgba(accent, 0.75), width=px(4.5))
    return banner_finish(image)


# =========================================================================

STATIC_FRAMES = {
    "frame-steel": lambda: frame_plain(SILVER),
    "frame-bronze": lambda: frame_double(BRONZE, GOLD),
    "frame-gold": lambda: frame_studs(GOLD, (255, 236, 178)),
    "frame-reticle": lambda: frame_notched(CYAN),
    "frame-brand": lambda: frame_double(BRAND, (255, 190, 200)),
}

ANIMATED_FRAMES = {
    "frame-orbit": lambda: anim_orbit(CYAN, (200, 250, 255)),
    "frame-sweep": lambda: anim_sweep(VIOLET, (235, 215, 255)),
    "frame-pulse": lambda: anim_pulse(MINT),
    "frame-rotor": lambda: anim_spin_segments(BRAND, GOLD),
    "frame-ember": lambda: anim_ember(EMBER, (255, 214, 130)),
    "frame-toxic": lambda: anim_orbit(LIME, (225, 255, 180), dots=4),
}

BANNERS = {
    "banner-synthwave": lambda: banner_grid((38, 18, 62), (96, 26, 96), (255, 90, 200)),
    "banner-hex": lambda: banner_hexes((16, 32, 44), (10, 18, 28), CYAN),
    "banner-circuit": lambda: banner_circuit((12, 24, 20), (8, 14, 14), MINT, LIME),
    "banner-crt": lambda: banner_scanlines((18, 18, 26), (10, 10, 16), (120, 200, 255)),
    "banner-bokeh": lambda: banner_bokeh((30, 16, 40), (16, 12, 30), VIOLET, BRAND),
    "banner-arena": lambda: banner_arena((26, 12, 16), (14, 10, 14), BRAND, GOLD),
    "banner-void": lambda: banner_starfield((14, 16, 34), (6, 6, 16), VIOLET, CYAN),
    "banner-dusk": lambda: banner_grid((44, 24, 16), (18, 14, 24), EMBER, horizon=0.58),
}


def main() -> None:
    FRAMES_DIR.mkdir(parents=True, exist_ok=True)
    BANNERS_DIR.mkdir(parents=True, exist_ok=True)

    print("Frames (static)")
    for name, build in STATIC_FRAMES.items():
        build().save(FRAMES_DIR / f"{name}.png")
        print(f"  {name}.png")

    print("Frames (animated)")
    for name, build in ANIMATED_FRAMES.items():
        frames = build()
        path = FRAMES_DIR / f"{name}.webp"
        # Lossless, because a ring of thin bright arcs over transparency is exactly what
        # lossy WebP smears — and the alpha edge is the whole product.
        # Lossy with alpha, not lossless. Lossless was the instinct — thin bright arcs
        # over transparency is exactly what lossy smears — but it produced 2MB rings, and
        # at 96px on a phone the difference is invisible while the download is not.
        # method=6 is the slowest, smallest setting; this runs once, offline.
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
        print(f"  {name}.webp  ({path.stat().st_size // 1024}KB, {len(frames)} frames)")

    print("Banners")
    for name, build in BANNERS.items():
        build().save(BANNERS_DIR / f"{name}.jpg", quality=88, optimize=True)
        print(f"  {name}.jpg")

    contact_sheets()


def contact_sheets() -> None:
    """Two sheets to eyeball the sets as sets. Local only; never uploaded."""
    names = list(STATIC_FRAMES) + list(ANIMATED_FRAMES)
    cols = 6
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGB", (FRAME_SIZE * cols, FRAME_SIZE * rows), (32, 33, 40))
    for i, name in enumerate(names):
        path = FRAMES_DIR / f"{name}.png"
        if not path.exists():
            path = FRAMES_DIR / f"{name}.webp"
        item = Image.open(path).convert("RGBA")
        # On a mid-grey disc, standing in for an avatar — a frame judged against nothing
        # is not being judged at all.
        cell = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (32, 33, 40, 255))
        ImageDraw.Draw(cell).ellipse(
            [FRAME_SIZE * 0.16, FRAME_SIZE * 0.16, FRAME_SIZE * 0.84, FRAME_SIZE * 0.84],
            fill=(96, 102, 122, 255),
        )
        cell = Image.alpha_composite(cell, item)
        sheet.paste(cell.convert("RGB"), ((i % cols) * FRAME_SIZE, (i // cols) * FRAME_SIZE))
    sheet.save(OUT / "frames-sheet.png")

    banner_sheet = Image.new("RGB", (BANNER_W // 2 * 2, BANNER_H // 2 * len(BANNERS) // 2))
    for i, name in enumerate(BANNERS):
        item = Image.open(BANNERS_DIR / f"{name}.jpg").resize((BANNER_W // 2, BANNER_H // 2))
        banner_sheet.paste(item, ((i % 2) * (BANNER_W // 2), (i // 2) * (BANNER_H // 2)))
    banner_sheet.save(OUT / "banners-sheet.png")
    print("\ncontact sheets written")


if __name__ == "__main__":
    main()
