"""Hand-authored 16x16 pixel art for the Factory Controller block (prefab mod).
Regenerates block/controller_{top,side,bottom}.png with the same composition
as before (status screen+button / LED grid+lights / cross plate) but with
proper dithered fills instead of flat "plastic" color blocks.
"""
from PIL import Image
from pixelart_common import dither

SIZE = 16

PALETTE = {
    ".": (74, 78, 84),     # outer frame
    "x": (52, 55, 60),     # dark ring / cross bars
    "m": (139, 144, 152),  # plate mid (top/side)
    "n": (108, 113, 121),  # plate mid (bottom, darker)
    "R": (196, 200, 207),  # corner rivet
    "Y": (176, 148, 48),   # screen bezel (amber)
    "y": (230, 200, 79),   # screen glow (amber)
    "u": (236, 64, 48),    # red indicator
    "z": (40, 40, 46),     # LED bezel (dark)
    "L": (37, 80, 143),    # LED dark blue
    "j": (120, 165, 220),  # LED light blue
    "g": (70, 196, 90),    # green indicator
}

DITHER_SPEC = {
    ".": (10, 0.05, 22),
    "x": (8, 0.04, 20),
    "m": (16, 0.07, 26),
    "n": (14, 0.07, 24),
    "R": (6, 0.0, 0),
    "Y": (10, 0.03, 20),
    "y": (10, 0.03, 18),
    "u": (6, 0.0, 0),
    "z": (8, 0.02, 18),
    "L": (10, 0.03, 20),
    "j": (10, 0.03, 20),
    "g": (6, 0.0, 0),
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


def build_top():
    c = new_canvas()
    rect(c, 0, 0, 15, 15, ".")
    rect(c, 1, 1, 14, 14, "x")
    rect(c, 2, 2, 13, 13, "m")
    for (rx, ry) in CORNERS:
        px(c, rx, ry, "R")
    # status screen: amber bezel ring, glowing interior, red indicator button
    rect(c, 5, 5, 10, 5, "Y")
    rect(c, 5, 10, 10, 10, "Y")
    for y in range(6, 10):
        px(c, 5, y, "Y")
        px(c, 10, y, "Y")
    rect(c, 6, 6, 9, 9, "y")
    rect(c, 7, 7, 8, 8, "u")
    return c


def build_side():
    c = new_canvas()
    rect(c, 0, 0, 15, 15, ".")
    rect(c, 1, 1, 14, 14, "x")
    rect(c, 2, 2, 13, 13, "m")
    for (rx, ry) in CORNERS:
        px(c, rx, ry, "R")
    # LED display bezel
    rect(c, 4, 4, 11, 4, "z")
    rect(c, 4, 10, 11, 10, "z")
    for y in range(5, 10):
        px(c, 4, y, "z")
        px(c, 11, y, "z")
    # checkered LED grid
    for y in range(5, 10):
        for x in range(5, 11):
            c[y][x] = "j" if (x + y) % 2 == 0 else "L"
    # status lights
    rect(c, 5, 12, 6, 12, "u")
    rect(c, 9, 12, 10, 12, "g")
    return c


def build_bottom():
    c = new_canvas()
    rect(c, 0, 0, 15, 15, ".")
    rect(c, 1, 1, 14, 14, "x")
    rect(c, 2, 2, 13, 13, "n")
    for (rx, ry) in CORNERS:
        px(c, rx, ry, "R")
    # quartering cross plate
    rect(c, 3, 8, 12, 8, "x")
    rect(c, 8, 3, 8, 12, "x")
    return c


if __name__ == "__main__":
    base = "src/main/resources/assets/prefab/textures/block"
    save(build_top(), f"{base}/controller_top.png", seed=11)
    save(build_side(), f"{base}/controller_side.png", seed=12)
    save(build_bottom(), f"{base}/controller_bottom.png", seed=13)
    print("done")
