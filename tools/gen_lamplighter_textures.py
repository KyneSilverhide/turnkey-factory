"""Hand-authored 16x16 pixel art for the Lamplighter block (prefab mod).
Generates block/lamplighter_top.png, lamplighter_side.png, lamplighter_bottom.png.
Not AI image generation: every pixel is placed deliberately via palette + rules below.

Theme: an iron dispenser box with a lit lantern icon stamped on it (top: the lantern
seen head-on inside an iron bezel; side: the same lantern hanging off a small bracket
and chain, echoing the fixture the block actually builds).
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
    "s": (120, 122, 128),  # strut steel (bracket)
    "k": (30, 30, 32),     # lantern frame / chain link, near-black iron
    "y": (255, 196, 92),   # lantern glow, warm core
    "o": (214, 146, 46),   # lantern glow, amber edge
}

DITHER_SPEC = {
    ".": (10, 0.05, 22),
    "x": (8, 0.04, 20),
    "m": (14, 0.06, 24),
    "h": (10, 0.03, 20),
    "R": (6, 0.0, 0),
    "d": (6, 0.0, 0),
    "s": (10, 0.03, 20),
    "k": (6, 0.0, 0),
    "y": (10, 0.0, 0),
    "o": (10, 0.0, 0),
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


def draw_lantern(canvas, cx, top_y):
    """Small lantern glyph (frame + glowing core), cx = horizontal center, top_y = frame top."""
    # cap
    px(canvas, cx, top_y, "k")
    # frame sides
    for y in (top_y + 1, top_y + 2, top_y + 3):
        px(canvas, cx - 1, y, "k")
        px(canvas, cx + 1, y, "k")
    # glow core
    px(canvas, cx, top_y + 1, "o")
    px(canvas, cx, top_y + 2, "y")
    px(canvas, cx, top_y + 3, "o")
    # base
    px(canvas, cx - 1, top_y + 4, "k")
    px(canvas, cx, top_y + 4, "k")
    px(canvas, cx + 1, top_y + 4, "k")


def build_top():
    c = new_canvas()
    rect(c, 0, 0, 15, 15, ".")
    rect(c, 1, 1, 14, 14, "h")
    rect(c, 2, 2, 13, 13, "m")
    for (rx, ry) in CORNERS:
        px(c, rx, ry, "R")
    # lantern seen head-on, centered in the bezel
    draw_lantern(c, 7, 5)
    draw_lantern(c, 8, 5)
    return c


def build_side():
    c = new_canvas()
    rect(c, 0, 0, 15, 15, ".")
    rect(c, 1, 1, 14, 14, "x")
    rect(c, 2, 2, 13, 13, "m")
    for (rx, ry) in CORNERS:
        px(c, rx, ry, "R")

    # mounting bracket, top corners (echoes the fixture's trapdoor bracket)
    rect(c, 3, 3, 5, 4, "s")
    rect(c, 10, 3, 12, 4, "s")

    # chain dropping from the bracket
    for y in (5, 6, 7):
        px(c, 7, y, "k")
        px(c, 8, y, "k")

    # lantern hanging at the bottom of the chain
    draw_lantern(c, 7, 8)
    draw_lantern(c, 8, 8)

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
    save(build_top(), f"{base}/lamplighter_top.png", seed=7)
    save(build_side(), f"{base}/lamplighter_side.png", seed=8)
    save(build_bottom(), f"{base}/lamplighter_bottom.png", seed=9)
    print("done")
