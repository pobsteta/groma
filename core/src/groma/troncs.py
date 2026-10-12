"""Trunk detection in the 1.20-1.40 m slice of a reconstructed point cloud.

Steps: keep the points between ``h_bas`` and ``h_haut`` above the ground,
group them per trunk (DBSCAN on the horizontal plane), fit a circle on each
group (RANSAC + geometric fit), then merge the groups that belong to the same
large trunk: on a 200 cm stem the visible bark is a long arc that occlusions
and texture-less areas break into several pieces, each piece fitting a circle
that shares the same centre.
"""

from __future__ import annotations

import logging
import math
from dataclasses import dataclass
from typing import Callable

import numpy as np
from scipy.spatial import cKDTree

from .cercle import CercleAjuste, ajuster_cercle_geometrique, ajuster_cercle_ransac, couverture_angulaire_deg

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class TroncDetecte:
    x: float
    y: float
    dbh_cm: float
    rmse_m: float
    couverture_deg: float
    n_points: int
    score: float


def extraire_tranche(
    points: np.ndarray,
    hauteur_sol: Callable[[np.ndarray, np.ndarray], np.ndarray] | np.ndarray,
    h_bas: float = 1.20,
    h_haut: float = 1.40,
) -> np.ndarray:
    """Return the 2D points whose height above ground is in ``[h_bas, h_haut]``.

    ``hauteur_sol`` is either a function ``(x, y) -> z_sol`` (e.g. the DTM
    sampler of :mod:`groma.mnt`) or an array of ground heights, one per point.
    """
    points = np.asarray(points, dtype=float)
    if callable(hauteur_sol):
        z_sol = hauteur_sol(points[:, 0], points[:, 1])
    else:
        z_sol = np.asarray(hauteur_sol, dtype=float)
    h = points[:, 2] - z_sol
    garde = np.isfinite(h) & (h >= h_bas) & (h <= h_haut)
    return points[garde, :2]


def dbscan(pts: np.ndarray, eps: float, min_points: int) -> np.ndarray:
    """Plain DBSCAN on 2D points. Returns labels, -1 for noise."""
    n = pts.shape[0]
    labels = np.full(n, -1, dtype=int)
    if n == 0:
        return labels
    arbre = cKDTree(pts)
    voisins = arbre.query_ball_point(pts, eps)
    coeur = np.array([len(v) >= min_points for v in voisins])
    etiquette = 0
    for i in range(n):
        if labels[i] != -1 or not coeur[i]:
            continue
        labels[i] = etiquette
        pile = [i]
        while pile:
            j = pile.pop()
            if not coeur[j]:
                continue
            for k in voisins[j]:
                if labels[k] == -1:
                    labels[k] = etiquette
                    pile.append(k)
        etiquette += 1
    return labels


def _score(c: CercleAjuste) -> float:
    s_couv = min(1.0, c.couverture_deg / 180.0)
    s_rmse = max(0.0, 1.0 - c.rmse_m / max(0.03, 0.1 * c.rayon_m))
    return round(0.6 * s_couv + 0.4 * s_rmse, 3)


def detecter_troncs(
    pts: np.ndarray,
    eps_m: float = 0.05,
    min_points: int = 10,
    seuil_ransac_m: float = 0.015,
    rayon_max_m: float = 1.5,
    couverture_min_deg: float = 40.0,
    graine: int | None = 0,
) -> list[TroncDetecte]:
    """Detect trunks in a 2D slice. ``pts`` in metres (any planar frame)."""
    pts = np.asarray(pts, dtype=float)
    labels = dbscan(pts, eps_m, min_points)
    groupes = [pts[labels == k] for k in range(labels.max() + 1)] if labels.size else []

    ajustes: list[tuple[CercleAjuste, np.ndarray]] = []
    for g in groupes:
        if g.shape[0] < min_points:
            continue
        c = ajuster_cercle_ransac(g, seuil_m=seuil_ransac_m, rayon_max_m=rayon_max_m, graine=graine)
        if c is not None:
            ajustes.append((c, g))

    ajustes = _fusionner_arcs(ajustes, seuil_ransac_m, rayon_max_m)

    troncs: list[TroncDetecte] = []
    for c, g in ajustes:
        if c.couverture_deg < couverture_min_deg:
            log.debug("Arc rejeté (couverture %.0f°) en %.2f, %.2f", c.couverture_deg, c.xc, c.yc)
            continue
        troncs.append(
            TroncDetecte(
                x=c.xc,
                y=c.yc,
                dbh_cm=round(c.dbh_cm, 1),
                rmse_m=c.rmse_m,
                couverture_deg=c.couverture_deg,
                n_points=c.n_inliers,
                score=_score(c),
            )
        )
    return troncs


def _fusionner_arcs(
    ajustes: list[tuple[CercleAjuste, np.ndarray]], seuil_m: float, rayon_max_m: float
) -> list[tuple[CercleAjuste, np.ndarray]]:
    """Merge groups whose points lie on the circle of a larger group."""
    ajustes = sorted(ajustes, key=lambda t: -t[0].rayon_m)
    absorbe = [False] * len(ajustes)
    sortie: list[tuple[CercleAjuste, np.ndarray]] = []
    for i, (ci, gi) in enumerate(ajustes):
        if absorbe[i]:
            continue
        pts = gi
        cercle = ci
        for j in range(i + 1, len(ajustes)):
            if absorbe[j]:
                continue
            cj, gj = ajustes[j]
            if math.hypot(cj.xc - cercle.xc, cj.yc - cercle.yc) > cercle.rayon_m + cj.rayon_m + 0.1:
                continue
            ecart = np.abs(np.hypot(gj[:, 0] - cercle.xc, gj[:, 1] - cercle.yc) - cercle.rayon_m)
            if np.mean(ecart < 2.5 * seuil_m) >= 0.8:
                absorbe[j] = True
                pts = np.vstack([pts, gj])
        if pts is not gi:
            xc, yc, r = ajuster_cercle_geometrique(pts, (cercle.xc, cercle.yc, cercle.rayon_m))
            if r <= rayon_max_m:
                resid = np.hypot(pts[:, 0] - xc, pts[:, 1] - yc) - r
                inl = np.abs(resid) < 1.5 * seuil_m
                cercle = CercleAjuste(
                    xc=xc,
                    yc=yc,
                    rayon_m=r,
                    rmse_m=float(np.sqrt(np.mean(resid[inl] ** 2))) if inl.any() else float("inf"),
                    n_points=pts.shape[0],
                    n_inliers=int(inl.sum()),
                    couverture_deg=couverture_angulaire_deg(pts[inl], xc, yc),
                )
        sortie.append((cercle, pts))
    return sortie
