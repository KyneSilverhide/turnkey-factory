"""Hand-authored pixel art icon for the mod project page (CurseForge/GitHub).

Not a game asset (no blockstate/model references it) — a miniature of what the
mod's Brique theme actually builds (cf. BuildStyles.brickStyle/Palette): walls
are a per-block mosaic of brick/granite/polished-granite/andesite (no mortar
lines — Minecraft blocks just butt against each other), a dark deepslate-tile
pitched roof with an off-center vent, glass-pane windows, andesite corner
pilasters, an andesite foundation course, and a wide paneled loading door.

Deliberately NOT two symmetric square windows + a centered vertical accent +
a narrow centered dark slot below (that specific arrangement reads as a face —
2 eyes/nose/mouth — per user feedback on an earlier version): this uses THREE
evenly spaced windows and a single off-center roof vent instead, and the
building fills almost the entire canvas rather than sitting in a padded badge.
"""
from PIL import Image
from pixelart_common import dither

SIZE = 32
SCALE = 16  # -> 512x512 output

PALETTE = {
    "F0": (10, 12, 16),     # thin outer outline
    "B":  (20, 26, 36),     # deep navy background/sky
    "R":  (54, 59, 67),     # dark tile roof (deepslate tiles, per brickStyle.roofStair/Ridge)
    "V":  (120, 122, 118),  # roof vent (andesite curb, off-center — cf. ExteriorDecorator.roofVent)
    "K":  (145, 82, 66),    # brick (wall mosaic)
    "G":  (176, 130, 116),  # granite (wall mosaic)
    "P":  (190, 145, 130),  # polished granite (wall mosaic + pilasters)
    "A":  (140, 140, 136),  # andesite (wall mosaic + foundation)
    "GF": (110, 120, 124),  # window frame
    "GL": (196, 214, 219),  # window glass
    "D":  (24, 27, 33),     # loading door, dark panel
    "D2": (34, 38, 45),     # loading door, lighter panel (paneled/ribbed look)
}

DITHER_SPEC = {
    "F0": (0, 0.0, 0),
    "B":  (6, 0.0, 0),
    "R":  (8, 0.02, 16),
    "V":  (6, 0.02, 12),
    "K":  (10, 0.03, 18),
    "G":  (10, 0.03, 18),
    "P":  (8, 0.02, 14),
    "A":  (8, 0.02, 14),
    "GF": (0, 0.0, 0),
    "GL": (4, 0.0, 0),
    "D":  (0, 0.0, 0),
    "D2": (0, 0.0, 0),
}

# Wall mosaic weights, matching BuildStyles.brickStyle's wall Palette roughly
# (brick variants ~9, granite 6, polished granite 3, andesite 1 out of 19).
_MOSAIC = [("K", 9), ("G", 6), ("P", 3), ("A", 1)]
_MOSAIC_TOTAL = sum(w for _, w in _MOSAIC)
_CELL = 2  # pixels per "block" cell


def new_canvas():
    return [[None for _ in range(SIZE)] for _ in range(SIZE)]


def rect(canvas, x0, y0, x1, y1, ch):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            canvas[y][x] = ch


def _hash(cx, cy):
    h = (cx * 374761393 + cy * 668265263) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFFFFFFFF


def _mosaic_pick(cx, cy):
    r = _hash(cx, cy) % _MOSAIC_TOTAL
    for ch, w in _MOSAIC:
        if r < w:
            return ch
        r -= w
    return _MOSAIC[-1][0]


def fill_mosaic(canvas, x0, y0, x1, y1):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            canvas[y][x] = _mosaic_pick((x - x0) // _CELL, (y - y0) // _CELL)


def build_icon():
    c = new_canvas()
    rect(c, 0, 0, 31, 31, "B")
    rect(c, 0, 0, 31, 31, "F0")   # thin 1px outline
    rect(c, 1, 1, 30, 30, "B")    # interior background — building fills almost all of this

    # Roof: big pitched triangle, apex near the very top, base 26 wide.
    roof_rows = [
        (1, 15, 16), (2, 14, 17), (3, 13, 18), (4, 12, 19), (5, 11, 20),
        (6, 10, 21), (7, 9, 22), (8, 8, 23), (9, 7, 24), (10, 6, 25),
        (11, 5, 26), (12, 4, 27), (13, 3, 28),
    ]
    for y, left, right in roof_rows:
        rect(c, left, y, right, y, "R")

    # Roof vent, off-center (not on the ridge) — avoids a symmetric "nose" cue.
    rect(c, 20, 5, 21, 7, "V")

    # Wall: brick/granite/andesite mosaic, no mortar lines, full width.
    fill_mosaic(c, 3, 14, 28, 29)

    # Corner pilasters (solid polished granite).
    rect(c, 3, 14, 4, 29, "P")
    rect(c, 27, 14, 28, 29, "P")

    # Three evenly spaced windows (not two — avoids reading as eyes).
    for wx in (5, 13, 21):
        rect(c, wx, 17, wx + 5, 21, "GF")
        rect(c, wx + 1, 18, wx + 4, 20, "GL")

    # Wide paneled loading door, low and off the vertical centerline of any
    # single feature above it.
    rect(c, 9, 24, 22, 29, "D")
    for dx in range(9, 23, 3):
        rect(c, dx, 24, dx, 29, "D2")

    # Foundation course (andesite), slightly wider than the wall.
    rect(c, 2, 30, 29, 30, "A")

    return c


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
    img = img.resize((SIZE * SCALE, SIZE * SCALE), Image.NEAREST)
    img.save(path)


if __name__ == "__main__":
    save(build_icon(), "branding/mod_icon.png", seed=21)
    print("done")
