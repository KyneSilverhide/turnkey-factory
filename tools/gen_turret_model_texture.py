"""Hand-authored texture sheet for the Turret block-entity-renderer model (prefab mod).
Generates block/turret_cannon.png (64x64), UV-mapped to the exact box layout declared in
TurretModel.java (client/TurretModel.java) — NOT AI image generation, every pixel placed via
palette + the standard Minecraft cuboid box-UV unwrap (texOffs + addBox sizes).

Layout (must stay in sync with TurretModel.createBodyLayer):
  turntable  texOffs(0, 0)   size 10x3x10  -> footprint 40x13 at (0,0)
  mount      texOffs(0, 13)  size  6x4x6   -> footprint 24x10 at (0,13)
  barrel     texOffs(0, 23)  size  3x3x10  -> footprint 26x13 at (0,23)
  muzzle     texOffs(0, 36)  size  4x4x2   -> footprint 12x6  at (0,36)
  cog        texOffs(0, 46)  size 14x1x14  -> footprint 56x15 at (0,46)

The cog is only ever drawn for the Create-powered variant (cf. ITurret#cogAngle) — brass palette,
distinct from the gunmetal cannon, radial 8-tooth gear painted onto its top face.
"""
import math

from PIL import Image
from pixelart_common import dither

TEX_W, TEX_H = 64, 64

PALETTE = {
    "m": (94, 96, 102),     # gunmetal mid (top faces)
    "x": (58, 60, 64),      # gunmetal dark (side faces)
    "d": (38, 40, 44),      # gunmetal darkest (bottom/underside)
    "h": (140, 142, 148),   # bevel highlight
    "y": (214, 158, 40),    # hazard amber accent
    "b": (18, 18, 20),      # near-black accent
    "e": (150, 24, 24),     # lens outer red
    "L": (255, 70, 60),     # lens glowing core
    "c": (196, 142, 58),    # cog: brass mid
    "C": (232, 178, 90),    # cog: brass tooth highlight
    "k": (94, 64, 24),      # cog: brass root/shadow
    "K": (46, 32, 14),      # cog: brass darkest (edge/underside)
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
    for y in range(y0, y1):
        for x in range(x0, x1):
            canvas[y][x] = ch


def paint_cube(canvas, u, v, w, h, d, colors):
    """Fills the 6 faces of a box at texOffs (u,v) with size (w,h,d), matching Minecraft's
    standard CubeListBuilder UV unwrap (same layout used by every vanilla/modded ModelPart)."""
    rect(canvas, u, v + d, u + d, v + d + h, colors["east"])
    rect(canvas, u + d, v + d, u + d + w, v + d + h, colors["front"])
    rect(canvas, u + d + w, v + d, u + 2 * d + w, v + d + h, colors["west"])
    rect(canvas, u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h, colors["back"])
    rect(canvas, u + d, v, u + d + w, v + d, colors["top"])
    rect(canvas, u + d + w, v, u + d + 2 * w, v + d, colors["bottom"])


def paint_cog_top(canvas, u, v, size):
    """Radial 8-tooth gear (brass), painted onto the cog's top-face UV footprint — the only part
    of the shared cannon sheet the Create variant animates independently of the cannon's aim."""
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
    rect(c, 0, 0, TEX_W, TEX_H, "d")  # filler: everything outside the 4 UV footprints below

    # Turntable: gunmetal disc, lighter top, darker rim, amber warning stripe on the front face.
    paint_cube(c, 0, 0, 10, 3, 10, {
        "top": "m", "bottom": "d",
        "east": "x", "west": "x", "front": "y", "back": "x",
    })

    # Mount: plain dark gunmetal block.
    paint_cube(c, 0, 13, 6, 4, 6, {
        "top": "h", "bottom": "d", "east": "x", "front": "x", "west": "x", "back": "x",
    })

    # Barrel: dark gunmetal shaft, highlight strip on top.
    paint_cube(c, 0, 23, 3, 3, 10, {
        "top": "h", "bottom": "d", "east": "x", "front": "x", "west": "x", "back": "x",
    })

    # Muzzle: glowing red lens tip, matches the static block's targeting-lens theme.
    paint_cube(c, 0, 36, 4, 4, 2, {
        "top": "e", "bottom": "b", "east": "e", "front": "L", "west": "e", "back": "b",
    })

    # Cog (Create variant only, cf. ITurret#cogAngle): brass plate, edges first then the radial
    # gear pattern painted over its top face.
    paint_cube(c, 0, 46, 14, 1, 14, {
        "top": "k", "bottom": "K", "east": "k", "front": "k", "west": "k", "back": "k",
    })
    paint_cog_top(c, 14, 46, 14)

    return c


if __name__ == "__main__":
    base = "src/main/resources/assets/turnkey_factory/textures/block"
    save(build(), f"{base}/turret_cannon.png", seed=27)
    print("done")
