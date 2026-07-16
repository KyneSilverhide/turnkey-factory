"""Shared helpers for the prefab mod's hand-authored 16x16 pixel art.

Adds an ordered (Bayer) dither + deterministic grime speckle on top of flat
palette fills, so large flat areas read as brushed/worn metal instead of a
single flat "plastic" color.
"""

_BAYER4 = [
    [0, 8, 2, 10],
    [12, 4, 14, 6],
    [3, 11, 1, 9],
    [15, 7, 13, 5],
]


def _hash01(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 2246822519) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    n = (n ^ (n >> 16)) & 0xFFFFFFFF
    return n / 0xFFFFFFFF


def _clamp(v):
    return max(0, min(255, int(round(v))))


def dither(base_rgb, x, y, spread=14, grime_chance=0.06, grime_strength=26, seed=0):
    """Bayer-dithered brightness offset + sparse deterministic dark speckles."""
    bayer_t = _BAYER4[y % 4][x % 4] / 15.0 - 0.5
    offset = bayer_t * spread

    grime = 0.0
    if grime_chance > 0 and _hash01(x, y, seed) < grime_chance:
        grime = -grime_strength

    return tuple(_clamp(c + offset + grime) for c in base_rgb)
