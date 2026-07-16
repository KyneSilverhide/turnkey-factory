"""Hand-authored 16x16 pixel art for the Leveler block (prefab mod).
Generates block/leveler_top.png, leveler_side.png, leveler_bottom.png.
Not AI image generation: every pixel is placed deliberately via palette + rules below.
"""
from PIL import Image
from pixelart_common import dither

SIZE = 16

# per-character dither profile: (spread, grime_chance, grime_strength)
# broad flat fields get heavier dither; small crisp accents stay near-flat.
DITHER_SPEC = {
    ".": (10, 0.05, 22),
    "d": (10, 0.05, 22),
    "m": (16, 0.07, 26),
    "h": (14, 0.04, 22),
    "s": (8, 0.0, 0),
    "r": (4, 0.0, 0),
    "R": (6, 0.0, 0),
    "y": (10, 0.03, 20),
    "k": (8, 0.02, 18),
    "g": (4, 0.0, 0),
    "l": (6, 0.0, 0),
    "b": (14, 0.05, 24),
    "B": (12, 0.05, 22),
    "e": (14, 0.05, 22),
}

PALETTE = {
    ".": (66, 70, 76),     # frame / deep shadow
    "d": (52, 55, 60),     # darkest shadow (under blade, cut line)
    "m": (108, 112, 120),  # iron mid (base plate)
    "h": (146, 150, 158),  # iron highlight (bevel)
    "s": (88, 92, 99),     # seam line (darker mid)
    "r": (36, 38, 42),     # rivet dark core
    "R": (176, 180, 188),  # rivet highlight
    "y": (232, 178, 41),   # safety yellow
    "k": (26, 26, 28),     # safety black
    "g": (66, 205, 104),   # level-bubble green
    "l": (206, 232, 224),  # level-glass pale
    "b": (198, 203, 210),  # blade steel
    "B": (140, 145, 153),  # blade steel shadow
    "e": (122, 86, 50),    # scraped-earth accent
}


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


def build_top():
    c = new_canvas()
    rect(c, 0, 0, 15, 15, ".")          # outer frame
    rect(c, 1, 1, 14, 14, "h")          # bevel highlight ring
    rect(c, 2, 2, 13, 13, "m")          # plate interior
    rect(c, 2, 5, 13, 5, "s")           # upper seam
    rect(c, 2, 10, 13, 10, "s")         # lower seam
    for (rx, ry) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        px(c, rx, ry, "R")
    # level gauge: tube with glass and a centered green bubble
    rect(c, 4, 7, 11, 8, "r")
    rect(c, 5, 7, 10, 8, "l")
    rect(c, 7, 7, 8, 8, "g")
    return c


def stripe_row(offset):
    row = []
    for x in range(SIZE):
        idx = x + offset
        band = (idx // 2) % 2
        row.append("y" if band == 0 else "k")
    return row


def build_side():
    c = new_canvas()
    # hazard tape top/bottom (diagonal 2px bands, full width)
    c[0] = stripe_row(0)
    c[1] = stripe_row(1)
    c[14] = stripe_row(0)
    c[15] = stripe_row(1)
    # frame + bevel down the sides for rows 2..13
    for y in range(2, 14):
        c[y][0] = "."
        c[y][1] = "h"
        c[y][14] = "h"
        c[y][15] = "."
    rect(c, 2, 2, 13, 13, "m")
    # mounting rivets near the top of the plate
    px(c, 3, 3, "R")
    px(c, 12, 3, "R")
    # grader blade: widening trapezoid, beveled highlight top, dark cutting edge, scraped-earth accent
    rect(c, 6, 5, 9, 5, "h")
    rect(c, 5, 6, 10, 6, "b")
    rect(c, 4, 7, 11, 7, "b")
    rect(c, 3, 8, 12, 8, "b")
    rect(c, 2, 9, 13, 9, "b")
    rect(c, 2, 10, 13, 10, "B")
    rect(c, 2, 11, 13, 11, "d")
    rect(c, 2, 12, 13, 12, "e")
    px(c, 3, 13, "R")
    px(c, 12, 13, "R")
    return c


def build_bottom():
    c = new_canvas()
    rect(c, 0, 0, 15, 15, ".")
    rect(c, 1, 1, 14, 14, "d")
    for (rx, ry) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        px(c, rx, ry, "r")
    # diamond-plate tread bumps
    for y in range(2, 14):
        for x in range(2, 14):
            if (x + y) % 4 == 0:
                c[y][x] = "m"
            elif (x + y) % 4 == 1:
                c[y][x] = "h"
    return c


if __name__ == "__main__":
    base = "src/main/resources/assets/prefab/textures/block"
    save(build_top(), f"{base}/leveler_top.png", seed=1)
    save(build_side(), f"{base}/leveler_side.png", seed=2)
    save(build_bottom(), f"{base}/leveler_bottom.png", seed=3)
    print("done")
