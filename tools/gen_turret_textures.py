"""Hand-authored 16x16 pixel art for the Turret block (prefab mod).
Generates block/turret_top.png, turret_side.png, turret_bottom.png.
Not AI image generation: every pixel is placed deliberately via palette + rules below.

Theme: a gunmetal weapons housing. Top: a red targeting lens seen head-on inside a riveted
bezel (echoes the block-entity renderer's barrel, which pivots above this static base).
Side: riveted armor plate with a black/amber hazard stripe band, signalling "danger, turret".
"""
from PIL import Image
from pixelart_common import dither

SIZE = 16

PALETTE = {
    ".": (58, 60, 64),      # outer frame, dark gunmetal
    "x": (38, 40, 44),      # dark ring / shadow line
    "m": (94, 96, 102),     # plate mid
    "h": (140, 142, 148),   # bevel highlight
    "R": (190, 192, 198),   # rivet highlight
    "d": (26, 26, 28),      # rivet dark core
    "b": (18, 18, 20),      # hazard stripe, black
    "y": (214, 158, 40),    # hazard stripe, amber
    "e": (150, 24, 24),     # lens, outer red
    "L": (255, 70, 60),     # lens, glowing core
    "g": (60, 12, 12),      # lens, dark bezel ring
}

DITHER_SPEC = {
    ".": (10, 0.05, 22),
    "x": (8, 0.04, 20),
    "m": (14, 0.06, 24),
    "h": (10, 0.03, 20),
    "R": (6, 0.0, 0),
    "d": (6, 0.0, 0),
    "b": (6, 0.0, 0),
    "y": (8, 0.02, 16),
    "e": (8, 0.0, 0),
    "L": (6, 0.0, 0),
    "g": (6, 0.0, 0),
}

CORNERS = [(2, 2), (13, 2), (2, 13), (13, 13)]


def new_canvas():
    return [[None for _ in range(SIZE)] for _ in range(SIZE)]


def rect(canvas, x0, y0, x1, y1, ch):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            canvas[y][x] = ch


def px(canvas, x, y, ch):
    canvas[y][x] = ch


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


def draw_lens(canvas, cx, cy):
    """Circular targeting lens (3-pixel radius), glowing core surrounded by a dark bezel ring."""
    for y in range(cy - 3, cy + 4):
        for x in range(cx - 3, cx + 4):
            dx, dy = x - cx, y - cy
            d2 = dx * dx + dy * dy
            if d2 <= 1:
                px(canvas, x, y, "L")
            elif d2 <= 5:
                px(canvas, x, y, "e")
            elif d2 <= 10:
                px(canvas, x, y, "g")


def build_top():
    c = new_canvas()
    rect(c, 0, 0, 15, 15, ".")
    rect(c, 1, 1, 14, 14, "h")
    rect(c, 2, 2, 13, 13, "m")
    for (rx, ry) in CORNERS:
        px(c, rx, ry, "R")
    draw_lens(c, 7, 7)
    draw_lens(c, 8, 7)
    return c


def build_side():
    c = new_canvas()
    rect(c, 0, 0, 15, 15, ".")
    rect(c, 1, 1, 14, 14, "x")
    rect(c, 2, 2, 13, 13, "m")
    for (rx, ry) in CORNERS:
        px(c, rx, ry, "R")

    # hazard stripe band across the middle, diagonal alternating black/amber
    for y in (6, 7, 8, 9):
        for x in range(2, 14):
            c[y][x] = "y" if (x + y) % 4 < 2 else "b"

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
    save(build_top(), f"{base}/turret_top.png", seed=17)
    save(build_side(), f"{base}/turret_side.png", seed=18)
    save(build_bottom(), f"{base}/turret_bottom.png", seed=19)
    print("done")
