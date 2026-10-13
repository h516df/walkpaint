"""One classifier for "is the veil actually on screen", shared by every acceptance lane.

The emulator lane and the phone lane look at different screens at different resolutions, so their
crops differ, but the question they ask of a pixel is the same and must stay the same: the fog is
opaque black, so it is the only thing in this scene that is *both* very dark and neutral. Splitting
the classifier out here is what keeps a pass on one lane comparable to a pass on the other —
otherwise two copies drift and a "pass" stops meaning the same thing twice.

The colour test changed on 2026-10-12 together with the effect: the veil is opaque black now
(0xFF000000), not the dark navy it used to be, so the old "blue above red and green" predicate
classified the new veil as 0.001 and failed a correct screen. That was a measurement bug, and it is
recorded as one in docs/evidence/V039.md rather than being papered over with a lower threshold.
"""
from __future__ import annotations

# Below this on all three channels is the veil. Pure black is 0; 24 leaves room for the odd
# compositor rounding without admitting the dark navy of the old veil (which sat around 40/60/90).
VEIL_BLACK_LEVEL = 24
# Dark but not black: basemap ink that the veil is supposed to be covering.
VEIL_DARK_LEVEL = 70
# A screen with less than this share of veil is not showing fog at all.
VEIL_PRESENTED_RATIO = 0.05


def is_veil(r: int, g: int, b: int) -> bool:
    """True when this pixel is the opaque black the fog is specified as."""
    return r < VEIL_BLACK_LEVEL and g < VEIL_BLACK_LEVEL and b < VEIL_BLACK_LEVEL


def classify_region(pixels) -> dict:
    """Buckets an iterable of (r, g, b) into veil, bare basemap and walked-ground tint.

    `veil_ratio` is the share of the sampled region the fog genuinely covers. On a phone that has
    never walked anywhere, correct behaviour is a ratio close to 1: the person asked for unknown
    ground to be unreadable, so a phone showing the basemap on a fresh install is the bug, not the
    absence of one.
    """
    veil = 0
    bare = 0
    tinted = 0
    total = 0
    for r, g, b in pixels:
        total += 1
        luminance = 0.299 * r + 0.587 * g + 0.114 * b
        if is_veil(r, g, b):
            veil += 1
        elif luminance > 200 and g >= r:
            bare += 1
        if g > r + 8 and g > b and 90 < g < 200:
            tinted += 1
    if total == 0:
        return {'veil_ratio': 0.0, 'bare_map_ratio': 0.0, 'tint_ratio': 0.0, 'sampled': 0}
    return {'veil_ratio': round(veil / total, 3),
            'bare_map_ratio': round(bare / total, 3),
            'tint_ratio': round(tinted / total, 3),
            'sampled': total}
