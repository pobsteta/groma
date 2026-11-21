"""Build the real example ``exemples/marsannay_p067/`` (schema 1.7.0, mission 044).

Plot P067 of the communal forest of Marsannay-la-Côte, filmed on 2026-11-20,
on real data read **read-only** (GeoPackages opened with ``mode=ro`` or by
pyogrio/rasterio in read mode; nothing is ever written next to the sources):

* LiDAR HD DTM and CHM at 1 m (EPSG:2154), from the nemeton cache;
* the project GeoPackage copied from the phone on 2026-11-20;
* the field package ``P067.groma.zip`` (measured C0, targets C1 to C3, frames);
* optionally (``--cvat``) the CVAT relief computed by nemetonshiny.

What is written:

* ``inventaire_video.gpkg`` (``creer_gpkg``): P067 centred on the measured C0
  (decision of 2026-11-21), radius and plan attributes of the real project,
  slope, aspect and cover recomputed at the measured C0; the C0 in
  ``centres_mesures``; targets C1 to C3 placed from the package values; the real
  ``ugf`` and ``parcelle`` holding the C0, clipped to a 100 m square around it
  (none holds the C0 of 2026-11-20, about 107 m outside the communal forest: both
  layers are left empty, never replaced by an invented outline);
  crowns segmented by Groma on the CHM (local maxima + watershed,
  ``scipy.ndimage``), apex = pixel of ``h_max``, ``plot_id`` P067 when the crown
  touches the plot;
* ``rasters/mnt_0849_6689.tif``: the 1 km DTM tile of ``rasters.dalles``, bilinear
  from the 1 m DTM on the forest extent (clipped ``ugf``, or the 100 m square
  without one, plus ``marge_foret_m``), nodata elsewhere;
  ``rasters/P067_mnt.tif`` / ``P067_mnh.tif`` around the C0;
* ``rasters/cvat.tif`` clipped to the extent, only with ``--cvat``;
* ``controle/mnt_1m.tif``: the 1 m DTM window used, unchanged (test reference);
* ``paquets/P067.groma.zip``: reduced copy of the field package (every frame
  kept in the manifest, files of one frame in ``--pas-images`` only).

Run (sources at their default place):

    python exemples/creer_marsannay_p067.py [--cvat chemin/cvat.tif]
"""

from __future__ import annotations

import argparse
import copy
import json
import logging
import math
import shutil
import sqlite3
import zipfile
from pathlib import Path
from typing import Sequence

import numpy as np

log = logging.getLogger("creer_marsannay_p067")

ICI = Path(__file__).resolve().parent
SORTIE = ICI / "marsannay_p067"

# ---- default sources (read-only) ---------------------------------------------

_NEMETON = Path.home() / ".local/share/nemeton/projects/20261015_194200_yzoh/cache/layers"
_SAUVEGARDE = Path.home() / "groma-sauvegardes/2026-11-20_P067-film"
MNT_DEFAUT = _NEMETON / "lidar_mnt/dtm.tif"
MNH_DEFAUT = _NEMETON / "lidar_mnh/chm.tif"
GPKG_DEFAUT = _SAUVEGARDE / "projets/20261015_194200_yzoh/inventaire_video.gpkg"
PAQUET_DEFAUT = _SAUVEGARDE / "P067.groma.zip"

PLOT_ID = "P067"
# Own identifier: the real project (20261015_194200_yzoh) lives on the phone and
# must never be overwritten by importing the example.
PROJET_ID = "exemple_marsannay_p067"
DEMI_CARRE_M = 50.0  # forest extent: 100 m square around the C0, plus marge_foret_m
PAS_IMAGES = 25  # files of one frame in 25 kept in the package (at most one in ten)
DATE_PREPARATION = "2026-11-21 12:00:00"  # fixed: the script is reproducible

# Crown segmentation (provisional values, mission 044; replaced by nemeton's crowns).
H_MIN_SOMMET_M = 5.0
H_MIN_HOUPPIER_M = 2.0
FENETRE_MAXIMA_M = 3.0
SIGMA_LISSAGE_M = 0.5
SURFACE_MIN_M2 = 2.0


