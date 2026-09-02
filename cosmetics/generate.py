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
middle competes with the person whose profile it is. Dark enough for white text,
interesting at the edges, quiet where the avatar lands.

There are two sets of them. The first is abstract texture; the second says a game *genre*
— a platformer skyline, a torchlit wall, keycaps, a pad, a kerb, a minimap, dice, a closing
circle. Nothing in the second set is taken from an actual game: no logos, characters,
sprites or console outlines, only generic shapes drawn from primitives here. These are sold
for coins, which makes that a licensing rule before it is a taste one.

Both are drawn at 4x and downsampled — Pillow does not anti-alias its shape primitives,
and on a ring made of arcs that is the difference between jewellery and a sawblade.
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

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

#: Animated banners render at half the still banners' resolution.
#:
#: The largest place a banner is drawn is the profile header at 96dp, which is about 288
#: physical pixels on a 3x phone — so a 200px-tall source is upscaled roughly 1.4x by
#: `contentFit="cover"`. On art made of gradients, blurs and sub-pixel stars that is
#: invisible, and it costs a quarter of the pixels. A 22-frame loop at the full 1200x400
#: lands in the megabytes; this keeps it inside the same budget the frames live under.
ANIM_BANNER_W, ANIM_BANNER_H = 600, 200

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

# Added with the second drop. Gold is deliberately absent from all of it: gold is the
# membership signal, and a bought frame that reads as Gold devalues the one that is not
# for sale.
# A slate, not the near-white this started as. Frames are composited over `raised`, which
# is #1E1E2D in the dark theme and #EFEFF6 in the light one, so a near-white ring is
# invisible on half the installs — Scope was a blank disc in light mode and Glitch was blank
# for the sixteen frames of its loop that do not tear. This sits far enough from both
# grounds to read on either.
ARCTIC = (128, 144, 176)
ICE = (190, 224, 255)      # the cold end, against EMBER's warm one
GRAPHITE = (44, 46, 56)    # a dark body to carry bright markings


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


def ring(d, radius, width, colour, alpha=1.0, start=0, end=360, dx=0.0):
    """An arc of the main ring, centred on the canvas.

    `dx` shifts the whole arc sideways. Only Glitch uses it, to lay a red and a cyan copy
    either side of the ring — the chromatic split every broken display has.
    """
    c = FRAME_SIZE / 2
    box = [px(c - radius + dx), px(c - radius), px(c + radius + dx), px(c + radius)]
    d.arc(box, start=start, end=end, fill=rgba(colour, alpha), width=px(width))


def dot(d, angle_deg, radius, size, colour, alpha=1.0):
    c = FRAME_SIZE / 2
    a = math.radians(angle_deg)
    x, y = c + math.cos(a) * radius, c + math.sin(a) * radius
    d.ellipse(
        [px(x - size / 2), px(y - size / 2), px(x + size / 2), px(y + size / 2)],
        fill=rgba(colour, alpha),
    )


def polar(angle_deg, radius):
    """A point on the circle, already scaled for drawing."""
    c = FRAME_SIZE / 2
    a = math.radians(angle_deg)
    return (px(c + math.cos(a) * radius), px(c + math.sin(a) * radius))


def tick(d, angle_deg, r0, r1, width, colour, alpha=1.0):
    """A radial line between two radii — the mark on an instrument bezel."""
    d.line([polar(angle_deg, r0), polar(angle_deg, r1)], fill=rgba(colour, alpha), width=px(width))


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
    """A band with a brighter inlay along its inner edge. Bronze and Signature.

    **The two rings have to touch.** `ImageDraw.arc` grows its width *inwards* from the
    radius it is given, so the original `RING_R + 8` band ran 112.9–119.9 while the inlay
    ran 97.9–101.9 — eleven pixels of nothing between them, and the inlay itself all but
    swallowed by the hole punch at 99.8. On a 64dp shelf thumbnail that passed for a thin
    double ring; at preview size it read as a hoop suspended around the picture with a gap
    where the frame should meet it, which is exactly the complaint.

    Now the band ends where the inlay begins, and the inlay ends on the avatar's rim, so
    the whole thing sits on the portrait the way every other frame in the set does.
    """
    image, d = canvas(FRAME_SIZE)
    ring(d, RING_R + 5, 12, outer)      # 104.9 → 116.9
    ring(d, RING_R - 8, 4, inner, 0.9)  # 99.9 → 103.9, flush to the hole
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


def frame_dashed(colour, count=36):
    """Many short dashes and an inner hairline.

    Reticle is already a broken ring, so this had to break it differently: eight fat
    segments read as a gunsight, thirty-six thin ones read as a bezel with graduations on
    it. The unbroken hairline inside is what stops the dashes looking like a ring that
    failed to render.
    """
    image, d = canvas(FRAME_SIZE)
    span = 360 / count
    for i in range(count):
        ring(d, RING_R, 9, colour, 1.0, i * span, i * span + span * 0.55)
    ring(d, RING_R - 9, 2, colour, 0.45)
    return finish(image)


def frame_scope(colour, accent):
    """A thin ring with instrument ticks, four of them in the accent.

    The four long marks are on the cardinals, where a scope puts them, and they run right
    down to the avatar's edge — `finish()` cuts them flush, which is why they can be drawn
    past the hole without any arithmetic here.
    """
    image, d = canvas(FRAME_SIZE)
    ring(d, RING_R, 4, colour, 0.95)
    ring(d, RING_R + 11, 1.5, colour, 0.35)

    for a in range(0, 360, 90):
        tick(d, a, HOLE_R + 2, RING_R + 13, 5, accent)
    for a in range(0, 360, 30):
        if a % 90:
            tick(d, a, RING_R - 6, RING_R + 6, 2.5, colour)
    return finish(image)


def frame_hazard(base, stripe, count=24):
    """A caution band: slanted stripes on a dark body.

    Orange on graphite rather than the usual yellow on black. Yellow at this size is gold,
    and gold means membership — the one thing a 350-coin frame must not claim to be.

    The stripes are drawn as thick lines between two points either side of the band, which
    is what makes them lean; a stripe drawn radially would read as a tick mark instead.
    """
    image, d = canvas(FRAME_SIZE)
    ring(d, RING_R, 14, base)

    # The stripes are drawn long and then cut to the band by a ring-shaped mask. Drawing
    # them short instead leaves the ends of a slanted line sticking out past the band at
    # both ends, which at 64dp reads as a cog rather than as a caution stripe.
    stripes = Image.new("RGBA", image.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(stripes)
    for i in range(count):
        a = i * (360 / count)
        sd.line([polar(a - 5, RING_R - 11), polar(a + 5, RING_R + 11)],
                fill=rgba(stripe, 0.92), width=px(7))

    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).ellipse(
        [px(FRAME_SIZE / 2 - RING_R - 7), px(FRAME_SIZE / 2 - RING_R - 7),
         px(FRAME_SIZE / 2 + RING_R + 7), px(FRAME_SIZE / 2 + RING_R + 7)], fill=255)
    ImageDraw.Draw(mask).ellipse(
        [px(FRAME_SIZE / 2 - RING_R + 7), px(FRAME_SIZE / 2 - RING_R + 7),
         px(FRAME_SIZE / 2 + RING_R - 7), px(FRAME_SIZE / 2 + RING_R - 7)], fill=0)
    stripes.putalpha(Image.composite(stripes.getchannel("A"), Image.new("L", image.size, 0), mask))
    image = Image.alpha_composite(image, stripes)
    return finish(image)


