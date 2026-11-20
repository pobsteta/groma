"""Forest-wide rasters of a project (schema 1.7.0): DTM tiles and the CVAT relief.

* Tile naming (reference of the Kotlin tests of mission 039): a tile is named after
  its south-west corner in Lambert-93 kilometres, four digits
  (``rasters/mnt_0849_6689.tif`` covers 849 000-850 000 m in X and
  6 689 000-6 690 000 m in Y). Intervals are half-open: a point exactly on a
  kilometre line belongs to the tile north-east of it.
* :func:`verifier_projet` checks the tiles and ``cvat.tif`` of a project folder
  written by nemeton against the schema and returns the deviations, in French.

Tile size, pixel size and paths are read from the schema
(``rasters.dalles``, ``rasters.cvat``), never hard-coded. The pixel types and
nodata values below are those written in the schema text (``rasters.format``,
``rasters.cvat.format``); a test keeps them in step.
"""

from __future__ import annotations

import logging
import math
import re
from pathlib import Path

from .gpkg import SRS_ID, charger_schema

log = logging.getLogger(__name__)

# Pixel type and nodata of the DTM tiles (``rasters.format``) and of the CVAT
# (``rasters.cvat.format``).
TYPE_MNT = "float32"
NODATA_MNT = -9999.0
TYPE_CVAT = "uint8"
NODATA_CVAT = 0.0

_NOM_TYPE = {"float32": "Float32", "float64": "Float64", "uint8": "Byte", "int16": "Int16", "uint16": "UInt16",
             "int32": "Int32", "uint32": "UInt32"}
_KM_MAX = 9999  # four digits in the name


def _rasters() -> dict:
    return charger_schema()["rasters"]


def cote_m() -> float:
    """Tile side (m), from the schema."""
    return float(_rasters()["dalles"]["cote_m"])


def _verifier_km(k: int) -> None:
    if k < 0 or k > _KM_MAX:
        raise ValueError(f"Kilomètre Lambert-93 hors de 0-{_KM_MAX} : {k}.")


def nom_dalle(x_km: int, y_km: int) -> str:
    """Path of the DTM tile whose south-west corner is (``x_km``, ``y_km``) km, Lambert-93."""
    _verifier_km(x_km)
    _verifier_km(y_km)
    return _rasters()["dalles"]["mnt"].format(x=f"{x_km:04d}", y=f"{y_km:04d}")


