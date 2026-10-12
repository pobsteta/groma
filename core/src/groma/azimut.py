"""Azimuth arithmetic: units, magnetic declination, Lambert-93 meridian convergence.

Conventions used throughout Groma:

* Planar coordinates are ``x`` towards east and ``y`` towards north.
* An azimuth is measured clockwise from north, in ``[0, 360)`` degrees
  (or ``[0, 400)`` grades when a plot is set to ``unite_azimut = "gr"``).
  Internally every computation is done in degrees; conversion to the plot
  unit happens only when values are stored or displayed.
* Three norths are involved:

  - magnetic north (compass): ``az_geo = az_mag + declinaison_deg``
    (declination positive towards east);
  - geographic (true) north: the reference of the numbering rule;
  - Lambert-93 grid north: ``az_geo = az_grille + convergence_deg`` with
    ``convergence_deg = n * (lon - 3°)``. It reaches about ±3° at the edges
    of mainland France, far above the 1° tie tolerance of the numbering
    rule, so it cannot be ignored.
"""

from __future__ import annotations

import math
from typing import Literal

Unite = Literal["deg", "gr"]

DEGRES_PAR_GRADE = 0.9

# Cone constant of the Lambert-93 projection (EPSG:2154) and its central meridian.
LAMBERT93_N = 0.7256077650532670
LAMBERT93_LON0_DEG = 3.0


def normaliser_deg(az_deg: float) -> float:
    """Wrap an angle in degrees to ``[0, 360)``."""
    a = math.fmod(az_deg, 360.0)
    if a < 0:
        a += 360.0
    # fmod can return 360.0 - epsilon rounding to 360.0 after the addition.
    return 0.0 if a >= 360.0 else a


def ecart_deg(a_deg: float, b_deg: float) -> float:
    """Signed smallest difference ``a - b`` wrapped to ``(-180, 180]``."""
    d = normaliser_deg(a_deg - b_deg)
    return d - 360.0 if d > 180.0 else d


def vers_unite(az_deg: float, unite: Unite) -> float:
    """Convert an azimuth in degrees to the plot unit."""
    if unite == "deg":
        return normaliser_deg(az_deg)
    if unite == "gr":
        return normaliser_deg(az_deg) / DEGRES_PAR_GRADE
    raise ValueError(f"Unité d'azimut inconnue : {unite!r} (attendu 'deg' ou 'gr').")


def depuis_unite(valeur: float, unite: Unite) -> float:
    """Convert an azimuth from the plot unit to degrees."""
    if unite == "deg":
        return normaliser_deg(valeur)
    if unite == "gr":
        return normaliser_deg(valeur * DEGRES_PAR_GRADE)
    raise ValueError(f"Unité d'azimut inconnue : {unite!r} (attendu 'deg' ou 'gr').")


def azimut_deg(dx: float, dy: float) -> float:
    """Azimuth (degrees, clockwise from the +y axis) of the vector ``(dx, dy)``."""
    return normaliser_deg(math.degrees(math.atan2(dx, dy)))


def convergence_lambert93_deg(lon_deg: float) -> float:
    """Meridian convergence of Lambert-93 at a given longitude (degrees).

    ``az_geo = az_grille + convergence``. Positive east of 3°E.
    """
    return LAMBERT93_N * (lon_deg - LAMBERT93_LON0_DEG)


def grille_vers_geographique(az_grille_deg: float, convergence_deg: float) -> float:
    return normaliser_deg(az_grille_deg + convergence_deg)


def geographique_vers_grille(az_geo_deg: float, convergence_deg: float) -> float:
    return normaliser_deg(az_geo_deg - convergence_deg)


def magnetique_vers_geographique(az_mag_deg: float, declinaison_deg: float) -> float:
    return normaliser_deg(az_mag_deg + declinaison_deg)


def distance_horizontale(distance_pente_m: float, denivele_m: float) -> float:
    """Horizontal distance from a slope distance and the height difference."""
    if abs(denivele_m) > distance_pente_m:
        raise ValueError("Le dénivelé dépasse la distance mesurée.")
    return math.sqrt(distance_pente_m**2 - denivele_m**2)
