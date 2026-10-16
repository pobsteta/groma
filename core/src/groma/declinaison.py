"""Magnetic declination from the World Magnetic Model WMM2025.

Coefficients: ``donnees/WMM2025.COF``, the official NOAA/NCEI file (public
domain), epoch 2025.0, valid until 2030.0. Spherical harmonics to degree 12
with secular variation, following the WMM2025 technical report: geodetic
WGS84 coordinates are converted to geocentric, the field is computed in the
geocentric frame, then rotated back to the geodetic frame.

Same algorithm in Kotlin (``groma.core.Declinaison``); both are checked
against ``fixtures/declinaison/wmm2025.json``.
"""

from __future__ import annotations

import datetime as dt
import math
from functools import lru_cache
from importlib import resources

DEGRE = 12
# WGS84 ellipsoid (km) and geomagnetic reference radius.
A_WGS84 = 6378.137
F_WGS84 = 1 / 298.257223563
E2 = F_WGS84 * (2 - F_WGS84)
RAYON_REF = 6371.2


@lru_cache(maxsize=1)
def _coefficients() -> tuple[float, dict[tuple[int, int], tuple[float, float, float, float]]]:
    """Epoch and ``(n, m) -> (g, h, g_dot, h_dot)`` read from the COF file."""
    lignes = resources.files("groma").joinpath("donnees/WMM2025.COF").read_text("ascii").splitlines()
    epoque = float(lignes[0].split()[0])
    coefs = {}
    for ligne in lignes[1:]:
        champs = ligne.split()
        if len(champs) != 6:
            break  # trailer line of 9s
        n, m = int(champs[0]), int(champs[1])
        coefs[(n, m)] = tuple(float(c) for c in champs[2:])
    return epoque, coefs


def annee_decimale(date: dt.date | dt.datetime | float) -> float:
    """Decimal year as in the WMM software: year + (day of year - 1) / days in year."""
    if isinstance(date, (int, float)):
        return float(date)
    if isinstance(date, dt.datetime):
        date = date.date()
    jours = 366 if dt.date(date.year, 12, 31).timetuple().tm_yday == 366 else 365
    return date.year + (date.timetuple().tm_yday - 1) / jours


def declinaison_deg(lat: float, lon: float, date: dt.date | dt.datetime | float, altitude_m: float = 0.0) -> float:
    """Magnetic declination (degrees, positive east) at a WGS84 point.

    ``date``: date, datetime or decimal year. ``altitude_m``: height above
    the ellipsoid. Raises ``ValueError`` outside the model validity
    (2025.0 to 2030.0): an extrapolated value would be wrong without warning.
    """
    epoque, coefs = _coefficients()
    t = annee_decimale(date)
    if not epoque <= t <= epoque + 5:
        raise ValueError(f"Date {t:.2f} hors de la validité du modèle WMM2025 ({epoque:.1f} à {epoque + 5:.1f}).")
    if not -90 <= lat <= 90:
        raise ValueError(f"Latitude {lat} hors de [-90, 90].")
    dt_annees = t - epoque
    h = altitude_m / 1000

    # Geodetic -> geocentric spherical coordinates.
    phi = math.radians(lat)
    lam = math.radians(lon)
    rc = A_WGS84 / math.sqrt(1 - E2 * math.sin(phi) ** 2)
    p = (rc + h) * math.cos(phi)
    z = (rc * (1 - E2) + h) * math.sin(phi)
    r = math.hypot(p, z)
    phi_c = math.asin(z / r)

    # Schmidt semi-normalised associated Legendre functions of the
    # colatitude and their derivatives (Gauss recursion, then scaling).
    theta = math.pi / 2 - phi_c
    ct, st = math.cos(theta), math.sin(theta)
    st = max(st, 1e-12)  # poles: Y is undefined there, avoid a division by zero
    P = [[0.0] * (DEGRE + 1) for _ in range(DEGRE + 1)]
    dP = [[0.0] * (DEGRE + 1) for _ in range(DEGRE + 1)]
    P[0][0] = 1.0
    for n in range(1, DEGRE + 1):
        for m in range(n + 1):
            if n == m:
                P[n][m] = st * P[n - 1][m - 1]
                dP[n][m] = st * dP[n - 1][m - 1] + ct * P[n - 1][m - 1]
            elif n == 1:
                P[n][m] = ct * P[n - 1][m]
                dP[n][m] = ct * dP[n - 1][m] - st * P[n - 1][m]
            else:
                k = ((n - 1) ** 2 - m ** 2) / ((2 * n - 1) * (2 * n - 3))
                P[n][m] = ct * P[n - 1][m] - k * P[n - 2][m]
                dP[n][m] = ct * dP[n - 1][m] - st * P[n - 1][m] - k * dP[n - 2][m]
    S = [[0.0] * (DEGRE + 1) for _ in range(DEGRE + 1)]
    S[0][0] = 1.0
    for n in range(1, DEGRE + 1):
        S[n][0] = S[n - 1][0] * (2 * n - 1) / n
        for m in range(1, n + 1):
            S[n][m] = S[n][m - 1] * math.sqrt((n - m + 1) * (2 if m == 1 else 1) / (n + m))

    # Field components in the geocentric frame (north, east, down).
    x_c = y_c = z_c = 0.0
    for n in range(1, DEGRE + 1):
        rapport = (RAYON_REF / r) ** (n + 2)
        for m in range(n + 1):
            g, hh, gd, hd = coefs[(n, m)]
            g += dt_annees * gd
            hh += dt_annees * hd
            cm, sm = math.cos(m * lam), math.sin(m * lam)
            pnm, dpnm = S[n][m] * P[n][m], S[n][m] * dP[n][m]
            x_c += rapport * (g * cm + hh * sm) * dpnm
            y_c += rapport * m * (g * sm - hh * cm) * pnm / st
            z_c -= (n + 1) * rapport * (g * cm + hh * sm) * pnm
    # dP is taken with respect to the colatitude: X = -B_theta = +sum(... dP/dtheta)
    # with B_theta = -(1/r) dV/dtheta, hence the sign above. Rotate to geodetic.
    psi = phi_c - phi
    x = x_c * math.cos(psi) - z_c * math.sin(psi)
    return math.degrees(math.atan2(y_c, x))
