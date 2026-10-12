"""Use of the LiDAR crowns (output of nemeton::segment_houppiers()).

* Height pre-fill by point-in-polygon on the trunk position: tallest crown
  when crowns overlap, nothing when the trunk is in no crown (no fallback to
  the nearest one), heights outside 1-70 m ignored, a measured height is
  never overwritten. Same contract as the Marculus ``houppier`` layer.
* Independent heading check: 2D registration of the trunk map on the apexes
  of the dominant crowns.
* Possibly missed trees: dominant crown with no detected trunk underneath.
  The LiDAR may predate a cut or a windthrow: this is an alert, never a proof.
* Diameter / height / crown area consistency.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Iterable, Sequence

import numpy as np
from shapely.geometry import Point, Polygon
from shapely.strtree import STRtree

H_MIN_M = 1.0
H_MAX_M = 70.0


@dataclass(frozen=True)
class Houppier:
    houppier_id: int
    polygone: Polygon
    h_max: float
    surface_m2: float | None = None

    @property
    def apex(self) -> tuple[float, float]:
        # The segmentation gives polygons, not apexes; the pole of
        # inaccessibility would be better, the centroid is close enough for
        # crowns of a few metres.
        c = self.polygone.centroid
        if not self.polygone.covers(c):
            c = self.polygone.representative_point()
        return (c.x, c.y)


@dataclass(frozen=True)
class HauteurAffectee:
    h_m: float | None
    h_source: str  # "mnh" | "mesuree" | "aucune"
    houppier_id: int | None


def affecter_hauteur(
    x: float,
    y: float,
    houppiers: Sequence[Houppier],
    h_m: float | None = None,
    h_source: str | None = None,
    index: STRtree | None = None,
) -> HauteurAffectee:
    """Height of one trunk from the crowns containing it."""
    if h_source == "mesuree" and h_m is not None:
        return HauteurAffectee(h_m, "mesuree", None)
    p = Point(x, y)
    if index is not None:
        candidats = [houppiers[i] for i in index.query(p)]
    else:
        candidats = list(houppiers)
    contenant = [
        h
        for h in candidats
        if h.h_max is not None and H_MIN_M <= h.h_max <= H_MAX_M and h.polygone.covers(p)
    ]
    if not contenant:
        return HauteurAffectee(None, "aucune", None)
    meilleur = max(contenant, key=lambda h: (h.h_max, -h.houppier_id))
    return HauteurAffectee(float(meilleur.h_max), "mnh", meilleur.houppier_id)


def affecter_hauteurs(arbres: Iterable[dict], houppiers: Sequence[Houppier]) -> list[HauteurAffectee]:
    """Vectorised :func:`affecter_hauteur` on dicts with x, y, h_m, h_source."""
    index = STRtree([h.polygone for h in houppiers]) if houppiers else None
    return [
        affecter_hauteur(a["x"], a["y"], houppiers, a.get("h_m"), a.get("h_source"), index)
        for a in arbres
    ]


def houppiers_dominants(houppiers: Sequence[Houppier], quantile: float = 0.5) -> list[Houppier]:
    """Crowns whose height is at or above the given quantile of the plot."""
    valides = [h for h in houppiers if h.h_max is not None and H_MIN_M <= h.h_max <= H_MAX_M]
    if not valides:
        return []
    seuil = float(np.quantile([h.h_max for h in valides], quantile))
    return [h for h in valides if h.h_max >= seuil]


@dataclass(frozen=True)
class RecalageApex:
    rotation_deg: float
    translation: tuple[float, float]
    n_apparies: int
    rmse_m: float
    alerte: bool


def recaler_sur_apex(
    troncs: np.ndarray,
    apex: np.ndarray,
    centre: Sequence[float],
    tolerance_m: float = 2.0,
    rotation_max_deg: float = 30.0,
    pas_deg: float = 0.5,
    seuil_alerte_deg: float = 5.0,
) -> RecalageApex | None:
    """Rotation about the centre (plus small translation) aligning trunks on apexes.

    Exhaustive search on the rotation, counting the trunks with an apex
    within ``tolerance_m`` (apexes are not exactly above the stems, so the
    tolerance is loose), then least-squares refinement on the matched pairs.
    ``alerte`` is set when the rotation exceeds ``seuil_alerte_deg``, i.e.
    when the compass heading and the crowns disagree.
    """
    troncs = np.asarray(troncs, dtype=float)
    apex = np.asarray(apex, dtype=float)
    if len(troncs) < 3 or len(apex) < 3:
        return None
    from scipy.spatial import cKDTree

    arbre = cKDTree(apex)
    c = np.asarray(centre[:2], dtype=float)
    rel = troncs - c

    def tourner(theta_deg: float) -> np.ndarray:
        t = math.radians(theta_deg)
        co, si = math.cos(t), math.sin(t)
        return np.column_stack([rel[:, 0] * co + rel[:, 1] * si, -rel[:, 0] * si + rel[:, 1] * co]) + c

    meilleur = (-1, float("inf"), 0.0)
    for theta in np.arange(-rotation_max_deg, rotation_max_deg + 1e-9, pas_deg):
        d, _ = arbre.query(tourner(theta), distance_upper_bound=tolerance_m)
        ok = np.isfinite(d)
        n, s = int(ok.sum()), float(np.sum(d[ok] ** 2))
        # Most matches first, then smallest residual, then smallest rotation.
        cle = (n, -s, -abs(theta))
        if cle > (meilleur[0], -meilleur[1], -abs(meilleur[2])):
            meilleur = (n, s, float(theta))
    n, _, theta = meilleur
    if n < 3:
        return None

    # Refinement: 2D rigid least squares (Kabsch) on the matched pairs.
    p = tourner(theta)
    d, j = arbre.query(p, distance_upper_bound=tolerance_m)
    ok = np.isfinite(d)
    a, b = p[ok], apex[j[ok]]
    ma, mb = a.mean(axis=0), b.mean(axis=0)
    h = (a - ma).T @ (b - mb)
    dtheta = math.degrees(math.atan2(h[0, 1] - h[1, 0], h[0, 0] + h[1, 1]))
    # atan2 above gives a counter-clockwise angle; azimuth rotations are clockwise.
    rotation = theta - dtheta
    t = math.radians(dtheta)
    rot = np.array([[math.cos(t), -math.sin(t)], [math.sin(t), math.cos(t)]])
    a2 = (a - ma) @ rot.T + mb
    rmse = float(np.sqrt(np.mean(np.sum((a2 - b) ** 2, axis=1))))
    trans = (float(mb[0] - ma[0]), float(mb[1] - ma[1]))
    return RecalageApex(
        rotation_deg=float(rotation),
        translation=trans,
        n_apparies=int(ok.sum()),
        rmse_m=rmse,
        alerte=abs(rotation) > seuil_alerte_deg,
    )


def houppiers_sans_tronc(
    houppiers: Sequence[Houppier],
    troncs: np.ndarray,
    centre: Sequence[float],
    rayon_m: float,
    quantile_dominant: float = 0.5,
    marge_m: float = 1.0,
) -> list[int]:
    """Dominant crowns, with apex inside the plot, containing no detected trunk."""
    troncs = np.asarray(troncs, dtype=float).reshape(-1, 2)
    alertes = []
    for h in houppiers_dominants(houppiers, quantile_dominant):
        ax, ay = h.apex
        if math.hypot(ax - centre[0], ay - centre[1]) > rayon_m - marge_m:
            continue
        zone = h.polygone.buffer(marge_m)
        if not any(zone.covers(Point(x, y)) for x, y in troncs):
            alertes.append(h.houppier_id)
    return alertes


@dataclass(frozen=True)
class Incoherence:
    indice: int
    motif: str
    valeur: float


def controler_coherence(
    dbh_cm: Sequence[float],
    h_m: Sequence[float | None],
    surface_houppier_m2: Sequence[float | None] | None = None,
    elancement_min: float = 30.0,
    elancement_max: float = 150.0,
    ratio_houppier_min: float = 5.0,
    ratio_houppier_max: float = 60.0,
) -> list[Incoherence]:
    """Flag implausible diameter / height / crown combinations.

    * slenderness ``100 * h_m / dbh_cm`` outside [30, 150];
    * crown diameter (from its area) over trunk diameter outside [5, 60].
    """
    out: list[Incoherence] = []
    for i, d in enumerate(dbh_cm):
        h = h_m[i] if i < len(h_m) else None
        if d and d > 0 and h:
            e = 100.0 * h / d
            if not (elancement_min <= e <= elancement_max):
                out.append(Incoherence(i, "elancement", round(e, 1)))
        if surface_houppier_m2 is not None and d and d > 0:
            s = surface_houppier_m2[i]
            if s:
                ratio = 2.0 * math.sqrt(s / math.pi) / (d / 100.0)
                if not (ratio_houppier_min <= ratio <= ratio_houppier_max):
                    out.append(Incoherence(i, "houppier_diametre", round(ratio, 1)))
    return out
