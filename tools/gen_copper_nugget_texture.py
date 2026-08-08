"""Icône du copper_nugget (munition tourelle "moitié dégâts", cf. TurretCombat) : lingot de fer
vanilla n'a pas d'équivalent cuivre en 1.21.1 (COPPER_NUGGET n'existe qu'à partir d'une version
Minecraft plus récente, vérifié par javap sur le jar 1.21.1), d'où cet item custom. Petit amas asymétrique
16x16, même esprit qu'un nugget vanilla (silhouette différente pour ne pas ressembler à une simple
recoloration de iron_nugget), teinte cuivre chaud avec le dithering standard du mod.
"""
from PIL import Image
from pixelart_common import dither

SIZE = 16

PALETTE = {
    "O": (74, 42, 26),    # contour sombre
    "M": (184, 115, 51),  # cuivre "classique"
    "H": (232, 170, 120), # reflet chaud
    "S": (128, 74, 40),   # ombre portée
}

DITHER_SPEC = {
    "O": (0, 0.0, 0),
    "M": (10, 0.04, 20),
    "H": (6, 0.02, 12),
    "S": (8, 0.03, 16),
}

# Grille 16x16, '.' = transparent. Amas asymétrique (pas un simple cercle) pour lire comme un
# fragment métallique plutôt qu'une bille.
ROWS = [
    "................",
    "................",
    "................",
    "................",
    "................",
    ".....OOMM.......",
    "....OHHMMO......",
    "....OHHMMMO.....",
    "....OMMMMSO.....",
    ".....OMMSSO.....",
    "......OOOO......",
    "................",
    "................",
    "................",
    "................",
    "................",
]


def build():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for y, row in enumerate(ROWS):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            spread, grime_chance, grime_strength = DITHER_SPEC[ch]
            r, g, b = dither(PALETTE[ch], x, y, spread, grime_chance, grime_strength, seed=7)
            img.putpixel((x, y), (r, g, b, 255))
    return img


if __name__ == "__main__":
    out = "src/main/resources/assets/turnkey_factory/textures/item/copper_nugget.png"
    build().save(out)
    print(f"wrote {out}")
