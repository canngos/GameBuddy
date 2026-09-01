"""Generate Google Play Store promotional screenshots for GameBuddy.

Each frame: 1080x1920 (9:16) PNG. The raw app screenshot is pasted whole,
uniformly scaled, inside a drawn phone mockup — the UI itself is never
cropped, redrawn, or altered. Headline sits above the device.

Run:  python store-listing/generate.py
"""

from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "phone-preview-assets"
OUT = Path(__file__).resolve().parent / "output"

FONTS = ROOT / "GameBuddy-App" / "node_modules" / "@expo-google-fonts"
HEADLINE_FONT = FONTS / "chakra-petch" / "600SemiBold" / "ChakraPetch_600SemiBold.ttf"

CANVAS_W, CANVAS_H = 1080, 1920
SS = 3  # supersampling factor for crisp vector shapes

BG_TOP = (13, 11, 20)      # #0D0B14
BG_BOTTOM = (22, 18, 38)   # #161226
BRAND = (124, 92, 255)     # #7C5CFF

# (source suffix, output name, headline, per-screen accent RGB)
FRAMES = [
    ("204950", "01-discover.png", "Find Your Next\nGaming Buddy", (61, 189, 120)),
    ("211318", "02-match.png", "Swipe. Match. Play.", (255, 77, 109)),
    ("205107", "03-profile.png", "Connect With Gamers\nWho Share Your Games", (124, 92, 255)),
    ("211553", "04-messages.png", "Chat And Start\nPlaying Together", (99, 102, 241)),
    ("lobby", "05-lobby.png", "Discover Your Perfect\nGaming Squad", (79, 121, 255)),
    ("211608", "06-badges.png", "Level Up\nYour Profile", (56, 189, 190)),
    ("205048", "07-market.png", "Earn Coins.\nUnlock Rewards.", (240, 177, 42)),
]


