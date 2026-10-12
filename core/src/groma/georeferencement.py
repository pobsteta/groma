"""Georeferencing of a local reconstruction with the RTK centre and the targets.

The reconstruction frame is metric and gravity-aligned (ARCore), with an
arbitrary heading. Georeferencing therefore estimates a 2D similarity with a
fixed point:

* translation: C0 (marker on the RTK pole) goes to the RTK centre;
* rotation about the vertical: least squares (circular mean) between the
  local azimuths of C1-C3 seen from C0 and their measured azimuths, corrected
  for magnetic declination and Lambert-93 meridian convergence;
* scale: applied only when the measured distances justify it (consistent
  ratio, deviation above a threshold).

The heights are kept as reconstructed (z offset given by the ground model).
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Mapping, Sequence

import numpy as np

from .azimut import (
    Unite,
    azimut_deg,
    depuis_unite,
    ecart_deg,
    geographique_vers_grille,
    magnetique_vers_geographique,
    normaliser_deg,
)


@dataclass(frozen=True)
class CibleMesuree:
    id_cible: str
    azimut_mesure: float  # magnetic azimuth, plot unit
    distance_mesuree_m: float  # horizontal distance centre -> target
    dbh_mesure_cm: float | None = None


@dataclass
class Georeferencement:
    centre_local: tuple[float, float]
    centre_grille: tuple[float, float]
    rotation_deg: float  # clockwise rotation, local azimuth -> grid azimuth
    echelle: float
    echelle_appliquee: bool
    residus_azimut_deg: dict[str, float] = field(default_factory=dict)
    residus_distance_m: dict[str, float] = field(default_factory=dict)
    residus_position_m: dict[str, float] = field(default_factory=dict)

    @property
    def rmse_azimut_deg(self) -> float:
        v = list(self.residus_azimut_deg.values())
        return math.sqrt(sum(x * x for x in v) / len(v)) if v else float("nan")

    def vers_grille(self, pts_local: np.ndarray) -> np.ndarray:
        """Transform local (x, y[, z]) points to Lambert-93 (x, y[, z])."""
        pts = np.atleast_2d(np.asarray(pts_local, dtype=float))
        dx = pts[:, 0] - self.centre_local[0]
        dy = pts[:, 1] - self.centre_local[1]
        t = math.radians(self.rotation_deg)
        c, s = math.cos(t), math.sin(t)
        k = self.echelle if self.echelle_appliquee else 1.0
        x = self.centre_grille[0] + k * (dx * c + dy * s)
        y = self.centre_grille[1] + k * (-dx * s + dy * c)
        out = np.column_stack([x, y])
        if pts.shape[1] > 2:
            out = np.column_stack([out, pts[:, 2:]])
        return out


def azimut_grille_mesure(
    azimut_mesure: float, unite: Unite, declinaison_deg: float, convergence_deg: float
) -> float:
    """Grid azimuth (degrees) of a compass reading in the plot unit."""
    az_mag = depuis_unite(azimut_mesure, unite)
    return geographique_vers_grille(magnetique_vers_geographique(az_mag, declinaison_deg), convergence_deg)


def georeferencer(
    centre_local: Sequence[float],
    cibles_locales: Mapping[str, Sequence[float]],
    cibles_mesurees: Sequence[CibleMesuree],
    centre_grille: Sequence[float],
    unite: Unite = "deg",
    declinaison_deg: float = 0.0,
    convergence_deg: float = 0.0,
    seuil_echelle: float = 0.01,
    dispersion_echelle_max: float = 0.01,
) -> Georeferencement:
    """Estimate the local -> Lambert-93 transform.

    ``centre_local``: C0 in the reconstruction (x, y). ``cibles_locales``:
    reconstructed positions of C1-C3 (x, y). Targets missing on either side
    are ignored; at least one is required.
    """
    communs = [c for c in cibles_mesurees if c.id_cible in cibles_locales]
    if not communs:
        raise ValueError("Aucune cible commune entre la reconstruction et les mesures.")
    cx, cy = float(centre_local[0]), float(centre_local[1])

    az_loc, az_mes, d_loc, d_mes = {}, {}, {}, {}
    for c in communs:
        px, py = cibles_locales[c.id_cible][:2]
        az_loc[c.id_cible] = azimut_deg(px - cx, py - cy)
        d_loc[c.id_cible] = math.hypot(px - cx, py - cy)
        az_mes[c.id_cible] = azimut_grille_mesure(c.azimut_mesure, unite, declinaison_deg, convergence_deg)
        d_mes[c.id_cible] = c.distance_mesuree_m

    # Rotation: weighted circular mean of the azimuth differences. Distant
    # targets constrain the heading better; weight by the local distance.
    sx = sy = 0.0
    for k in az_loc:
        t = math.radians(ecart_deg(az_mes[k], az_loc[k]))
        w = d_loc[k]
        sx += w * math.cos(t)
        sy += w * math.sin(t)
    rotation = normaliser_deg(math.degrees(math.atan2(sy, sx)))

    ratios = np.array([d_mes[k] / d_loc[k] for k in d_loc if d_loc[k] > 0])
    echelle = float(np.median(ratios)) if ratios.size else 1.0
    dispersion = float(np.max(np.abs(ratios - echelle))) if ratios.size > 1 else float("inf")
    appliquer = (
        ratios.size >= 2 and abs(echelle - 1.0) > seuil_echelle and dispersion <= dispersion_echelle_max
    )

    geo = Georeferencement(
        centre_local=(cx, cy),
        centre_grille=(float(centre_grille[0]), float(centre_grille[1])),
        rotation_deg=rotation,
        echelle=echelle,
        echelle_appliquee=appliquer,
    )
    for c in communs:
        k = c.id_cible
        geo.residus_azimut_deg[k] = ecart_deg(normaliser_deg(az_loc[k] + rotation), az_mes[k])
        d_corr = d_loc[k] * (echelle if appliquer else 1.0)
        geo.residus_distance_m[k] = d_corr - d_mes[k]
        p = geo.vers_grille(np.array([cibles_locales[k][:2]]))[0]
        a = math.radians(az_mes[k])
        attendu = (
            geo.centre_grille[0] + d_mes[k] * math.sin(a),
            geo.centre_grille[1] + d_mes[k] * math.cos(a),
        )
        geo.residus_position_m[k] = math.hypot(p[0] - attendu[0], p[1] - attendu[1])
    return geo