def frame_hex(edge, plate, band=9):
    """A hexagonal rim around a round avatar.

    The only frame in the set that is not a circle, which is the entire point of it — at
    thumbnail size the silhouette is what distinguishes it, not the colour.

    Two polygons, the inner one filled with a translucent plate rather than cleared: the
    corners between a round avatar and a hex rim are dead space, and tinting them is what
    makes this read as a badge with a picture in it instead of a ring that went wrong.
    The inner apothem is 103, comfortably outside HOLE_R at 100, so the avatar is never
    touched.
    """
    image, d = canvas(FRAME_SIZE)
    outer_v = 126.0
    inner_v = outer_v - band / math.cos(math.radians(30))

    def hexagon(vertex_r):
        return [polar(30 + 60 * k, vertex_r) for k in range(6)]

    d.polygon(hexagon(outer_v), fill=rgba(edge, 1.0))
    d.polygon(hexagon(inner_v), fill=rgba(plate, 0.55))
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


def anim_frost(colour, accent, count=12):
    """Flecks drifting inward and melting. Ember's cold twin.

    Ember throws sparks outward and they fade as they go; this pulls flecks in and fades
    them at both ends, so the two read as opposites rather than as a recolour. The fixed
    crystal ticks are what stops it looking like Ember-in-blue while nothing is moving.
    """
    out = []
    for f in range(LOOP_FRAMES):
        image, d = canvas(FRAME_SIZE)
        ring(d, RING_R, 9, colour, 0.85)
        for a in range(30, 360, 60):
            tick(d, a, RING_R + 5, RING_R + 13, 2, accent, 0.6)
        for i in range(count):
            phase = ((f / LOOP_FRAMES) + i / count) % 1.0
            angle = i * (360 / count) + 11 - phase * 18
            radius = RING_R + 15 - phase * 24
            # Fades in and out again, so a fleck never pops into or out of existence.
            dot(d, angle, radius, 3 + 6 * phase, accent, 0.9 * math.sin(math.pi * phase))
        # glow(4), and no outer haze ring: both were blurred gradients that changed on
        # every frame, and together they cost 100KB of inter-frame delta for something
        # nobody can see at 64dp. This is the one item in the set that had to be tuned for
        # bytes rather than for looks.
        out.append(finish(glow(image, 4)))
    return out


def anim_radar(colour, accent, blips=(37, 121, 190, 262, 318)):
    """A sweep with contacts that light up as it passes them.

    Sweep already runs a band of light around the ring, so this had to sweep something
    else: a wedge with depth, drawn as pieslices from the centre and then cut back to a
    band by `finish()`. That gives a mark about twice the width of Sweep's arc, which is
    the difference between "light travelling" and "something scanning".

    The blips are the actual idea. A radar with nothing on it is a rotating line.
    """
    out = []
    for f in range(LOOP_FRAMES):
        turn = 360 * f / LOOP_FRAMES
        image, d = canvas(FRAME_SIZE)
        ring(d, RING_R, 7, colour, 0.4)

        c = FRAME_SIZE / 2
        r = RING_R + 10
        box = [px(c - r), px(c - r), px(c + r), px(c + r)]
        for k in range(36):
            a = turn - k * 2.5
            d.pieslice(box, start=a - 2.6, end=a, fill=rgba(colour, 0.85 * (1 - k / 36) ** 1.6))
        tick(d, turn, HOLE_R, RING_R + 12, 3, accent)

        for a in blips:
            since = (turn - a) % 360
            dot(d, a, RING_R + 3, 8, accent, max(0.0, 1 - since / 140) ** 2)
        out.append(finish(glow(image, 5)))
    return out


def anim_comet(base, tail, head):
    """One bright head dragging a tail that thins as it goes.

    Orbit's dots have a five-step trail of equal-width dots; this tapers *width* as well as
    alpha over forty steps and most of the circle, which is what makes it read as one
    object moving fast rather than several objects in a row.
    """
    out = []
    for f in range(LOOP_FRAMES):
        turn = 360 * f / LOOP_FRAMES
        image, d = canvas(FRAME_SIZE)
        ring(d, RING_R, 6, base, 0.28)

        for k in range(40):
            t = k / 40
            a = turn - k * (170 / 40)
            # ^1.3 rather than ^2.2: the steeper falloff put the whole tail inside the
            # first thirty degrees, so at rest it read as a dot on a grey ring.
            ring(d, RING_R, 13 * (1 - t) + 2, tail, 0.95 * (1 - t) ** 1.3, a - 4.5, a)

        dot(d, turn, RING_R, 26, head, 0.35)
        dot(d, turn, RING_R, 17, base)

        for i in range(6):
            phase = ((f / LOOP_FRAMES) + i / 6) % 1.0
            dot(d, turn - 20 - phase * 120, RING_R + (10 if i % 2 else -8) * phase,
                4 * (1 - phase), head, 0.8 * (1 - phase))
        out.append(finish(glow(image, 5)))
    return out


def anim_glitch(colour, red, cyan, broken=(2, 3, 9, 10, 11, 17)):
    """A clean ring that tears for a few frames at a time.

    Every other animation here is continuous motion. This one is mostly *still* — sixteen
    of the twenty-two frames are the same plain ring, drawn identically, which is what
    makes the six torn ones land as an interruption rather than as a texture. It also
    keeps the file small: identical frames cost almost nothing to encode.

    Frame 21 and frame 0 are both calm, so the loop closes without a seam even though the
    middle of it does not move smoothly at all.
    """
    out = []
    for f in range(LOOP_FRAMES):
        image, d = canvas(FRAME_SIZE)

        # The fringe is always there, faintly. Without it the sixteen calm frames are a
        # plain white ring — which is Steel, the free one, and an item that spends most of
        # its loop impersonating the giveaway is a bad thing to charge 1300 coins for. At
        # rest this now reads as a slightly misconverged display; the torn frames are the
        # same fault getting worse.
        ring(d, RING_R, 9, red, 0.22, dx=-1.5)
        ring(d, RING_R, 9, cyan, 0.22, dx=1.5)

        if f in broken:
            ring(d, RING_R, 9, red, 0.7, dx=-3)
            ring(d, RING_R, 9, cyan, 0.7, dx=3)

        ring(d, RING_R, 9, colour, 0.9)

        if f in broken:
            seed = f * 7919
            for _ in range(4):
                seed = (seed * 1103515245 + 12345) % (2**31)
                i = seed % 24
                start = i * 15
                # Cleared, not painted over: this is a pixel replace on an RGBA canvas, so
                # a fully transparent arc punches the segment out of the ring.
                ring(d, RING_R, 11, (0, 0, 0), 0.0, start, start + 15)
                if (seed >> 7) % 2:
                    offset = 5 if (seed >> 9) % 2 else -5
                    ring(d, RING_R + offset, 9, colour, 0.9, start, start + 15)
        out.append(finish(image))
    return out


def anim_pulsar(colour, accent):
    """Deep Space's frame: a beacon sending pulses outward.

    Two wavefronts run at once, half a period apart, so there is never a moment with
    nothing travelling — and because the second is exactly the first shifted by 0.5, the
    loop closes without either of them jumping.

    The pulses expand *outward* past the ring, where Frost's flecks drift inward. That is
    the whole distinction between them at a glance, and it is why this one had to have a
    quiet base ring: two moving rings plus a bright band would be soup at 64dp.
    """
    out = []
    for f in range(LOOP_FRAMES):
        image, d = canvas(FRAME_SIZE)
        ring(d, RING_R, 7, colour, 0.55)

        # 13px of travel, not 20. Every pixel a wavefront crosses is a pixel that differs
        # from the previous frame, and an inter-frame codec pays for exactly that: the
        # longer reach cost 429KB against a 380KB band for a difference nobody can see on
        # a 64dp ring.
        for offset in (0.0, 0.5):
            phase = ((f / LOOP_FRAMES) + offset) % 1.0
            ring(d, RING_R + phase * 13, 3, accent, (1 - phase) ** 1.6 * 0.75)

        # Two opposed beams, so it reads as something rotating and emitting rather than
        # as a ring that happens to throb.
        turn = 360 * f / LOOP_FRAMES
        for i in range(2):
            angle = turn + i * 180
            for t in range(3):
                dot(d, angle - t * 6, RING_R, 13 - t * 3, accent, 0.75 - t * 0.25)
            dot(d, angle, RING_R, 14, (255, 255, 255))
        # glow(4), not 6: a wide blur over two travelling wavefronts changes every pixel
        # of every frame, and that is exactly what an inter-frame codec cannot compress.
        # At 6 this loop was 430KB; the difference is invisible at 64dp.
        out.append(finish(glow(image, 4)))
    return out


