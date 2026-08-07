"""Hand-authored 16x16 pixel art for the Create-powered Turret variant (prefab mod).
Generates block/turret_create_top.png, turret_create_side.png, turret_create_bottom.png.
Not AI image generation: every pixel is placed deliberately via palette + rules below.

Theme: andesite-and-brass kinetic housing (Create's own palette), distinct from the coal turret's
cold gunmetal/targeting-lens look. Top: a collar with a dark shaft hole at the centre — the animated
brass cog (TurretModel/TurretRenderer, cf. ITurret#cogAngle) visually sits right on top of it, in
phase with the block's shaft. Side: andesite plate with brass rivets and a rotation-arrow band
instead of the coal turret's black/amber hazard stripe.
"""
from PIL import Image
from pixelart_common import dither

SIZE = 16

PALETTE = {
    ".": (74, 76, 78),       # andesite outer frame
    "x": (52, 54, 56),       # andesite dark ring / shadow line
    "m": (110, 112, 114),    # andesite plate mid
    "h": (158, 160, 162),    # andesite bevel highlight
    "R": (232, 178, 90),     # brass rivet highlight
    "d": (94, 64, 24),       # brass rivet dark core
    "b": (46, 32, 14),       # brass shadow band
    "y": (196, 142, 58),     # brass accent band
    "s": (26, 26, 28),       # shaft hole, near-black
    "S": (48, 48, 50),       # shaft hole rim
}

DITHER_SPEC = {
    ".": (10, 0.04, 20),
    "x": (8, 0.03, 18),
    "m": (14, 0.05, 22),
    "h": (10, 0.03, 20),
    "R": (6, 0.0, 0),
    "d": (6, 0.0, 0),
    "b": (6, 0.0, 0),
    "y": (8, 0.02, 16),
    "s": (6, 0.0, 0),
    "S": (6, 0.0, 0),
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


def draw_shaft_hole(canvas, cx, cy):
    """Dark circular hole (2-pixel radius) with a lighter rim — where the animated shaft/cog
    (TurretRenderer) visually plugs into the static block below it."""
    for y in range(cy - 3, cy + 4):
        for x in range(cx - 3, cx + 4):
            dx, dy = x - cx, y - cy
            d2 = dx * dx + dy * dy
            if d2 <= 4:
                px(canvas, x, y, "s")
            elif d2 <= 9:
                px(canvas, x, y, "S")


def build_top():
    c = new_canvas()
    rect(c, 0, 0, 15, 15, ".")
    rect(c, 1, 1, 14, 14, "h")
    rect(c, 2, 2, 13, 13, "m")
    for (rx, ry) in CORNERS:
        px(c, rx, ry, "R")
    # brass collar ring around the shaft hole
    for y in range(4, 12):
        for x in range(4, 12):
            dx, dy = x - 7.5, y - 7.5
            if 9 <= dx * dx + dy * dy <= 16:
                c[y][x] = "y"
    draw_shaft_hole(c, 7, 7)
    draw_shaft_hole(c, 8, 8)
    return c


def build_side():
    c = new_canvas()
    rect(c, 0, 0, 15, 15, ".")
    rect(c, 1, 1, 14, 14, "x")
    rect(c, 2, 2, 13, 13, "m")
    for (rx, ry) in CORNERS:
        px(c, rx, ry, "R")

    # rotation band across the middle, brass instead of the coal turret's black/amber hazard stripe
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
    # diamond-plate tread bumps, andesite tones
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
    save(build_top(), f"{base}/turret_create_top.png", seed=37)
    save(build_side(), f"{base}/turret_create_side.png", seed=38)
    save(build_bottom(), f"{base}/turret_create_bottom.png", seed=39)
    print("done")
