"""Hand-authored GUI sprites shared by every machine screen (prefab mod).

Generates textures/gui/sprites/panel/{background,slot,slot_tool}.png (+ the
nine-slice .mcmeta for the background).

Not AI image generation: every pixel is placed deliberately via palette + rules below.

Theme: a riveted, slightly worn steel plate. The panel is deliberately DARK — every
text colour used by the screens (0xFFE070, 0x4FA83D, 0xC24B4B, 0xC0C0FF) was picked
against the previous flat 0xD0101010 background and is drawn WITHOUT a shadow, so the
interior stays near-black and all the visual interest lives in the frame: outline,
bevel, brushed band, rivets.

The background is a nine-slice sprite: BORDER px of frame on each side, and the inner
region (SIZE - 2*BORDER, a multiple of 4 so the Bayer pattern is seamless) is TILED,
never stretched — the five screens range from 210x230 to 500x184 and stretching would
smear the pixel art at those sizes.
"""
import json
import os

from PIL import Image
from pixelart_common import dither

SIZE = 64
BORDER = 6

OUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "turnkey_factory", "textures", "gui", "sprites", "panel",
)

# --- palette (RGB ; l'alpha est appliqué séparément, cf. ALPHA_*) ---------------------
OUTLINE = (6, 7, 9)         # liseré extérieur, presque noir
BEVEL_HI = (86, 92, 102)    # biseau haut/gauche (lumière en haut à gauche)
BEVEL_LO = (14, 15, 18)     # biseau bas/droite
FRAME = (44, 47, 54)        # bande d'acier brossé du cadre
INNER_EDGE = (10, 11, 13)   # ligne d'ombre entre le cadre et l'intérieur
PLATE = (20, 21, 25)        # intérieur du panneau

RIVET_HI = (120, 127, 140)
RIVET_MID = (92, 98, 110)
RIVET_LO = (46, 49, 56)

ALPHA_FRAME = 246           # cadre quasi opaque : c'est lui qui « tient » la fenêtre
ALPHA_PLATE = 214           # intérieur translucide, comme l'ancien 0xD0101010

INNER = SIZE - 2 * BORDER   # 52 : multiple de 4 → le tramage de Bayer se raccorde au pavage
assert INNER % 4 == 0, "la zone pavée doit être un multiple de 4 pour un tramage sans couture"


def _px(img, x, y, rgb, alpha):
    img.putpixel((x, y), (rgb[0], rgb[1], rgb[2], alpha))


def _rivet(img, x, y):
    """Rivet 2x2, lumière toujours en haut à gauche (source de lumière globale)."""
    _px(img, x, y, RIVET_HI, ALPHA_FRAME)
    _px(img, x + 1, y, RIVET_MID, ALPHA_FRAME)
    _px(img, x, y + 1, RIVET_MID, ALPHA_FRAME)
    _px(img, x + 1, y + 1, RIVET_LO, ALPHA_FRAME)


def build_background():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    last = SIZE - 1

    for y in range(SIZE):
        for x in range(SIZE):
            depth = min(x, y, last - x, last - y)
            if depth == 0:
                _px(img, x, y, OUTLINE, 255)
            elif depth == 1:
                # Haut/gauche éclairé, bas/droite dans l'ombre.
                lit = (y == 1) or (x == 1)
                _px(img, x, y, BEVEL_HI if lit else BEVEL_LO, ALPHA_FRAME)
            elif depth < BORDER - 1:
                _px(img, x, y, dither(FRAME, x, y, spread=12, grime_chance=0.05,
                                      grime_strength=18, seed=3), ALPHA_FRAME)
            elif depth == BORDER - 1:
                _px(img, x, y, INNER_EDGE, ALPHA_FRAME)
            else:
                # Amplitude volontairement faible : à l'échelle GUI 3 ou 4, un tramage plus marqué
                # deviendrait un damier bien visible derrière le texte (dessiné sans ombre).
                _px(img, x, y, dither(PLATE, x, y, spread=5, grime_chance=0.035,
                                      grime_strength=10, seed=7), ALPHA_PLATE)

    # Rivets : un par coin (dessiné une seule fois) + un au milieu de chaque bande de
    # bord (celles-ci sont pavées, donc ils se répètent régulièrement le long du cadre).
    near, far = 2, SIZE - 4
    mid = BORDER + INNER // 2 - 1
    for rx, ry in ((near, near), (far, near), (near, far), (far, far),
                   (mid, near), (mid, far), (near, mid), (far, mid)):
        _rivet(img, rx, ry)

    return img


def build_slot(ring_lo, ring_hi, interior, seed):
    """Slot 18x18 « en creux » : ombre en haut/gauche, lumière en bas/droite."""
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    for y in range(18):
        for x in range(18):
            _px(img, x, y, dither(interior, x, y, spread=5, grime_chance=0.04,
                                  grime_strength=12, seed=seed), 255)
    for i in range(18):
        _px(img, i, 0, ring_lo, 255)
        _px(img, 0, i, ring_lo, 255)
        _px(img, i, 17, ring_hi, 255)
        _px(img, 17, i, ring_hi, 255)
    # Coins de transition entre les deux moitiés du liseré.
    mid = tuple((a + b) // 2 for a, b in zip(ring_lo, ring_hi))
    _px(img, 17, 0, mid, 255)
    _px(img, 0, 17, mid, 255)
    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)

    background = build_background()
    background.save(os.path.join(OUT_DIR, "background.png"))
    meta = {"gui": {"scaling": {"type": "nine_slice", "width": SIZE, "height": SIZE, "border": BORDER}}}
    with open(os.path.join(OUT_DIR, "background.png.mcmeta"), "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2)
        f.write("\n")

    build_slot((10, 11, 13), (124, 130, 142), (48, 51, 58), seed=11).save(
        os.path.join(OUT_DIR, "slot.png"))
    # Slot outil/machine : liseré violet, l'affordance existante (ex-0xFFB080FF).
    build_slot((108, 74, 168), (201, 166, 255), (52, 46, 66), seed=13).save(
        os.path.join(OUT_DIR, "slot_tool.png"))

    print("wrote", OUT_DIR)


if __name__ == "__main__":
    main()
