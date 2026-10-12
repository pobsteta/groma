"""DTM / CHM helpers: sampling, slope and aspect, canopy cover, vertical check.

Rasters are handled as a plain numpy grid plus a north-up affine geotransform,
so the same logic runs without GDAL (tests, phone port). :func:`lire_geotiff`
uses rasterio when it is installed.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from pathlib import Path

import numpy as np

from .azimut import grille_vers_geographique, normaliser_deg

EXPOSITIONS = ("N", "NE", "E", "SE", "S", "SW", "W", "NW")


@dataclass
class Raster:
    """North-up grid. ``x0, y0``: top-left corner; ``res``: pixel size (m)."""

    valeurs: np.ndarray
    x0: float
    y0: float
    res: float
    nodata: float | None = -9999.0

    def __post_init__(self) -> None:
        v = np.asarray(self.valeurs, dtype=float)
        if self.nodata is not None:
            v = np.where(v == self.nodata, np.nan, v)
        self.valeurs = v

    @property
    def forme(self) -> tuple[int, int]:
        return self.valeurs.shape

    def centres(self) -> tuple[np.ndarray, np.ndarray]:
        lignes, colonnes = self.forme
        xs = self.x0 + (np.arange(colonnes) + 0.5) * self.res
        ys = self.y0 - (np.arange(lignes) + 0.5) * self.res
        return np.meshgrid(xs, ys)

    def echantillonner(self, x, y) -> np.ndarray:
        """Bilinear interpolation; NaN outside the grid or on nodata."""
        x = np.atleast_1d(np.asarray(x, dtype=float))
        y = np.atleast_1d(np.asarray(y, dtype=float))
        c = (x - self.x0) / self.res - 0.5
        l = (self.y0 - y) / self.res - 0.5
        lignes, colonnes = self.forme
        c0 = np.floor(c).astype(int)
        l0 = np.floor(l).astype(int)
        fc, fl = c - c0, l - l0
        out = np.full(x.shape, np.nan)
        ok = (c0 >= 0) & (l0 >= 0) & (c0 + 1 < colonnes) & (l0 + 1 < lignes)
        if ok.any():
            v = self.valeurs
            a = v[l0[ok], c0[ok]]
            b = v[l0[ok], c0[ok] + 1]
            cc = v[l0[ok] + 1, c0[ok]]
            d = v[l0[ok] + 1, c0[ok] + 1]
            out[ok] = (
                a * (1 - fc[ok]) * (1 - fl[ok])
                + b * fc[ok] * (1 - fl[ok])
                + cc * (1 - fc[ok]) * fl[ok]
                + d * fc[ok] * fl[ok]
            )
        return out


def lire_geotiff(chemin: str | Path) -> Raster:
    import rasterio

    with rasterio.open(chemin) as src:
        t = src.transform
        if abs(t.b) > 1e-12 or abs(t.d) > 1e-12 or abs(abs(t.a) - abs(t.e)) > 1e-9:
            raise ValueError("Seules les grilles nord en haut à pixels carrés sont prises en charge.")
        return Raster(src.read(1).astype(float), t.c, t.f, t.a, src.nodata)


def ecrire_geotiff(raster: Raster, chemin: str | Path, crs: int = 2154) -> None:
    """Write the Groma raster flavour: Float32, strips, DEFLATE without predictor."""
    import rasterio
    from rasterio.transform import from_origin

    nodata = -9999.0
    v = np.where(np.isnan(raster.valeurs), nodata, raster.valeurs).astype("float32")
    with rasterio.open(
        chemin,
        "w",
        driver="GTiff",
        height=v.shape[0],
        width=v.shape[1],
        count=1,
        dtype="float32",
        crs=f"EPSG:{crs}",
        transform=from_origin(raster.x0, raster.y0, raster.res, raster.res),
        nodata=nodata,
        compress="deflate",
        predictor=1,
        tiled=False,
    ) as dst:
        dst.write(v, 1)


def decouper(raster: Raster, x: float, y: float, demi_cote_m: float) -> Raster:
    """Square window centred on (x, y), snapped to the pixel grid."""
    c0 = max(0, int(math.floor((x - demi_cote_m - raster.x0) / raster.res)))
    c1 = min(raster.forme[1], int(math.ceil((x + demi_cote_m - raster.x0) / raster.res)))
    l0 = max(0, int(math.floor((raster.y0 - (y + demi_cote_m)) / raster.res)))
    l1 = min(raster.forme[0], int(math.ceil((raster.y0 - (y - demi_cote_m)) / raster.res)))
    return Raster(
        raster.valeurs[l0:l1, c0:c1].copy(),
        raster.x0 + c0 * raster.res,
        raster.y0 - l0 * raster.res,
        raster.res,
        None,
    )


def pente_exposition(
    mnt: Raster,
    x: float,
    y: float,
    rayon_m: float,
    convergence_deg: float = 0.0,
    seuil_plat_pct: float = 5.0,
) -> tuple[float, str]:
    """Slope (%) and aspect class of the plane fitted on the DTM in the plot.

    The aspect is the geographic azimuth of the steepest descent, in the
    eight nemeton classes, or ``"flat"`` under ``seuil_plat_pct``.
    """
    gx, gy = mnt.centres()
    dans = (np.hypot(gx - x, gy - y) <= rayon_m) & np.isfinite(mnt.valeurs)
    if dans.sum() < 3:
        raise ValueError("MNT insuffisant dans la placette.")
    a = np.column_stack([gx[dans] - x, gy[dans] - y, np.ones(dans.sum())])
    (px, py, _), *_ = np.linalg.lstsq(a, mnt.valeurs[dans], rcond=None)
    pente = 100.0 * math.hypot(px, py)
    if pente < seuil_plat_pct:
        return round(pente, 1), "flat"
    az_grille = normaliser_deg(math.degrees(math.atan2(-px, -py)))
    az = grille_vers_geographique(az_grille, convergence_deg)
    return round(pente, 1), EXPOSITIONS[int(((az + 22.5) % 360.0) // 45.0)]


def recouvrement_pct(mnh: Raster, x: float, y: float, rayon_m: float, seuil_h_m: float = 2.0) -> float:
    """Share (%) of CHM cells above ``seuil_h_m`` inside the plot."""
    gx, gy = mnh.centres()
    dans = (np.hypot(gx - x, gy - y) <= rayon_m) & np.isfinite(mnh.valeurs)
    if not dans.any():
        raise ValueError("MNH absent dans la placette.")
    return round(100.0 * float(np.mean(mnh.valeurs[dans] > seuil_h_m)), 1)


@dataclass(frozen=True)
class CalageVertical:
    decalage_median_m: float
    dispersion_m: float  # median absolute deviation
    n_points: int
    conforme: bool


def controler_calage_vertical(
    sol_reconstruit: np.ndarray, mnt: Raster, tolerance_m: float = 0.3
) -> CalageVertical:
    """Compare reconstructed ground points (x, y, z, Lambert-93 / NGF) to the DTM."""
    pts = np.asarray(sol_reconstruit, dtype=float)
    z = mnt.echantillonner(pts[:, 0], pts[:, 1])
    d = pts[:, 2] - z
    d = d[np.isfinite(d)]
    if d.size == 0:
        return CalageVertical(float("nan"), float("nan"), 0, False)
    med = float(np.median(d))
    mad = float(np.median(np.abs(d - med)))
    return CalageVertical(med, mad, int(d.size), abs(med) <= tolerance_m and mad <= tolerance_m)
