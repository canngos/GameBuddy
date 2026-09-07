"""Play in-app product icons for the four coin packs.

Google shows this image in the purchase sheet, next to the price, at the moment
somebody decides to pay. Every pack was inheriting the app icon, so all four looked
identical and the sheet said nothing about what was being bought -- the one place a
picture has a job to do.

These are the same four silhouettes the app draws in `src/market/CoinPackIcon.tsx`:
two coins, a sack, a heap, a chest. Somebody who taps "7,000 coins" in the Market and
then sees a chest in the Google Pay sheet is being told, twice, that they tapped the
right row.

**Why they are drawn rather than rasterised from the app's SVG.** No SVG rasteriser is
installed and pulling one in for four images is not worth it. The silhouettes are what
carry the meaning, not the exact beziers, so they are rebuilt from primitives here at a
size that suits a 512px canvas -- the app's marks are tuned for 26dp and would look thin
blown up.

**The background is the app's own adaptive-icon gradient**, composited exactly the way
`make_icon_512` does it. That is what keeps these recognisably GameBuddy rather than
four stock coin clip-arts, and it is already a full-bleed 512x512 square, so there are
no transparent corners for Play to mask into dark wedges -- the bug ac74be6 fixed on the
store icon.

Run:  python store-listing/coin-pack-icons.py
"""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
BG_SRC = ROOT / "GameBuddy-App" / "assets" / "android-icon-background.png"
OUT = Path(__file__).resolve().parent / "output"

SIZE = 512
SS = 4  # supersample; PIL has no antialiasing on shapes, so draw big and shrink
C = SIZE * SS

# The app's gold, plus a deeper rim and a highlight. One flat gold on a blue gradient
# reads as a sticker; the rim is what gives the coins an edge to catch.
GOLD = (255, 197, 61)
GOLD_DEEP = (176, 118, 10)
GOLD_LIGHT = (255, 231, 166)

RIM = int(6 * SS)


def px(v: float) -> float:
    """Normalised 0..1 coordinate to supersampled pixels."""
    return v * C


def coin(d: ImageDraw.ImageDraw, cx: float, cy: float, r: float, *, ring: bool = False) -> None:
    """A gold disc with a rim, and optionally the inner ring that says 'coin'."""
    box = (px(cx - r), px(cy - r), px(cx + r), px(cy + r))
    d.ellipse(box, fill=GOLD, outline=GOLD_DEEP, width=RIM)
    # A crescent of light along the upper-left, which is where every other surface in
    # the brand is lit from.
    inset = r * 0.30
    d.arc(
        (px(cx - r + inset * 0.35), px(cy - r + inset * 0.35),
         px(cx + r - inset * 0.35), px(cy + r - inset * 0.35)),
        start=150, end=280, fill=GOLD_LIGHT, width=int(r * C * 0.16),
    )
    if ring:
        rr = r * 0.36
        d.ellipse((px(cx - rr), px(cy - rr), px(cx + rr), px(cy + rr)),
                  outline=GOLD_DEEP, width=RIM)


def draw_two_coins(d: ImageDraw.ImageDraw) -> None:
    coin(d, 0.40, 0.62, 0.20)
    coin(d, 0.60, 0.41, 0.225, ring=True)


def draw_sack(d: ImageDraw.ImageDraw) -> None:
    # Round body, cinched neck, and cloth that flares *outward* above the tie. The first
    # attempt gathered the cloth into two points above the knot and they read as rabbit
    # ears; a money bag opens wider at the top than at the tie, never narrower.
    d.ellipse((px(0.20), px(0.44), px(0.80), px(0.89)),
              fill=GOLD, outline=GOLD_DEEP, width=RIM)
    # Taper from the knot down into the body.
    d.polygon([(px(0.40), px(0.33)), (px(0.60), px(0.33)),
               (px(0.74), px(0.58)), (px(0.26), px(0.58))],
              fill=GOLD, outline=GOLD_DEEP)
    # The flare above the knot, with a rounded lip so the cloth has no sharp corners.
    d.polygon([(px(0.405), px(0.34)), (px(0.595), px(0.34)),
               (px(0.665), px(0.17)), (px(0.335), px(0.17))],
              fill=GOLD, outline=GOLD_DEEP)
    d.ellipse((px(0.335), px(0.125), px(0.665), px(0.215)),
              fill=GOLD, outline=GOLD_DEEP, width=RIM)
    # The knot. Drawn last of the cloth so it sits over both the flare and the taper --
    # without it this is a pot, not a bag.
    d.rounded_rectangle((px(0.355), px(0.30), px(0.645), px(0.375)),
                        radius=int(0.028 * C), fill=GOLD_DEEP)
    d.arc((px(0.26), px(0.50), px(0.60), px(0.84)), start=150, end=245,
          fill=GOLD_LIGHT, width=int(0.05 * C))
    coin(d, 0.50, 0.68, 0.135, ring=True)


