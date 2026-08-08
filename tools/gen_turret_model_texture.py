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

    return c


if __name__ == "__main__":
    base = "src/main/resources/assets/turnkey_factory/textures/block"
    save(build(), f"{base}/turret_cannon.png", seed=27)
    print("done")
