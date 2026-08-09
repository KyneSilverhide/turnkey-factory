"""Icônes des deux composants d'artisanat du contrôleur (cf. data/turnkey_factory/recipe/).

Deux icônes 16x16 dans l'esprit des autres icônes du mod (dithering Bayer + salissure déterministe
de pixelart_common, contour sombre, éclairage en haut à gauche) :

  architect_blueprint  lapis + papier : une feuille de plan bleue portant l'élévation d'un bâtiment
                       (mur + toit à deux pans, ce que construit justement le contrôleur), coin
                       inférieur droit corné pour montrer le dos pâle du papier
  control_core         quartz + redstone + or : un noyau serti, châssis doré, plots de quartz aux
                       quatre angles, cœur de redstone incandescent dans un logement d'acier

Les deux remplaçaient des aplats symétriques sans relief qui débordaient jusqu'au bord des 16x16 ;
ici le motif reste dans x/y 2..13 comme ammo_slug et incendiary_charge, pour que l'objet respire
dans la case d'inventaire.

Lancer depuis la racine du dépôt : `python tools/gen_control_items_textures.py`.
"""
from PIL import Image
from pixelart_common import dither

SIZE = 16
OUT_DIR = "src/main/resources/assets/turnkey_factory/textures/item"

PALETTE = {
    # Plan d'architecte
    "O": (24, 30, 46),     # contour, bleu nuit
    "H": (66, 118, 196),   # tranche gauche éclairée de la feuille
    "B": (42, 86, 158),    # papier bleu
    "b": (28, 58, 112),    # tranche droite dans l'ombre
    "W": (232, 238, 246),  # encre blanche du tracé
    "P": (208, 208, 198),  # dos du papier (coin corné)
    "p": (156, 156, 146),  # dos du papier, ombre du pli
    # Cœur de contrôle
    "o": (22, 22, 26),     # contour, presque noir
    "G": (198, 158, 54),   # or moyen
    "g": (238, 206, 110),  # or éclairé (arête haute)
    "k": (140, 104, 28),   # or dans l'ombre (arête basse)
    "Q": (238, 236, 228),  # plot de quartz, éclairé
    "q": (176, 172, 162),  # plot de quartz, ombre
    "S": (74, 78, 86),     # logement d'acier
    "R": (206, 44, 34),    # redstone
    "r": (255, 134, 100),  # redstone, cœur incandescent
    "w": (255, 214, 186),  # redstone, point le plus chaud
    "d": (104, 20, 16),    # redstone, bord éteint
}

# spread, grime_chance, grime_strength — contours nets, papier peu bruité (c'est une feuille neuve),
# métal et acier plus marqués, incandescence lisse (le bruit tuerait l'effet de lueur).
DITHER_SPEC = {
    "O": (0, 0.00, 0),
    "H": (8, 0.02, 12),
    "B": (9, 0.03, 14),
    "b": (7, 0.02, 12),
    "W": (5, 0.02, 10),
    "P": (7, 0.03, 14),
    "p": (6, 0.02, 12),
    "o": (0, 0.00, 0),
    "G": (10, 0.04, 18),
    "g": (8, 0.02, 12),
    "k": (9, 0.04, 18),
    "Q": (7, 0.03, 14),
    "q": (8, 0.03, 16),
    "S": (10, 0.05, 22),
    "R": (10, 0.00, 0),
    "r": (8, 0.00, 0),
    "w": (6, 0.00, 0),
    "d": (8, 0.02, 12),
}

# Feuille de plan. Tranche gauche éclairée (H), droite dans l'ombre (b). Le tracé blanc est une
# élévation : faîte, deux pans de toit, ligne d'égout, murs, une ouverture, ligne de sol. Le coin
# inférieur droit est corné — la silhouette elle-même est entamée, sinon le pli se lirait comme
# une tache plutôt que comme un rabat.
ARCHITECT_BLUEPRINT = [
    "                ",
    "  OOOOOOOOOOOO  ",
    "  OHBBBBBBBBbO  ",
    "  OHBBBWWBBBbO  ",
    "  OHBBWBBWBBbO  ",
    "  OHBWBBBBWBbO  ",
    "  OHWWWWWWWWbO  ",
    "  OHBWBBBBWBbO  ",
    "  OHBWBWWBWBbO  ",
    "  OHBWBWWBWBbO  ",
    "  OHBWWWWWWBbO  ",
    "  OHBBBBBBPPOO  ",
    "  OHBBBBBPpO    ",
    "  OOOOOOOOOO    ",
    "                ",
    "                ",
]

# Noyau serti. Bande dorée éclairée en haut (g) et dans l'ombre en bas (k), plots de quartz aux
# quatre angles (Q éclairé / q ombré), logement d'acier, cœur de redstone dégradé du haut-gauche
# incandescent (r) vers le bas-droite éteint (d).
CONTROL_CORE = [
    "                ",
    "                ",
    "  oooooooooooo  ",
    "  oQQGggggGQQo  ",
    "  oQqGGGGGGQqo  ",
    "  oGoSSSSSSoGo  ",
    "  oGoSwrRdSoGo  ",
    "  oGoSrRRdSoGo  ",
    "  oGoSRRRdSoGo  ",
    "  oGoSddddSoGo  ",
    "  oGoSSSSSSoGo  ",
    "  oQqGGGGGGQqo  ",
    "  oQqGkkkkGQqo  ",
    "  oooooooooooo  ",
    "                ",
    "                ",
]


def render(rows, name, seed):
    for y, row in enumerate(rows):
        assert len(row) == SIZE, f"{name} ligne {y} fait {len(row)} caractères"
    assert len(rows) == SIZE, f"{name} a {len(rows)} lignes"

    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == " ":
                continue
            spread, chance, strength = DITHER_SPEC[ch]
            rgb = dither(PALETTE[ch], x, y, spread=spread, grime_chance=chance,
                         grime_strength=strength, seed=seed)
            img.putpixel((x, y), (rgb[0], rgb[1], rgb[2], 255))

    path = f"{OUT_DIR}/{name}.png"
    img.save(path)
    print("wrote", path)


def main():
    render(ARCHITECT_BLUEPRINT, "architect_blueprint", seed=21)
    render(CONTROL_CORE, "control_core", seed=23)


if __name__ == "__main__":
    main()
