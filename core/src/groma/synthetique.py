"""Synthetic plot: trees, sloping ground, DTM, CHM, crowns and a point cloud.

Used by the tests, by the server end-to-end test and to build the example
GeoPackage. Everything is deterministic for a given seed.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field

import numpy as np
from shapely.geometry import Point

from .houppiers import Houppier
from .mnt import Raster


@dataclass(frozen=True)
class ArbreSynthetique:
    id: str
    x: float  # local metres, origin at the plot centre, y = grid north
    y: float
    dbh_cm: float
    h_m: float
    rayon_houppier_m: float
    decalage_apex: tuple[float, float]


@dataclass
class PlacetteSynthetique:
    centre: tuple[float, float]  # Lambert-93
    arbres: list[ArbreSynthetique]
    pente_x: float  # dz/dx
    pente_y: float  # dz/dy
    z0: float
    cibles: dict[str, str] = field(default_factory=dict)  # id_cible -> tree id

    def z_sol(self, x, y):
        """Ground height at Lambert-93 coordinates."""
        return self.z0 + self.pente_x * (np.asarray(x) - self.centre[0]) + self.pente_y * (
            np.asarray(y) - self.centre[1]
        )

    def position(self, a: ArbreSynthetique) -> tuple[float, float]:
        return self.centre[0] + a.x, self.centre[1] + a.y


def placette_synthetique(
    centre: tuple[float, float] = (925_000.0, 6_700_000.0),
    n_arbres: int = 25,
    rayon_m: float = 20.0,
    graine: int = 42,
    gros_bois: bool = True,
) -> PlacetteSynthetique:
    rng = np.random.default_rng(graine)
    arbres: list[ArbreSynthetique] = []
    tentatives = 0
    while len(arbres) < n_arbres and tentatives < 10_000:
        tentatives += 1
        d = rayon_m * 1.15 * math.sqrt(rng.uniform(0.02, 1.0))
        az = rng.uniform(0, 2 * math.pi)
        x, y = d * math.sin(az), d * math.cos(az)
        dbh = float(np.clip(rng.lognormal(math.log(30), 0.45), 8.0, 90.0))
        if any(math.hypot(x - a.x, y - a.y) < (dbh + a.dbh_cm) / 200 + 1.0 for a in arbres):
            continue
        h = float(np.clip(1.3 + 0.9 * dbh, 8.0, 40.0))
        arbres.append(
            ArbreSynthetique(
                id=f"S{len(arbres) + 1:02d}",
                x=round(x, 3),
                y=round(y, 3),
                dbh_cm=round(dbh, 1),
                h_m=round(h, 1),
                rayon_houppier_m=round(1.0 + 0.06 * dbh, 2),
                decalage_apex=(float(rng.normal(0, 0.4)), float(rng.normal(0, 0.4))),
            )
        )
    if gros_bois:
        # A 210 cm veteran, 9 m from the centre, to exercise partial arcs.
        arbres = [a for a in arbres if math.hypot(a.x - 6.4, a.y - 6.4) > 3.0]
        arbres.append(ArbreSynthetique("S99", 6.4, 6.4, 210.0, 38.0, 7.0, (0.5, -0.3)))
    # Targets: three well spread trees between 5 and 15 m.
    candidats = sorted(
        (a for a in arbres if 5 <= math.hypot(a.x, a.y) <= 15 and a.dbh_cm < 100),
        key=lambda a: math.atan2(a.x, a.y),
    )
    cibles = {}
    if len(candidats) >= 3:
        k = len(candidats)
        for i, idx in enumerate((0, k // 3, 2 * k // 3)):
            cibles[f"C{i + 1}"] = candidats[idx].id
    return PlacetteSynthetique(centre, arbres, pente_x=0.12, pente_y=-0.03, z0=420.0, cibles=cibles)


def mnt_synthetique(p: PlacetteSynthetique, demi_cote_m: float = 30.0, res: float = 0.5) -> Raster:
    n = int(2 * demi_cote_m / res)
    x0, y0 = p.centre[0] - demi_cote_m, p.centre[1] + demi_cote_m
    xs = x0 + (np.arange(n) + 0.5) * res
    ys = y0 - (np.arange(n) + 0.5) * res
    gx, gy = np.meshgrid(xs, ys)
    return Raster(p.z_sol(gx, gy), x0, y0, res, None)


def houppiers_synthetiques(p: PlacetteSynthetique) -> list[Houppier]:
    out = []
    for k, a in enumerate(p.arbres, start=1):
        cx, cy = p.position(a)
        poly = Point(cx + a.decalage_apex[0], cy + a.decalage_apex[1]).buffer(a.rayon_houppier_m, 16)
        out.append(Houppier(k, poly, a.h_m, round(poly.area, 1)))
    return out


def mnh_synthetique(p: PlacetteSynthetique, demi_cote_m: float = 30.0, res: float = 0.5) -> Raster:
    n = int(2 * demi_cote_m / res)
    x0, y0 = p.centre[0] - demi_cote_m, p.centre[1] + demi_cote_m
    xs = x0 + (np.arange(n) + 0.5) * res
    ys = y0 - (np.arange(n) + 0.5) * res
    gx, gy = np.meshgrid(xs, ys)
    h = np.zeros_like(gx)
    for a in p.arbres:
        cx, cy = p.position(a)
        cx += a.decalage_apex[0]
        cy += a.decalage_apex[1]
        r = np.hypot(gx - cx, gy - cy)
        cone = a.h_m * (1 - 0.3 * (r / a.rayon_houppier_m) ** 2)
        h = np.where(r <= a.rayon_houppier_m, np.maximum(h, cone), h)
    return Raster(h, x0, y0, res, None)


def nuage_synthetique(
    p: PlacetteSynthetique,
    bruit_m: float = 0.006,
    densite_pts_par_m2: float = 8000.0,
    graine: int = 1,
    repere_local: bool = True,
) -> np.ndarray:
    """Point cloud of the trunks (0-3 m above ground) and of the ground.

    Each trunk is seen on the half facing the plot centre (the operator walks
    around the centre) plus a random extra arc, with gaps, and Gaussian noise.
    Coordinates are local (origin at the centre) unless ``repere_local`` is
    False. z is the absolute altitude.
    """
    rng = np.random.default_rng(graine)
    blocs = []
    for a in p.arbres:
        r = a.dbh_cm / 200.0
        vers_centre = math.atan2(-a.y, -a.x)
        demi_ouverture = math.radians(rng.uniform(70, 120))
        # Visible bark area over 0-3 m times the surface density.
        n_pts = max(200, int(2 * demi_ouverture * r * 3.0 * densite_pts_par_m2))
        ang = vers_centre + rng.uniform(-demi_ouverture, demi_ouverture, n_pts)
        # Two gaps on large trunks (occlusion, bark without texture).
        if a.dbh_cm > 100:
            for c in (vers_centre - 0.3, vers_centre + 0.45):
                ang = ang[np.abs(np.angle(np.exp(1j * (ang - c)))) > 0.12]
        z_rel = rng.uniform(0.0, 3.0, ang.size)
        x = a.x + r * np.cos(ang) + rng.normal(0, bruit_m, ang.size)
        y = a.y + r * np.sin(ang) + rng.normal(0, bruit_m, ang.size)
        blocs.append(np.column_stack([x, y, z_rel]))
    pts = np.vstack(blocs) if blocs else np.zeros((0, 3))
    gx, gy = rng.uniform(-25, 25, (2, 20_000))
    sol = np.column_stack([gx, gy, np.zeros_like(gx)])
    # Ground points must not fall inside a trunk.
    for a in p.arbres:
        sol = sol[np.hypot(sol[:, 0] - a.x, sol[:, 1] - a.y) > a.dbh_cm / 200.0]
    pts = np.vstack([pts, sol])
    ax = pts[:, 0] + p.centre[0]
    ay = pts[:, 1] + p.centre[1]
    pts[:, 2] = pts[:, 2] + p.z_sol(ax, ay) + rng.normal(0, bruit_m, pts.shape[0])
    if not repere_local:
        pts[:, 0], pts[:, 1] = ax, ay
    return pts