def anim_reactor(base, stripe, count=24):
    """Meltdown's frame: Hazard, turning.

    Deliberately the same construction as `frame_hazard` — the masked slanted stripes on a
    graphite band — rotated by exactly one stripe period over the loop so it closes. The
    two are meant to be recognisably related: Hazard is the warning sign, this is the
    warning sign spinning, and Meltdown's banner shares the palette.
    """
    out = []
    span = 360 / count
    for f in range(LOOP_FRAMES):
        turn = span * f / LOOP_FRAMES
        image, d = canvas(FRAME_SIZE)
        ring(d, RING_R, 14, base)

        stripes = Image.new("RGBA", image.size, (0, 0, 0, 0))
        sd = ImageDraw.Draw(stripes)
        for i in range(count):
            a = turn + i * span
            sd.line([polar(a - 5, RING_R - 11), polar(a + 5, RING_R + 11)],
                    fill=rgba(stripe, 0.92), width=px(7))

        mask = Image.new("L", image.size, 0)
        md = ImageDraw.Draw(mask)
        c = FRAME_SIZE / 2
        md.ellipse([px(c - RING_R - 7), px(c - RING_R - 7),
                    px(c + RING_R + 7), px(c + RING_R + 7)], fill=255)
        md.ellipse([px(c - RING_R + 7), px(c - RING_R + 7),
                    px(c + RING_R - 7), px(c + RING_R - 7)], fill=0)
        stripes.putalpha(Image.composite(stripes.getchannel("A"),
                                         Image.new("L", image.size, 0), mask))
        image = Image.alpha_composite(image, stripes)

        # The core, breathing under the band.
        d = ImageDraw.Draw(image)
        ring(d, RING_R - 9, 2, stripe, 0.5 + 0.3 * math.sin(2 * math.pi * f / LOOP_FRAMES))
        out.append(finish(glow(image, 4)))
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


def anim_banner_canvas(top, bottom):
    """`banner_canvas` at the animated banners' smaller canvas."""
    w, h = ANIM_BANNER_W * SCALE, ANIM_BANNER_H * SCALE
    image = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(image)
    for y in range(h):
        t = y / h
        d.line(
            [(0, y), (w, y)],
            fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,),
        )
    return image, d


def anim_banner_finish(image):
    return image.resize((ANIM_BANNER_W, ANIM_BANNER_H), Image.LANCZOS).convert("RGB")


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


def banner_laurel(top, bottom, accent, glow):
    """Slow diagonal rays with a warm bloom, for the membership banner.

    Deliberately the quietest thing in the set. This one is not chosen — it arrives with a
    subscription and stays on somebody's profile for as long as they keep it, so it has to
    survive being looked at for a month. The rest of the banners can afford to be loud
    because they were picked on purpose; a reward that shouts gets tiring and then gets
    swapped out, which defeats the point of giving it away.

    No crown, no laurel drawn literally. Gold reads as gold from the palette alone, and a
    badge shape behind a profile header would fight the avatar sitting on top of it.
    """
    image, d = banner_canvas(top, bottom)
    w, h = BANNER_W * SCALE, BANNER_H * SCALE

    # Rays on their own layer so the whole fan can be blurred at once. Drawn sharp and
    # then softened is not the same as drawn soft: overlapping thin lines accumulate into
    # a gradient this way, which is what makes it read as light rather than as stripes.
    rays = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    rd = ImageDraw.Draw(rays)
    origin_x, origin_y = -w * 0.20, h * 0.42

    for i in range(9):
        angle = math.radians(-26 + i * 6.4)
        end = (origin_x + math.cos(angle) * w * 1.6, origin_y + math.sin(angle) * w * 1.6)
        rd.line([origin_x, origin_y, end[0], end[1]], fill=rgba(accent, 0.30), width=px(6))

    rays = rays.filter(ImageFilter.GaussianBlur(px(9)))
    image.alpha_composite(rays)

    # The bloom where they converge, well off the left edge. Large and very soft — this is
    # the whole reason the banner reads as warm rather than as a set of lines.
    bloom = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bd = ImageDraw.Draw(bloom)
    r = px(260)
    bd.ellipse([origin_x - r, origin_y - r, origin_x + r, origin_y + r], fill=rgba(glow, 0.55))
    bloom = bloom.filter(ImageFilter.GaussianBlur(px(90)))
    image.alpha_composite(bloom)

    # One hairline low enough to stay clear of the avatar and the name. The only hard
    # edge in the image, and it is there so the eye has something to land on.
    d.line([0, h * 0.84, w, h * 0.74], fill=rgba(accent, 0.48), width=px(2))
    return banner_finish(image)


# -- game-genre banners -----------------------------------------------------
#
# The second banner drop is about *games* rather than abstract texture, because the first
# set could have belonged to any product. Each of these says a genre — platformer, RPG, PC,
# console, racing, MOBA, tabletop, battle royale — using the conventions of the genre and
# nothing else.
#
# Nothing here is drawn from any real game. No logos, no characters, no sprites anybody
# owns, no console outlines, no recognisable maps: every shape is a generic one that any
# game in the genre could have, drawn from primitives like the rest of this file. That is a
# licensing rule first and a taste one second — a banner is on somebody's profile in a shop
# we charge coins for, which is exactly where borrowed art becomes a legal problem.
#
# The old rule still holds on top of that: the middle stays quiet, because a name and an
# avatar sit on it.


def lcg(seed):
    """The same deterministic walk the older banners use, as a generator."""
    while True:
        seed = (seed * 1103515245 + 12345) % (2**31)
        yield seed