def make_background(accent):
    """Vertical gradient + two soft radial glows (brand purple, screen accent)."""
    bg = Image.new("RGB", (CANVAS_W, CANVAS_H))
    px = bg.load()
    for y in range(CANVAS_H):
        t = y / (CANVAS_H - 1)
        px_row = tuple(round(a + (b - a) * t) for a, b in zip(BG_TOP, BG_BOTTOM))
        for x in range(CANVAS_W):
            px[x, y] = px_row

    glow = Image.new("RGB", (CANVAS_W, CANVAS_H), (0, 0, 0))
    gd = ImageDraw.Draw(glow)
    # brand glow upper-left, accent glow lower-right; additive so it stays dark
    gd.ellipse((-420, 120, 480, 1020), fill=tuple(c // 5 for c in BRAND))
    gd.ellipse((620, 1050, 1520, 1950), fill=tuple(c // 5 for c in accent))
    glow = glow.filter(ImageFilter.GaussianBlur(220))
    return ImageChops.add(bg, glow)


def rounded_rect_layer(size, box, radius, fill):
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle(box, radius=radius, fill=fill)
    return layer


def draw_headline(canvas, text, band_top, band_bottom, accent):
    draw = ImageDraw.Draw(canvas)
    lines = text.split("\n")
    size = 96
    while size > 40:
        font = ImageFont.truetype(str(HEADLINE_FONT), size)
        widths = [draw.textlength(line, font=font) for line in lines]
        if max(widths) <= CANVAS_W - 120:
            break
        size -= 4
    line_h = round(size * 1.22)
    total_h = line_h * len(lines)
    y = band_top + (band_bottom - band_top - total_h - 46) // 2
    for line in lines:
        w = draw.textlength(line, font=font)
        x = (CANVAS_W - w) // 2
        # soft dark halo so text stays legible over the glow
        draw.text((x + 3, y + 3), line, font=font, fill=(0, 0, 0, 140))
        draw.text((x, y), line, font=font, fill=(255, 255, 255))
        y += line_h
    # accent bar under the headline
    bar_w = 130
    bar_y = y + 18
    draw.rounded_rectangle(
        ((CANVAS_W - bar_w) // 2, bar_y, (CANVAS_W + bar_w) // 2, bar_y + 10),
        radius=5, fill=accent,
    )


def compose(src_path, headline, accent, out_path):
    canvas = make_background(accent).convert("RGBA")

    shot = Image.open(src_path).convert("RGB")  # 1080x2280

    # --- phone geometry (canvas coords) ---
    # The whole device fits inside the canvas: no bottom bleed, so the
    # app's bottom navigation stays fully visible.
    band_h = 330                      # headline band height
    bezel = 16
    bottom_margin = 56
    body_y = band_h + 56
    screen_h = CANVAS_H - bottom_margin - body_y - bezel * 2   # ~1446
    scale = screen_h / shot.height
    screen_w = round(shot.width * scale)                       # ~685
    body_w = screen_w + bezel * 2
    body_x = (CANVAS_W - body_w) // 2
    body_h = screen_h + bezel * 2
    body_radius = 76
    screen_radius = body_radius - bezel

    # ambient glow behind the device
    glow = Image.new("RGBA", (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).rounded_rectangle(
        (body_x - 26, body_y - 26, body_x + body_w + 26, body_y + body_h + 26),
        radius=body_radius + 26, fill=BRAND + (110,),
    )
    glow = glow.filter(ImageFilter.GaussianBlur(60))
    canvas.alpha_composite(glow)

    # device body drawn supersampled for smooth corners
    hi = Image.new("RGBA", (CANVAS_W * SS, CANVAS_H * SS), (0, 0, 0, 0))
    hd = ImageDraw.Draw(hi)
    hd.rounded_rectangle(
        tuple(v * SS for v in (body_x - 3, body_y - 3, body_x + body_w + 3, body_y + body_h)),
        radius=(body_radius + 3) * SS, fill=(58, 52, 82, 255),
    )  # thin edge highlight
    hd.rounded_rectangle(
        tuple(v * SS for v in (body_x, body_y, body_x + body_w, body_y + body_h)),
        radius=body_radius * SS, fill=(16, 14, 24, 255),
    )
    body = hi.resize((CANVAS_W, CANVAS_H), Image.LANCZOS)
    canvas.alpha_composite(body)

    # screenshot: uniform scale only, rounded-corner mask, pasted whole
    shot_scaled = shot.resize((screen_w, screen_h), Image.LANCZOS)
    mask_hi = Image.new("L", (screen_w * SS, screen_h * SS), 0)
    ImageDraw.Draw(mask_hi).rounded_rectangle(
        (0, 0, screen_w * SS, screen_h * SS), radius=screen_radius * SS, fill=255
    )
    mask = mask_hi.resize((screen_w, screen_h), Image.LANCZOS)
    screen_x = body_x + bezel
    screen_y = body_y + bezel
    canvas.paste(shot_scaled, (screen_x, screen_y), mask)

    draw_headline(canvas, headline, 90, band_h + 60, accent)

    canvas.convert("RGB").save(out_path, "PNG")
    print(f"wrote {out_path.name}  {CANVAS_W}x{CANVAS_H}")


ICON_SRC = ROOT / "GameBuddy-App" / "assets" / "icon.png"
ICON_BG = (11, 11, 18)  # #0B0B12, the adaptive-icon background color
WORDMARK_FONT = FONTS / "chakra-petch" / "700Bold" / "ChakraPetch_700Bold.ttf"
TAGLINE_FONT = FONTS / "poppins" / "400Regular" / "Poppins_400Regular.ttf"
TAGLINE = "Find people who actually want to play games with you."
# the brand mark's own gradient endpoints, for the feature-graphic glows
MARK_PURPLE = (124, 77, 255)   # #7C4DFF
MARK_CYAN = (0, 229, 255)      # #00E5FF


def make_icon_512(out_path):
    """Play wants a full 512x512 square; the tile's transparent corners are
    flattened onto the brand background."""
    tile = Image.open(ICON_SRC).convert("RGBA")
    flat = Image.new("RGBA", tile.size, ICON_BG + (255,))
    flat.alpha_composite(tile)
    flat.convert("RGB").resize((512, 512), Image.LANCZOS).save(out_path, "PNG")
    print(f"wrote {out_path.name}  512x512")


def make_feature_graphic(out_path):
    """1024x500 feature graphic: brand tile + wordmark + tagline on the same
    gradient/glow identity as the screenshots. No small text — Play reuses
    this image as a promo/video thumbnail."""
    w, h = 1024, 500
    bg = Image.new("RGB", (w, h))
    px = bg.load()
    for y in range(h):
        t = y / (h - 1)
        row = tuple(round(a + (b - a) * t) for a, b in zip(BG_TOP, BG_BOTTOM))
        for x in range(w):
            px[x, y] = row
    glow = Image.new("RGB", (w, h), (0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((-260, -180, 380, 460), fill=tuple(c // 5 for c in MARK_PURPLE))
    gd.ellipse((700, 120, 1340, 760), fill=tuple(c // 7 for c in MARK_CYAN))
    glow = glow.filter(ImageFilter.GaussianBlur(120))
    canvas = ImageChops.add(bg, glow).convert("RGBA")

    tile_size = 220
    tile = Image.open(ICON_SRC).convert("RGBA").resize(
        (tile_size, tile_size), Image.LANCZOS
    )

    wordmark_font = ImageFont.truetype(str(WORDMARK_FONT), 96)
    draw = ImageDraw.Draw(canvas)
    wm_w = draw.textlength("GameBuddy", font=wordmark_font)
    # shrink the tagline until the whole group keeps a comfortable margin
    tagline_size = 30
    while tagline_size > 18:
        tagline_font = ImageFont.truetype(str(TAGLINE_FONT), tagline_size)
        tg_w = draw.textlength(TAGLINE, font=tagline_font)
        if tile_size + 52 + max(wm_w, tg_w) <= w - 72:
            break
        tagline_size -= 1
    text_w = max(wm_w, tg_w)
    gap = 52
    group_w = tile_size + gap + text_w
    gx = int((w - group_w) // 2)
    gy = (h - tile_size) // 2

    # soft glow behind the tile so it sits on the background like the phones do
    tile_glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(tile_glow).rounded_rectangle(
        (gx - 16, gy - 16, gx + tile_size + 16, gy + tile_size + 16),
        radius=70, fill=MARK_PURPLE + (90,),
    )
    canvas.alpha_composite(tile_glow.filter(ImageFilter.GaussianBlur(30)))
    canvas.alpha_composite(tile, (gx, int(gy)))

    tx = gx + tile_size + gap
    ty = gy + 22
    draw.text((tx + 3, ty + 3), "GameBuddy", font=wordmark_font, fill=(0, 0, 0, 140))
    draw.text((tx, ty), "GameBuddy", font=wordmark_font, fill=(255, 255, 255))
    draw.text((tx + 2, ty + 128 + 2), TAGLINE, font=tagline_font, fill=(0, 0, 0, 120))
    draw.text((tx, ty + 128), TAGLINE, font=tagline_font, fill=(196, 190, 214))

    canvas.convert("RGB").save(out_path, "PNG")
    print(f"wrote {out_path.name}  {w}x{h}")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for suffix, out_name, headline, accent in FRAMES:
        matches = list(SRC.glob(f"*{suffix}*.jpg"))
        assert len(matches) == 1, f"expected one source for {suffix}, got {matches}"
        compose(matches[0], headline, accent, OUT / out_name)
    make_icon_512(OUT / "icon-512.png")
    make_feature_graphic(OUT / "feature-graphic.png")


if __name__ == "__main__":
    main()
