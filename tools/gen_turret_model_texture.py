"""Hand-authored texture sheet for the Turret block-entity-renderer model (prefab mod).
Generates block/turret_cannon.png (128x128), UV-mapped to the exact box layout declared in
TurretModel.java (client/TurretModel.java) -- NOT AI image generation, every pixel placed via
palette + the standard Minecraft cuboid box-UV unwrap (texOffs + addBox sizes).

Layout (must stay in sync with TurretModel.createBodyLayer). Footprint of a box at texOffs(u,v)
with size (w,h,d) is 2*(w+d) wide by (d+h) tall:

  base        texOffs(0, 0)    12x2x12 -> 48x14 at (0,0)
  receiver    texOffs(48, 0)    5x5x8  -> 26x13 at (48,0)
  collar      texOffs(76, 0)    8x2x8  -> 32x10 at (76,0)
  cheek_left  texOffs(0, 16)    2x5x6  -> 16x11 at (0,16)
  cheek_right texOffs(16, 16)   2x5x6  -> 16x11 at (16,16)
  ammo_box    texOffs(32, 16)   6x5x5  -> 22x10 at (32,16)
  shroud      texOffs(54, 16)   4x4x7  -> 22x11 at (54,16)
  feed_cover  texOffs(76, 16)   4x2x5  -> 18x7  at (76,16)
  barrel      texOffs(0, 32)    2x2x4  -> 12x6  at (0,32)
  muzzle      texOffs(12, 32)   3x3x2  -> 10x5  at (12,32)
  sight       texOffs(22, 32)   1x2x1  ->  4x3  at (22,32)
  cog teeth   texOffs(26, 32)   shared flat swatch, 18x9 at (26,32) -- all 8 tooth boxes reuse it
  cog hub     texOffs(0, 48)   12x4x12 -> 48x16 at (0,48)
  item swatches            row y=64, five 16x16 squares -- see paint_item_swatches

The sheet is 128x128 rather than 64x64 purely for room: the machine-gun model has 13 footprints and
hand-packing them into 64x64 leaves no slack for the detail passes (rivets, cooling slots). Verify
the packing with the checker in the repo rather than by eye -- silent UV overlap looks like a
texturing mistake, not a layout one.

The cog is only ever drawn for the Create-powered variant (cf. ITurret#cogAngle) -- brass palette,
distinct from the gunmetal gun. It rings the block's waist, so the hub is entirely enclosed by the
opaque block model and never actually visible in game (depth test discards it); only the teeth poke
out past the side faces.
"""
import math

from PIL import Image
from pixelart_common import dither

TEX_W, TEX_H = 128, 128

PALETTE = {
    "m": (94, 96, 102),     # gunmetal mid (top faces)
    "x": (58, 60, 64),      # gunmetal dark (side faces)
    "d": (38, 40, 44),      # gunmetal darkest (bottom/underside)
    "h": (140, 142, 148),   # bevel highlight
    "y": (214, 158, 40),    # hazard amber accent
    "b": (18, 18, 20),      # near-black accent (bore, cooling slots)
    "e": (150, 24, 24),     # lens outer red
    "L": (255, 70, 60),     # lens glowing core
    "c": (196, 142, 58),    # brass mid
    "C": (232, 178, 90),    # brass highlight
    "k": (94, 64, 24),      # brass root/shadow
    "K": (46, 32, 14),      # brass darkest (edge/underside)
}

DITHER_SPEC = {
    "m": (14, 0.05, 22),
    "x": (10, 0.04, 20),
    "d": (8, 0.03, 18),
    "h": (10, 0.03, 20),
    "y": (8, 0.02, 16),
    "b": (6, 0.0, 0),
    "e": (8, 0.0, 0),
    "L": (6, 0.0, 0),
    "c": (12, 0.05, 20),
    "C": (10, 0.03, 18),
    "k": (10, 0.04, 20),
    "K": (8, 0.03, 18),
}


def new_canvas():
    return [[None for _ in range(TEX_W)] for _ in range(TEX_H)]


def rect(canvas, x0, y0, x1, y1, ch):
    """Half-open on the upper bound (x1/y1 exclusive), matching the UV footprint arithmetic."""
    for y in range(y0, y1):
        for x in range(x0, x1):
            canvas[y][x] = ch