# ---- read-only access ----------------------------------------------------------


def ouvrir_ro(chemin: Path) -> sqlite3.Connection:
    """SQLite connection that cannot write (``mode=ro``)."""
    return sqlite3.connect(f"file:{Path(chemin).resolve()}?mode=ro", uri=True)


def lire_fenetre(chemin: Path, xmin: float, ymin: float, xmax: float, ymax: float):
    """Window of a north-up GeoTIFF covering the rectangle, as a :class:`groma.mnt.Raster`.

    The window is snapped outwards to the source pixel grid; the source is read only.
    """
    import rasterio
    from rasterio.windows import Window

    from groma.mnt import Raster

    with rasterio.open(chemin) as src:
        if src.crs is None or src.crs.to_epsg() != 2154:
            raise ValueError(f"{chemin} : EPSG:2154 attendu, {src.crs} trouvé.")
        t = src.transform
        res = t.a
        c0 = int(math.floor((xmin - t.c) / res))
        c1 = int(math.ceil((xmax - t.c) / res))
        l0 = int(math.floor((t.f - ymax) / res))
        l1 = int(math.ceil((t.f - ymin) / res))
        if c0 < 0 or l0 < 0 or c1 > src.width or l1 > src.height:
            raise ValueError(f"{chemin} : la fenêtre ({xmin}, {ymin}, {xmax}, {ymax}) sort du raster.")
        v = src.read(1, window=Window(c0, l0, c1 - c0, l1 - l0)).astype(float)
        return Raster(v, t.c + c0 * res, t.f - l0 * res, res, src.nodata)


# ---- geometry ------------------------------------------------------------------


def grille(x0: float, y0: float, res: float, largeur: int, hauteur: int):
    """Pixel centres of a north-up grid whose top-left corner is (x0, y0)."""
    xs = x0 + (np.arange(largeur) + 0.5) * res
    ys = y0 - (np.arange(hauteur) + 0.5) * res
    return np.meshgrid(xs, ys)


def reechantillonner(source, x0: float, y0: float, res: float, largeur: int, hauteur: int, masque=None) -> np.ndarray:
    """Bilinear resampling of ``source`` (Raster) onto a grid; NaN outside ``masque`` (shapely geometry)."""
    import shapely

    gx, gy = grille(x0, y0, res, largeur, hauteur)
    z = source.echantillonner(gx.ravel(), gy.ravel()).reshape(gx.shape)
    if masque is not None:
        z = np.where(shapely.contains_xy(masque, gx, gy), z, np.nan)
    return z