def draw_heap(d: ImageDraw.ImageDraw) -> None:
    for cx in (0.27, 0.50, 0.73):
        coin(d, cx, 0.74, 0.155)
    for cx in (0.385, 0.615):
        coin(d, cx, 0.535, 0.155)
    coin(d, 0.50, 0.33, 0.165, ring=True)


def draw_chest(d: ImageDraw.ImageDraw) -> None:
    # A domed lid, thrown back. The first attempt used a flat trapezoid and it read as a
    # roof -- a chest lid is curved, and the curve is most of what identifies it.
    d.pieslice((px(0.15), px(0.10), px(0.85), px(0.52)), start=180, end=360,
               fill=GOLD_DEEP, outline=GOLD_DEEP)
    d.pieslice((px(0.21), px(0.16), px(0.79), px(0.50)), start=180, end=360,
               fill=GOLD, outline=GOLD_DEEP, width=RIM)
    # Coins in the mouth, in front of the lid's edge so they read as spilling out.
    coin(d, 0.31, 0.46, 0.115)
    coin(d, 0.50, 0.42, 0.140, ring=True)
    coin(d, 0.69, 0.46, 0.105)
    # Body, with a band and a clasp.
    d.rounded_rectangle((px(0.15), px(0.52), px(0.85), px(0.86)),
                        radius=int(0.035 * C), fill=GOLD, outline=GOLD_DEEP, width=RIM)
    d.rectangle((px(0.15), px(0.635), px(0.85), px(0.685)), fill=GOLD_DEEP)
    d.rounded_rectangle((px(0.445), px(0.605), px(0.555), px(0.725)),
                        radius=int(0.012 * C), fill=GOLD_LIGHT, outline=GOLD_DEEP, width=RIM)


PACKS = [
    ("gamebuddy.coins.500", "iap-coins-500.png", draw_two_coins),
    ("gamebuddy.coins.1200", "iap-coins-1200.png", draw_sack),
    ("gamebuddy.coins.3000", "iap-coins-3000.png", draw_heap),
    ("gamebuddy.coins.7000", "iap-coins-7000.png", draw_chest),
]


def build(draw_fn) -> Image.Image:
    background = Image.open(BG_SRC).convert("RGB").resize((SIZE, SIZE), Image.LANCZOS)

    art = Image.new("RGBA", (C, C), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(art))

    # A soft drop shadow. The gradient is mid-blue and the gold is bright, so contrast
    # is not the problem -- separation is, and a shadow is what stops the art looking
    # pasted on.
    alpha = art.split()[3]
    shadow = Image.new("RGBA", (C, C), (0, 0, 0, 0))
    shadow.putalpha(alpha.filter(ImageFilter.GaussianBlur(int(0.018 * C))))
    shadow = Image.new("RGBA", (C, C), (10, 20, 60, 150)).convert("RGBA")
    shadow.putalpha(alpha.filter(ImageFilter.GaussianBlur(int(0.018 * C))).point(lambda a: int(a * 0.55)))

    plate = Image.new("RGBA", (C, C), (0, 0, 0, 0))
    plate.alpha_composite(shadow, (0, int(0.012 * C)))
    plate.alpha_composite(art)

    out = background.convert("RGBA")
    out.alpha_composite(plate.resize((SIZE, SIZE), Image.LANCZOS))
    # Play asks for a 32-bit PNG, so keep the alpha channel even though it is fully
    # opaque -- the gradient is full-bleed and there is nothing to see through. Saving
    # as RGB gives a 24-bit file the console refuses.
    return out


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    sheet = Image.new("RGB", (SIZE * len(PACKS), SIZE), (255, 255, 255))
    for i, (product_id, name, fn) in enumerate(PACKS):
        img = build(fn)
        img.save(OUT / name, "PNG")
        sheet.paste(img.convert("RGB"), (i * SIZE, 0))
        print(f"wrote {name}  512x512  for {product_id}")
    sheet.save(OUT / "iap-coins-sheet.png", "PNG")
    print("wrote iap-coins-sheet.png (contact sheet, not for upload)")


if __name__ == "__main__":
    main()