def paint_cube(canvas, u, v, w, h, d, colors):
    """Fills the 6 faces of a box at texOffs (u,v) with size (w,h,d), matching Minecraft's
    standard CubeListBuilder UV unwrap (same layout used by every vanilla/modded ModelPart).

    Returns the face rectangles so callers can paint detail into a specific face without
    recomputing this arithmetic -- getting a face offset wrong is the classic way to end up with
    rivets on the underside.
    """
    faces = {
        "east":   (u, v + d, u + d, v + d + h),
        "front":  (u + d, v + d, u + d + w, v + d + h),
        "west":   (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back":   (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
        "top":    (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
    }
    for name, (x0, y0, x1, y1) in faces.items():
        rect(canvas, x0, y0, x1, y1, colors[name])
    return faces


def paint_rivets(canvas, face, ch="C", inset=1):
    """Brass rivet at each corner of a face -- the cheapest way to read as bolted-together plate
    rather than a flat slab, and the same motif the block textures use."""
    x0, y0, x1, y1 = face
    if x1 - x0 < 2 * inset + 1 or y1 - y0 < 2 * inset + 1:
        return
    for x in (x0 + inset, x1 - 1 - inset):
        for y in (y0 + inset, y1 - 1 - inset):
            canvas[y][x] = ch


def paint_slots(canvas, face, ch="b", step=2, margin=1):
    """Evenly spaced cooling slots down a face -- what makes the shroud read as a perforated barrel
    jacket (the single most recognisable machine-gun cue) instead of a plain tube."""
    x0, y0, x1, y1 = face
    for x in range(x0 + margin, x1 - margin, step):
        for y in range(y0 + margin, y1 - margin):
            canvas[y][x] = ch


def paint_cog_top(canvas, u, v, size):
    """Radial 8-tooth gear (brass), painted onto the cog hub's top-face UV footprint."""
    teeth = 8
    center = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            dx, dy = x - center, y - center
            r = math.hypot(dx, dy)
            if r > size / 2:
                continue
            bin_ = int((math.atan2(dy, dx) + math.pi) / (2 * math.pi) * teeth) % teeth
            tooth = bin_ % 2 == 0
            if r > size / 2 - 2:
                canvas[v + y][u + x] = "C" if tooth else "k"
            elif r > size / 2 - 5:
                canvas[v + y][u + x] = "c"
            else:
                canvas[v + y][u + x] = "k"


SWATCH_PX = 16
SWATCH_ROW = 64
SWATCH_ORDER = ("deck", "body", "plate", "dark", "bore")


def paint_item_swatches(canvas):
    """Five flat 16x16 swatches for the inventory icon (models/item/turret_machinegun.json).

    The icon cannot reuse the 3D model as-is (it is far wider than one block: the barrel alone
    reaches z=-9) nor the base block's textures (turret_top is a 16x16 plate with a red lens in the
    middle -- every small face of the icon sampled a random crop of that lens through the automatic
    UVs, which is exactly what it looked like). So the icon keeps its own compact silhouette and
    picks its colours here, through explicit "uv" rectangles.

    Each swatch is stretched over a whole face, so anything painted here must survive being squashed
    to any aspect ratio: bands and centred marks, never a detail that only reads at 1:1.

    UV arithmetic, since the sheet is 128px wide but block-model UVs are always in [0, 16]:
    a swatch at column i covers px x=16*i..16*i+16, y=64..80, i.e. uv [2*i, 8, 2*i+2, 10].
    Keep in sync with the "textures"/"uv" pairs in models/item/turret_machinegun.json.
    """
    x = {name: SWATCH_PX * i for i, name in enumerate(SWATCH_ORDER)}
    y0, y1 = SWATCH_ROW, SWATCH_ROW + SWATCH_PX

    # Deck (top faces): bevel highlight with brass rivets, same motif as the base plate's real top.
    rect(canvas, x["deck"], y0, x["deck"] + SWATCH_PX, y1, "h")
    paint_rivets(canvas, (x["deck"], y0, x["deck"] + SWATCH_PX, y1), "C", inset=3)

    # Body (carriage and receiver flanks): gunmetal with the hazard band across the middle -- the
    # same waistline the receiver wears on the 3D model, so the icon reads as the same machine.
    rect(canvas, x["body"], y0, x["body"] + SWATCH_PX, y1, "m")
    for col in range(SWATCH_PX):
        for row in (7, 8):
            canvas[y0 + row][x["body"] + col] = "y" if col % 3 else "b"

    # Plate (cheeks and barrel): darker, with three lengthwise slots -- stretched along the barrel
    # they read as the cooling jacket, which is the machine-gun cue at icon size.
    rect(canvas, x["plate"], y0, x["plate"] + SWATCH_PX, y1, "x")
    for col in (3, 8, 13):
        for row in range(3, SWATCH_PX - 3):
            canvas[y0 + row][x["plate"] + col] = "b"

    # Dark: the muzzle brake, one step blacker than the barrel so the nose steps out of it.
    rect(canvas, x["dark"], y0, x["dark"] + SWATCH_PX, y1, "b")

    # Bore: muzzle face only. Centred, so it stays centred whatever the face's proportions.
    rect(canvas, x["bore"], y0, x["bore"] + SWATCH_PX, y1, "b")
    rect(canvas, x["bore"] + 5, y0 + 5, x["bore"] + 11, y1 - 5, "e")
    rect(canvas, x["bore"] + 7, y0 + 7, x["bore"] + 9, y1 - 7, "L")


def save(canvas, path, seed=0):
    img = Image.new("RGB", (TEX_W, TEX_H))
    for y in range(TEX_H):
        for x in range(TEX_W):
            ch = canvas[y][x]
            if ch is None:
                raise ValueError(f"unset pixel at {x},{y}")
            spread, grime_chance, grime_strength = DITHER_SPEC.get(ch, (8, 0.02, 18))
            color = dither(PALETTE[ch], x, y, spread, grime_chance, grime_strength, seed)
            img.putpixel((x, y), color)
    img.save(path)


def build():
    c = new_canvas()
    rect(c, 0, 0, TEX_W, TEX_H, "d")  # filler: everything outside the UV footprints below

    # --- Carriage (yaw) ---

    # Base plate: broad gunmetal deck, brass rivets at the corners of the top face.
    f = paint_cube(c, 0, 0, 12, 2, 12, {
        "top": "m", "bottom": "d", "east": "x", "west": "x", "front": "x", "back": "x",
    })
    paint_rivets(c, f["top"], "C", inset=1)

    # Raised collar: lighter, so the carriage reads as stepped rather than one slab.
    paint_cube(c, 76, 0, 8, 2, 8, {
        "top": "h", "bottom": "d", "east": "m", "west": "m", "front": "m", "back": "m",
    })

    # Cradle cheeks: dark plate with a brass pivot boss on the outward face.
    for u, outward in ((0, "east"), (16, "west")):
        f = paint_cube(c, u, 16, 2, 5, 6, {
            "top": "m", "bottom": "d", "east": "x", "west": "x", "front": "x", "back": "x",
        })
        x0, y0, x1, y1 = f[outward]
        cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                c[cy + dy][cx + dx] = "k" if (dx or dy) else "C"

    # Ammo box: brass crate with a lighter lid -- deliberately the warmest object on the model, so
    # the eye reads "this is the ammunition" without a label.
    f = paint_cube(c, 32, 16, 6, 5, 5, {
        "top": "C", "bottom": "K", "east": "c", "west": "c", "front": "c", "back": "k",
    })
    paint_rivets(c, f["front"], "K", inset=1)
    paint_rivets(c, f["back"], "K", inset=1)

    # --- Elevating group (pitch) ---

    # Receiver: the gun's body. Highlight along the top, hazard stripe on the flanks.
    f = paint_cube(c, 48, 0, 5, 5, 8, {
        "top": "h", "bottom": "d", "east": "x", "west": "x", "front": "m", "back": "m",
    })
    for side in ("east", "west"):
        x0, y0, x1, y1 = f[side]
        for x in range(x0, x1):
            c[y1 - 2][x] = "y" if (x - x0) % 3 else "b"

    # Cooling shroud: slotted jacket around the barrel. The slots go on the two long (d x h) flanks
    # and the top, i.e. every face actually seen in profile.
    f = paint_cube(c, 54, 16, 4, 4, 7, {
        "top": "m", "bottom": "d", "east": "x", "west": "x", "front": "x", "back": "x",
    })
    paint_slots(c, f["east"])
    paint_slots(c, f["west"])
    paint_slots(c, f["top"])

    # Bare barrel beyond the shroud, then the muzzle brake with a hot amber bore.
    paint_cube(c, 0, 32, 2, 2, 4, {
        "top": "x", "bottom": "d", "east": "d", "west": "d", "front": "d", "back": "d",
    })
    f = paint_cube(c, 12, 32, 3, 3, 2, {
        "top": "b", "bottom": "b", "east": "b", "west": "b", "front": "e", "back": "b",
    })
    x0, y0, x1, y1 = f["front"]
    c[(y0 + y1) // 2][(x0 + x1) // 2] = "L"   # glowing bore, dead centre of the muzzle face

    # Feed cover over the receiver, and the front sight on the shroud.
    paint_cube(c, 76, 16, 4, 2, 5, {
        "top": "m", "bottom": "d", "east": "x", "west": "x", "front": "h", "back": "x",
    })
    paint_cube(c, 22, 32, 1, 2, 1, {
        "top": "b", "bottom": "d", "east": "b", "west": "b", "front": "b", "back": "b",
    })

    # --- Kinetic cog (Create variant only) ---

    # Teeth: one shared brass swatch reused by all 8 tooth boxes. These ARE the visible part of the
    # gear, so they take the bright tooth highlight rather than the hub's darker root tone.
    rect(c, 26, 32, 44, 41, "C")

    paint_cube(c, 0, 48, 12, 4, 12, {
        "top": "k", "bottom": "K", "east": "k", "west": "k", "front": "k", "back": "k",
    })
    paint_cog_top(c, 12, 48, 12)

    # --- Inventory icon ---

    paint_item_swatches(c)

    return c


if __name__ == "__main__":
    base = "src/main/resources/assets/turnkey_factory/textures/block"
    save(build(), f"{base}/turret_cannon.png", seed=27)
    print("done")
