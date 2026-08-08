"""Hand-authored 16x16 pixel art for the Create-powered Turret variant (prefab mod).
Generates block/turret_create_top.png, turret_create_side.png, turret_create_bottom.png.
Not AI image generation: every pixel is placed deliberately via palette + rules below.

Theme: andesite-and-brass kinetic housing (Create's own palette), distinct from the coal turret's
cold gunmetal/targeting-lens look.

Two rotation power inputs (TurretCreateBlock implements ICogWheel, verified via javap on
RotationPropagator — cf. its class javadoc), each with its own visual tell so a player can find them
without guessing:
  - Bottom: the actual vertical-shaft socket (a plain shaft, or another kinetic block, connects here
    from below — like a Millstone).
  - Side: a recessed slot at the block's waist, out of which the animated brass cog
    (TurretModel/TurretRenderer, cf. ITurret#cogAngle) physically protrudes — that gear is real
    geometry, so this texture only draws the housing it emerges from, never teeth of its own. This
    is where a Large Cogwheel meshes in horizontally (also like a Millstone's side gear).
  - Top: a collar with a dark shaft hole at the centre. Decorative only — no shaft connects here, and
    the cannon assembly covers most of it anyway.
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


def draw_shaft_hole(canvas):
    """Dark shaft socket with a lighter rim, centred on the texture.

    Measured from the TEXTURE MIDLINE (dx = x - 7.5), not from a pixel centre. A 16x16 grid has no
    middle pixel -- the centre falls on the 7/8 boundary -- and the previous version faked it by
    stamping this shape twice, at (7,7) and (8,8). Two circles one pixel apart on the diagonal do
    not add up to a centred circle: they made a lopsided blob skewed along that diagonal, which is
    what showed up in game as an off-axis smudge.

    The <=5.0 core lands on exactly the 4x4 block at pixels 6..9 -- the same footprint as a real
    Create shaft end (verified against create:block/axis_top.png, whose shaft occupies 6..9 on both
    axes), so the socket reads at the right scale for the thing that plugs into it.
    """
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x - 7.5, y - 7.5
            d2 = dx * dx + dy * dy
            if d2 <= 5.0:
                px(canvas, x, y, "s")
            elif d2 <= 11.0:
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
    draw_shaft_hole(c)
    return c


def build_side():
    c = new_canvas()
    rect(c, 0, 0, 15, 15, ".")
    rect(c, 1, 1, 14, 14, "x")
    rect(c, 2, 2, 13, 13, "m")
    for (rx, ry) in CORNERS:
        px(c, rx, ry, "R")

    # Recessed slot at the block's waist (rows 6-9 == block y 6..10), where the real 3D gear
    # (TurretModel.COG) spins and pokes 2px out past this face. Deliberately NOT painted teeth: the
    # teeth exist as geometry now, so drawing them here too would render teeth over teeth. A plain
    # dark slot instead, so the gear reads as emerging from inside the housing.
    for x in range(2, 14):
        c[6][x] = "s"   # ombre portée sous la lèvre supérieure
        c[7][x] = "x"
        c[8][x] = "x"
        c[9][x] = "S"   # lèvre inférieure, légèrement plus claire

    rect(c, 2, 13, 13, 13, "x")
    return c


def build_bottom():
    c = new_canvas()
    rect(c, 0, 0, 15, 15, ".")
    rect(c, 1, 1, 14, 14, "x")
    rect(c, 2, 2, 13, 13, "m")
    for (rx, ry) in CORNERS:
        px(c, rx, ry, "d")

    # THE functional connection point: a vertical shaft plugs in here from below (cf.
    # TurretCreateBlock#hasShaftTowards == DOWN). Drawn as a proper coupling rather than the top's
    # decorative collar, because this is the face a player inspects when working out where the
    # rotation goes -- and it's only ever visible with nothing attached (a connected shaft block
    # covers it).
    #
    # Every ring below is symmetric about the 7/8 midline (4..11, 5..10, 6..9 all straddle it
    # evenly), so nothing sits half a pixel off-axis.

    # Mounting flange, bevelled: lit along the top/left lip, shadowed along the bottom/right.
    rect(c, 4, 4, 11, 11, "S")
    rect(c, 4, 4, 11, 4, "h")
    rect(c, 4, 4, 4, 11, "h")
    rect(c, 4, 11, 11, 11, "x")
    rect(c, 11, 4, 11, 11, "x")

    # Four brass bolts holding the flange to the housing.
    for (bx, by) in ((5, 5), (10, 5), (5, 10), (10, 10)):
        px(c, bx, by, "R")

    # The bore itself: 4x4 at pixels 6..9, matching a real Create shaft's cross-section exactly
    # (create:block/axis_top.png occupies 6..9 on both axes).
    rect(c, 6, 6, 9, 9, "s")
    return c


if __name__ == "__main__":
    base = "src/main/resources/assets/turnkey_factory/textures/block"
    save(build_top(), f"{base}/turret_create_top.png", seed=37)
    save(build_side(), f"{base}/turret_create_side.png", seed=38)
    save(build_bottom(), f"{base}/turret_create_bottom.png", seed=39)
    print("done")
