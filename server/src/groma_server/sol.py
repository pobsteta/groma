"""Ground model: DTM first (when present), otherwise from the reconstruction.

The LiDAR DTM is the reference for the 1.30 m slice, preferred over the
reconstructed ground that understorey hides. It is placed in the local
frame through the georeferencing and a vertical offset estimated on the
lowest reconstructed points, which also gives the ground/DTM check.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Callable

import numpy as np
from scipy.interpolate import NearestNDInterpolator

from groma.georeferencement import Georeferencement
from groma.mnt import Raster


@dataclass
class ModeleSol:
    hauteur: Callable[[np.ndarray, np.ndarray], np.ndarray]  # local x, y -> local z
    source: str  # "mnt" | "reconstruit"
    decalage_mnt_m: float | None = None
    dispersion_mnt_m: float | None = None
    n_points_controle: int = 0


def points_sol(points: np.ndarray, cellule_m: float = 1.0, quantile: float = 0.02) -> np.ndarray:
    """Lowest points per grid cell (one representative per cell)."""
    if len(points) == 0:
        return points
    cles = np.floor(points[:, :2] / cellule_m).astype(np.int64)
    ordre = np.lexsort((points[:, 2], cles[:, 1], cles[:, 0]))
    cles, pts = cles[ordre], points[ordre]
    debut = np.r_[0, np.nonzero(np.any(np.diff(cles, axis=0) != 0, axis=1))[0] + 1]
    fin = np.r_[debut[1:], len(pts)]
    out = []
    for a, b in zip(debut, fin):
        if b - a < 5:
            continue
        k = a + int(quantile * (b - a))
        out.append(pts[k])
    return np.array(out) if out else np.zeros((0, 3))


def sol_reconstruit(points: np.ndarray, cellule_m: float = 1.0) -> ModeleSol:
    s = points_sol(points, cellule_m)
    if len(s) < 3:
        raise ValueError("Sol reconstruit insuffisant.")
    interp = NearestNDInterpolator(s[:, :2], s[:, 2])
    return ModeleSol(lambda x, y: interp(np.column_stack([np.ravel(x), np.ravel(y)])).reshape(np.shape(x)), "reconstruit")


def sol_depuis_mnt(points: np.ndarray, mnt: Raster, geo: Georeferencement, cellule_m: float = 1.0) -> ModeleSol:
    """DTM in the local frame, with the vertical offset fitted on the ground points."""
    s = points_sol(points, cellule_m)
    g = geo.vers_grille(s[:, :2])
    z_mnt = mnt.echantillonner(g[:, 0], g[:, 1])
    ok = np.isfinite(z_mnt)
    if ok.sum() < 10:
        raise ValueError("MNT ne couvre pas la reconstruction.")
    ecarts = z_mnt[ok] - s[ok, 2]
    decalage = float(np.median(ecarts))
    mad = float(np.median(np.abs(ecarts - decalage)))

    def hauteur(x, y):
        gg = geo.vers_grille(np.column_stack([np.ravel(x), np.ravel(y)]))
        return (mnt.echantillonner(gg[:, 0], gg[:, 1]) - decalage).reshape(np.shape(x))

    return ModeleSol(hauteur, "mnt", decalage, mad, int(ok.sum()))
