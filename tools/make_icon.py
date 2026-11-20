"""Generate the dot-matrix "terminal" launcher icon from the Hermes Agent artwork.

The source is the original monochrome artwork (white background, black ink). It is sampled onto a
coarse grid and drawn as amber phosphor dots with glow and faint scanlines, so the icon reads like
the picture rendered on an old terminal screen.

Usage:  python tools/make_icon.py [--preview out.png]
Writes app/src/main/res/mipmap-*/ic_launcher{,_round,_foreground}.png and the background colour.
"""
import argparse
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
SOURCE = os.path.join(ROOT, "tools", "icon-source.png")

SCREEN = (0x17, 0x0D, 0x02)       # app background (dark amber terminal)
PHOSPHOR = (0xFF, 0xAC, 0x02)     # app accent
GRID = 44
INK, FACE, BG = 0, 255, 90       # picture shown positive: skin glows, ink off, paper dim                         # dots across the artwork
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def sample(src: Image.Image, grid: int) -> list[list[float]]:
    """Brightness level 0..1 per cell.

    White inside the figure (face, headband, highlights) glows brightest, black ink is off, and the
    paper around the figure (white connected to the border) is a dim level so the dark hair
    silhouette stays readable at launcher size.
    """
    g = src.convert("L").point(lambda p: 255 if p > 128 else 0)
    w, h = g.size
    seeds = [(x, 0) for x in range(0, w, 4)] + [(x, h - 1) for x in range(0, w, 4)] + \
            [(0, y) for y in range(0, h, 4)] + [(w - 1, y) for y in range(0, h, 4)]
    filled = g.copy()
    for s in seeds:
        if filled.getpixel(s) == 255:
            ImageDraw.floodfill(filled, s, 128)
    level = filled.point(lambda p: {0: INK, 128: BG, 255: FACE}.get(p, 0))
    small = level.resize((grid, grid), Image.Resampling.BOX)
    return [[small.getpixel((x, y)) / 255 for x in range(grid)] for y in range(grid)]


def render_screen(size: int, cells: list[list[float]], art_frac: float) -> Image.Image:
    """Dark screen with phosphor dots; the artwork spans art_frac of the canvas, centred."""
    scale = 4  # supersample for smooth glow
    S = size * scale
    img = Image.new("RGB", (S, S), SCREEN)
    grid = len(cells)
    art = S * art_frac
    pitch = art / grid
    off = (S - art) / 2
    dots = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(dots)
    gap = pitch * 0.14
    for y, row in enumerate(cells):
        for x, v in enumerate(row):
            if v < 0.08:
                continue
            # Quantise to three phosphor levels like a real low-colour terminal.
            level = 255 if v > 0.62 else (150 if v > 0.4 else 70)
            x0 = off + x * pitch + gap
            y0 = off + y * pitch + gap
            d.rectangle([x0, y0, x0 + pitch - 2 * gap, y0 + pitch - 2 * gap], fill=level)
    glow = dots.filter(ImageFilter.GaussianBlur(pitch * 0.6))
    for layer, strength in ((glow, 0.55), (dots, 1.0)):
        tint = Image.new("RGB", (S, S), PHOSPHOR)
        mask = layer.point(lambda p, s=strength: int(p * s))
        img.paste(tint, (0, 0), mask)
    # Scanlines.
    lines = Image.new("L", (S, S), 0)
    ld = ImageDraw.Draw(lines)
    step = max(2, int(pitch / 2))
    for yy in range(0, S, step):
        ld.rectangle([0, yy, S, yy + step // 3], fill=38)
    img.paste(Image.new("RGB", (S, S), (0, 0, 0)), (0, 0), lines)
    return img.resize((size, size), Image.Resampling.LANCZOS)


def legacy(size: int, cells, round_: bool) -> Image.Image:
    screen = render_screen(size, cells, 0.78).convert("RGBA")
    mask = Image.new("L", (size * 4, size * 4), 0)
    md = ImageDraw.Draw(mask)
    if round_:
        md.ellipse([0, 0, size * 4 - 1, size * 4 - 1], fill=255)
    else:
        md.rounded_rectangle([0, 0, size * 4 - 1, size * 4 - 1], radius=size * 4 * 0.18, fill=255)
    mask = mask.resize((size, size), Image.Resampling.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(screen, (0, 0), mask)
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    args = ap.parse_args()
    src = Image.open(SOURCE).convert("RGBA")
    flat = Image.new("RGBA", src.size, (255, 255, 255, 255))
    flat.alpha_composite(src)
    bbox = src.getchannel("A").getbbox()
    cells = sample(flat.crop(bbox), GRID)
    if args.preview:
        row = [render_screen(432, cells, 66 / 108), legacy(192, cells, False), legacy(192, cells, True)]
        sheet = Image.new("RGB", (432 + 192 * 2 + 40, 432), (240, 240, 240))
        sheet.paste(row[0], (0, 0))
        sheet.paste(row[1], (452, 0), row[1])
        sheet.paste(row[2], (452 + 192 - 192, 220), row[2])
        sheet.save(args.preview)
        return
    for name, k in DENSITIES.items():
        folder = os.path.join(RES, f"mipmap-{name}")
        # Adaptive foreground: 108dp canvas, art inside the 66dp safe zone. The screen colour fills
        # the whole layer so masks of any shape show a seamless terminal.
        render_screen(int(108 * k), cells, 66 / 108).save(os.path.join(folder, "ic_launcher_foreground.png"))
        legacy(int(48 * k), cells, False).save(os.path.join(folder, "ic_launcher.png"))
        legacy(int(48 * k), cells, True).save(os.path.join(folder, "ic_launcher_round.png"))
    colors = os.path.join(RES, "values", "colors.xml")
    with open(colors, "w", encoding="utf-8", newline="\n") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                '    <color name="ic_launcher_background">#FF170D02</color>\n</resources>\n')


if __name__ == "__main__":
    main()
