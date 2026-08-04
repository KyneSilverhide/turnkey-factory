"""Hand-authored 16x16 pixel art for the Texturizer block (prefab mod).
Generates block/texturizer_top.png, texturizer_side.png, texturizer_bottom.png.
Not AI image generation: every pixel is placed deliberately via palette + rules below.

Theme: an iron paving stamp that presses a mosaic of stone/gravel/andesite tiles into
the ground (a roller drum on the side view, the mosaic itself on the top view).
"""
from PIL import Image
from pixelart_common import dither

SIZE = 16

PALETTE = {
    ".": (72, 74, 78),     # outer frame
    "x": (48, 50, 54),     # dark ring / shadow line
    "m": (108, 110, 116),  # plate mid
    "h": (150, 152, 158),  # bevel highlight
    "R": (196, 198, 204),  # rivet highlight
    "d": (40, 40, 44),     # rivet dark core / axle cap
    "s": (120, 122, 128),  # strut steel (roller mount)
    "c": (122, 122, 122),  # mosaic tile: cobblestone
    "g": (150, 138, 122),  # mosaic tile: gravel
    "a": (158, 158, 160),  # mosaic tile: andesite
    "e": (140, 140, 142),  # mosaic tile: stone
}

DITHER_SPEC = {
    ".": (10, 0.05, 22),
    "x": (8, 0.04, 20),
    "m": (14, 0.06, 24),
    "h": (10, 0.03, 20),
    "R": (6, 0.0, 0),
    "d": (6, 0.0, 0),
    "s": (10, 0.03, 20),
    "c": (16, 0.10, 26),
    "g": (16, 0.12, 28),
    "a": (14, 0.08, 24),
    "e": (14, 0.08, 24),
}

CORNERS = [(2, 2), (13, 2), (2, 13), (13, 13)]
MOSAIC_SEQ = ["c", "g", "a", "e"]


def new_canvas():
    return [[None for _ in range(SIZE)] for _ in range(SIZE)]


def rect(canvas, x0, y0, x1, y1, ch):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            canvas[y][x] = ch


def px(canvas, x, y, ch):
    canvas[y][x] = ch


def mosaic_char(col, row, phase=0):
    """Deterministic pseudo-random 2x2-block tile assignment: reads as a mixed stone
    floor (cobble/gravel/andesite/stone) without ever repeating in an obvious grid."""
    bx = col // 2 + phase
    by = row // 2
    idx = (bx * 3 + by * 5 + ((bx ^ by) % 4)) % 4
    return MOSAIC_SEQ[idx]


def mosaic_rect(canvas, x0, y0, x1, y1, phase=0):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            canvas[y][x] = mosaic_char(x, y, phase)


def save(canvas, path, seed=0):
    img = Image.new("RGB", (SIZE, SIZE))
    for y in range(SIZE):
        for x in range(SIZE):
            ch = canvas[y][x]
            if ch is None:
                raise ValueError(f"unset pixel at {x},{y}")
            spread, grime_chance, grime_strength = DITHER_SPEC.get(ch, (8, 0.02, 18))
            color = dither(PALETTE[ch], x, y, spread, grime_chance, grime_strength, seed)
            img.putpixel((x, y), color)
    img.save(path)


def build_top():
    c = new_canvas()
    rect(c, 0, 0, 15, 15, ".")
    rect(c, 1, 1, 14, 14, "h")
    mosaic_rect(c, 2, 2, 13, 13, phase=0)
    for (rx, ry) in CORNERS:
        px(c, rx, ry, "R")
    return c


def build_side():
    c = new_canvas()
    rect(c, 0, 0, 15, 15, ".")
    rect(c, 1, 1, 14, 14, "x")
    rect(c, 2, 2, 13, 13, "m")
    for (rx, ry) in CORNERS:
        px(c, rx, ry, "R")

    # mounting struts holding the roller drum
    rect(c, 4, 3, 5, 5, "s")
    rect(c, 10, 3, 11, 5, "s")

    # roller drum: mosaic band wrapped between two dark axle caps
    mosaic_rect(c, 2, 6, 13, 9, phase=1)
    for y in (6, 7, 8, 9):
        px(c, 2, y, "d")
        px(c, 13, y, "d")

    # freshly stamped ground strip, pressed just below the drum
    mosaic_rect(c, 2, 11, 13, 12, phase=2)
    rect(c, 2, 13, 13, 13, "x")
    return c


def build_bottom():
    c = new_canvas()
    rect(c, 0, 0, 15, 15, ".")
    rect(c, 1, 1, 14, 14, "x")
    for (rx, ry) in CORNERS:
        px(c, rx, ry, "d")
    # diamond-plate tread bumps
    for y in range(2, 14):
        for x in range(2, 14):
            if (x + y) % 4 == 0:
                c[y][x] = "m"
            elif (x + y) % 4 == 1:
                c[y][x] = "h"
            else:
                c[y][x] = "x"
    return c


if __name__ == "__main__":
    base = "src/main/resources/assets/turnkey_factory/textures/block"
    save(build_top(), f"{base}/texturizer_top.png", seed=4)
    save(build_side(), f"{base}/texturizer_side.png", seed=5)
    save(build_bottom(), f"{base}/texturizer_bottom.png", seed=6)
    print("done")
