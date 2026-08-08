"""Icônes des munitions manufacturées de la tourelle (cf. TurretCombat pour les paliers).

Quatre icônes 16x16 partageant une silhouette d'obus pour se lire comme une même famille, dans
l'esprit des autres icônes du mod (dithering Bayer + salissure déterministe de pixelart_common) :

  ammo_slug            obus perforant : corps en fer, ceinture de cuivre, pointe de silex
  ammo_incendiary      même obus, pointe et ceinture virées à la braise
  incomplete_ammo_slug l'ébauche qui circule sur le tapis pendant l'assemblage séquencé — volontairement
                       nue (ni pointe ni ceinture) pour se distinguer d'un coup d'œil du produit fini
  incendiary_charge    l'amorce à 8 charges : une fiole, silhouette délibérément étrangère aux trois
                       autres puisqu'on ne la tire pas, on l'applique

Lancer depuis la racine du dépôt : `python tools/gen_ammo_textures.py` (les chemins de sortie sont
relatifs à la racine, et Python ajoute tools/ au path pour l'import de pixelart_common).
"""
from PIL import Image
from pixelart_common import dither

SIZE = 16
OUT_DIR = "src/main/resources/assets/turnkey_factory/textures/item"

PALETTE = {
    "O": (38, 38, 44),     # contour
    # Fer
    "H": (206, 208, 214),  # reflet
    "I": (166, 168, 174),  # fer moyen
    "S": (116, 118, 126),  # ombre
    # Cuivre (mêmes teintes que copper_nugget, la ceinture doit rappeler la pépite)
    "c": (214, 150, 92),
    "C": (184, 115, 51),
    "s": (128, 74, 40),
    # Silex
    "f": (108, 112, 122),
    "F": (74, 78, 88),
    "d": (50, 54, 62),
    # Braise
    "y": (255, 186, 84),
    "Y": (232, 118, 32),
    "D": (128, 42, 16),
    # Verre et poudre de la fiole
    "g": (188, 206, 210),
    "k": (74, 40, 30),
    "K": (52, 28, 22),
    "t": (150, 122, 78),   # bouchon de liège
}

# spread, grime_chance, grime_strength — le contour reste net, le métal est le plus marqué.
DITHER_SPEC = {
    "O": (0, 0.00, 0),
    "H": (8, 0.03, 14),
    "I": (10, 0.04, 20),
    "S": (8, 0.03, 16),
    "c": (6, 0.02, 12),
    "C": (10, 0.04, 20),
    "s": (8, 0.03, 16),
    "f": (8, 0.03, 14),
    "F": (10, 0.04, 18),
    "d": (6, 0.02, 12),
    "y": (10, 0.00, 0),
    "Y": (12, 0.00, 0),
    "D": (8, 0.02, 12),
    "g": (6, 0.02, 10),
    "k": (10, 0.05, 22),
    "K": (8, 0.04, 18),
    "t": (10, 0.04, 18),
}

# Obus perforant. Pointe effilée en silex (r1-r5), corps en fer, ceinture de cuivre aux deux tiers,
# culot marqué. Éclairage standard du mod : reflet à gauche, ombre à droite.
AMMO_SLUG = [
    "................",
    ".......OO.......",
    "......OffO......",
    ".....OffFdO.....",
    "....OffFFFdO....",
    "....OfFFFFdO....",
    "....OHHIISSO....",
    "....OHHIISSO....",
    "....OHHIISSO....",
    "....OccCCssO....",
    "....OccCCssO....",
    "....OHHIISSO....",
    "....OHHIISSO....",
    "....OSSSSSSO....",
    "....OOOOOOOO....",
    "................",
]

# Obus incendiaire : silhouette identique, seule la pointe change — c'est littéralement ce que fait la
# recette (une amorce déposée sur un obus perforant fini), et la ceinture de cuivre conservée garde
# les deux obus lisibles comme un même projectile. Réutiliser la grille plutôt que la recopier garantit
# qu'ils restent alignés au pixel près si la silhouette bouge un jour.
_INCENDIARY_SWAP = {"f": "y", "F": "Y", "d": "D"}
AMMO_INCENDIARY = [
    "".join(_INCENDIARY_SWAP.get(ch, ch) for ch in row) for row in AMMO_SLUG
]

# Ébauche : le lingot pressé, avant pointe et ceinture. Plus courte et à bouts plats.
INCOMPLETE_AMMO_SLUG = [
    "................",
    "................",
    "................",
    "....OOOOOOOO....",
    "....OHIIISSO....",
    "....OHIIISSO....",
    "....OHIIISSO....",
    "....OHIIISSO....",
    "....OHIIISSO....",
    "....OHIIISSO....",
    "....OHIIISSO....",
    "....OHIIISSO....",
    "....OSSSSSSO....",
    "....OOOOOOOO....",
    "................",
    "................",
]

# Amorce incendiaire : fiole bouchée, poudre sombre avec une veine de braise au centre.
INCENDIARY_CHARGE = [
    "................",
    "......OOOO......",
    "......OttO......",
    "......OggO......",
    "......OggO......",
    ".....OggggO.....",
    "....OggggggO....",
    "...OggkkkkggO...",
    "...OgkkkkkkgO...",
    "...OgkkYYkkgO...",
    "...OgkYyyYkgO...",
    "...OgkkYYkkgO...",
    "...OgKkkkkKgO...",
    "...OggKKKKggO...",
    "....OOOOOOOO....",
    "................",
]

SPRITES = {
    "ammo_slug": (AMMO_SLUG, 11),
    "ammo_incendiary": (AMMO_INCENDIARY, 23),
    "incomplete_ammo_slug": (INCOMPLETE_AMMO_SLUG, 31),
    "incendiary_charge": (INCENDIARY_CHARGE, 43),
}


def build(rows, seed):
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        if len(row) != SIZE:
            raise ValueError(f"ligne {y} de largeur {len(row)}, attendu {SIZE}")
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            spread, grime_chance, grime_strength = DITHER_SPEC[ch]
            r, g, b = dither(PALETTE[ch], x, y, spread, grime_chance, grime_strength, seed=seed)
            img.putpixel((x, y), (r, g, b, 255))
    return img


if __name__ == "__main__":
    if len(SPRITES["ammo_slug"][0]) != SIZE:
        raise ValueError("grille non carrée")
    for name, (rows, seed) in SPRITES.items():
        out = f"{OUT_DIR}/{name}.png"
        build(rows, seed).save(out)
        print(f"wrote {out}")
