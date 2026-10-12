"""Circle fitting on 2D trunk cross-sections (slice at 1.30 m).

Reconstructed trunks are seldom seen all around: a video walked around the
plot centre mostly sees the side of each stem facing the path, so the points
form an arc. The algebraic (Kasa) fit is biased towards small radii on short
arcs; it is only used to initialise a geometric least-squares fit
(Gauss-Newton on orthogonal distances), wrapped in RANSAC against branches,
understorey and reconstruction noise.
"""

from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np


@dataclass(frozen=True)
class CercleAjuste:
    xc: float
    yc: float
    rayon_m: float
    rmse_m: float
    n_points: int
    n_inliers: int
    couverture_deg: float

    @property
    def dbh_cm(self) -> float:
        return 200.0 * self.rayon_m


def ajuster_cercle_algebrique(pts: np.ndarray) -> tuple[float, float, float]:
    """Kasa fit: least squares on ``x² + y² + D x + E y + F = 0``."""
    pts = np.asarray(pts, dtype=float)
    if pts.shape[0] < 3:
        raise ValueError("Au moins 3 points sont nécessaires.")
    x, y = pts[:, 0], pts[:, 1]
    # Centre the data for numerical stability with Lambert-93 sized values.
    mx, my = x.mean(), y.mean()
    u, v = x - mx, y - my
    a = np.column_stack([u, v, np.ones_like(u)])
    b = -(u**2 + v**2)
    (d, e, f), *_ = np.linalg.lstsq(a, b, rcond=None)
    xc, yc = -d / 2.0, -e / 2.0
    r2 = xc**2 + yc**2 - f
    if r2 <= 0:
        raise ValueError("Ajustement dégénéré.")
    return float(xc + mx), float(yc + my), float(math.sqrt(r2))


def ajuster_cercle_geometrique(
    pts: np.ndarray, init: tuple[float, float, float] | None = None, iterations: int = 50
) -> tuple[float, float, float]:
    """Geometric fit minimising orthogonal distances (Gauss-Newton)."""
    pts = np.asarray(pts, dtype=float)
    xc, yc, r = init if init is not None else ajuster_cercle_algebrique(pts)
    for _ in range(iterations):
        dx, dy = pts[:, 0] - xc, pts[:, 1] - yc
        di = np.hypot(dx, dy)
        di = np.where(di < 1e-12, 1e-12, di)
        res = di - r
        j = np.column_stack([-dx / di, -dy / di, -np.ones_like(di)])
        step, *_ = np.linalg.lstsq(j, -res, rcond=None)
        xc, yc, r = xc + step[0], yc + step[1], r + step[2]
        if np.linalg.norm(step) < 1e-10:
            break
    return float(xc), float(yc), float(abs(r))


def couverture_angulaire_deg(pts: np.ndarray, xc: float, yc: float, secteurs: int = 72) -> float:
    """Angular coverage of the points around the centre (degrees)."""
    ang = np.degrees(np.arctan2(pts[:, 1] - yc, pts[:, 0] - xc)) % 360.0
    occupes = np.unique((ang // (360.0 / secteurs)).astype(int))
    return float(len(occupes) * 360.0 / secteurs)


def ajuster_cercle_ransac(
    pts: np.ndarray,
    seuil_m: float = 0.01,
    iterations: int = 300,
    rayon_min_m: float = 0.02,
    rayon_max_m: float = 2.0,
    graine: int | None = 0,
) -> CercleAjuste | None:
    """Robust circle fit; ``None`` when no plausible circle is found."""
    pts = np.asarray(pts, dtype=float)
    n = pts.shape[0]
    if n < 5:
        return None
    rng = np.random.default_rng(graine)
    meilleur: np.ndarray | None = None
    for _ in range(iterations):
        idx = rng.choice(n, 3, replace=False)
        try:
            xc, yc, r = _cercle_trois_points(pts[idx])
        except ValueError:
            continue
        if not (rayon_min_m <= r <= rayon_max_m):
            continue
        inl = np.abs(np.hypot(pts[:, 0] - xc, pts[:, 1] - yc) - r) < seuil_m
        if meilleur is None or inl.sum() > meilleur.sum():
            meilleur = inl
    if meilleur is None or meilleur.sum() < 5:
        return None
    sel = pts[meilleur]
    try:
        xc, yc, r = ajuster_cercle_geometrique(sel)
    except (ValueError, np.linalg.LinAlgError):
        return None
    if not (rayon_min_m <= r <= rayon_max_m):
        return None
    # Final inlier set re-evaluated on the refined circle.
    resid = np.hypot(pts[:, 0] - xc, pts[:, 1] - yc) - r
    inl = np.abs(resid) < seuil_m * 1.5
    rmse = float(np.sqrt(np.mean(resid[inl] ** 2))) if inl.any() else float("inf")
    return CercleAjuste(
        xc=xc,
        yc=yc,
        rayon_m=r,
        rmse_m=rmse,
        n_points=n,
        n_inliers=int(inl.sum()),
        couverture_deg=couverture_angulaire_deg(pts[inl], xc, yc),
    )


def _cercle_trois_points(p: np.ndarray) -> tuple[float, float, float]:
    (x1, y1), (x2, y2), (x3, y3) = p
    d = 2.0 * (x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2))
    if abs(d) < 1e-12:
        raise ValueError("Points alignés.")
    s1, s2, s3 = x1**2 + y1**2, x2**2 + y2**2, x3**2 + y3**2
    xc = (s1 * (y2 - y3) + s2 * (y3 - y1) + s3 * (y1 - y2)) / d
    yc = (s1 * (x3 - x2) + s2 * (x1 - x3) + s3 * (x2 - x1)) / d
    return xc, yc, math.hypot(x1 - xc, y1 - yc)