def ecrire_dalle(source, emprise, sortie: Path) -> list[Path]:
    """DTM tiles of ``rasters.dalles`` touching ``emprise``, bilinear from ``source``, nodata outside.

    Values are rounded to 1/128 m (error under 4 mm) so that DEFLATE without
    predictor keeps the tile small, as in ``creer_exemple.py``.
    """
    from groma.dalles import dalles_du_rectangle, nom_dalle
    from groma.gpkg import charger_schema
    from groma.mnt import Raster, ecrire_geotiff

    r = charger_schema()["rasters"]
    res, cote = float(r["resolution_m"]), float(r["dalles"]["cote_m"])
    n = int(round(cote / res))
    xmin, ymin, xmax, ymax = emprise.bounds
    out = []
    for x_km, y_km in dalles_du_rectangle(xmin, ymin, xmax, ymax):
        x0, y0 = x_km * 1000.0, y_km * 1000.0 + cote
        z = np.full((n, n), np.nan)
        # Only the part of the tile under the extent is computed.
        c0, c1 = max(0, int((xmin - x0) // res)), min(n, int(math.ceil((xmax - x0) / res)))
        l0, l1 = max(0, int((y0 - ymax) // res)), min(n, int(math.ceil((y0 - ymin) / res)))
        if c1 > c0 and l1 > l0:
            sous = reechantillonner(source, x0 + c0 * res, y0 - l0 * res, res, c1 - c0, l1 - l0, emprise)
            z[l0:l1, c0:c1] = np.round(sous * 128) / 128
        chemin = sortie / nom_dalle(x_km, y_km)
        chemin.parent.mkdir(parents=True, exist_ok=True)
        ecrire_geotiff(Raster(z, x0, y0, res, None), chemin)
        out.append(chemin)
    return out


def raster_placette(source, cx: float, cy: float, demi_cote_m: float, res: float):
    """Per-plot raster: square around (cx, cy) snapped to the ``res`` grid, bilinear from ``source``."""
    from groma.mnt import Raster

    x0 = math.floor((cx - demi_cote_m) / res) * res
    x1 = math.ceil((cx + demi_cote_m) / res) * res
    y0 = math.ceil((cy + demi_cote_m) / res) * res
    y1 = math.floor((cy - demi_cote_m) / res) * res
    w, h = int(round((x1 - x0) / res)), int(round((y0 - y1) / res))
    return Raster(reechantillonner(source, x0, y0, res, w, h), x0, y0, res, None)


# ---- crowns --------------------------------------------------------------------


def segmenter_houppiers(
    mnh,
    emprise,
    h_min_sommet_m: float = H_MIN_SOMMET_M,
    h_min_houppier_m: float = H_MIN_HOUPPIER_M,
    fenetre_m: float = FENETRE_MAXIMA_M,
    sigma_m: float = SIGMA_LISSAGE_M,
    surface_min_m2: float = SURFACE_MIN_M2,
) -> list[dict]:
    """Crowns of the CHM inside ``emprise``: local maxima of the smoothed CHM as
    markers, watershed (``scipy.ndimage.watershed_ift``) on the inverted CHM,
    restricted to pixels at least ``h_min_houppier_m`` high.

    Each crown: ``houppier_id`` (1.. by apex north to south, then west to east),
    ``h_max`` (raw CHM), apex ``x_sommet``/``y_sommet`` = centre of the pixel of
    ``h_max`` (Lambert-93), ``surface_m2``, ``geometry`` (pixel outline, the part
    holding the apex).
    """
    import shapely
    from rasterio.features import shapes
    from rasterio.transform import from_origin
    from scipy import ndimage
    from shapely.geometry import Point, shape

    res = mnh.res
    h = np.nan_to_num(mnh.valeurs, nan=0.0)
    gx, gy = mnh.centres()
    dedans = shapely.contains_xy(emprise, gx, gy)
    lisse = ndimage.gaussian_filter(h, sigma=sigma_m / res)
    taille = max(3, int(round(fenetre_m / res)) | 1)
    maxima = (lisse == ndimage.maximum_filter(lisse, size=taille)) & (lisse >= h_min_sommet_m) & dedans
    marqueurs, n = ndimage.label(maxima, structure=np.ones((3, 3)))
    couronne = (h >= h_min_houppier_m) & dedans
    marqueurs = marqueurs.astype(np.int32)
    marqueurs[~couronne] = 0
    # No background marker: watershed_ift starts every marker at cost 0, so a
    # background seed would flood the canopy as fast as the crowns. Crowns flood
    # everything, low vegetation and the outside of the extent are cleared after.
    cout = np.clip(np.round((lisse.max() - lisse) * 100), 0, 65535).astype(np.uint16)
    etiquettes = ndimage.watershed_ift(cout, marqueurs, structure=np.ones((3, 3), dtype=int))
    etiquettes = np.where(couronne & (etiquettes > 0), etiquettes, 0).astype(np.int32)

    parties: dict[int, list] = {}
    transform = from_origin(mnh.x0, mnh.y0, res, res)
    for geom, val in shapes(etiquettes, mask=etiquettes > 0, transform=transform, connectivity=4):
        parties.setdefault(int(val), []).append(shape(geom))

    out = []
    for k in range(1, n + 1):
        region = etiquettes == k
        if not region.any():
            continue
        l, c = np.unravel_index(np.argmax(np.where(region, h, -np.inf)), h.shape)
        apex = Point(float(gx[l, c]), float(gy[l, c]))
        poly = next((p for p in parties.get(k, []) if p.contains(apex)), None)
        if poly is None or poly.area < surface_min_m2:
            continue
        out.append({"h_max": round(float(h[l, c]), 2), "surface_m2": round(poly.area, 1),
                    "x_sommet": apex.x, "y_sommet": apex.y, "geometry": poly})
    out.sort(key=lambda d: (-d["y_sommet"], d["x_sommet"]))
    for i, d in enumerate(out, start=1):
        d["houppier_id"] = i
    return out


# ---- CVAT ----------------------------------------------------------------------

# Nodata of the 8-bit CVAT written by foretaccess for nemetonshiny (relief 0 to 254).
NODATA_CVAT_FORETACCESS = 255.0


def decouper_cvat(source: Path, emprise, destination: Path) -> Path:
    """Clip the CVAT relief to ``emprise`` (``rasters.cvat``): pixels whose centre is
    outside the extent set to nodata (0), source grid kept (no resampling).

    The source is Byte, EPSG:2154, pixel of ``resolution_m``, with one of two codings:

    * ``rasters.cvat``: nodata 0, relief 1 to 255 (kept as is);
    * foretaccess (``*_CVAT_8bit_foretaccess.tif`` written by nemetonshiny):
      nodata 255, relief 0 to 254, i.e. ``round(v * 254)``; recoded to
      ``rasters.cvat`` as value + 1, nodata 0, which is ``1 + round(v * 254)``.

    Anything else raises ``ValueError`` (never silently converted).
    """
    import rasterio
    import shapely
    from rasterio.transform import from_origin
    from rasterio.windows import Window

    from groma.dalles import NODATA_CVAT, TYPE_CVAT
    from groma.gpkg import charger_schema

    res = float(charger_schema()["rasters"]["cvat"]["resolution_m"])
    with rasterio.open(source) as src:
        t = src.transform
        if src.dtypes[0] != TYPE_CVAT:
            raise ValueError(f"{source} : type {src.dtypes[0]}, Byte attendu (rasters.cvat).")
        if src.crs is None or src.crs.to_epsg() != 2154:
            raise ValueError(f"{source} : EPSG:2154 attendu, {src.crs} trouvé.")
        if not (math.isclose(t.a, res) and math.isclose(-t.e, res) and t.b == 0 and t.d == 0):
            raise ValueError(f"{source} : pixel de {t.a} × {-t.e} m, {res} m attendu (rasters.cvat).")
        if src.nodata is None or src.nodata not in (NODATA_CVAT, NODATA_CVAT_FORETACCESS):
            raise ValueError(
                f"{source} : nodata {src.nodata}, {NODATA_CVAT:g} (rasters.cvat) "
                f"ou {NODATA_CVAT_FORETACCESS:g} (foretaccess) attendu.")
        foretaccess = src.nodata == NODATA_CVAT_FORETACCESS
        xmin, ymin, xmax, ymax = emprise.bounds
        c0 = max(0, int(math.floor((xmin - t.c) / res)))
        c1 = min(src.width, int(math.ceil((xmax - t.c) / res)))
        l0 = max(0, int(math.floor((t.f - ymax) / res)))
        l1 = min(src.height, int(math.ceil((t.f - ymin) / res)))
        if c1 <= c0 or l1 <= l0:
            raise ValueError(f"{source} : ne recouvre pas l'emprise de la forêt.")
        octets = src.read(1, window=Window(c0, l0, c1 - c0, l1 - l0))
    if foretaccess:
        # 0..254 -> 1..255, nodata 255 -> 0 (rasters.cvat).
        octets = np.where(octets == int(NODATA_CVAT_FORETACCESS), int(NODATA_CVAT),
                          octets.astype("uint16") + 1).astype("uint8")
    x0, y0 = t.c + c0 * res, t.f - l0 * res
    gx, gy = grille(x0, y0, res, octets.shape[1], octets.shape[0])
    octets = np.where(shapely.contains_xy(emprise, gx, gy), octets, int(NODATA_CVAT)).astype("uint8")
    with rasterio.open(
        destination, "w", driver="GTiff", width=octets.shape[1], height=octets.shape[0], count=1, dtype="uint8",
        crs="EPSG:2154", transform=from_origin(x0, y0, res, res), nodata=NODATA_CVAT, compress="deflate",
    ) as dst:
        dst.write(octets, 1)
    return destination


# ---- field package ---------------------------------------------------------------


_DATE_ZIP = (2026, 11, 20, 0, 0, 0)  # fixed entry dates: same bytes at each run


def alleger_paquet(source: Path, destination: Path, pas: int = PAS_IMAGES, projet_id: str | None = PROJET_ID) -> dict:
    """Reduced copy of a field package.

    The manifest keeps every key and every frame (poses and intrinsics feed the
    target triangulation); for frames whose ``id`` is not a multiple of ``pas``
    the ``image``, ``profondeur`` and ``confiance`` paths are set to null and the
    files left out, which the server readers accept. ``projet_id`` replaced when
    given. Returns the new manifest.
    """
    if pas < 10:
        raise ValueError(f"Une image sur dix au plus : pas {pas} < 10.")
    with zipfile.ZipFile(source) as z:
        m = json.loads(z.read("manifest.json"))
        neuf = copy.deepcopy(m)
        if projet_id is not None:
            neuf["projet_id"] = projet_id
        garder: list[str] = []
        for img in neuf["images"]:
            cles = [k for k in ("image", "profondeur", "confiance") if img.get(k)]
            if img["id"] % pas == 0:
                garder += [img[k] for k in cles]
            else:
                for k in cles:
                    img[k] = None
        noms = set(z.namelist())
        absents = [g for g in garder if g not in noms]
        if absents:
            raise ValueError(f"{source} : fichiers du manifeste absents du paquet : {absents[:5]}")
        destination.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(destination, "w") as dst:
            entree = zipfile.ZipInfo("manifest.json", date_time=_DATE_ZIP)
            dst.writestr(entree, json.dumps(neuf, ensure_ascii=False, indent=1), compress_type=zipfile.ZIP_DEFLATED)
            for nom in sorted(garder):
                entree = zipfile.ZipInfo(nom, date_time=_DATE_ZIP)
                dst.writestr(entree, z.read(nom), compress_type=zipfile.ZIP_DEFLATED)
    return neuf


def position_cible(cx: float, cy: float, azimut_mag_deg: float, distance_m: float,
                   declinaison_deg: float, convergence_deg: float) -> tuple[float, float]:
    """Lambert-93 (x, y) of a target from the C0: magnetic azimuth -> geographic -> grid."""
    from groma.azimut import geographique_vers_grille, magnetique_vers_geographique

    az = math.radians(geographique_vers_grille(magnetique_vers_geographique(azimut_mag_deg, declinaison_deg), convergence_deg))
    return cx + distance_m * math.sin(az), cy + distance_m * math.cos(az)


# ---- real project ----------------------------------------------------------------


def _lignes(cx: sqlite3.Connection, sql: str, params: Sequence = ()) -> list[dict]:
    cur = cx.execute(sql, params)
    noms = [d[0] for d in cur.description]
    return [dict(zip(noms, r)) for r in cur.fetchall()]


def lire_projet_reel(gpkg: Path, centre: tuple[float, float]) -> dict:
    """Plot row, measured C0 row (the one at ``centre``, 1 cm) and project row of the real GeoPackage."""
    from groma.gpkg import decoder_point

    with ouvrir_ro(gpkg) as cx:
        placette = _lignes(cx, "SELECT * FROM placettes WHERE plot_id = ?", (PLOT_ID,))
        centres = _lignes(cx, "SELECT * FROM centres_mesures WHERE plot_id = ? ORDER BY date_mesure DESC", (PLOT_ID,))
        projet = _lignes(cx, "SELECT * FROM projet")
    if len(placette) != 1:
        raise ValueError(f"{gpkg} : {len(placette)} placette(s) {PLOT_ID}.")
    c0 = [c for c in centres if math.dist(decoder_point(c["geom"]), centre) < 0.01]
    if not c0:
        raise ValueError(f"{gpkg} : aucun centre de {PLOT_ID} au C0 du paquet {centre}.")
    return {"placette": placette[0], "centre": c0[0], "projet": projet[0] if projet else {}}


def polygone_au_centre(gpkg: Path, couche: str, centre, carre):
    """The feature of ``couche`` holding the C0, clipped to ``carre`` (GeoDataFrame of one
    row), or ``None`` when no feature holds it (never replaced by an invented outline)."""
    import pyogrio
    from shapely.geometry import Point

    g = pyogrio.read_dataframe(gpkg, layer=couche, bbox=carre.bounds)
    g = g[g.geometry.contains(Point(centre))]
    if len(g) == 0:
        voisins = pyogrio.read_dataframe(gpkg, layer=couche, bbox=carre.buffer(500).bounds)
        d = float(voisins.distance(Point(centre)).min()) if len(voisins) else float("nan")
        log.warning("%s : aucune entité de %s ne contient le C0 (la plus proche à %.1f m) ; couche laissée vide.",
                    gpkg, couche, d)
        return None
    if len(g) > 1:
        raise ValueError(f"{gpkg} : {len(g)} entités de {couche} au C0, une attendue.")
    g = g.copy()
    couverture = g.geometry.iloc[0].intersection(carre).area / carre.area
    if couverture < 0.999:
        log.warning("%s : %s ne couvre que %.1f %% du carré de %g m.", gpkg, couche, 100 * couverture, 2 * DEMI_CARRE_M)
    g["geometry"] = g.geometry.intersection(carre)
    return g.drop(columns=[c for c in ("fid",) if c in g.columns])


# ---- main ------------------------------------------------------------------------

# GeoPackage timestamp (ISO 8601 UTC, GeoPackage format) of the example, fixed.
HORODATAGE_GPKG = "2026-11-21T12:00:00.000Z"


def figer_horodatage(gpkg: Path, horodatage: str = HORODATAGE_GPKG) -> None:
    """Set ``gpkg_contents.last_change`` (stamped by GDAL at write time) to a fixed
    value and rewrite the file with ``VACUUM INTO``: a fresh file whose header
    change counter no longer depends on how many transactions GDAL made, so that
    two runs give the same bytes. Only the example being written is touched."""
    with sqlite3.connect(gpkg) as cx:
        cx.execute("UPDATE gpkg_contents SET last_change = ?", (horodatage,))
    cx.close()
    neuf = gpkg.with_suffix(".neuf.gpkg")
    neuf.unlink(missing_ok=True)
    cx = sqlite3.connect(gpkg)
    try:
        cx.execute("VACUUM INTO ?", (str(neuf),))
    finally:
        cx.close()
    neuf.replace(gpkg)


def construire(mnt: Path, mnh: Path, gpkg: Path, paquet: Path, sortie: Path = SORTIE,
               cvat: Path | None = None, pas_images: int = PAS_IMAGES) -> Path:
    import geopandas as gpd
    import shapely
    from shapely.geometry import Point, box

    from groma.gpkg import charger_schema, creer_gpkg, noms_colonnes
    from groma.mnt import ecrire_geotiff, pente_exposition, recouvrement_pct

    schema = charger_schema()
    r = schema["rasters"]
    res = float(r["resolution_m"])

    with zipfile.ZipFile(paquet) as z:
        manifeste = json.loads(z.read("manifest.json"))
    if manifeste["plot_id"] != PLOT_ID:
        raise ValueError(f"{paquet} : placette {manifeste['plot_id']}, {PLOT_ID} attendue.")
    cx, cy = float(manifeste["centre"]["x"]), float(manifeste["centre"]["y"])
    reel = lire_projet_reel(gpkg, (cx, cy))
    p_reel, c_reel, projet_reel = reel["placette"], reel["centre"], reel["projet"]
    params = manifeste["placette"]
    rayon = float(p_reel["rayon_m"] or params["rayon_m"])
    if not math.isclose(rayon, float(params["rayon_m"])):
        raise ValueError(f"Rayon du projet ({rayon}) différent de celui du paquet ({params['rayon_m']}).")

    # Generated parts replaced; README.md (written by hand) kept.
    for d in ("rasters", "controle", "paquets"):
        shutil.rmtree(sortie / d, ignore_errors=True)
    (sortie / "rasters").mkdir(parents=True)
    (sortie / "controle").mkdir()

    # Forest extent: 100 m square around the C0 plus marge_foret_m. The real UGF and
    # parcel holding the C0 are clipped to the square; when none holds it (P067 was
    # filmed about 107 m outside the communal forest) both layers stay empty and the
    # tile still covers the square, a superset of the schema fallback (plot + marge_m).
    carre = box(cx - DEMI_CARRE_M, cy - DEMI_CARRE_M, cx + DEMI_CARRE_M, cy + DEMI_CARRE_M)
    ugf = polygone_au_centre(gpkg, "ugf", (cx, cy), carre)
    if ugf is not None:
        ugf["surface_m2"] = round(float(ugf.geometry.iloc[0].area), 1)  # SIG area of the clipped unit
    parcelles = polygone_au_centre(gpkg, "parcelles", (cx, cy), carre)  # contenance_m2: cadastral, kept
    base = shapely.union_all(list(ugf.geometry)) if ugf is not None else carre
    emprise = base.buffer(float(r["dalles"]["marge_foret_m"]))
    xmin, ymin, xmax, ymax = emprise.bounds

    # 1 m sources on the extent (+2 m so that bilinear sampling has its neighbours).
    mnt_1m = lire_fenetre(mnt, xmin - 2, ymin - 2, xmax + 2, ymax + 2)
    mnh_1m = lire_fenetre(mnh, xmin - 2, ymin - 2, xmax + 2, ymax + 2)
    ecrire_geotiff(mnt_1m, sortie / "controle" / "mnt_1m.tif")
    ecrire_dalle(mnt_1m, emprise, sortie)

    demi = rayon + float(r["marge_m"])
    mnt_p = raster_placette(mnt_1m, cx, cy, demi, res)
    mnh_p = raster_placette(mnh_1m, cx, cy, demi, res)
    ecrire_geotiff(mnt_p, sortie / r["chemins"]["mnt"].format(plot_id=PLOT_ID))
    ecrire_geotiff(mnh_p, sortie / r["chemins"]["mnh"].format(plot_id=PLOT_ID))
    if cvat is not None:
        decouper_cvat(cvat, emprise, sortie / r["cvat"]["chemin"])

    # Crowns computed by Groma (not nemeton) on the 1 m CHM.
    disque = Point(cx, cy).buffer(rayon, 64)
    houppiers = segmenter_houppiers(mnh_1m, emprise)
    for h in houppiers:
        h["plot_id"] = PLOT_ID if h["geometry"].intersects(disque) else None

    # Plot centred on the measured C0; plan attributes of the real project.
    convergence = float(p_reel["convergence_deg"] if p_reel["convergence_deg"] is not None else params["convergence_deg"])
    declinaison = float(params["declinaison_deg"])  # value used on the field (NULL in the project)
    pente, exposition = pente_exposition(mnt_p, cx, cy, rayon, convergence)
    attributs = {k: p_reel[k] for k in ("plot_id", "visit_order", "type", "wgt", "ip", "stratum", "strat_height",
                                        "strat_type", "strat_topo", "rayon_m", "seuil_diam_cm", "unite_azimut")}
    attributs |= {"declinaison_deg": declinaison, "convergence_deg": convergence, "pente_pct": pente,
                  "exposition": exposition, "recouvrement_pct": recouvrement_pct(mnh_p, cx, cy, rayon)}
    placettes = gpd.GeoDataFrame([attributs], geometry=[Point(cx, cy)], crs=2154)

    centre = {k: c_reel[k] for k in noms_colonnes("centres_mesures") if k in c_reel}
    centre["ecart_prevu_m"] = 0.0  # the example plot is placed on the C0 (151.9 m in the real project)
    centres = gpd.GeoDataFrame([centre], geometry=[Point(cx, cy)], crs=2154)

    cibles = []
    for c in sorted(manifeste["cibles_mesurees"], key=lambda c: c["id_cible"]):
        x, y = position_cible(cx, cy, c["azimut_mesure"], c["distance_mesuree_m"], declinaison, convergence)
        cibles.append({"plot_id": PLOT_ID, "id_cible": c["id_cible"], "azimut_mesure": c["azimut_mesure"],
                       "distance_mesuree_m": c["distance_mesuree_m"], "dbh_mesure_cm": c.get("dbh_mesure_cm"),
                       "source_azimut": c.get("source_azimut", "boussole_visee"), "geometry": Point(x, y)})

    creer_gpkg(
        sortie / "inventaire_video.gpkg",
        placettes,
        houppiers=gpd.GeoDataFrame(houppiers, crs=2154),
        cibles=gpd.GeoDataFrame(cibles, crs=2154),
        centres_mesures=centres,
        parcelles=parcelles,
        ugf=ugf,
        projet={
            "projet_id": PROJET_ID,
            "projet_nom": "Marsannay-la-Côte, P067 (exemple réel)",
            "description": (
                "Exemple réel de Groma (mission 044) : placette P067 du plan de la forêt communale de "
                "Marsannay-la-Côte, filmée le 2026-11-20, centrée sur le C0 mesuré "
                + ("; UGF et parcelle réelles découpées sur 100 m" if ugf is not None else
                   "(hors des UGF et parcelles du projet : couches vides)")
                + " ; MNT LiDAR HD rééchantillonné à 0,5 m ; houppiers et sommets calculés par Groma "
                "(exemples/creer_marsannay_p067.py), pas par nemeton."
            ),
            "communes": projet_reel.get("communes"),
            "surface_zone_ha": round(float(ugf.geometry.iloc[0].area) / 10_000, 2) if ugf is not None else None,
            "nb_parcelles": 0 if parcelles is None else len(parcelles),
            "nb_ug": 0 if ugf is None else len(ugf),
            "plan_genere_le": projet_reel.get("plan_genere_le"),
            "prepare_le": DATE_PREPARATION,
            "prepare_par": "exemples/creer_marsannay_p067.py",
        },
    )
    figer_horodatage(sortie / "inventaire_video.gpkg")

    alleger_paquet(paquet, sortie / "paquets" / f"{PLOT_ID}.groma.zip", pas_images)
    log.info("Exemple écrit dans %s : %d houppiers, emprise %s", sortie, len(houppiers), emprise.bounds)
    return sortie


def main(argv: Sequence[str] | None = None) -> None:
    a = argparse.ArgumentParser(description="Exemple réel P067 de Marsannay (schéma 1.7.0), sources lues sans écriture.")
    a.add_argument("--mnt", type=Path, default=MNT_DEFAUT, help=f"MNT LiDAR HD 1 m (défaut : {MNT_DEFAUT})")
    a.add_argument("--mnh", type=Path, default=MNH_DEFAUT, help=f"MNH LiDAR HD 1 m (défaut : {MNH_DEFAUT})")
    a.add_argument("--gpkg", type=Path, default=GPKG_DEFAUT, help=f"GeoPackage du projet réel (défaut : {GPKG_DEFAUT})")
    a.add_argument("--paquet", type=Path, default=PAQUET_DEFAUT, help=f"paquet de terrain (défaut : {PAQUET_DEFAUT})")
    a.add_argument("--cvat", type=Path, default=None, help="relief CVAT de nemetonshiny (Byte, 2 m ; nodata 0, ou 255 codage foretaccess), facultatif")
    a.add_argument("--sortie", type=Path, default=SORTIE, help=f"dossier écrit, parties générées remplacées, README gardé (défaut : {SORTIE})")
    a.add_argument("--pas-images", type=int, default=PAS_IMAGES, help="une image sur N gardée dans le paquet (N >= 10)")
    args = a.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    for p in (args.mnt, args.mnh, args.gpkg, args.paquet) + ((args.cvat,) if args.cvat else ()):
        if not p.is_file():
            a.error(f"source absente : {p}")
    sources = {p.resolve() for p in (args.mnt, args.mnh, args.gpkg, args.paquet, args.cvat) if p}
    if any(args.sortie.resolve() in (s, *s.parents) for s in sources):
        a.error("le dossier de sortie contient une source : refusé (lecture seule)")
    construire(args.mnt, args.mnh, args.gpkg, args.paquet, args.sortie, args.cvat, args.pas_images)


if __name__ == "__main__":
    main()
