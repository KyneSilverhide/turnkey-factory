"""Hand-authored 16x16 pixel art for the shared props of the machine block models.

The four site machines (controller, leveler, texturizer, lamplighter) are no
longer plain cubes: each is a workbench with a frame, an overhanging worktop and
tools/props on top (cf. models/block/*.json). Most prop faces reuse vanilla
block textures (dirt, cobblestone, iron block, stripped log...), which already
sit in the block atlas and match the game's look. Only three surfaces have no
sensible vanilla equivalent, and they live here:

  * machine_frame     -- dark steel of the legs, posts and lantern cage
  * controller_blueprint -- the blue drafting sheet on the controller
  * lamplighter_lamp  -- the warm glass of the lamplighter's lantern

Same dithering conventions as the other generators (cf. pixelart_common).
"""
from PIL import Image
from pixelart_common import dither

SIZE = 16
OUT = "src/main/resources/assets/turnkey_factory/textures/block"

PALETTE = {
    # -- machine_frame
    "s": (66, 70, 78),     # steel mid
    "S": (88, 93, 102),    # steel highlight (left bevel)
    "d": (44, 47, 53),     # steel shadow (right bevel)
    "R": (150, 156, 166),  # rivet
    # -- controller_blueprint
    "b": (34, 68, 132),    # blueprint paper
    "B": (26, 52, 104),    # blueprint paper, darker band
    "w": (206, 220, 240),  # ink line
    # -- lamplighter_lamp
    "o": (232, 172, 62),   # lamp glass
    "O": (255, 226, 150),  # lamp core
    "k": (120, 78, 26),    # lamp glass, shaded edge
}

DITHER_SPEC = {
    "s": (14, 0.06, 24),
    "S": (10, 0.04, 20),
    "d": (10, 0.05, 22),
    "R": (6, 0.0, 0),
    "b": (10, 0.03, 16),
    "B": (10, 0.03, 16),
    "w": (6, 0.0, 0),
    "o": (12, 0.0, 0),
    "O": (10, 0.0, 0),
    "k": (10, 0.02, 14),
}


def new_canvas(fill):
    return [[fill for _ in range(SIZE)] for _ in range(SIZE)]


def rect(canvas, x0, y0, x1, y1, ch):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            canvas[y][x] = ch


def frame_rect(canvas, x0, y0, x1, y1, ch):
    """Outline only -- the ink lines of the blueprint are drawings, not fills."""
    for x in range(x0, x1 + 1):
        canvas[y0][x] = ch
        canvas[y1][x] = ch
    for y in range(y0, y1 + 1):
        canvas[y][x0] = ch
        canvas[y][x1] = ch


def save(canvas, name, seed=0):
    img = Image.new("RGB", (SIZE, SIZE))
    for y in range(SIZE):
        for x in range(SIZE):
            ch = canvas[y][x]
            spread, grime_chance, grime_strength = DITHER_SPEC[ch]
            img.putpixel((x, y), dither(PALETTE[ch], x, y, spread, grime_chance, grime_strength, seed))
    img.save(f"{OUT}/{name}.png")
    print(f"wrote {OUT}/{name}.png")


def machine_frame():
    """Brushed steel post: bevelled on both sides so a 2px-wide leg still reads
    as round-ish, plus rivets every few pixels along its length."""
    c = new_canvas("s")
    for x in (0, 1, 8, 9):
        rect(c, x, 0, x, 15, "S")
    for x in (6, 7, 14, 15):
        rect(c, x, 0, x, 15, "d")
    for y in (2, 7, 12):
        for x in (3, 11):
            c[y][x] = "R"
    return c


def controller_blueprint():
    """Drafting sheet seen from above: a floor plan sketched in white ink over
    blue paper -- an outer wall, an inner room and a dimension line."""
    c = new_canvas("b")
    rect(c, 0, 0, 15, 0, "B")
    rect(c, 0, 15, 15, 15, "B")
    frame_rect(c, 2, 2, 13, 12, "w")
    frame_rect(c, 4, 4, 8, 8, "w")
    rect(c, 10, 6, 12, 6, "w")     # cote horizontale
    rect(c, 10, 9, 10, 11, "w")    # cote verticale
    c[10][7] = "w"                 # porte / repere
    return c


def lamplighter_lamp():
    """Lantern glass: bright core fading to a shaded rim, so the 4px-tall band
    visible between the cage bars still looks like it glows."""
    c = new_canvas("o")
    rect(c, 0, 0, 15, 1, "k")
    rect(c, 0, 14, 15, 15, "k")
    rect(c, 0, 0, 1, 15, "k")
    rect(c, 14, 0, 15, 15, "k")
    rect(c, 4, 4, 11, 11, "O")
    return c


if __name__ == "__main__":
    save(machine_frame(), "machine_frame", seed=11)
    save(controller_blueprint(), "controller_blueprint", seed=23)
    save(lamplighter_lamp(), "lamplighter_lamp", seed=37)