def banner_overworld(top, bottom, hill, star, sun):
    """Platformer: a blocky parallax skyline.

    Drawn in 12px blocks rather than smooth curves, because the stepping *is* the reference
    — a smooth silhouette would just be a landscape, and the whole genre cue is that the
    landscape has pixels.
    """
    image, d = banner_canvas(top, bottom)
    w, h = BANNER_W * SCALE, BANNER_H * SCALE
    block = px(12)

    rnd = lcg(20260831)
    for star_i in range(40):
        x = (next(rnd) % (w // block)) * block
        y = (next(rnd) % int(h * 0.45 / block)) * block
        if abs(x - w / 2) < w * 0.16:
            continue
        d.rectangle([x, y, x + block, y + block], fill=rgba(star, 0.35 + (star_i % 5) * 0.1))

    # A pixel sun, far enough left to be out of the way of anything centred.
    cx, cy, r = w * 0.11, h * 0.24, px(46)
    for gy in range(int((cy - r) // block), int((cy + r) // block) + 1):
        for gx in range(int((cx - r) // block), int((cx + r) // block) + 1):
            x, y = gx * block, gy * block
            if math.hypot(x + block / 2 - cx, y + block / 2 - cy) <= r:
                d.rectangle([x, y, x + block, y + block], fill=rgba(sun, 0.5))

    # Two ridges. The far one is lighter and taller-shouldered; the near one is darker and
    # sits lower, which is the whole of the parallax.
    for layer, (amp, base, shade, alpha) in enumerate(
        ((0.16, 0.66, hill, 0.55), (0.11, 0.80, (hill[0] // 2, hill[1] // 2, hill[2] // 2), 0.95))
    ):
        for gx in range(0, w // block + 1):
            x = gx * block
            u = x / w
            top_y = h * base - h * amp * (
                0.5 + 0.5 * math.sin(u * (7.0 + layer * 3) + layer * 2.1)
            ) * (0.55 + 0.45 * math.sin(u * (2.3 + layer)))
            top_y = (int(top_y) // block) * block
            d.rectangle([x, top_y, x + block, h], fill=rgba(shade, alpha))

    return banner_finish(image)


def banner_dungeon(top, bottom, mortar, torch):
    """RPG: a torchlit stone wall.

    The vignette is doing the real work. Bricks tile evenly by nature, and an evenly tiled
    wall behind a profile header is a busy grey rectangle; darkening the middle turns it
    into a room with two torches at the far ends of it.
    """
    image, d = banner_canvas(top, bottom)
    w, h = BANNER_W * SCALE, BANNER_H * SCALE
    bw, bh = px(60), px(28)

    rnd = lcg(776610)
    row = 0
    y = 0
    while y < h + bh:
        offset = (bw // 2) if row % 2 else 0
        x = -offset
        while x < w + bw:
            shade = next(rnd) % 13 - 6
            face = tuple(max(0, min(255, top[i] + shade + 6)) for i in range(3))
            d.rectangle([x + px(1.5), y + px(1.5), x + bw - px(1.5), y + bh - px(1.5)],
                        fill=rgba(face, 1.0))
            x += bw
        y += bh
        row += 1

    # Mortar reasserted over the whole wall, so the joints are one colour rather than
    # whatever each brick happened to leave behind.
    for gy in range(0, h + bh, bh):
        d.line([(0, gy), (w, gy)], fill=rgba(mortar, 0.85), width=px(3))

    # Three torches, not two. The shop lists this at 96x64dp, which crops to the middle
    # half of the width — so a wall lit only at its far edges arrives in the shop as a
    # black rectangle, which is what it did. The middle one is dimmer and higher so the
    # wall still reads as lit from the sides on a profile, where the whole width shows.
    glow_layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow_layer)
    for tx, ty, strength in ((w * 0.06, 0.45, 1.0), (w * 0.94, 0.45, 1.0), (w * 0.50, 0.30, 0.5)):
        r = px(90 * (1.0 if strength == 1.0 else 1.3))
        gd.ellipse([tx - r, h * ty - r, tx + r, h * ty + r], fill=rgba(torch, 0.55 * strength))
        gd.ellipse([tx - r / 3, h * ty - r / 3, tx + r / 3, h * ty + r / 3],
                   fill=rgba((255, 235, 190), 0.7 * strength))
    image = Image.alpha_composite(image, glow_layer.filter(ImageFilter.GaussianBlur(px(50))))

    # A gentle vignette, not a black band. It was 0.42 and swallowed the middle: the name
    # on a profile sits *below* the banner, not on it, so there is far less to protect here
    # than the older banners in this file assume.
    shade_layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shade_layer)
    for x in range(0, w, px(4)):
        t = 1 - min(1.0, abs(x - w / 2) / (w * 0.42))
        sd.rectangle([x, 0, x + px(4), h], fill=rgba((0, 0, 0), 0.16 * t))
    image = Image.alpha_composite(image, shade_layer)
    return banner_finish(image)


def keycap(d, x, y, size, rim, face, alpha=1.0):
    """One blank keycap: an outer skirt with an inset top face.

    The inset is not decoration. A plain rounded square is a block, and four blocks in a
    cluster are a tetromino — which is both the wrong genre and somebody else's shape. The
    step from skirt to face is the one detail that says "key" without a letter on it, and
    no letter means no typeface licence to worry about either.
    """
    inset = size * 0.16
    d.rounded_rectangle([x, y, x + size, y + size], radius=px(7),
                        fill=rgba(face, 0.06 * alpha), outline=rgba(rim, 0.40 * alpha),
                        width=px(1.5))
    d.rounded_rectangle([x + inset, y + inset * 0.7, x + size - inset, y + size - inset * 1.4],
                        radius=px(4), fill=rgba(face, 0.12 * alpha),
                        outline=rgba(rim, 0.60 * alpha), width=px(1.5))


def banner_wasd(top, bottom, backlight, rim):
    """PC: the movement keys, backlit.

    Four caps in the WASD arrangement are recognisable to anyone who plays on a keyboard,
    and the arrangement alone does it — no letters are drawn. The right-hand row is blank
    caps fading out, so the banner has weight at both edges and nothing in the centre.
    """
    image, d = banner_canvas(top, bottom)
    w, h = BANNER_W * SCALE, BANNER_H * SCALE
    size = px(70)
    gap = px(10)

    under = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ud = ImageDraw.Draw(under)
    left = w * 0.06
    row_y = h * 0.56
    ud.rounded_rectangle([left - gap, row_y - size - gap * 2, left + size * 3 + gap * 3, row_y + size + gap],
                         radius=px(14), fill=rgba(backlight, 0.28))
    image = Image.alpha_composite(image, under.filter(ImageFilter.GaussianBlur(px(26))))

    d = ImageDraw.Draw(image)
    # Staggered, like the rows of an actual keyboard: the home row sits a quarter-key to
    # the right of the one above it. Square-on-square is the tetromino silhouette again,
    # and the offset is both more accurate and the thing that breaks it.
    stagger = size * 0.28
    keycap(d, left + size + gap - stagger, row_y - size - gap, size, rim, backlight)   # W
    for i in range(3):                                                                 # A S D
        keycap(d, left + i * (size + gap), row_y, size, rim, backlight)

    # A blank row across the middle, brightening to the right. It started at 0.62w, which
    # put every cap outside the middle half the shop thumbnail crops to — the item was
    # listed as an almost empty rectangle. Beginning at 0.38w it reads in both places.
    right = w * 0.38
    for i in range(6):
        keycap(d, right + i * (size + gap), h * 0.30, size, rim, backlight, alpha=0.28 + i * 0.13)
    return banner_finish(image)


def banner_gamepad(top, bottom, outline, buttons):
    """Console: a generic controller and a diamond of face buttons.

    Deliberately nobody's controller. Two grips, a d-pad cross and two stick rings is the
    shape every pad has had for thirty years; the proportions here are drawn to be
    obviously generic, and both clusters are cut off by the banner edge so neither reads as
    a product shot.
    """
    image, d = banner_canvas(top, bottom)
    w, h = BANNER_W * SCALE, BANNER_H * SCALE
    # Clear of the far left. The avatar is pulled up over the banner's bottom-left corner
    # (see `useProfileHeaderLayout`), and at 0.10w the pad sat directly behind it — the one
    # recognisable object in the image, hidden by the one thing guaranteed to be on top of
    # it. Nothing else lands on a banner, so the middle is free to hold it.
    cx, cy = w * 0.32, h * 0.52
    lw = px(3)

    # One silhouette rather than a body with two circles stuck on it: the grips are drawn
    # as part of the outline by overlapping three rounded rectangles on a single mask, so
    # what survives is a pad shape instead of the diagram of one.
    pad = Image.new("L", (w, h), 0)
    pd = ImageDraw.Draw(pad)
    body_w, body_h = px(300), px(120)
    pd.rounded_rectangle([cx - body_w / 2, cy - body_h / 2, cx + body_w / 2, cy + body_h / 2],
                         radius=px(46), fill=255)
    for side in (-1, 1):
        gx = cx + side * body_w * 0.30
        grip = Image.new("L", (w, h), 0)
        ImageDraw.Draw(grip).rounded_rectangle(
            [gx - px(46), cy - px(20), gx + px(46), cy + px(128)], radius=px(44), fill=255)
        grip = grip.rotate(side * 14, resample=Image.BILINEAR, center=(gx, cy))
        pad = ImageChops.lighter(pad, grip)

    edge = pad.filter(ImageFilter.FIND_EDGES).filter(ImageFilter.MaxFilter(3))
    silhouette = Image.new("RGBA", (w, h), rgba(outline, 0.75))
    silhouette.putalpha(ImageChops.multiply(edge, Image.new("L", (w, h), 190)))
    image.alpha_composite(silhouette)

    d = ImageDraw.Draw(image)
    arm, thick = px(38), px(14)
    dx, dy = cx - px(78), cy - px(6)
    d.rounded_rectangle([dx - arm, dy - thick, dx + arm, dy + thick], radius=px(4),
                        outline=rgba(outline, 0.7), width=lw)
    d.rounded_rectangle([dx - thick, dy - arm, dx + thick, dy + arm], radius=px(4),
                        outline=rgba(outline, 0.7), width=lw)
    for sx, sy in ((cx + px(4), cy + px(28)), (cx + px(86), cy + px(28))):
        d.ellipse([sx - px(26), sy - px(26), sx + px(26), sy + px(26)],
                  outline=rgba(outline, 0.7), width=lw)
        d.ellipse([sx - px(11), sy - px(11), sx + px(11), sy + px(11)],
                  outline=rgba(outline, 0.45), width=px(2))

    # The face buttons on their own, large, at the far right. Rings rather than filled
    # discs, and no letters or symbols inside them.
    bx, by, spread, r = w * 0.90, h * 0.42, px(62), px(26)
    for i, (ox, oy) in enumerate(((0, -spread), (spread, 0), (0, spread), (-spread, 0))):
        d.ellipse([bx + ox - r, by + oy - r, bx + ox + r, by + oy + r],
                  outline=rgba(buttons[i], 0.6), width=px(4))
    return banner_finish(image)


def banner_chicane(top, bottom, edge, kerb):
    """Racing: a curve with kerbs, seen from the outside.

    Kept along the bottom third. A track through the middle of a banner is a stripe across
    somebody's face; a track under it is a horizon.
    """
    image, d = banner_canvas(top, bottom)
    w, h = BANNER_W * SCALE, BANNER_H * SCALE

    def centre_y(x):
        u = x / w
        # Centred at 0.68 rather than 0.76: the displayed slice of a banner is about the
        # middle two thirds of the art (see `banner_minimap` for the arithmetic), and at
        # 0.76 the outer kerb — the thing that makes it a racetrack — fell in the crop.
        return h * (0.68 + 0.09 * math.sin(u * 5.0 + 0.6) + 0.03 * math.sin(u * 11.0))

    half = px(46)
    step = px(6)
    for x in range(0, w, step):
        y = centre_y(x)
        d.rectangle([x, y - half, x + step, y + half], fill=rgba((28, 28, 32), 1.0))

    for x in range(0, w, step):
        y = centre_y(x)
        d.line([(x, y - half), (x + step, centre_y(x + step) - half)], fill=rgba(edge, 0.5), width=px(2))
        d.line([(x, y + half), (x + step, centre_y(x + step) + half)], fill=rgba(edge, 0.5), width=px(2))

    # Kerb dashes on the outer edge, alternating like every kerb everywhere.
    dash = px(26)
    for i, x in enumerate(range(0, w, dash)):
        colour = kerb if i % 2 else (240, 240, 240)
        y = centre_y(x)
        d.line([(x, y + half + px(6)), (x + dash, centre_y(x + dash) + half + px(6))],
               fill=rgba(colour, 0.85), width=px(9))

    # A short chequer at the far right. Two rows is a finish line; a whole flag would be a
    # focal point.
    sq = px(18)
    for row in range(2):
        for col in range(6):
            x = w - px(150) + col * sq
            y = centre_y(x) - half + row * sq + px(4)
            if (row + col) % 2 == 0:
                d.rectangle([x, y, x + sq, y + sq], fill=rgba((235, 235, 235), 0.8))

    for i in range(14):
        x = (i * w) // 14
        y = centre_y(x) + px(10)
        d.line([(x, y), (x + px(60), centre_y(x + px(60)) + px(10))],
               fill=rgba((0, 0, 0), 0.35), width=px(4))
    return banner_finish(image)


def banner_minimap(top, bottom, lane, blue, red):
    """MOBA/RTS: three lanes under fog.

    A minimap is a square in a corner, so this one is a square in a corner rather than a
    pattern stretched across 1200px — it stays a minimap, and the fog does the fading the
    other banners do with alpha ramps.
    """
    image, d = banner_canvas(top, bottom)
    w, h = BANNER_W * SCALE, BANNER_H * SCALE

    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)

    # A whole square panel, not a corner of one. The first version ran the map off the top
    # left and relied on fog to end it, which left three diagonal lines and no minimap:
    # the square border is what makes the rest read as a map at all.
    #
    # It is sized to survive the crop. `ProfileBanner` shows this 3:1 art in a box nearer
    # 4:1 with `contentFit="cover"`, so roughly the top and bottom sixth never appear on a
    # phone — and a square that loses two of its four edges is not a panel any more.
    # Positioned so it straddles the centre: the shop crops to the middle half of the
    # width, and a map hugging the left edge was invisible there.
    size = h * 0.62
    ox, oy = w * 0.30, h * 0.19
    ld.rounded_rectangle([ox, oy, ox + size, oy + size], radius=px(10),
                         fill=rgba((0, 0, 0), 0.30), outline=rgba(lane, 0.35), width=px(4))

    river = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(river).polygon(
        [(ox + size * 0.04, oy + size * 0.70), (ox + size * 0.70, oy + size * 0.04),
         (ox + size * 0.92, oy + size * 0.24), (ox + size * 0.24, oy + size * 0.92)],
        fill=rgba((120, 170, 220), 0.20))
    layer = Image.alpha_composite(layer, river.filter(ImageFilter.GaussianBlur(px(9))))
    ld = ImageDraw.Draw(layer)

    def at(u, v):
        return (ox + size * u, oy + size * v)

    # Three lanes between two bases in opposite corners, which is the shape of the genre.
    ld.line([at(0.12, 0.88), at(0.12, 0.16), at(0.84, 0.16)], fill=rgba(lane, 0.34), width=px(6))
    ld.line([at(0.12, 0.88), at(0.84, 0.88), at(0.84, 0.16)], fill=rgba(lane, 0.34), width=px(6))
    ld.line([at(0.20, 0.80), at(0.76, 0.24)], fill=rgba(lane, 0.34), width=px(6))

    for t in (0.28, 0.50, 0.72):
        for u, v, friendly in ((0.12, 0.88 - t * 0.72, True), (0.12 + t * 0.72, 0.88, True),
                               (0.84, 0.16 + t * 0.72, False), (0.84 - t * 0.72, 0.16, False),
                               (0.20 + t * 0.56, 0.80 - t * 0.56, t < 0.5)):
            x, y = at(u, v)
            r = px(7)
            ld.ellipse([x - r, y - r, x + r, y + r], fill=rgba(blue if friendly else red, 0.6))

    for u, v, friendly in ((0.12, 0.88, True), (0.84, 0.16, False)):
        x, y = at(u, v)
        r = px(15)
        ld.ellipse([x - r, y - r, x + r, y + r], outline=rgba(blue if friendly else red, 0.7),
                   width=px(4))

    # Fog thins the panel towards the middle of the banner rather than cutting it off.
    fog = Image.new("L", (w, h), 255)
    fd = ImageDraw.Draw(fog)
    for x in range(0, w, px(4)):
        t = max(0.0, min(1.0, (x - w * 0.62) / (w * 0.16)))
        fd.rectangle([x, 0, x + px(4), h], fill=int(255 * (1 - t)))
    layer.putalpha(Image.composite(layer.getchannel("A"), Image.new("L", (w, h), 0), fog))
    image = Image.alpha_composite(image, layer)

    d = ImageDraw.Draw(image)
    for gx in range(0, w, px(52)):
        d.line([(gx, 0), (gx, h)], fill=rgba(lane, 0.07), width=px(1))
    for gy in range(0, h, px(52)):
        d.line([(0, gy), (w, gy)], fill=rgba(lane, 0.07), width=px(1))
    for r in (px(26), px(44)):
        d.ellipse([w * 0.86 - r, h * 0.28 - r, w * 0.86 + r, h * 0.28 + r],
                  outline=rgba(red, 0.55), width=px(3))
    return banner_finish(image)


def banner_tabletop(top, bottom, line):
    """Board and card games: a d20, two cards, and pips.

    Pips rather than suits, and a wireframe rather than a rendered die — partly because
    line art survives being 64dp tall, and partly because suits and faces are where a
    tabletop illustration starts belonging to a publisher.
    """
    image, d = banner_canvas(top, bottom)
    w, h = BANNER_W * SCALE, BANNER_H * SCALE

    # d20: a hexagonal outline with a triangle inside it and spokes between, which is what
    # an icosahedron looks like from a vertex.
    # Both objects pulled toward the centre — the shop thumbnail shows only the middle half
    # of the width, and with the die at 0.10w and the cards at 0.86w it listed as a scatter
    # of dice pips on black.
    cx, cy, r = w * 0.26, h * 0.50, px(150)
    outer = [(cx + math.cos(math.radians(30 + 60 * k)) * r,
              cy + math.sin(math.radians(30 + 60 * k)) * r) for k in range(6)]
    inner = [(cx + math.cos(math.radians(90 + 120 * k)) * r * 0.52,
              cy + math.sin(math.radians(90 + 120 * k)) * r * 0.52) for k in range(3)]
    d.polygon(outer, outline=rgba(line, 0.55), width=px(3))
    d.polygon(inner, outline=rgba(line, 0.45), width=px(3))
    for k in range(3):
        d.line([inner[k], outer[(k * 2) % 6]], fill=rgba(line, 0.35), width=px(2))
        d.line([inner[k], outer[(k * 2 + 1) % 6]], fill=rgba(line, 0.35), width=px(2))
        d.line([inner[k], inner[(k + 1) % 3]], fill=rgba(line, 0.45), width=px(2))

    # Two cards at the other end, fanned. Rounded outlines with plain pips.
    for i, (ox, angle) in enumerate(((px(0), 0), (px(90), 12))):
        card = Image.new("RGBA", (px(190), px(270)), (0, 0, 0, 0))
        cd = ImageDraw.Draw(card)
        cd.rounded_rectangle([px(4), px(4), px(186), px(266)], radius=px(14),
                             outline=rgba(line, 0.6), width=px(3))
        for pip_y in (px(40), px(135), px(230)):
            cd.ellipse([px(88), pip_y - px(9), px(106), pip_y + px(9)], fill=rgba(line, 0.45))
        card = card.rotate(-angle, resample=Image.BICUBIC, expand=True)
        image.alpha_composite(card, (int(w * 0.64 + ox - card.width / 2),
                                     int(h * 0.36 - card.height / 2)))

    d = ImageDraw.Draw(image)
    rnd = lcg(31415926)
    for i in range(9):
        x = px(180) + (next(rnd) % int(w * 0.62))
        y = h * 0.74 + (next(rnd) % px(40))
        for ox, oy in ((-px(12), -px(12)), (px(12), px(12)), (0, 0))[: 2 + i % 2]:
            d.ellipse([x + ox - px(5), y + oy - px(5), x + ox + px(5), y + oy + px(5)],
                      fill=rgba(line, 0.30))
    return banner_finish(image)


def banner_zone(top, bottom, safe, storm, marker):
    """Battle royale: the circle closing on an island.

    The safe circle is centred off the right edge so only its arc crosses the banner. A
    circle drawn whole in the middle would be a target around somebody's face, which is
    both a worse image and a different genre.
    """
    image, _ = banner_canvas(top, bottom)
    w, h = BANNER_W * SCALE, BANNER_H * SCALE

    land = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ld = ImageDraw.Draw(land)
    rnd = lcg(90210)
    # Low and to the left, and dark. The first version put a mid-teal landmass across two
    # thirds of the banner, which is a fine map and a useless banner: white text has to sit
    # on this, so the island has to be a shape in the dark rather than the lit half of the
    # image.
    blobs = [(w * 0.09, h * 0.78, px(150)), (w * 0.24, h * 0.66, px(120)),
             (w * 0.20, h * 0.96, px(120)), (w * 0.36, h * 0.86, px(100)),
             (w * 0.02, h * 0.55, px(86))]
    for bx, by, br in blobs:
        jitter = next(rnd) % px(30)
        ld.ellipse([bx - br - jitter, by - br, bx + br + jitter, by + br],
                   fill=rgba((26, 46, 38), 0.92))
    land = land.filter(ImageFilter.GaussianBlur(px(14)))

    # A sand rim, from the difference between the landmass and a slightly shrunken copy of
    # it. Without it the island is a soft green cloud; the coastline is what makes it land.
    solid = land.getchannel("A").point(lambda v: 255 if v > 150 else 0)
    coast = ImageChops.subtract(solid.filter(ImageFilter.MaxFilter(9)), solid)
    rim = Image.new("RGBA", (w, h), rgba((150, 136, 102), 0.45))
    rim.putalpha(ImageChops.multiply(coast, Image.new("L", (w, h), 110)))
    image = Image.alpha_composite(image, land)
    image = Image.alpha_composite(image, rim)

    d = ImageDraw.Draw(image)
    cx, cy, r = w * 1.05, h * 0.55, px(520)

    # The storm is everything outside the circle. Painted as a full-bleed wash with the
    # circle cleared out of it — a pixel replace on RGBA, the same trick `finish()` uses on
    # the frames, and cheaper than testing every pixel against the radius.
    storm_layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sd = ImageDraw.Draw(storm_layer)
    sd.rectangle([0, 0, w, h], fill=rgba(storm, 0.20))
    sd.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(0, 0, 0, 0))
    image = Image.alpha_composite(image, storm_layer.filter(ImageFilter.GaussianBlur(px(3))))

    d = ImageDraw.Draw(image)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=rgba(safe, 0.75), width=px(4))
    r2 = px(300)
    for k in range(48):
        a = k * 7.5
        d.arc([cx - r2, cy - r2, cx + r2, cy + r2], start=a, end=a + 4.2,
              fill=rgba(safe, 0.45), width=px(3))

    for i, (dx_, dy_) in enumerate(((0.10, 0.26), (0.19, 0.16), (0.30, 0.30))):
        x, y = w * dx_, h * dy_
        d.line([(x - px(26), y - px(26)), (x, y)], fill=rgba(marker, 0.5), width=px(3))
        d.ellipse([x - px(7), y - px(7), x + px(7), y + px(7)], fill=rgba(marker, 0.85))
    return banner_finish(image)


# -- animated banners -------------------------------------------------------
#
# All three follow one rule, and it is a file-size rule before it is an art rule: **the
# base is composed once and copied, and only small or soft things move.** libwebp encodes
# an animation as the difference between consecutive frames, so a loop where the whole
# 600x200 field changes every frame costs megabytes, while one where a glow breathes and a
# dozen specks fade costs a few tens of kilobytes per frame.
#
# Everything the still banners must respect still applies: the profile header shows the
# full width and crops the top and bottom sixth, the shop lists the middle half of the
# width, and the avatar covers the bottom-left corner.


def banner_nebula(top, bottom, accent, other):
    """Deep Space: a drifting starfield.

    The animated sibling of `banner-void`, on the same palette so the set reads as a set.
    Two things move: a dust field that scrolls exactly one of its own periods over the
    loop (so the wrap is invisible), and a handful of stars that fade up and back down.
    The other 130-odd stars are painted into the base and never change.
    """
    base, _ = anim_banner_canvas(top, bottom)
    w, h = ANIM_BANNER_W * SCALE, ANIM_BANNER_H * SCALE

    wash = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    wd = ImageDraw.Draw(wash)
    rnd = lcg(424242)
    for i in range(12):
        x = next(rnd) % w
        y = next(rnd) % h
        r = px(30) + next(rnd) % px(75)
        # 0.11, not void's 0.16: the same alpha over a canvas half the size reads as a
        # much larger, milkier cloud, and this has to stay dark enough for white text.
        wd.ellipse([x - r, y - r, x + r, y + r], fill=rgba(accent if i % 2 else other, 0.11))
    base = Image.alpha_composite(base, wash.filter(ImageFilter.GaussianBlur(px(20))))

    bd = ImageDraw.Draw(base)
    rnd = lcg(20260805)
    twinklers = []
    for i in range(150):
        x = next(rnd) % w
        y = next(rnd) % h
        r = px(0.7) + (next(rnd) % 100) / 100 * px(1.8)
        brightness = 0.35 + (next(rnd) % 65) / 100
        if i % 9 == 0:
            twinklers.append((x, y, r * 1.7))
            continue
        bd.ellipse([x - r, y - r, x + r, y + r], fill=rgba((255, 255, 255), brightness))

    # Drawn one period wider than the canvas and sampled through a moving window: the
    # cheapest way to scroll something seamlessly is to make it repeat.
    period = w // 6
    dust = Image.new("RGBA", (w + period, h), (0, 0, 0, 0))
    dd = ImageDraw.Draw(dust)
    rnd = lcg(777001)
    for _ in range(26):
        x = next(rnd) % period
        y = next(rnd) % h
        r = px(6) + next(rnd) % px(16)
        for k in range(0, w + period, period):
            dd.ellipse([x + k - r, y - r, x + k + r, y + r], fill=rgba(other, 0.10))
    dust = dust.filter(ImageFilter.GaussianBlur(px(7)))

    out = []
    for f in range(LOOP_FRAMES):
        frame = base.copy()
        shift = int(period * f / LOOP_FRAMES)
        frame.alpha_composite(dust, (0, 0), (shift, 0, shift + w, h))
        fd = ImageDraw.Draw(frame)
        for i, (x, y, r) in enumerate(twinklers):
            phase = ((f / LOOP_FRAMES) + i / len(twinklers)) % 1.0
            fd.ellipse([x - r, y - r, x + r, y + r],
                       fill=rgba((255, 255, 255), 0.9 * math.sin(math.pi * phase)))
        out.append(anim_banner_finish(frame))
    return out


def banner_core(top, bottom, glow_c, hot):
    """Meltdown: something under load, off to the right.

    The glow sits right of centre and the flecks climb through it, which keeps the
    bottom-left corner — where the avatar lands — dark, and leaves the middle readable.
    Hazard dashes along the floor tie it to the Reactor frame it is sold with.
    """
    base, bd = anim_banner_canvas(top, bottom)
    w, h = ANIM_BANNER_W * SCALE, ANIM_BANNER_H * SCALE

    # Static furniture, placed inside the band the profile header actually shows (it
    # crops the top and bottom sixth): the floor line was at 0.86 and the chevrons ran
    # from 0.10, so on a real profile neither existed.
    dash = px(22)
    for i, x in enumerate(range(0, w, dash)):
        bd.line([(x, h * 0.78), (x + dash, h * 0.78)],
                fill=rgba(glow_c if i % 2 else GRAPHITE, 0.55), width=px(4))
    for i in range(7):
        x = w * 0.06 + i * px(26)
        bd.line([(x, h * 0.22), (x + px(10), h * 0.28), (x, h * 0.34)],
                fill=rgba(GRAPHITE, 0.9), width=px(3))

    out = []
    for f in range(LOOP_FRAMES):
        frame = base.copy()
        pulse = 0.22 + 0.10 * math.sin(2 * math.pi * f / LOOP_FRAMES)

        # Small, dim and mostly off the right edge — a rim light, not a wash. At r=180 and
        # alpha 0.22 this flooded the right half in bright green: nothing else in the set
        # is that loud, and white text on it would have been unreadable.
        halo = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        hd = ImageDraw.Draw(halo)
        cx, cy, r = w * 0.88, h * 0.56, px(120)
        hd.ellipse([cx - r, cy - r, cx + r, cy + r], fill=rgba(glow_c, pulse * 0.55))
        hd.ellipse([cx - r / 3, cy - r / 3, cx + r / 3, cy + r / 3], fill=rgba(hot, pulse * 0.8))
        frame = Image.alpha_composite(frame, halo.filter(ImageFilter.GaussianBlur(px(34))))

        fd = ImageDraw.Draw(frame)
        for i in range(10):
            phase = ((f / LOOP_FRAMES) + i / 10) % 1.0
            x = cx + math.sin(i * 2.1) * px(60)
            y = h * 0.80 - phase * h * 0.5
            r2 = px(2.5) * (1 - phase) + px(1)
            fd.ellipse([x - r2, y - r2, x + r2, y + r2],
                       fill=rgba(hot, 0.75 * math.sin(math.pi * phase)))
        out.append(anim_banner_finish(frame))
    return out


def banner_signal(top, bottom, trace, grid):
    """A wave crossing an instrument screen.

    The phase advances exactly one cycle over the loop, which is what makes it seamless,
    and the whole trace lives in the bottom third so a name and a face are never on it.
    Drawn wide-and-dim, then narrow-and-bright, then blurred underneath — the same
    accumulate-into-light trick `banner_laurel` uses.
    """
    base, bd = anim_banner_canvas(top, bottom)
    w, h = ANIM_BANNER_W * SCALE, ANIM_BANNER_H * SCALE

    for x in range(0, w, px(28)):
        bd.line([(x, 0), (x, h)], fill=rgba(grid, 0.07), width=px(1))
    for y in range(0, h, px(28)):
        bd.line([(0, y), (w, y)], fill=rgba(grid, 0.07), width=px(1))
    bd.line([(0, h * 0.72), (w, h * 0.72)], fill=rgba(grid, 0.16), width=px(1))

    def wave(x, f):
        return h * 0.72 + h * 0.10 * math.sin(2 * math.pi * (3 * x / w) - 2 * math.pi * f / LOOP_FRAMES)

    step = px(6)
    out = []
    for f in range(LOOP_FRAMES):
        glow_layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        gd = ImageDraw.Draw(glow_layer)
        for x in range(0, w, step):
            gd.line([(x, wave(x, f)), (x + step, wave(x + step, f))],
                    fill=rgba(trace, 0.5), width=px(9))
        frame = Image.alpha_composite(base, glow_layer.filter(ImageFilter.GaussianBlur(px(9))))

        fd = ImageDraw.Draw(frame)
        for x in range(0, w, step):
            fd.line([(x, wave(x, f)), (x + step, wave(x + step, f))],
                    fill=rgba(trace, 0.85), width=px(3))

        # The crest, travelling with the phase — the eye needs one thing to follow.
        head_x = w * (((f / LOOP_FRAMES) + 1 / 12) % (1 / 3)) * 3 / 3
        hx = (head_x + w / 12) % w
        hy = wave(hx, f)
        fd.ellipse([hx - px(6), hy - px(6), hx + px(6), hy + px(6)],
                   fill=rgba((235, 250, 255), 0.95))
        out.append(anim_banner_finish(frame))
    return out


# =========================================================================

STATIC_FRAMES = {
    "frame-steel": lambda: frame_plain(SILVER),
    "frame-bronze": lambda: frame_double(BRONZE, GOLD),
    "frame-gold": lambda: frame_studs(GOLD, (255, 236, 178)),
    "frame-reticle": lambda: frame_notched(CYAN),
    "frame-brand": lambda: frame_double(BRAND, (255, 190, 200)),
    "frame-ticker": lambda: frame_dashed(VIOLET),
    "frame-scope": lambda: frame_scope(ARCTIC, BRAND),
    "frame-hazard": lambda: frame_hazard(GRAPHITE, EMBER),
    "frame-hive": lambda: frame_hex(MINT, GRAPHITE),
}

ANIMATED_FRAMES = {
    "frame-orbit": lambda: anim_orbit(CYAN, (200, 250, 255)),
    "frame-sweep": lambda: anim_sweep(VIOLET, (235, 215, 255)),
    "frame-pulse": lambda: anim_pulse(MINT),
    "frame-rotor": lambda: anim_spin_segments(BRAND, GOLD),
    "frame-ember": lambda: anim_ember(EMBER, (255, 214, 130)),
    "frame-toxic": lambda: anim_orbit(LIME, (225, 255, 180), dots=4),
    "frame-frost": lambda: anim_frost(ICE, (255, 255, 255)),
    "frame-radar": lambda: anim_radar(MINT, (200, 255, 225)),
    "frame-comet": lambda: anim_comet((255, 255, 255), (255, 150, 165), BRAND),
    "frame-glitch": lambda: anim_glitch(ARCTIC, BRAND, CYAN),
    # The two bundle sets. Each is sold with the banner of the same name below, and drawn
    # to match it — Pulsar with Nebula, Reactor with Core.
    "frame-pulsar": lambda: anim_pulsar(ICE, CYAN),
    "frame-reactor": lambda: anim_reactor(GRAPHITE, LIME),
    # --- trophies: earned, never sold --------------------------------------
    #
    # One per PRISMATIC badge that pays in a cosmetic rather than in coins. The `cosmetic`
    # row carries `unlocked_by_badge`, which is what keeps them off the shelf; nothing here
    # knows about that, and nothing here needs to.
    #
    # Each takes its badge's accent from badges/generate.py, so the pair reads as one award
    # — the two generators already share a palette for exactly this kind of reason. They
    # reuse existing loops rather than inventing four more: the colour and the count are
    # what make a trophy recognisable next to its badge, and a fifth animation style would
    # be four more things to get subtly wrong.
    "frame-magnetic": lambda: anim_orbit(BRAND, (255, 190, 200), dots=5),
    "frame-unbroken": lambda: anim_sweep(CYAN, (200, 250, 255)),
    "frame-collector": lambda: anim_spin_segments(GOLD, (255, 236, 178), count=9),
    "frame-trailblazer": lambda: anim_comet((255, 255, 255), (200, 255, 180), LIME),
}

#: Animated banners. Separate from BANNERS because they save as looping WebP rather than
#: a single JPEG, and at a smaller canvas — see ANIM_BANNER_W.
ANIMATED_BANNERS = {
    "banner-signal": lambda: banner_signal((14, 14, 22), (8, 8, 14), CYAN, ARCTIC),
    "banner-nebula": lambda: banner_nebula((14, 16, 34), (6, 6, 16), VIOLET, CYAN),
    "banner-core": lambda: banner_core((20, 22, 18), (8, 10, 8), LIME, (225, 255, 180)),
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
    # The genre set. See the note above `banner_overworld` for why none of these borrow
    # from an actual game.
    "banner-overworld": lambda: banner_overworld(
        (12, 18, 40), (8, 10, 24), (90, 110, 170), (255, 255, 255), EMBER),
    "banner-dungeon": lambda: banner_dungeon((26, 22, 26), (12, 10, 14), (48, 44, 52), EMBER),
    "banner-wasd": lambda: banner_wasd((14, 14, 22), (8, 8, 14), CYAN, ARCTIC),
    "banner-gamepad": lambda: banner_gamepad(
        (18, 14, 30), (10, 8, 20), VIOLET, (BRAND, MINT, CYAN, EMBER)),
    "banner-chicane": lambda: banner_chicane((16, 16, 20), (8, 8, 10), (200, 200, 200), BRAND),
    "banner-minimap": lambda: banner_minimap((10, 22, 16), (6, 12, 10), MINT, CYAN, BRAND),
    "banner-tabletop": lambda: banner_tabletop((30, 18, 14), (16, 10, 8), (230, 215, 190)),
    "banner-zone": lambda: banner_zone(
        (10, 20, 34), (6, 10, 20), (255, 255, 255), (80, 140, 255), BRAND),
    # Not for sale. Granted with a Gold membership and revoked with it — see the
    # membership cosmetics migration.
    "banner-gold": lambda: banner_laurel((58, 42, 14), (16, 13, 10), GOLD, (255, 226, 160)),
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
        size = path.stat().st_size
        # These are downloaded over mobile data, several at a time, to decorate a
        # thumbnail. The largest of the first set is 361KB; anything past this is a new
        # worst case and wants a cheaper loop rather than a shrug.
        warning = "  <-- over the size band, see the note above" if size > 380_000 else ""
        print(f"  {name}.webp  ({size // 1024}KB, {len(frames)} frames){warning}")

    print("Banners")
    for name, build in BANNERS.items():
        build().save(BANNERS_DIR / f"{name}.jpg", quality=88, optimize=True)
        print(f"  {name}.jpg")

    print("Banners (animated)")
    for name, build in ANIMATED_BANNERS.items():
        frames = build()
        path = BANNERS_DIR / f"{name}.webp"
        # No `disposal`: these are opaque RGB, so there is nothing to clear between
        # frames. quality is a little below the frames' 82 because a banner is a
        # background behind a face, not a thin bright ring against transparency.
        frames[0].save(
            path,
            format="WEBP",
            save_all=True,
            append_images=frames[1:],
            duration=int(1000 / FPS),
            loop=0,
            quality=78,
            method=6,
        )
        size = path.stat().st_size
        # Roomier than the frames' 380KB because the canvas is 12x the pixels even at
        # half resolution — but still a hard ceiling: this is downloaded to draw a strip
        # behind somebody's name. Past it, move more of the image into the static base.
        warning = "  <-- over the size band, see the note above" if size > 500_000 else ""
        print(f"  {name}.webp  ({size // 1024}KB, {len(frames)} frames){warning}")

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

    # Two columns, rounded up: `BANNER_H // 2 * len(BANNERS) // 2` reads as "half the height
    # per row" but evaluates left to right, so an odd count lost its last banner off the
    # bottom of the sheet.
    banner_names = list(BANNERS) + list(ANIMATED_BANNERS)
    banner_rows = (len(banner_names) + 1) // 2
    banner_sheet = Image.new("RGB", (BANNER_W // 2 * 2, BANNER_H // 2 * banner_rows))
    for i, name in enumerate(banner_names):
        # Animated banners are WebP; the sheet shows their first frame, which is all a
        # still contact sheet can say about them anyway.
        path = BANNERS_DIR / f"{name}.jpg"
        if not path.exists():
            path = BANNERS_DIR / f"{name}.webp"
        item = Image.open(path).convert("RGB").resize((BANNER_W // 2, BANNER_H // 2))
        banner_sheet.paste(item, ((i % 2) * (BANNER_W // 2), (i // 2) * (BANNER_H // 2)))
    banner_sheet.save(OUT / "banners-sheet.png")
    print("\ncontact sheets written")


if __name__ == "__main__":
    main()