def _km(v: float) -> int:
    """South-west corner (km) of the tile containing coordinate ``v`` (m)."""
    c = cote_m()
    return int(math.floor(v / c) * c // 1000)


def dalle_du_point(x: float, y: float) -> tuple[int, int]:
    """(x_km, y_km) of the tile containing the Lambert-93 point (x, y), in metres."""
    if x < 0 or y < 0:
        raise ValueError(f"Coordonnées Lambert-93 négatives : ({x}, {y}).")
    k = (_km(x), _km(y))
    _verifier_km(k[0])
    _verifier_km(k[1])
    return k


def _plage(vmin: float, vmax: float) -> list[int]:
    """Tiles (km) of the half-open interval [vmin, vmax); a degenerate interval gives the tile of vmin."""
    c = cote_m()
    premier = math.floor(vmin / c)
    dernier = max(premier, math.ceil(vmax / c) - 1)
    return [int(i * c // 1000) for i in range(premier, dernier + 1)]


def dalles_du_rectangle(xmin: float, ymin: float, xmax: float, ymax: float) -> list[tuple[int, int]]:
    """Tiles (x_km, y_km) covering the rectangle, Lambert-93 metres, sorted by x then y.

    Half-open: a maximum exactly on a tile edge does not add the next tile.
    """
    if xmin > xmax or ymin > ymax:
        raise ValueError(f"Rectangle inversé : ({xmin}, {ymin}, {xmax}, {ymax}).")
    dalle_du_point(xmin, ymin)  # validates the corner (negative, out of range)
    out = [(x, y) for x in _plage(xmin, xmax) for y in _plage(ymin, ymax)]
    for x, y in out:
        _verifier_km(x)
        _verifier_km(y)
    return out


# ---- project folder check ------------------------------------------------------


def _motif_dalle(prefixe: str) -> re.Pattern:
    """Regex of a tile file name built from the schema pattern (``mnt_{x}_{y}.tif``)."""
    gabarit = Path(_rasters()["dalles"]["mnt"]).name
    if prefixe != "mnt":
        gabarit = gabarit.replace("mnt", prefixe, 1)
    motif = re.escape(gabarit).replace(r"\{x\}", r"(\d{4})").replace(r"\{y\}", r"(\d{4})")
    return re.compile(motif)


def _type(dtype: str) -> str:
    return _NOM_TYPE.get(dtype, dtype)


def _ecarts_communs(src, fichier: str, type_attendu: str, nodata: float, res: float) -> list[str]:
    ecarts = []
    if src.count != 1:
        ecarts.append(f"{fichier} : {src.count} bandes, une seule attendue.")
    if src.dtypes[0] != type_attendu:
        ecarts.append(f"{fichier} : type {_type(src.dtypes[0])}, {_type(type_attendu)} attendu.")
    epsg = src.crs.to_epsg() if src.crs is not None else None
    if epsg != SRS_ID:
        ecarts.append(f"{fichier} : système {src.crs.to_string() if src.crs else 'absent'}, EPSG:{SRS_ID} attendu.")
    t = src.transform
    if not (math.isclose(t.a, res) and math.isclose(-t.e, res) and t.b == 0 and t.d == 0):
        ecarts.append(f"{fichier} : pixel de {t.a} × {-t.e} m, {res} m nord en haut attendu.")
    if src.nodata is None or not math.isclose(src.nodata, nodata):
        ecarts.append(f"{fichier} : nodata {src.nodata}, {nodata:g} attendu.")
    compression = src.compression.name.upper() if src.compression is not None else "aucune"
    if compression != "DEFLATE":
        ecarts.append(f"{fichier} : compression {compression}, DEFLATE attendue.")
    return ecarts


def _verifier_dalle(chemin: Path, x_km: int, y_km: int) -> list[str]:
    import rasterio

    fichier = chemin.name
    r = _rasters()
    res = float(r["resolution_m"])
    c = cote_m()
    n = int(round(c / res))
    try:
        with rasterio.open(chemin) as src:
            ecarts = _ecarts_communs(src, fichier, TYPE_MNT, NODATA_MNT, res)
            if (src.width, src.height) != (n, n):
                ecarts.append(f"{fichier} : {src.width} × {src.height} pixels, {n} × {n} attendus (dalle de {c:g} m).")
            x0, y0 = x_km * 1000.0, y_km * 1000.0 + c
            t = src.transform
            if not (math.isclose(t.c, x0, abs_tol=1e-6) and math.isclose(t.f, y0, abs_tol=1e-6)):
                ecarts.append(
                    f"{fichier} : coin nord-ouest ({t.c:.3f}, {t.f:.3f}), ({x0:.0f}, {y0:.0f}) attendu "
                    f"d'après le nom (coin sud-ouest {x_km:04d} km, {y_km:04d} km, dalle alignée sur le kilomètre)."
                )
            if src.profile.get("tiled"):
                ecarts.append(f"{fichier} : dalle tuilée, bandes (strips) attendues.")
            predicteur = src.tags(ns="IMAGE_STRUCTURE").get("PREDICTOR", "1")
            if str(predicteur) not in ("1", "NONE"):
                ecarts.append(f"{fichier} : prédicteur {predicteur}, DEFLATE sans prédicteur attendu.")
    except rasterio.errors.RasterioError as e:
        return [f"{fichier} : dalle MNT illisible ({e})."]
    return ecarts


def _verifier_cvat(chemin: Path) -> list[str]:
    import rasterio

    fichier = chemin.name
    res = float(_rasters()["cvat"]["resolution_m"])
    try:
        with rasterio.open(chemin) as src:
            return _ecarts_communs(src, fichier, TYPE_CVAT, NODATA_CVAT, res)
    except rasterio.errors.RasterioError as e:
        return [f"{fichier} : relief CVAT illisible ({e})."]


def verifier_projet(dossier: Path | str) -> list[str]:
    """Deviations of the DTM tiles and ``cvat.tif`` of a project folder from the schema.

    ``dossier`` holds ``rasters/``. One message per defect, in French, naming the
    file. Per-plot rasters are not checked. A folder without tiles nor CVAT
    (schema 1.6.0 project) has no deviation.
    """
    rasters = Path(dossier) / "rasters"
    if not rasters.is_dir():
        return []
    ecarts: list[str] = []
    motif_mnt, motif_mnh = _motif_dalle("mnt"), _motif_dalle("mnh")
    for chemin in sorted(rasters.iterdir()):
        if not chemin.is_file():
            continue
        if m := motif_mnt.fullmatch(chemin.name):
            ecarts += _verifier_dalle(chemin, int(m.group(1)), int(m.group(2)))
        elif motif_mnh.fullmatch(chemin.name):
            ecarts.append(f"{chemin.name} : dalle MNH, le schéma n'en prévoit pas (MNH par placette seulement).")
    cvat = Path(dossier) / _rasters()["cvat"]["chemin"]
    if cvat.is_file():
        ecarts += _verifier_cvat(cvat)
    for e in ecarts:
        log.warning("Projet %s : %s", dossier, e)
    return ecarts
